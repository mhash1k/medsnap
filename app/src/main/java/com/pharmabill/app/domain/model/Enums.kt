package com.pharmabill.app.domain.model

enum class MedicineKind { STRIP, UNIT }

enum class MedicineForm {
    TABLET, CAPSULE, SYRUP, INJECTION, CREAM, DROPS, OTHER;

    val defaultKind: MedicineKind
        get() = if (this == TABLET || this == CAPSULE) MedicineKind.STRIP else MedicineKind.UNIT

    companion object {
        fun parse(value: String): MedicineForm =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: OTHER
    }
}

enum class SaleUnit { PIECE, STRIP, BOX, UNIT }

/** Ordered from best to worst so that [worst] can compare ordinals. */
enum class Flag {
    GREEN, YELLOW, RED;

    fun worst(other: Flag): Flag = if (other.ordinal > ordinal) other else this
}

enum class LineSource { PHOTO, VOICE, MANUAL }
enum class BillStatus { DRAFT, CONFIRMED, VOID }
enum class DiscountType { PERCENT, FLAT }
enum class MovementReason { SALE, STOCK_IN, ADJUST, RETURN }
enum class AliasSource { OCR, VOICE, MANUAL }
enum class UserRole { OWNER, STAFF }

enum class DetClass {
    TABLET, CAPSULE, BOX;

    val isPiece: Boolean get() = this != BOX

    companion object {
        fun fromIndex(index: Int): DetClass? = entries.getOrNull(index)
    }
}
