package com.pharmabill.app

import com.pharmabill.app.TestData.napa
import com.pharmabill.app.TestData.napaSyrup
import com.pharmabill.app.TestData.seclo
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.pricing.BoxChipType
import com.pharmabill.app.domain.pricing.Quantity
import com.pharmabill.app.domain.pricing.QuantityResolver
import com.pharmabill.app.domain.pricing.StripsAndPieces
import org.junit.Assert.assertEquals
import org.junit.Test

class QuantityResolverTest {
    @Test fun countToSaleUnit() {
        assertEquals(Quantity(SaleUnit.STRIP, 1), QuantityResolver.fromDetectedCount(napa, 12))
        assertEquals(Quantity(SaleUnit.PIECE, 7), QuantityResolver.fromDetectedCount(napa, 7))
        assertEquals(Quantity(SaleUnit.PIECE, 16), QuantityResolver.fromDetectedCount(napa, 16))
        assertEquals(Quantity(SaleUnit.STRIP, 2), QuantityResolver.fromDetectedCount(napa, 24))
        assertEquals(Quantity(SaleUnit.UNIT, 1), QuantityResolver.fromDetectedCount(napaSyrup, 0))
    }

    @Test fun stripsAndPiecesDisplay() {
        assertEquals(StripsAndPieces(1, 4), QuantityResolver.split(napa, 16))
        assertEquals(StripsAndPieces(0, 5), QuantityResolver.split(napa, 5))
    }

    @Test fun stockUnits() {
        assertEquals(10L, QuantityResolver.stockUnits(seclo, SaleUnit.STRIP, 1))   // 1 of 3 strips in a box
        assertEquals(30L, QuantityResolver.stockUnits(seclo, SaleUnit.BOX, 1))
        assertEquals(3L, QuantityResolver.stockUnits(seclo, SaleUnit.PIECE, 3))
        assertEquals(1L, QuantityResolver.stockUnits(napaSyrup, SaleUnit.UNIT, 1))
    }

    @Test fun boxChipsForThreeStripBox() {
        val chips = QuantityResolver.boxChips(seclo)
        assertEquals(listOf(BoxChipType.FULL_BOX, BoxChipType.STRIPS, BoxChipType.STRIPS, BoxChipType.CUSTOM), chips.map { it.type })
        assertEquals(3, chips[0].strips)
        assertEquals(listOf(1, 2), chips.filter { it.type == BoxChipType.STRIPS }.map { it.strips })
    }

    @Test fun boxChipsForTenStripBox() {
        val chips = QuantityResolver.boxChips(napa)
        assertEquals(listOf(1, 2, 5), chips.filter { it.type == BoxChipType.STRIPS }.map { it.strips })
        assertEquals(10, chips.first().strips)
    }

    @Test fun defaultsAndSteps() {
        assertEquals(SaleUnit.PIECE, QuantityResolver.defaultUnit(MedicineKind.STRIP))
        assertEquals(SaleUnit.UNIT, QuantityResolver.defaultUnit(MedicineKind.UNIT))
        assertEquals(6, QuantityResolver.halfStripPieces(napa))
        assertEquals(1, QuantityResolver.step(1, -1))
        assertEquals(3, QuantityResolver.step(2, 1))
    }
}
