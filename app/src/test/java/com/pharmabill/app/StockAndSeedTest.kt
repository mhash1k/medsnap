package com.pharmabill.app

import com.pharmabill.app.ai.image.ImageQuality
import com.pharmabill.app.data.seed.SeedCsv
import com.pharmabill.app.dataset.YoloFormat
import com.pharmabill.app.domain.model.BBox
import com.pharmabill.app.domain.model.MedicineForm
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.domain.stock.StockLedger
import com.pharmabill.app.domain.stock.StockState
import com.pharmabill.app.util.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockAndSeedTest {
    @Test fun sellWithZeroStockGoesNegativeAndFlags() {
        val s = StockLedger.applySale(StockState(0, false), 20)
        assertEquals(-20L, s.quantity)
        assertTrue(s.needsStockEntry)
    }

    @Test fun stockInReconcilesNegative() {
        val s = StockLedger.applyStockIn(StockState(-20, true), 100)
        assertEquals(80L, s.quantity)
        assertFalse(s.needsStockEntry)
    }

    @Test fun partialStockInKeepsBadge() {
        val s = StockLedger.applyStockIn(StockState(-20, true), 10)
        assertEquals(-10L, s.quantity)
        assertTrue(s.needsStockEntry)
    }

    @Test fun rebuildFromMovements() {
        assertEquals(StockState(80, false), StockLedger.rebuild(listOf(100, -10, -10)))
        assertEquals(StockState(-5, true), StockLedger.rebuild(listOf(-5)))
    }

    @Test fun seedCsvParses() {
        val rows = SeedCsv.parse(
            listOf(
                "brand,generic,strength,form,kind,company,pieces_per_strip,strips_per_box,strip_price,unit_price,bangla_name,voice_aliases,price_verified",
                "Napa,Paracetamol,500 mg,TABLET,STRIP,Beximco,12,10,12.00,,নাপা,napa|nappa,false",
                "\"Napa, Syrup\",Paracetamol,120 mg/5 ml,SYRUP,UNIT,Beximco,,,,35,,,false",
            ),
        )
        assertEquals(2, rows.size)
        assertEquals(1200L, rows[0].stripPrice)
        assertEquals(listOf("napa", "nappa"), rows[0].voiceAliases)
        assertEquals("Napa, Syrup", rows[1].brand)
        assertEquals(MedicineForm.SYRUP, rows[1].form)
        assertEquals(MedicineKind.UNIT, rows[1].kind)
        assertEquals(3500L, rows[1].unitPrice)
        assertFalse(rows[1].priceVerified)
    }

    @Test fun moneyFormatting() {
        assertEquals("৳12.50", Fmt.taka(1250, false))
        assertEquals("৳১২.৫০", Fmt.taka(1250, true))
        assertEquals(1250L, Fmt.parseTaka("12.5"))
        assertEquals(1200L, Fmt.parseTaka("১২"))
        assertEquals(null, Fmt.parseTaka("abc"))
    }

    @Test fun yoloLabel() {
        assertEquals("0 0.500000 0.500000 0.200000 0.100000", YoloFormat.label(0, BBox(40f, 45f, 60f, 55f), 100, 100))
    }

    @Test fun blurAndGlare() {
        val flat = IntArray(100) { 128 }
        assertTrue(ImageQuality.isBlurry(flat, 10, 10))
        val checker = IntArray(100) { if ((it / 10 + it % 10) % 2 == 0) 0 else 255 }
        assertFalse(ImageQuality.isBlurry(checker, 10, 10))
        assertTrue(ImageQuality.hasGlare(IntArray(100) { 255 }))
        assertFalse(ImageQuality.hasGlare(flat))
    }
}

class SeedFileTest {
    @Test fun bundledSeedHasAbout200MedicinesWithPrices() {
        val f = listOf("src/main/assets/seed/medicines.csv", "app/src/main/assets/seed/medicines.csv").map { java.io.File(it) }.first { it.exists() }
        val rows = SeedCsv.parse(f.readLines(Charsets.UTF_8))
        assertTrue(rows.size >= 200)
        assertTrue(rows.all { it.stripPrice > 0 || it.unitPrice > 0 })
        assertTrue(rows.none { it.priceVerified })
        assertTrue(rows.map { it.form }.toSet().size >= 6)
    }
}
