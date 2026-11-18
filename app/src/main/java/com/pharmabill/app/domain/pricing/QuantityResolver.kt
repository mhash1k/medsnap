package com.pharmabill.app.domain.pricing

import com.pharmabill.app.domain.model.MedicineInfo
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.domain.model.SaleUnit

data class StripsAndPieces(val strips: Int, val pieces: Int)

enum class BoxChipType { FULL_BOX, STRIPS, CUSTOM }

data class BoxChip(val type: BoxChipType, val strips: Int, val unit: SaleUnit, val quantity: Int)

data class Quantity(val unit: SaleUnit, val quantity: Int)

object QuantityResolver {

    /** Stock is kept in pieces (strip medicines) or units (everything else). */
    fun stockUnits(m: MedicineInfo, unit: SaleUnit, quantity: Int): Long {
        val q = quantity.toLong()
        val pps = m.piecesPerStrip.coerceAtLeast(1).toLong()
        val spb = m.stripsPerBox.coerceAtLeast(1).toLong()
        return when (unit) {
            SaleUnit.PIECE -> q
            SaleUnit.STRIP -> q * pps
            SaleUnit.BOX -> if (m.kind == MedicineKind.UNIT) q * spb else q * pps * spb
            SaleUnit.UNIT -> q
        }
    }

    fun defaultUnit(kind: MedicineKind): SaleUnit =
        if (kind == MedicineKind.STRIP) SaleUnit.PIECE else SaleUnit.UNIT

    fun allowedUnits(kind: MedicineKind): List<SaleUnit> =
        if (kind == MedicineKind.STRIP) listOf(SaleUnit.PIECE, SaleUnit.STRIP, SaleUnit.BOX) else listOf(SaleUnit.UNIT)

    /** Turns a detected tablet count into a sale quantity: full strips when the count is an exact multiple. */
    fun fromDetectedCount(m: MedicineInfo, count: Int): Quantity {
        if (m.kind == MedicineKind.UNIT) return Quantity(SaleUnit.UNIT, 1)
        val pps = m.piecesPerStrip.coerceAtLeast(1)
        return if (count > 0 && count % pps == 0) Quantity(SaleUnit.STRIP, count / pps)
        else Quantity(SaleUnit.PIECE, count.coerceAtLeast(0))
    }

    fun split(m: MedicineInfo, pieces: Int): StripsAndPieces {
        val pps = m.piecesPerStrip.coerceAtLeast(1)
        return StripsAndPieces(pieces / pps, pieces % pps)
    }

    fun halfStripPieces(m: MedicineInfo): Int = (m.piecesPerStrip.coerceAtLeast(1) / 2).coerceAtLeast(1)

    /** Chips for a box line, built from strips_per_box: Full box, 1, 2, half box strips, Custom. */
    fun boxChips(m: MedicineInfo): List<BoxChip> {
        val spb = m.stripsPerBox.coerceAtLeast(1)
        val chips = mutableListOf(BoxChip(BoxChipType.FULL_BOX, spb, SaleUnit.BOX, 1))
        listOf(1, 2, spb / 2).filter { it in 1 until spb }.distinct().sorted().forEach {
            chips += BoxChip(BoxChipType.STRIPS, it, SaleUnit.STRIP, it)
        }
        chips += BoxChip(BoxChipType.CUSTOM, 0, SaleUnit.PIECE, 0)
        return chips
    }

    /** Applies +/- to a line. Pieces step by 1, other units step by 1 of that unit; never below 1. */
    fun step(quantity: Int, delta: Int): Int = (quantity + delta).coerceAtLeast(1)
}
