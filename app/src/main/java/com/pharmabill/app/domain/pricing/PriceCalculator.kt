package com.pharmabill.app.domain.pricing

import com.pharmabill.app.domain.model.DiscountType
import com.pharmabill.app.domain.model.MedicineInfo
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.domain.model.SaleUnit

/**
 * All money is Long paisa (1 Taka = 100 paisa).
 * Percent discounts are stored in basis points (100 = 1 %).
 */
object PriceCalculator {

    /** Rounded division for non-negative values (half up). */
    fun divRound(a: Long, b: Long): Long {
        require(b > 0) { "divisor must be positive" }
        return if (a >= 0) (a * 2 + b) / (b * 2) else -((-a * 2 + b) / (b * 2))
    }

    fun piecePrice(m: MedicineInfo): Long {
        m.piecePriceOverride?.let { return it }
        if (m.kind == MedicineKind.UNIT) return m.unitPrice
        val pps = m.piecesPerStrip.coerceAtLeast(1)
        return divRound(m.stripPrice, pps.toLong())
    }

    fun boxPrice(m: MedicineInfo): Long =
        m.boxPriceOverride ?: (m.stripPrice * m.stripsPerBox.coerceAtLeast(1))

    /**
     * Price of [quantity] of [unit]. Full strips are charged at strip price and full boxes at box
     * price, so owner overrides apply correctly. Loose pieces are charged per piece (override if set,
     * otherwise strip price split exactly and rounded once per line).
     */
    fun linePrice(m: MedicineInfo, unit: SaleUnit, quantity: Int): Long {
        if (quantity <= 0) return 0
        val q = quantity.toLong()
        return when (unit) {
            SaleUnit.UNIT -> q * m.unitPrice
            SaleUnit.STRIP -> q * m.stripPrice
            SaleUnit.BOX -> q * boxPrice(m)
            SaleUnit.PIECE -> piecesPrice(m, quantity)
        }
    }

    fun piecesPrice(m: MedicineInfo, pieces: Int): Long {
        if (pieces <= 0) return 0
        if (m.kind == MedicineKind.UNIT) return pieces.toLong() * m.unitPrice
        val pps = m.piecesPerStrip.coerceAtLeast(1)
        val strips = pieces / pps
        val rest = pieces % pps
        val restPrice = m.piecePriceOverride?.let { it * rest }
            ?: divRound(m.stripPrice * rest, pps.toLong())
        return strips * m.stripPrice + restPrice
    }

    /** Unit price stored on the bill line for display. */
    fun unitPriceFor(m: MedicineInfo, unit: SaleUnit): Long = when (unit) {
        SaleUnit.UNIT -> m.unitPrice
        SaleUnit.STRIP -> m.stripPrice
        SaleUnit.BOX -> boxPrice(m)
        SaleUnit.PIECE -> piecePrice(m)
    }

    fun subtotal(lineTotals: List<Long>): Long = lineTotals.sum()

    fun discountAmount(subtotal: Long, type: DiscountType?, value: Long): Long {
        if (type == null || value <= 0 || subtotal <= 0) return 0
        val raw = when (type) {
            DiscountType.PERCENT -> divRound(subtotal * value.coerceAtMost(10_000), 10_000)
            DiscountType.FLAT -> value
        }
        return raw.coerceIn(0, subtotal)
    }

    /** Rounds to the nearest whole Taka (half up). */
    fun roundToTaka(paisa: Long): Long = divRound(paisa, 100) * 100

    fun total(subtotal: Long, type: DiscountType?, value: Long): Long =
        roundToTaka(subtotal - discountAmount(subtotal, type, value)).coerceAtLeast(0)
}
