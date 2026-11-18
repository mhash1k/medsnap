package com.pharmabill.app.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pharmabill.app.ai.count.CountingEngineHolder
import com.pharmabill.app.ai.group.DetectionGrouper
import com.pharmabill.app.ai.group.NameHit
import com.pharmabill.app.ai.group.ScanAssembler
import com.pharmabill.app.ai.group.ScanLine
import com.pharmabill.app.ai.ocr.TextNormalizer
import com.pharmabill.app.ai.ocr.TextRecognizer
import com.pharmabill.app.ai.voice.VoiceParse
import com.pharmabill.app.ai.voice.VoiceParser
import com.pharmabill.app.data.db.entities.BillLineEntity
import com.pharmabill.app.data.db.entities.ScanEntity
import com.pharmabill.app.data.prefs.SettingsStore
import com.pharmabill.app.data.repo.BillRepository
import com.pharmabill.app.data.repo.ConfirmResult
import com.pharmabill.app.data.repo.MatcherProvider
import com.pharmabill.app.data.repo.ScanRepository
import com.pharmabill.app.data.repo.StockRepository
import com.pharmabill.app.dataset.DetectionJson
import com.pharmabill.app.domain.flags.FlagEvaluator
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.LineSource
import com.pharmabill.app.domain.model.MedicineInfo
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.pricing.PriceCalculator
import com.pharmabill.app.domain.pricing.QuantityResolver
import com.pharmabill.app.domain.stock.StockState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ScanOutput(
    val scanId: Long,
    val imagePath: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val lines: List<ScanLine>,
    val modelVersion: String,
    val modelAvailable: Boolean,
    val elapsedMs: Long,
)

/** Photo -> OCR + detection in parallel -> match -> group -> quantity + flags. */
@Singleton
class ScanPhotoUseCase @Inject constructor(
    private val recognizer: TextRecognizer,
    private val engines: CountingEngineHolder,
    private val matcher: MatcherProvider,
    private val settings: SettingsStore,
    private val scans: ScanRepository,
) {
    suspend operator fun invoke(path: String, oneMedicineOverride: Boolean? = null): ScanOutput = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        val bitmap = decode(path)
        val s = settings.current()
        val engine = engines.engine
        val (blocks, dets) = coroutineScope {
            val ocr = async { runCatching { recognizer.recognize(bitmap) }.getOrDefault(emptyList()) }
            val det = async { runCatching { engine.detect(bitmap) }.getOrDefault(emptyList()) }
            ocr.await() to det.await()
        }
        val snap = matcher.current()
        val fullText = blocks.joinToString(" ") { it.text }
        val names = blocks.mapNotNull { b ->
            val useful = b.lines.filterNot { TextNormalizer.isNoise(it.text) }
            if (useful.isEmpty()) return@mapNotNull null
            val text = useful.joinToString(" ") { it.text }
            val cands = snap.matcher.matchOcrBlock(text, fullText)
            if (cands.isEmpty()) null else NameHit(b.box, cands, text)
        }
        val oneMode = oneMedicineOverride ?: s.oneMedicineMode
        val grouped = DetectionGrouper.group(dets, names, bitmap.width, bitmap.height, oneMode)
        val lines = ScanAssembler(FlagEvaluator(s.thresholds)) { id -> snap.medicines[id]?.toInfo() }
            .assemble(grouped, engine.isAvailable)
            .let { l -> if (l.isEmpty()) listOf(emptyLine(engine.isAvailable)) else l }

        val ocrJson = JSONArray().apply {
            blocks.forEach { b ->
                put(JSONObject().apply {
                    put("text", b.text)
                    put("box", JSONArray(listOf(b.box.left, b.box.top, b.box.right, b.box.bottom).map { it.toDouble() }))
                })
            }
        }.toString()
        val scanId = scans.insert(
            ScanEntity(
                imagePath = if (s.collectTrainingData) path else null,
                imageWidth = bitmap.width, imageHeight = bitmap.height,
                modelVersion = engine.version, ocrRawJson = ocrJson, detectionsJson = DetectionJson.encode(dets),
            ),
        )
        ScanOutput(scanId, path, bitmap.width, bitmap.height, lines, engine.version, engine.isAvailable, System.currentTimeMillis() - start)
    }

    /** Nothing recognised at all: one red line so staff can search or quick-add. */
    @Suppress("UNUSED_PARAMETER")
    private fun emptyLine(modelAvailable: Boolean) = ScanLine(
        null, emptyList(), null, 0, false, 0f, 0.0, SaleUnit.PIECE, 1, Flag.RED, null, emptyList(),
    )

    private fun decode(path: String): Bitmap {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / sample > 2400) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Cannot read photo ${File(path).name}")
    }
}

/** Builds a priced bill line from a medicine + unit + quantity. Any manual fix produces a green line. */
object LineFactory {
    fun build(
        billId: Long,
        info: MedicineInfo,
        unit: SaleUnit,
        quantity: Int,
        flag: Flag,
        source: LineSource,
        scanId: Long? = null,
        ocrText: String? = null,
        detectedCount: Int = 0,
        confidence: Double = 1.0,
        candidates: List<Long> = emptyList(),
        isBox: Boolean = false,
        existingId: Long = 0,
    ): BillLineEntity {
        val q = quantity.coerceAtLeast(1)
        return BillLineEntity(
            id = existingId,
            billId = billId,
            medicineId = info.id,
            saleUnit = unit,
            quantity = q,
            qtyInStockUnits = QuantityResolver.stockUnits(info, unit, q),
            unitPrice = PriceCalculator.unitPriceFor(info, unit),
            lineTotal = PriceCalculator.linePrice(info, unit, q),
            flag = flag,
            source = source,
            scanId = scanId,
            ocrText = ocrText,
            detectedCount = detectedCount,
            confidence = confidence,
            candidateIds = candidates.joinToString(","),
            displayName = info.displayName,
            isBox = isBox,
        )
    }

    /** Re-prices an existing line after a medicine, unit or quantity change. */
    fun reprice(line: BillLineEntity, info: MedicineInfo, unit: SaleUnit, quantity: Int, flag: Flag, isBox: Boolean = line.isBox) =
        build(
            line.billId, info, unit, quantity, flag, line.source, line.scanId, line.ocrText, line.detectedCount,
            line.confidence, line.candidates, isBox, line.id,
        ).copy(createdAt = line.createdAt)
}

@Singleton
class AddLineUseCase @Inject constructor(private val bills: BillRepository, private val matcher: MatcherProvider) {
    suspend operator fun invoke(
        billId: Long,
        medicineId: Long,
        unit: SaleUnit,
        quantity: Int,
        flag: Flag,
        source: LineSource,
        candidates: List<Long> = emptyList(),
        scanId: Long? = null,
        ocrText: String? = null,
        detectedCount: Int = 0,
        confidence: Double = 1.0,
        isBox: Boolean = false,
    ): Long? {
        val info = matcher.infoOf(medicineId) ?: matcher.current().medicines[medicineId]?.toInfo() ?: return null
        return bills.addLine(LineFactory.build(billId, info, unit, quantity, flag, source, scanId, ocrText, detectedCount, confidence, candidates, isBox))
    }
}

@Singleton
class ConfirmBillUseCase @Inject constructor(private val bills: BillRepository) {
    suspend operator fun invoke(billId: Long): ConfirmResult = bills.confirm(billId)
}

@Singleton
class AdjustStockUseCase @Inject constructor(private val stock: StockRepository, private val matcher: MatcherProvider) {
    /** Adds stock given in strips, boxes or pieces/units. */
    suspend fun stockIn(medicineId: Long, unit: SaleUnit, quantity: Int, note: String?): StockState? {
        val info = matcher.infoOf(medicineId) ?: matcher.current().medicines[medicineId]?.toInfo() ?: return null
        val units = QuantityResolver.stockUnits(info, unit, quantity)
        if (units <= 0) return null
        return stock.stockIn(medicineId, units, note)
    }

    suspend fun setCount(medicineId: Long, count: Long, note: String?): StockState? = stock.setCount(medicineId, count, note)
}

@Singleton
class ParseVoiceUseCase @Inject constructor(private val matcher: MatcherProvider, private val settings: SettingsStore) {
    suspend operator fun invoke(alternatives: List<String>): VoiceParse {
        val snap = matcher.current()
        val parser = VoiceParser(snap.matcher, { snap.medicines[it]?.toInfo() }, FlagEvaluator(settings.current().thresholds))
        return withContext(Dispatchers.Default) { parser.parseBest(alternatives) }
    }
}
