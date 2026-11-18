package com.pharmabill.app.ai.count

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import com.pharmabill.app.BuildConfig
import com.pharmabill.app.domain.model.Detection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/** Everything that counts goes through this interface, so the model can be replaced or missing. */
interface CountingEngine {
    val version: String
    val isAvailable: Boolean
    suspend fun detect(bitmap: Bitmap): List<Detection>
}

/** No model shipped yet: returns nothing, so every count is yellow and entered by hand. */
class NoModelCountingEngine : CountingEngine {
    override val version: String = "none"
    override val isAvailable: Boolean = false
    override suspend fun detect(bitmap: Bitmap): List<Detection> = emptyList()
}

data class ModelMeta(
    val version: String,
    val inputSize: Int,
    val classNames: List<String>,
    val confThreshold: Float,
    val iouThreshold: Float,
) {
    companion object {
        val DEFAULT = ModelMeta("unknown", 640, listOf("tablet", "capsule", "box"), 0.40f, 0.5f)

        fun parse(json: String?): ModelMeta {
            if (json.isNullOrBlank()) return DEFAULT
            return try {
                val o = JSONObject(json)
                val names = o.optJSONArray("class_names")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: DEFAULT.classNames
                ModelMeta(
                    version = o.optString("version", DEFAULT.version),
                    inputSize = o.optInt("input_size", DEFAULT.inputSize),
                    classNames = names,
                    confThreshold = o.optDouble("conf_threshold", DEFAULT.confThreshold.toDouble()).toFloat(),
                    iouThreshold = o.optDouble("iou_threshold", DEFAULT.iouThreshold.toDouble()).toFloat(),
                )
            } catch (e: Exception) {
                DEFAULT
            }
        }
    }
}

/**
 * YOLOv8n / YOLO11n exported to TFLite (float16, float32 or int8). Input 640x640 RGB letterboxed,
 * output [1, 4 + classes, N] or [1, N, 4 + classes]. Confidence >= 0.40 and NMS IoU 0.5 done in Kotlin.
 */
class TfliteYoloCountingEngine private constructor(
    private val interpreter: Interpreter,
    private val meta: ModelMeta,
) : CountingEngine {
    override val version: String = meta.version
    override val isAvailable: Boolean = true
    private val lock = Mutex()

    override suspend fun detect(bitmap: Bitmap): List<Detection> = withContext(Dispatchers.Default) {
        lock.withLock {
            val inputTensor = interpreter.getInputTensor(0)
            val shape = inputTensor.shape() // [1, H, W, 3]
            val size = if (shape.size == 4) shape[1] else meta.inputSize
            val lb = Letterbox.compute(bitmap.width, bitmap.height, size)
            val input = toInput(bitmap, lb, size, inputTensor.dataType(), inputTensor.quantizationParams().scale, inputTensor.quantizationParams().zeroPoint)

            val outTensor = interpreter.getOutputTensor(0)
            val outShape = outTensor.shape()
            val out = ByteBuffer.allocateDirect(outTensor.numBytes()).order(ByteOrder.nativeOrder())
            interpreter.run(input, out)
            out.rewind()
            val count = outShape.fold(1) { a, b -> a * b }
            val floats = FloatArray(count)
            when (outTensor.dataType()) {
                DataType.FLOAT32 -> out.asFloatBuffer().get(floats)
                DataType.UINT8 -> {
                    val q = outTensor.quantizationParams()
                    for (i in 0 until count) floats[i] = ((out.get().toInt() and 0xFF) - q.zeroPoint) * q.scale
                }
                DataType.INT8 -> {
                    val q = outTensor.quantizationParams()
                    for (i in 0 until count) floats[i] = (out.get().toInt() - q.zeroPoint) * q.scale
                }
                else -> error("Unsupported output type ${outTensor.dataType()}")
            }
            val numClasses = (if (outShape[1] < outShape[2]) outShape[1] else outShape[2]) - 4
            val raw = YoloDecoder.decode(floats, outShape, numClasses, meta.confThreshold, lb, bitmap.width, bitmap.height)
            Nms.apply(raw, meta.iouThreshold)
        }
    }

    private fun toInput(src: Bitmap, lb: Letterbox, size: Int, type: DataType, scale: Float, zeroPoint: Int): ByteBuffer {
        val boxed = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(boxed)
        canvas.drawColor(Color.rgb(114, 114, 114))
        val dst = RectF(lb.padX, lb.padY, lb.padX + src.width * lb.scale, lb.padY + src.height * lb.scale)
        canvas.drawBitmap(src, Rect(0, 0, src.width, src.height), dst, Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(size * size)
        boxed.getPixels(pixels, 0, size, 0, 0, size, size)
        boxed.recycle()
        val bytesPer = if (type == DataType.FLOAT32) 4 else 1
        val buf = ByteBuffer.allocateDirect(size * size * 3 * bytesPer).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            when (type) {
                DataType.FLOAT32 -> { buf.putFloat(r); buf.putFloat(g); buf.putFloat(b) }
                DataType.UINT8 -> for (v in floatArrayOf(r, g, b)) buf.put((v / scale + zeroPoint).toInt().coerceIn(0, 255).toByte())
                DataType.INT8 -> for (v in floatArrayOf(r, g, b)) buf.put((v / scale + zeroPoint).toInt().coerceIn(-128, 127).toByte())
                else -> error("Unsupported input type $type")
            }
        }
        buf.rewind()
        return buf
    }

    fun close() = interpreter.close()

    companion object {
        const val MODEL_ASSET = "models/counter.tflite"
        const val META_ASSET = "models/model_meta.json"

        private fun options() = Interpreter.Options().apply { setNumThreads(4) }

        fun fromAssets(context: Context): TfliteYoloCountingEngine? = try {
            val fd = context.assets.openFd(MODEL_ASSET)
            val buffer: MappedByteBuffer = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            val meta = ModelMeta.parse(runCatching { context.assets.open(META_ASSET).bufferedReader().use { it.readText() } }.getOrNull())
            TfliteYoloCountingEngine(Interpreter(buffer, options()), meta)
        } catch (e: Throwable) {
            null
        }

        fun fromFile(model: File, metaJson: String?, fallbackVersion: String): TfliteYoloCountingEngine? = try {
            val buffer = FileInputStream(model).channel.use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) }
            val parsed = ModelMeta.parse(metaJson)
            val meta = if (metaJson == null) parsed.copy(version = fallbackVersion) else parsed
            TfliteYoloCountingEngine(Interpreter(buffer, options()), meta)
        } catch (e: Throwable) {
            null
        }
    }
}

/**
 * Picks the engine at startup: bundled YOLO model if it exists and loads, otherwise no-model mode.
 * In debug builds a model loaded from storage overrides the bundled one without a rebuild.
 */
@Singleton
class CountingEngineHolder @Inject constructor(@ApplicationContext private val context: Context) {
    private val debugDir = File(context.filesDir, "models")
    private val debugModel = File(debugDir, "debug_counter.tflite")
    private val debugMeta = File(debugDir, "debug_model_meta.json")

    @Volatile var engine: CountingEngine = pick()
        private set

    private val _version = MutableStateFlow(engine.version)
    val version: StateFlow<String> = _version

    val isDebugOverride: Boolean get() = BuildConfig.DEBUG && debugModel.exists()

    private fun pick(): CountingEngine {
        if (BuildConfig.DEBUG && debugModel.exists()) {
            val meta = if (debugMeta.exists()) debugMeta.readText() else null
            TfliteYoloCountingEngine.fromFile(debugModel, meta, "debug")?.let { return it }
        }
        return TfliteYoloCountingEngine.fromAssets(context) ?: NoModelCountingEngine()
    }

    private fun swap() {
        val old = engine
        engine = pick()
        _version.value = engine.version
        (old as? TfliteYoloCountingEngine)?.close()
    }

    /** Debug builds only: copy a .tflite (and optional model_meta.json) from storage and switch to it. */
    suspend fun loadFromStorage(model: Uri, meta: Uri?): Boolean = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) return@withContext false
        debugDir.mkdirs()
        context.contentResolver.openInputStream(model)?.use { input -> debugModel.outputStream().use { input.copyTo(it) } } ?: return@withContext false
        if (meta != null) {
            context.contentResolver.openInputStream(meta)?.use { input -> debugMeta.outputStream().use { input.copyTo(it) } }
        } else {
            debugMeta.delete()
            debugMeta.writeText("""{"version":"debug-${System.currentTimeMillis() / 1000}"}""")
        }
        swap()
        engine is TfliteYoloCountingEngine
    }

    suspend fun resetToBundled() = withContext(Dispatchers.IO) {
        debugModel.delete()
        debugMeta.delete()
        swap()
    }
}
