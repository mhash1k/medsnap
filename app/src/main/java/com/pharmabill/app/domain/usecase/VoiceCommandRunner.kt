package com.pharmabill.app.domain.usecase

import com.pharmabill.app.ai.voice.VoiceCommand
import com.pharmabill.app.ai.voice.VoiceItem
import com.pharmabill.app.data.db.entities.BillLineEntity
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.repo.BillRepository
import com.pharmabill.app.data.repo.MatcherProvider
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.LineSource
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VoiceOutcome {
    data class Added(val billId: Long, val lines: List<BillLineEntity>) : VoiceOutcome
    data class Removed(val line: BillLineEntity) : VoiceOutcome
    data class Changed(val before: BillLineEntity, val after: BillLineEntity) : VoiceOutcome
    data class Stock(val medicine: MedicineEntity) : VoiceOutcome
    data class Price(val medicine: MedicineEntity) : VoiceOutcome
    data class Finish(val billId: Long) : VoiceOutcome
    data class NewDraft(val billId: Long) : VoiceOutcome
    data class NotInBill(val name: String) : VoiceOutcome
    data class NotUnderstood(val text: String) : VoiceOutcome
    data class Failed(val errorCode: Int) : VoiceOutcome
}

/** Executes a parsed voice command against the active draft. Items are added directly. */
@Singleton
class VoiceCommandRunner @Inject constructor(
    private val parse: ParseVoiceUseCase,
    private val drafts: DraftManager,
    private val bills: BillRepository,
    private val matcher: MatcherProvider,
) {
    suspend fun run(alternatives: List<String>): VoiceOutcome {
        val result = parse(alternatives)
        val snap = matcher.current()
        return when (val c = result.command) {
            is VoiceCommand.AddItems -> {
                val billId = drafts.ensureActive()
                val added = c.items.mapNotNull { item ->
                    val info = snap.medicines[item.medicineId]?.toInfo() ?: return@mapNotNull null
                    val line = LineFactory.build(
                        billId, info, item.unit, item.quantity, item.flag, LineSource.VOICE,
                        ocrText = item.heard, confidence = item.score, candidates = item.candidates.map { it.id },
                    )
                    line.copy(id = bills.addLine(line))
                }
                if (added.isEmpty()) VoiceOutcome.NotUnderstood(result.text) else VoiceOutcome.Added(billId, added)
            }
            is VoiceCommand.RemoveItem -> {
                val line = findLine(c.item) ?: return VoiceOutcome.NotInBill(name(c.item))
                bills.removeLine(line.id)?.let { VoiceOutcome.Removed(it) } ?: VoiceOutcome.NotInBill(name(c.item))
            }
            is VoiceCommand.ChangeQuantity -> {
                val line = findLine(c.item) ?: return VoiceOutcome.NotInBill(name(c.item))
                val info = snap.medicines[line.medicineId ?: -1L]?.toInfo() ?: return VoiceOutcome.NotInBill(name(c.item))
                val unit = if (c.item.medicineId == line.medicineId) c.item.unit else line.saleUnit
                val after = LineFactory.reprice(line, info, unit, c.item.quantity, line.flag.let { if (it == Flag.RED) Flag.YELLOW else it })
                bills.updateLine(after)
                VoiceOutcome.Changed(line, after)
            }
            is VoiceCommand.StockQuery -> snap.medicines[c.item.medicineId]?.let { VoiceOutcome.Stock(it) } ?: VoiceOutcome.NotUnderstood(result.text)
            is VoiceCommand.PriceQuery -> snap.medicines[c.item.medicineId]?.let { VoiceOutcome.Price(it) } ?: VoiceOutcome.NotUnderstood(result.text)
            VoiceCommand.FinishBill -> VoiceOutcome.Finish(drafts.ensureActive())
            VoiceCommand.NewDraft -> VoiceOutcome.NewDraft(drafts.newDraft())
            is VoiceCommand.NotUnderstood -> VoiceOutcome.NotUnderstood(alternatives.firstOrNull().orEmpty())
        }
    }

    private suspend fun findLine(item: VoiceItem): BillLineEntity? {
        val billId = drafts.ensureActive()
        val lines = bills.lines(billId)
        val ids = item.candidates.map { it.id }
        return lines.lastOrNull { it.medicineId == item.medicineId }
            ?: ids.firstNotNullOfOrNull { id -> lines.lastOrNull { it.medicineId == id } }
    }

    private fun name(item: VoiceItem): String = matcher.medicine(item.medicineId)?.displayName ?: item.heard
}
