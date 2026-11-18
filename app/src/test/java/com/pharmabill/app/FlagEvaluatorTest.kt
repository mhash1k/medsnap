package com.pharmabill.app

import com.pharmabill.app.domain.flags.BillRules
import com.pharmabill.app.domain.flags.FlagEvaluator
import com.pharmabill.app.domain.flags.FlagThresholds
import com.pharmabill.app.domain.flags.LineCheck
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.MedicineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlagEvaluatorTest {
    private val f = FlagEvaluator()

    @Test fun nameFlags() {
        assertEquals(Flag.GREEN, f.nameFlag(0.95, 0.70))
        assertEquals(Flag.YELLOW, f.nameFlag(0.95, 0.93))   // too close
        assertEquals(Flag.YELLOW, f.nameFlag(0.70, null))
        assertEquals(Flag.RED, f.nameFlag(0.50, null))
        assertEquals(Flag.RED, f.nameFlag(null, null))
        assertEquals(Flag.GREEN, f.nameFlag(0.85, null))
    }

    @Test fun countFlags() {
        assertEquals(Flag.YELLOW, f.countFlag(MedicineKind.STRIP, false, false, 0, 0f)) // no model
        assertEquals(Flag.YELLOW, f.countFlag(MedicineKind.STRIP, true, true, 0, 0.9f)) // any box
        assertEquals(Flag.RED, f.countFlag(MedicineKind.STRIP, false, true, 0, 0f))     // no tablets
        assertEquals(Flag.YELLOW, f.countFlag(MedicineKind.STRIP, false, true, 8, 0.3f))
        assertEquals(Flag.GREEN, f.countFlag(MedicineKind.STRIP, false, true, 8, 0.9f))
        assertEquals(Flag.GREEN, f.countFlag(MedicineKind.UNIT, false, false, 0, 0f))
    }

    @Test fun worstWins() {
        assertEquals(Flag.RED, f.lineFlag(Flag.GREEN, Flag.RED))
        assertEquals(Flag.YELLOW, f.lineFlag(Flag.YELLOW, Flag.GREEN))
    }

    @Test fun tunableThresholds() {
        val strict = FlagEvaluator(FlagThresholds(green = 0.95, yellow = 0.7))
        assertEquals(Flag.YELLOW, strict.nameFlag(0.9, null))
        assertEquals(Flag.RED, strict.nameFlag(0.65, null))
    }

    @Test fun confirmLockedWhileAnyLineFlagged() {
        assertFalse(BillRules.canConfirm(listOf(LineCheck(Flag.GREEN, 1), LineCheck(Flag.YELLOW, 2))))
        assertEquals(1, BillRules.needsChecking(listOf(LineCheck(Flag.GREEN, 1), LineCheck(Flag.YELLOW, 2))))
        assertFalse(BillRules.canConfirm(listOf(LineCheck(Flag.GREEN, null))))
        assertFalse(BillRules.canConfirm(emptyList()))
        assertTrue(BillRules.canConfirm(listOf(LineCheck(Flag.GREEN, 1), LineCheck(Flag.GREEN, 2))))
    }
}
