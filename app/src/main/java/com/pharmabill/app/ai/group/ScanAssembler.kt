package com.pharmabill.app.ai.group

import com.pharmabill.app.ai.match.MatchCandidate
import com.pharmabill.app.domain.flags.FlagEvaluator
import com.pharmabill.app.domain.model.BBox
import com.pharmabill.app.domain.model.Detection
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.MedicineInfo
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.pricing.QuantityResolver

/** One proposed line on the scan result screen, before staff fixes it. */
data class ScanLine(
    val medicineId: Long?,
    val candidates: List<MatchCandidate>,
    val ocrText: String?,
    val detectedCount: Int,
    val isBox: Boolean,
    val countConfidence: Float,
    val nameScore: Double,
    val unit: SaleUnit,
    val quantity: Int,
    val flag: Flag,
    val area: BBox?,
    val detections: List<Detection>,
    /** How sure we are about WHICH medicine it is (separate from the count). Not green -> show the choices. */
    val nameFlag: Flag = flag,
)

/** Step 6 of the pipeline: turn grouped items into piece / strip / box / unit lines with flags. */
class ScanAssembler(private val flags: FlagEvaluator, private val infoOf: (Long) -> MedicineInfo?) {

    fun assemble(items: List<GroupedItem>, modelAvailable: Boolean): List<ScanLine> = items.map { item ->
        val group = item.group
        val name = item.name
        val candidates = buildList {
            name?.candidates?.let { addAll(it) }
            item.alternatives.mapNotNull { it.candidates.firstOrNull() }.forEach { alt -> if (none { it.id == alt.id }) add(alt) }
        }.sortedByDescending { it.score }.take(3)
        val top = candidates.firstOrNull()
        val info = top?.let { infoOf(it.id) }
        val count = group?.count ?: 0
        val isBox = group?.isBox == true
        val conf = group?.meanConfidence ?: 0f
        val area = listOfNotNull(group?.bounds, name?.box).reduceOrNull { a, b -> a.union(b) }
        val dets = group?.detections.orEmpty()

        if (info == null) {
            // Group with no name: "Unknown medicine, N pcs".
            ScanLine(null, candidates, name?.text, count, isBox, conf, top?.score ?: 0.0, SaleUnit.PIECE, count.coerceAtLeast(1), Flag.RED, area, dets, Flag.RED)
        } else {
            val nameFlag = flags.nameFlag(top.score, candidates.getOrNull(1)?.score)
            val countFlag = when {
                info.kind == MedicineKind.UNIT && !isBox -> Flag.GREEN
                group == null -> if (modelAvailable) Flag.RED else Flag.YELLOW
                else -> flags.countFlag(info.kind, isBox, modelAvailable, count, conf)
            }
            val (unit, qty) = when {
                isBox -> SaleUnit.BOX to 1
                info.kind == MedicineKind.UNIT -> SaleUnit.UNIT to 1
                count <= 0 -> SaleUnit.PIECE to info.piecesPerStrip.coerceAtLeast(1)
                else -> QuantityResolver.fromDetectedCount(info, count).let { it.unit to it.quantity }
            }
            ScanLine(info.id, candidates, name?.text, count, isBox, conf, top.score, unit, qty, flags.lineFlag(nameFlag, countFlag), area, dets, nameFlag)
        }
    }
}
