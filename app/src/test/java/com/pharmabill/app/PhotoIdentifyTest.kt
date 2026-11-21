package com.pharmabill.app

import com.pharmabill.app.ai.match.MatchEntry
import com.pharmabill.app.ai.match.MedicineMatcher
import com.pharmabill.app.domain.model.MedicineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whole-photo identification: one strip photo must give one medicine, not every word on the foil. */
class PhotoIdentifyTest {
    private val m = MedicineMatcher(
        TestData.entries + listOf(
            MatchEntry(20, "Napa Rapid", "500 mg", "Paracetamol", "Beximco", MedicineKind.STRIP),
            MatchEntry(21, "Paracetamol", "500 mg", "Paracetamol", "Generic", MedicineKind.STRIP),
            MatchEntry(22, "Azithrocin", "500 mg", "Azithromycin", "Beximco", MedicineKind.STRIP),
            MatchEntry(23, "Bextram Gold", "", "Multivitamin", "Beximco", MedicineKind.STRIP),
            MatchEntry(24, "Normens", "5 mg", "Norethisterone", "Square", MedicineKind.STRIP),
        ),
    )

    private val napaRapidStrip = listOf(
        "Napa Rapid", "Paracetamol 500 mg", "WITH ACTIZORB TECHNOLOGY", "BEXIMCO", "PHARMA",
        "Napa Rapid", "Paracotamol 500 mg", "WITH ACTIZ0RB TECHNOLOGY", "Mfg. Lic. No. 379 & 119", "MA No. 186-957-006",
    )

    @Test fun napaRapidStripIsOnlyNapaRapid() {
        val c = m.identify(napaRapidStrip)
        assertEquals(listOf(20L), c.map { it.id })
        assertTrue(c[0].score >= 0.85)
    }

    @Test fun moreSpecificBrandWins() {
        assertEquals(20L, m.identify(listOf("NAPA RAPID 500mg")).first().id)
        assertEquals(1L, m.identify(listOf("NAPA", "Paracetamol BP 500 mg", "Beximco")).first().id)
        assertEquals(5L, m.identify(listOf("Napa Extra", "Paracetamol 500 mg + Caffeine 65 mg")).first().id)
    }

    @Test fun sideTextIsNotAMedicine() {
        assertTrue(m.identify(listOf("WITH ACTIZORB TECHNOLOGY", "BEXIMCO PHARMA", "Mfg. Lic. No. 379 & 119")).isEmpty())
    }

    @Test fun unclearGivesChoicesBelowGreen() {
        val c = m.identify(listOf("SECLO"))
        assertTrue(c.size >= 2)
        assertTrue(c.all { it.id == 2L || it.id == 6L })
        assertTrue(c[0].score < 0.85)
        val blurry = m.identify(listOf("Nap Rapld"))
        assertTrue(blurry.isNotEmpty())
        assertTrue(blurry.all { it.score <= 0.80 })
    }

    @Test fun genericOnlyPackStillMatchesGenericEntry() {
        assertEquals(21L, m.identify(listOf("Paracetamol 500 mg", "Tablet BP")).first().id)
    }
}
