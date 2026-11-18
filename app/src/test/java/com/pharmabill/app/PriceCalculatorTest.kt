package com.pharmabill.app

import com.pharmabill.app.TestData.napa
import com.pharmabill.app.TestData.napaSyrup
import com.pharmabill.app.TestData.seclo
import com.pharmabill.app.domain.model.DiscountType
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.pricing.PriceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceCalculatorTest {
    @Test fun piecePriceIsStripPriceDividedByPieces() {
        assertEquals(100L, PriceCalculator.piecePrice(napa))           // 12 Tk / 12
        assertEquals(600L, PriceCalculator.piecePrice(seclo))          // 60 Tk / 10
    }

    @Test fun boxPriceIsStripTimesStrips() {
        assertEquals(18000L, PriceCalculator.boxPrice(seclo))
    }

    @Test fun piecesCutStripFullStripAndBox() {
        assertEquals(400L, PriceCalculator.linePrice(napa, SaleUnit.PIECE, 4))
        assertEquals(1200L, PriceCalculator.linePrice(napa, SaleUnit.PIECE, 12))
        assertEquals(1200L, PriceCalculator.linePrice(napa, SaleUnit.STRIP, 1))
        assertEquals(18000L, PriceCalculator.linePrice(seclo, SaleUnit.BOX, 1))
        assertEquals(6000L, PriceCalculator.linePrice(seclo, SaleUnit.STRIP, 1)) // box holding 1 of 3 strips
        assertEquals(3500L * 2, PriceCalculator.linePrice(napaSyrup, SaleUnit.UNIT, 2))
    }

    @Test fun piecePriceOverrideAppliesToLoosePiecesOnly() {
        val m = TestData.ace.copy(piecePriceOverride = 200)           // 1.20 -> 2 Tk per tablet
        assertEquals(600L, PriceCalculator.linePrice(m, SaleUnit.PIECE, 3))
        assertEquals(1200L, PriceCalculator.linePrice(m, SaleUnit.PIECE, 10))  // exact strip uses strip price
        assertEquals(1200L + 800L, PriceCalculator.linePrice(m, SaleUnit.PIECE, 14)) // 1 strip + 4 pcs
        assertEquals(1200L, PriceCalculator.linePrice(m, SaleUnit.STRIP, 1))
    }

    @Test fun boxOverride() {
        val m = seclo.copy(boxPriceOverride = 17000)
        assertEquals(17000L, PriceCalculator.linePrice(m, SaleUnit.BOX, 1))
        assertEquals(6000L, PriceCalculator.linePrice(m, SaleUnit.STRIP, 1))
    }

    @Test fun fractionalPiecePriceRoundsOncePerLine() {
        val m = napa.copy(piecesPerStrip = 3, stripPrice = 1000) // 3.333 Tk per piece
        assertEquals(333L, PriceCalculator.piecePrice(m))
        assertEquals(667L, PriceCalculator.linePrice(m, SaleUnit.PIECE, 2))
    }

    @Test fun discountsAndRoundingToTaka() {
        assertEquals(1000L, PriceCalculator.discountAmount(10000, DiscountType.PERCENT, 1000)) // 10 %
        assertEquals(500L, PriceCalculator.discountAmount(10000, DiscountType.FLAT, 500))
        assertEquals(10000L, PriceCalculator.discountAmount(10000, DiscountType.FLAT, 99999))
        assertEquals(0L, PriceCalculator.discountAmount(10000, null, 0))
        assertEquals(1300L, PriceCalculator.roundToTaka(1250))
        assertEquals(1200L, PriceCalculator.roundToTaka(1249))
        assertEquals(9000L, PriceCalculator.total(9050 + 0, DiscountType.FLAT, 50))
        assertEquals(0L, PriceCalculator.total(500, DiscountType.PERCENT, 10000))
    }

    @Test fun fullTestBill() {
        // piece, cut, full strip, box (1 of 3 strips), syrup and discount.
        val lines = listOf(
            PriceCalculator.linePrice(napa, SaleUnit.PIECE, 1),
            PriceCalculator.linePrice(napa, SaleUnit.PIECE, 5),
            PriceCalculator.linePrice(TestData.ace, SaleUnit.STRIP, 1),
            PriceCalculator.linePrice(seclo, SaleUnit.STRIP, 1),
            PriceCalculator.linePrice(napaSyrup, SaleUnit.UNIT, 1),
        )
        val sub = PriceCalculator.subtotal(lines)
        assertEquals(100L + 500L + 1200L + 6000L + 3500L, sub)
        assertEquals(10700L, PriceCalculator.total(sub, DiscountType.PERCENT, 500))
    }
}
