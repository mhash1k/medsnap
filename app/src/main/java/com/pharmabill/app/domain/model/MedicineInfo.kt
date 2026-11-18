package com.pharmabill.app.domain.model

/** The subset of a medicine that pricing and quantity rules need. Money is always paisa. */
data class MedicineInfo(
    val id: Long,
    val brand: String,
    val strength: String,
    val kind: MedicineKind,
    val piecesPerStrip: Int,
    val stripsPerBox: Int,
    val stripPrice: Long,
    val unitPrice: Long,
    val piecePriceOverride: Long? = null,
    val boxPriceOverride: Long? = null,
) {
    val displayName: String get() = if (strength.isBlank()) brand else "$brand $strength"
}
