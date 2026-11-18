package com.pharmabill.app.ui.bill

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.data.db.entities.BillEntity
import com.pharmabill.app.data.db.entities.BillLineEntity
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.prefs.AppSettings
import com.pharmabill.app.data.prefs.SettingsStore
import com.pharmabill.app.data.repo.BillRepository
import com.pharmabill.app.data.repo.ConfirmResult
import com.pharmabill.app.data.repo.MatcherProvider
import com.pharmabill.app.data.repo.MedicineRepository
import com.pharmabill.app.domain.flags.BillRules
import com.pharmabill.app.domain.flags.LineCheck
import com.pharmabill.app.domain.model.AliasSource
import com.pharmabill.app.domain.model.DiscountType
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.LineSource
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.usecase.AddLineUseCase
import com.pharmabill.app.domain.usecase.ConfirmBillUseCase
import com.pharmabill.app.domain.usecase.DraftManager
import com.pharmabill.app.domain.usecase.LineFactory
import com.pharmabill.app.ui.voice.VoiceHelperFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillUi(
    val bill: BillEntity? = null,
    val lines: List<BillLineEntity> = emptyList(),
    val medicines: Map<Long, MedicineEntity> = emptyMap(),
) {
    val needsChecking: Int get() = BillRules.needsChecking(lines.map { LineCheck(it.flag, it.medicineId) })
    val canConfirm: Boolean get() = BillRules.canConfirm(lines.map { LineCheck(it.flag, it.medicineId) })
}

@HiltViewModel
class BillViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val bills: BillRepository,
    private val matcher: MatcherProvider,
    private val medicines: MedicineRepository,
    private val drafts: DraftManager,
    private val addLine: AddLineUseCase,
    private val confirmBill: ConfirmBillUseCase,
    settingsStore: SettingsStore,
    voiceFactory: VoiceHelperFactory,
) : ViewModel() {
    val billId: Long = savedState.get<Long>("billId") ?: 0L
    val voice = voiceFactory.create(viewModelScope)

    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val openDrafts: StateFlow<List<BillEntity>> = bills.observeDrafts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ui: StateFlow<BillUi> = combine(bills.observeBill(billId), bills.observeLines(billId), matcher.snapshot) { b, l, snap ->
        BillUi(b, l, snap?.medicines.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillUi())

    init {
        viewModelScope.launch { if (billId > 0) drafts.setActive(billId) }
    }

    fun select(id: Long) { viewModelScope.launch { drafts.setActive(id) } }
    suspend fun newDraft(): Long = drafts.newDraft()

    suspend fun add(m: MedicineEntity, unit: SaleUnit, qty: Int): Long? = addLine(billId, m.id, unit, qty, Flag.GREEN, LineSource.MANUAL)

    suspend fun remove(lineId: Long): BillLineEntity? = bills.removeLine(lineId)
    fun restore(line: BillLineEntity) { viewModelScope.launch { bills.restoreLine(line) } }
    fun undoAdd(id: Long) { viewModelScope.launch { bills.removeLine(id) } }

    /** Saves a staff fix on a line: always green; learns the heard/OCR text as an alias when the medicine changed. */
    fun applyFix(line: BillLineEntity, medicine: MedicineEntity, unit: SaleUnit, qty: Int) {
        viewModelScope.launch {
            val fixed = LineFactory.reprice(line.copy(medicineId = medicine.id), medicine.toInfo(), unit, qty, Flag.GREEN, isBox = false)
            bills.updateLine(fixed)
            if (line.medicineId != medicine.id && !line.ocrText.isNullOrBlank()) {
                val source = if (line.source == LineSource.VOICE) AliasSource.VOICE else AliasSource.OCR
                medicines.learnAlias(medicine.id, line.ocrText, source)
            }
        }
    }

    fun setDiscount(type: DiscountType?, value: Long) { viewModelScope.launch { bills.setDiscount(billId, type, value) } }
    fun setLabel(label: String) { viewModelScope.launch { bills.setLabel(billId, label) } }

    suspend fun deleteDraft() {
        bills.deleteDraft(billId)
        drafts.setActive(null)
    }

    suspend fun confirm(): ConfirmResult {
        val r = confirmBill(billId)
        if (r is ConfirmResult.Confirmed) drafts.setActive(null)
        return r
    }
}
