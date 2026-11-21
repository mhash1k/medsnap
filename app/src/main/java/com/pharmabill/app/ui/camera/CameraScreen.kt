@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.R
import com.pharmabill.app.ui.components.PbBtnShape
import com.pharmabill.app.ai.image.ImageQuality
import com.pharmabill.app.data.prefs.SettingsStore
import com.pharmabill.app.ui.theme.FlagColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject

data class FrameQuality(val blurry: Boolean = false, val glare: Boolean = false)

@HiltViewModel
class CameraViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    val oneMedicine: StateFlow<Boolean> = settings.settings.map { it.oneMedicineMode }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _quality = MutableStateFlow(FrameQuality())
    val quality: StateFlow<FrameQuality> = _quality
    private var lastCheck = 0L

    fun toggleOneMedicine() { viewModelScope.launch { settings.setOneMedicineMode(!oneMedicine.value) } }

    /** Samples the Y plane on a coarse grid twice a second for blur and glare warnings. */
    fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastCheck < 500) return
        lastCheck = now
        val plane = image.planes[0]
        val buf = plane.buffer
        val w = 160
        val h = 120
        val lum = IntArray(w * h)
        val sx = image.width / w.toFloat()
        val sy = image.height / h.toFloat()
        for (y in 0 until h) for (x in 0 until w) {
            val idx = (y * sy).toInt() * plane.rowStride + (x * sx).toInt() * plane.pixelStride
            lum[y * w + x] = if (idx < buf.limit()) buf.get(idx).toInt() and 0xFF else 0
        }
        _quality.value = FrameQuality(
            blurry = ImageQuality.laplacianVariance(lum, w, h) < PREVIEW_BLUR_MIN,
            glare = ImageQuality.hasGlare(lum),
        )
    }

    companion object {
        const val PREVIEW_BLUR_MIN = 25.0
    }
}

private fun saveCapture(context: Context, image: ImageProxy): String {
    val rotation = image.imageInfo.rotationDegrees
    var bmp = image.toBitmap()
    image.close()
    if (rotation != 0) {
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
    val dir = File(context.filesDir, "scans").apply { mkdirs() }
    val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    bmp.recycle()
    return file.absolutePath
}

@Composable
fun CameraScreen(onCaptured: (String) -> Unit, onBack: () -> Unit, vm: CameraViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) CameraContent(vm, onCaptured) else {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.camera_permission), color = Color.White)
                Button(shape = PbBtnShape, onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.grant)) }
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
        }
    }
}

@Composable
private fun CameraContent(vm: CameraViewModel, onCaptured: (String) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val quality by vm.quality.collectAsStateWithLifecycle()
    val oneMedicine by vm.oneMedicine.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val worker = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(worker) { proxy -> try { vm.analyze(proxy) } finally { proxy.close() } }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
            } catch (e: Exception) {
                error = true
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            runCatching { future.get().unbindAll() }
            worker.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        // Frame guide
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width * 0.86f
            val h = size.height * 0.55f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset((size.width - w) / 2, (size.height - h) / 2.4f),
                size = Size(w, h),
                cornerRadius = CornerRadius(24f, 24f),
                style = Stroke(width = 4f),
            )
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = 64.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.camera_guide), color = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.45f)).padding(8.dp))
            if (quality.glare) Warning(stringResource(R.string.warn_glare))
            if (quality.blurry) Warning(stringResource(R.string.warn_blur))
            if (error) Warning(stringResource(R.string.camera_error))
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = oneMedicine, onClick = vm::toggleOneMedicine, label = { Text(stringResource(R.string.one_medicine_mode), color = Color.White) })
            }
            Surface(
                onClick = {
                    if (!busy) {
                    busy = true
                    imageCapture.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val path = runCatching { saveCapture(ctx, image) }.getOrNull()
                            ContextCompat.getMainExecutor(ctx).execute {
                                busy = false
                                if (path != null) onCaptured(path) else error = true
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            ContextCompat.getMainExecutor(ctx).execute { busy = false; error = true }
                        }
                    })
                    }
                },
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp).size(84.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator() else Text(stringResource(R.string.shutter), style = MaterialTheme.typography.labelLarge, color = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun Warning(text: String) {
    Text(text, color = Color.Black, modifier = Modifier.background(FlagColors.yellow).padding(horizontal = 12.dp, vertical = 6.dp))
}
