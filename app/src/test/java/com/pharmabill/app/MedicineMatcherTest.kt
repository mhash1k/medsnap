package com.pharmabill.app

import com.pharmabill.app.ai.match.JaroWinkler
import com.pharmabill.app.ai.match.PhoneticKey
import com.pharmabill.app.ai.ocr.TextNormalizer
import com.pharmabill.app.domain.flags.FlagEvaluator
import com.pharmabill.app.domain.model.Flag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineMatcherTest {
    private val m = TestData.matcher()
    private val flags = FlagEvaluator()

    @Test fun jaroWinklerBasics() {
        assertEquals(1.0, JaroWinkler.similarity("NAPA", "NAPA"), 1e-9)
        assertTrue(JaroWinkler.similarity("MARTHA", "MARHTA") > 0.95)
        assertEquals(0.0, JaroWinkler.similarity("", "ABC"), 1e-9)
    }

    @Test fun normaliserFixesOcrSwapsOnlyInBrandWords() {
        assertEquals("SECLO", TextNormalizer.fixOcrSwaps("SECL0"))
        assertEquals("NAPA", TextNormalizer.fixOcrSwaps("NAPA"))
        assertEquals("500", TextNormalizer.fixOcrSwaps("500"))
        assertEquals(listOf("500MG"), TextNormalizer.strengths("Napa 500 mg").map { it.key })
        assertEquals("120MG", TextNormalizer.firstStrengthKey("120 mg/5 ml"))
    }

    @Test fun noiseLinesIgnored() {
        assertTrue(TextNormalizer.isNoise("Batch No: 2304"))
        assertTrue(TextNormalizer.isNoise("EXP 12/2027"))
        assertTrue(TextNormalizer.isNoise("Rx only"))
        assertTrue(TextNormalizer.isNoise("MRP Tk 12.00"))
        assertFalse(TextNormalizer.isNoise("Napa 500 mg"))
    }

    @Test fun exactBrandAndStrengthIsGreen() {
        val c = m.matchOcrBlock("NAPA 500 mg", "NAPA 500 mg Paracetamol BP Beximco")
        assertEquals(1L, c.first().id)
        assertEquals(Flag.GREEN, flags.nameFlag(c[0].score, c.getOrNull(1)?.score))
    }

    @Test fun ocrSwapStillMatches() {
        val c = m.matchOcrBlock("SECL0 20mg", "SECL0 20mg")
        assertEquals(2L, c.first().id)
        assertTrue(c.first().score >= 0.85)
    }

    @Test fun strengthPicksVariant() {
        assertEquals(6L, m.matchOcrBlock("Seclo 40 mg").first().id)
        assertEquals(2L, m.matchOcrBlock("Seclo 20 mg").first().id)
    }

    @Test fun brandWithoutStrengthAndTwoVariantsIsYellow() {
        val c = m.matchOcrBlock("SECLO")
        assertEquals(Flag.YELLOW, flags.nameFlag(c[0].score, c.getOrNull(1)?.score))
        assertTrue(c.size <= 3)
    }

    @Test fun garbageIsRed() {
        val c = m.matchOcrBlock("ZQXW VBNM")
        assertEquals(Flag.RED, flags.nameFlag(c.firstOrNull()?.score, null))
    }

    @Test fun searchBrandGenericBangla() {
        assertEquals(1L, m.search("nap").first())
        assertTrue(m.search("omeprazole").containsAll(listOf(2L, 6L)))
        assertTrue(m.search("সেকলো").contains(2L))
        assertTrue(m.search("fexx").contains(7L)) // fuzzy fallback
    }

    @Test fun phoneticKeys() {
        assertEquals(PhoneticKey.of("napa"), PhoneticKey.of("nappa"))
        assertEquals(PhoneticKey.of("seclo"), PhoneticKey.of("seklo"))
    }
}
