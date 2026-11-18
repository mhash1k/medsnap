package com.pharmabill.app.ui.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.ai.group.ScanLine
import com.pharmabill.app.ai.image.ImageQuality
import com.pharmabill.app.ai.ocr.TextNormalizer
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.prefs.SettingsStore
import com.pharmabill.app.data.repo.MatcherProvider
import com.pharmabill.app.data.repo.MedicineRepository
import com.pharmabill.app.dataset.CorrectedLine
import com.pharmabill.app.dataset.TrainingSampleLogger
import com.pharmabill.app.domain.model.AliasSource
import com.pharmabill.app.domain.model.DetClass
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.LineSource
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.pricing.BoxChip
import com.pharmabill.app.domain.pricing.BoxChipType
import com.pharmabill.app.domain.pricing.PriceCalculator
import com.pharmabill.app.domain.pricing.QuantityResolver
import com.pharmabill.app.domain.usecase.AddLineUseCase
import com.pharmabill.app.domain.usecase.DraftManager
import com.pharmabill.app.domain.usecase.ScanOutput
import com.pharmabill.app.domain.usecase.ScanPhotoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** One editable line on the scan result screen. Any manual fix turns it green. */
data class ScanLineUi(
    val key: Int,
    val ai: ScanLine,
    val medicine: MedicineEntity?,
    val unit: SaleUnit,
    val quantity: Int,
    val flag: Flag,
    val isBox: Boolean,
    val boxChipChosen: Boolean,
    val corrected: Boolean,
    val customPieces: Boolean = false,
) {
    val lineTotal: Long get() = medicine?.let { PriceCalculator.linePrice(it.toInfo(), unit, quantity) } ?: 0
}

sealed interface ScanUi {
    data object Loading : ScanUi
    data class Error(val message: String) : ScanUi
    data class Ready(
        val output: ScanOutput,
        val image: Bitmap?,
        val lines: List<ScanLineUi>,
        val blurry: Boolean,
        val candidates: Map<Long, MedicineEntity>,
    ) : ScanUi
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val scan: ScanPhotoUseCase,
    private val matcher: MatcherProvider,
    private val medicines: MedicineRepository,
    private val addLine: AddLineUseCase,
    private val drafts: DraftManager,
    private val logger: TrainingSampleLogger,
    private val settings: SettingsStore,
) : ViewModel() {
    private val path: String = savedState.get<String>("path").orEmpty()
    private val _ui = MutableStateFlow<ScanUi>(ScanUi.Loading)
    val ui: StateFlow<ScanUi> = _ui
    private var added = false
    private var keepPhoto = true

    init { run(null) }

    fun rerun(oneMedicine: Boolean) = run(oneMedicine)

    private fun run(oneMedicine: Boolean?) {
        _ui.value = ScanUi.Loading
        viewModelScope.launch {
            try {
                keepPhoto = settings.current().collectTrainingData
                val out = scan(path, oneMedicine)
                val (image, blurry) = withContext(Dispatchers.Default) { loadPreview(path) }
                val snap = matcher.current()
                val lines = out.lines.mapIndexed { i, l ->
                    val m = l.medicineId?.let { snap.medicines[it] }
                    ScanLineUi(i, l, m, l.unit, l.quantity, l.flag, l.isBox, false, false)
                }
                val cands = out.lines.flatMap { l -> l.candidates.map { it.id } }.distinct().mapNotNull { snap.medicines[it] }.associateBy { it.id }
                _ui.value = ScanUi.Ready(out, image, lines, blurry, cands)
            } catch (e: Exception) {
                _ui.value = ScanUi.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun loadPreview(path: String): Pair<Bitmap?, Boolean> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / sample > 1280) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null to false
        // Laplacian variance on a centre crop at ~640 px for the "retake" hint.
        val small = Bitmap.createScaledBitmap(bmp, 640, (640f * bmp.height / bmp.width).toInt().coerceAtLeast(1), true)
        val w = small.width
        val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val lum = IntArray(px.size) { ((px[it] shr 16 and 0xFF) * 299 + (px[it] shr 8 and 0xFF) * 587 + (px[it] and 0xFF) * 114) / 1000 }
        if (small !== bmp) small.recycle()
        return bmp to ImageQuality.isBlurry(lum, w, h)
    }

    private fun edit(key: Int, f: (ScanLineUi) -> ScanLineUi) {
        _ui.update { s -> if (s is ScanUi.Ready) s.copy(lines = s.lines.map { if (it.key == key) f(it) else it }) else s }
    }

    fun pickMedicine(key: Int, m: MedicineEntity) = edit(key) { l ->
        val unit = when {
            m.kind == MedicineKind.UNIT -> SaleUnit.UNIT
            l.isBox -> SaleUnit.BOX
            l.ai.detectedCount > 0 -> QuantityResolver.fromDetectedCount(m.toInfo(), l.ai.detectedCount).unit
            else -> SaleUnit.PIECE
        }
        val qty = when {
            m.kind == MedicineKind.UNIT -> 1
            l.isBox -> 1
            l.ai.detectedCount > 0 -> QuantityResolver.fromDetectedCount(m.toInfo(), l.ai.detectedCount).quantity
            else -> m.piecesPerStrip.coerceAtLeast(1)
        }
        l.copy(medicine = m, unit = unit, quantity = qty, flag = if (l.isBox && !l.boxChipChosen) Flag.YELLOW else Flag.GREEN, corrected = true)
    }

    fun quickAdded(key: Int, id: Long) {
        viewModelScope.launch {
            val m = medicines.get(id) ?: return@launch
            pickMedicine(key, m)
        }
    }

    fun step(key: Int, delta: Int) = edit(key) { l ->
        if (l.medicine == null) return@edit l.copy(quantity = QuantityResolver.step(l.quantity, delta))
        l.copy(quantity = QuantityResolver.step(l.quantity, delta), flag = Flag.GREEN, corrected = true, boxChipChosen = true)
    }

    /** Strip/Box toggle on the line. */
    fun setBox(key: Int, box: Boolean) = edit(key) { l ->
        val m = l.medicine
        if (box) l.copy(isBox = true, unit = SaleUnit.BOX, quantity = 1, boxChipChosen = false, flag = Flag.YELLOW, corrected = true)
        else {
            val pieces = m?.piecesPerStrip?.coerceAtLeast(1) ?: 1
            l.copy(isBox = false, unit = SaleUnit.PIECE, quantity = if (l.ai.detectedCount > 0) l.ai.detectedCount else pieces, boxChipChosen = true,
                flag = if (m != null) Flag.GREEN else l.flag, corrected = true)
        }
    }

    fun chooseChip(key: Int, chip: BoxChip) = edit(key) { l ->
        if (chip.type == BoxChipType.CUSTOM) l.copy(unit = SaleUnit.PIECE, quantity = l.medicine?.piecesPerStrip ?: 1, boxChipChosen = true, customPieces = true, flag = if (l.medicine != null) Flag.GREEN else l.flag, corrected = true)
        else l.copy(unit = chip.unit, quantity = chip.quantity, boxChipChosen = true, flag = if (l.medicine != null) Flag.GREEN else l.flag, corrected = true)
    }

    fun setUnit(key: Int, unit: SaleUnit) = edit(key) { l ->
        l.copy(unit = unit, quantity = 1, flag = if (l.medicine != null) Flag.GREEN else l.flag, corrected = true, boxChipChosen = true)
    }

    fun markChecked(key: Int) = edit(key) { l -> if (l.medicine != null) l.copy(flag = Flag.GREEN, corrected = true, boxChipChosen = true) else l }

    fun remove(key: Int) = _ui.update { s -> if (s is ScanUi.Ready) s.copy(lines = s.lines.filterNot { it.key == key }) else s }

    /** Adds every line that has a medicine to the active draft; yellow lines still block Confirm there. */
    suspend fun addToDraft(): Int {
        val s = _ui.value as? ScanUi.Ready ?: return 0
        val billId = drafts.ensureActive()
        var n = 0
        val corrections = mutableListOf<CorrectedLine>()
        for (l in s.lines) {
            val m = l.medicine ?: continue
            val flag = if (l.isBox && !l.boxChipChosen) Flag.YELLOW else l.flag
            addLine(
                billId, m.id, l.unit, l.quantity, flag, LineSource.PHOTO,
                candidates = l.ai.candidates.map { it.id }, scanId = s.output.scanId, ocrText = l.ai.ocrText,
                detectedCount = l.ai.detectedCount, confidence = l.ai.nameScore, isBox = l.isBox && !l.boxChipChosen,
            )
            n++
            if (l.ai.medicineId != m.id && l.ai.ocrText != null) {
                TextNormalizer.brandTokens(l.ai.ocrText).take(2).joinToString(" ").takeIf { it.isNotBlank() }?.let {
                    medicines.learnAlias(m.id, it, AliasSource.OCR)
                }
            }
            corrections += CorrectedLine(
                medicineId = m.id, brand = m.brand, aiMedicineId = l.ai.medicineId, aiCount = l.ai.detectedCount,
                finalPieces = QuantityResolver.stockUnits(m.toInfo(), l.unit, l.quantity),
                cls = when { l.isBox -> DetClass.BOX; m.form.name == "CAPSULE" -> DetClass.CAPSULE; else -> DetClass.TABLET },
                corrected = l.corrected, boxes = l.ai.detections.map { it.box },
            )
        }
        if (settings.current().collectTrainingData) logger.log(s.output.scanId, corrections)
        added = true
        return n
    }

    override fun onCleared() {
        super.onCleared()
        // Without training-data collection the photo is not kept after the result screen closes.
        if (!keepPhoto) File(path).delete()
    }
}
