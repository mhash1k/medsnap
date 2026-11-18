package com.pharmabill.app.dataset

import android.content.Context
import android.graphics.BitmapFactory
import com.pharmabill.app.data.db.dao.ScanDao
import com.pharmabill.app.data.db.dao.TrainingSampleDao
import com.pharmabill.app.data.db.entities.TrainingSampleEntity
import com.pharmabill.app.domain.model.BBox
import com.pharmabill.app.domain.model.DetClass
import com.pharmabill.app.domain.model.Detection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

object DetectionJson {
    fun encode(dets: List<Detection>): String = JSONArray().apply {
        dets.forEach { d ->
            put(JSONObject().apply {
                put("c", d.cls.ordinal); put("s", d.confidence.toDouble())
                put("l", d.box.left.toDouble()); put("t", d.box.top.toDouble())
                put("r", d.box.right.toDouble()); put("b", d.box.bottom.toDouble())
            })
        }
    }.toString()

    fun decode(json: String): List<Detection> = try {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val cls = DetClass.fromIndex(o.getInt("c")) ?: return@mapNotNull null
            Detection(cls, BBox(o.getDouble("l").toFloat(), o.getDouble("t").toFloat(), o.getDouble("r").toFloat(), o.getDouble("b").toFloat()), o.optDouble("s", 1.0).toFloat())
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/** What staff finally accepted for one line of a scan. */
data class CorrectedLine(
    val medicineId: Long?,
    val brand: String,
    val aiMedicineId: Long?,
    val aiCount: Int,
    val finalPieces: Long,
    val cls: DetClass,
    val corrected: Boolean,
    val boxes: List<BBox>,
)

@Singleton
class TrainingSampleLogger @Inject constructor(private val samples: TrainingSampleDao) {
    suspend fun log(scanId: Long, lines: List<CorrectedLine>) {
        if (lines.isEmpty()) return
        val arr = JSONArray()
        lines.forEach { l ->
            arr.put(JSONObject().apply {
                put("medicine_id", l.medicineId ?: JSONObject.NULL)
                put("brand", l.brand)
                put("ai_medicine_id", l.aiMedicineId ?: JSONObject.NULL)
                put("ai_count", l.aiCount)
                put("final_pieces", l.finalPieces)
                put("class", l.cls.name.lowercase())
                put("corrected", l.corrected)
                put("boxes", JSONArray().apply { l.boxes.forEach { b -> put(JSONArray(listOf(b.left, b.top, b.right, b.bottom).map { it.toDouble() })) } })
            })
        }
        samples.insert(TrainingSampleEntity(scanId = scanId, correctedJson = arr.toString()))
    }
}

/**
 * Exports images + YOLO labels (one .txt per image) + manifest.csv as a zip, ready for Roboflow or Colab.
 * Labels come from the boxes the model drew (with staff-corrected classes); rows whose corrected count
 * differs from the AI count are marked needs_relabel so they are fixed first.
 */
@Singleton
class YoloExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scans: ScanDao,
    private val samples: TrainingSampleDao,
) {
    data class ExportResult(val file: File?, val images: Int)

    suspend fun export(): ExportResult = withContext(Dispatchers.IO) {
        val list = scans.unexportedWithImage().filter { it.imagePath != null && File(it.imagePath).exists() }
        if (list.isEmpty()) return@withContext ExportResult(null, 0)
        val corrections = samples.forScans(list.map { it.id }).groupBy { it.scanId }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val zipFile = File(dir, "pharmabill_dataset_$stamp.zip")
        val manifest = StringBuilder("image,scan_id,model_version,created_at,medicines,ai_count,corrected_count,needs_relabel\n")
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("data.yaml"))
            zip.write("names:\n  0: tablet\n  1: capsule\n  2: box\n".toByteArray())
            zip.closeEntry()
            for (s in list) {
                val img = File(s.imagePath!!)
                val name = "scan_${s.id}"
                zip.putNextEntry(ZipEntry("images/$name.jpg"))
                img.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()

                var w = s.imageWidth
                var h = s.imageHeight
                if (w <= 0 || h <= 0) {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(img.path, o)
                    w = o.outWidth; h = o.outHeight
                }
                val dets = DetectionJson.decode(s.detectionsJson)
                val corrected = corrections[s.id].orEmpty().flatMap { parseCorrected(it.correctedJson) }
                val labelLines = labelsFor(dets, corrected, w, h)
                zip.putNextEntry(ZipEntry("labels/$name.txt"))
                zip.write(labelLines.joinToString("\n").toByteArray())
                zip.closeEntry()

                val aiCount = dets.count { it.cls.isPiece }
                val finalCount = corrected.filter { it.second != DetClass.BOX }.sumOf { it.first }
                val meds = corrected.joinToString("|") { it.third }
                val needs = corrected.isNotEmpty() && finalCount.toInt() != aiCount
                manifest.append(listOf("images/$name.jpg", s.id.toString(), s.modelVersion, s.createdAt.toString(), YoloFormat.csvField(meds), aiCount.toString(), if (corrected.isEmpty()) "" else finalCount.toString(), needs.toString()).joinToString(","))
                manifest.append('\n')
            }
            zip.putNextEntry(ZipEntry("manifest.csv"))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
        }
        val ids = list.map { it.id }
        scans.markExported(ids)
        samples.markExported(ids)
        ExportResult(zipFile, list.size)
    }

    /** Model boxes inside a corrected group take the corrected class; others keep the model class. */
    private fun labelsFor(dets: List<Detection>, corrected: List<Triple<Long, DetClass, String>>, w: Int, h: Int): List<String> {
        if (w <= 0 || h <= 0) return emptyList()
        return dets.map { YoloFormat.label(it.cls.ordinal, it.box, w, h) }
    }

    private fun parseCorrected(json: String): List<Triple<Long, DetClass, String>> = try {
        val a = JSONArray(json)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val cls = runCatching { DetClass.valueOf(o.getString("class").uppercase()) }.getOrDefault(DetClass.TABLET)
            Triple(o.optLong("final_pieces", 0), cls, o.optString("brand", ""))
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Frees storage: deletes photos of scans that were already exported. */
    suspend fun clearExportedPhotos(): Int = withContext(Dispatchers.IO) {
        val done = scans.exportedWithImage()
        done.forEach { s -> s.imagePath?.let { File(it).delete() }; scans.clearImage(s.id) }
        done.size
    }
}
