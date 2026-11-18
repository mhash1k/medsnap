package com.pharmabill.app.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pharmabill.app.domain.model.AliasSource
import com.pharmabill.app.domain.model.BillStatus
import com.pharmabill.app.domain.model.DiscountType
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.LineSource
import com.pharmabill.app.domain.model.MedicineForm
import com.pharmabill.app.domain.model.MedicineInfo
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.domain.model.MovementReason
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.model.UserRole

@Entity(tableName = "medicine", indices = [Index("brand"), Index("needs_stock_entry")])
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val generic: String = "",
    val strength: String = "",
    val form: MedicineForm = MedicineForm.TABLET,
    val kind: MedicineKind = MedicineKind.STRIP,
    val company: String = "",
    @ColumnInfo(name = "pieces_per_strip") val piecesPerStrip: Int = 10,
    @ColumnInfo(name = "strips_per_box") val stripsPerBox: Int = 1,
    @ColumnInfo(name = "strip_price") val stripPrice: Long = 0,
    @ColumnInfo(name = "unit_price") val unitPrice: Long = 0,
    @ColumnInfo(name = "piece_price_override") val piecePriceOverride: Long? = null,
    @ColumnInfo(name = "box_price_override") val boxPriceOverride: Long? = null,
    @ColumnInfo(name = "stock_qty") val stockQty: Long = 0,
    @ColumnInfo(name = "low_stock_threshold") val lowStockThreshold: Long = 0,
    @ColumnInfo(name = "needs_stock_entry") val needsStockEntry: Boolean = false,
    @ColumnInfo(name = "bangla_name") val banglaName: String? = null,
    @ColumnInfo(name = "is_user_added") val isUserAdded: Boolean = false,
    @ColumnInfo(name = "price_verified") val priceVerified: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toInfo() = MedicineInfo(id, brand, strength, kind, piecesPerStrip, stripsPerBox, stripPrice, unitPrice, piecePriceOverride, boxPriceOverride)
    val displayName: String get() = if (strength.isBlank()) brand else "$brand $strength"
    val isLowStock: Boolean get() = stockQty < 0 || stockQty < lowStockThreshold
}

@Entity(tableName = "medicine_alias", indices = [Index("alias"), Index("medicine_id")])
data class MedicineAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "medicine_id") val medicineId: Long,
    val alias: String,
    val source: AliasSource,
)

@Entity(tableName = "bill", indices = [Index("status"), Index("created_at"), Index("bill_no")])
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bill_no") val billNo: Long? = null,
    val status: BillStatus = BillStatus.DRAFT,
    @ColumnInfo(name = "draft_label") val draftLabel: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "confirmed_at") val confirmedAt: Long? = null,
    val subtotal: Long = 0,
    @ColumnInfo(name = "discount_type") val discountType: DiscountType? = null,
    @ColumnInfo(name = "discount_value") val discountValue: Long = 0,
    val total: Long = 0,
    @ColumnInfo(name = "created_by_user_id") val createdByUserId: Long = 1,
)

@Entity(tableName = "bill_line", indices = [Index("bill_id"), Index("medicine_id")])
data class BillLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bill_id") val billId: Long,
    @ColumnInfo(name = "medicine_id") val medicineId: Long?,
    @ColumnInfo(name = "sale_unit") val saleUnit: SaleUnit,
    val quantity: Int,
    @ColumnInfo(name = "qty_in_stock_units") val qtyInStockUnits: Long,
    @ColumnInfo(name = "unit_price") val unitPrice: Long,
    @ColumnInfo(name = "line_total") val lineTotal: Long,
    val flag: Flag,
    val source: LineSource,
    @ColumnInfo(name = "scan_id") val scanId: Long? = null,
    @ColumnInfo(name = "ocr_text") val ocrText: String? = null,
    @ColumnInfo(name = "detected_count") val detectedCount: Int = 0,
    val confidence: Double = 1.0,
    /** Comma-separated top-3 medicine ids kept for yellow lines (voice and photo suggestions). */
    @ColumnInfo(name = "candidate_ids") val candidateIds: String = "",
    /** Name snapshot so receipts stay correct if the medicine is renamed later. */
    @ColumnInfo(name = "display_name") val displayName: String = "",
    /** True for box lines until a chip is tapped. */
    @ColumnInfo(name = "is_box") val isBox: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    val candidates: List<Long> get() = candidateIds.split(',').mapNotNull { it.trim().toLongOrNull() }
}

@Entity(tableName = "scan", indices = [Index("bill_id")])
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bill_id") val billId: Long? = null,
    @ColumnInfo(name = "image_path") val imagePath: String? = null,
    @ColumnInfo(name = "image_width") val imageWidth: Int = 0,
    @ColumnInfo(name = "image_height") val imageHeight: Int = 0,
    @ColumnInfo(name = "model_version") val modelVersion: String,
    @ColumnInfo(name = "ocr_raw_json") val ocrRawJson: String,
    @ColumnInfo(name = "detections_json") val detectionsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    val exported: Boolean = false,
)

@Entity(tableName = "training_sample", indices = [Index("scan_id")])
data class TrainingSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "scan_id") val scanId: Long,
    @ColumnInfo(name = "corrected_json") val correctedJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    val exported: Boolean = false,
)

@Entity(tableName = "stock_movement", indices = [Index("medicine_id"), Index("created_at")])
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "medicine_id") val medicineId: Long,
    val change: Long,
    val reason: MovementReason,
    @ColumnInfo(name = "bill_id") val billId: Long? = null,
    val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: UserRole,
    @ColumnInfo(name = "pin_hash") val pinHash: String? = null,
    val active: Boolean = true,
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
