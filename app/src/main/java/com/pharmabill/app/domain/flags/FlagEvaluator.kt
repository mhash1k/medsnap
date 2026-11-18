package com.pharmabill.app.domain.flags

import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.MedicineKind

data class FlagThresholds(
    val green: Double = 0.85,
    val yellow: Double = 0.60,
    val minLead: Double = 0.05,
    val modelConfidence: Double = 0.60,
)

class FlagEvaluator(private val t: FlagThresholds = FlagThresholds()) {

    /** Green needs a strong best match with a clear lead; red when nothing reaches the yellow threshold. */
    fun nameFlag(topScore: Double?, secondScore: Double?): Flag {
        if (topScore == null || topScore < t.yellow) return Flag.RED
        val lead = topScore - (secondScore ?: 0.0)
        return if (topScore >= t.green && lead >= t.minLead) Flag.GREEN else Flag.YELLOW
    }

    fun countFlag(
        kind: MedicineKind?,
        isBox: Boolean,
        modelAvailable: Boolean,
        detectedCount: Int,
        meanConfidence: Float,
    ): Flag {
        if (isBox) return Flag.YELLOW
        if (kind == MedicineKind.UNIT) return Flag.GREEN
        if (!modelAvailable) return Flag.YELLOW
        if (detectedCount <= 0) return Flag.RED
        if (meanConfidence < t.modelConfidence) return Flag.YELLOW
        return Flag.GREEN
    }

    fun lineFlag(nameFlag: Flag, countFlag: Flag): Flag = nameFlag.worst(countFlag)
}

data class LineCheck(val flag: Flag, val medicineId: Long?)

object BillRules {
    fun needsChecking(lines: List<LineCheck>): Int =
        lines.count { it.flag != Flag.GREEN || it.medicineId == null }

    fun canConfirm(lines: List<LineCheck>): Boolean = lines.isNotEmpty() && needsChecking(lines) == 0
}
