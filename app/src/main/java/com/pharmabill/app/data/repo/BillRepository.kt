package com.pharmabill.app.data.repo

import androidx.room.withTransaction
import com.pharmabill.app.data.db.AppDatabase
import com.pharmabill.app.data.db.entities.BillEntity
import com.pharmabill.app.data.db.entities.BillLineEntity
import com.pharmabill.app.data.db.entities.StockMovementEntity
import com.pharmabill.app.domain.flags.BillRules
import com.pharmabill.app.domain.flags.LineCheck
import com.pharmabill.app.domain.model.BillStatus
import com.pharmabill.app.domain.model.DiscountType
import com.pharmabill.app.domain.model.MovementReason
import com.pharmabill.app.domain.pricing.PriceCalculator
import com.pharmabill.app.domain.stock.StockLedger
import com.pharmabill.app.domain.stock.StockState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ConfirmResult {
    data class Confirmed(val billId: Long, val billNo: Long, val wentNegative: List<Long>) : ConfirmResult
    data class Blocked(val needsChecking: Int) : ConfirmResult
    data object Empty : ConfirmResult
}

@Singleton
class BillRepository @Inject constructor(private val db: AppDatabase) {
    private val bills = db.billDao()
    private val lines = db.billLineDao()

    fun observeDrafts(): Flow<List<BillEntity>> = bills.observeDrafts()
    fun observeConfirmed(): Flow<List<BillEntity>> = bills.observeConfirmed()
    fun observeConfirmedSince(from: Long) = bills.observeConfirmedSince(from)
    fun observeBill(id: Long): Flow<BillEntity?> = bills.observe(id)
    fun observeLines(billId: Long): Flow<List<BillLineEntity>> = lines.observeForBill(billId)

    suspend fun bill(id: Long) = bills.get(id)
    suspend fun lines(billId: Long) = lines.forBill(billId)
    suspend fun line(id: Long) = lines.get(id)

    suspend fun createDraft(label: String? = null): Long = bills.insert(BillEntity(draftLabel = label?.ifBlank { null }))

    suspend fun setLabel(billId: Long, label: String?) {
        val b = bills.get(billId) ?: return
        bills.update(b.copy(draftLabel = label?.trim()?.ifBlank { null }))
    }

    suspend fun deleteDraft(billId: Long) = db.withTransaction {
        val b = bills.get(billId)
        if (b != null && b.status == BillStatus.DRAFT) {
            lines.deleteForBill(billId)
            bills.delete(billId)
        }
    }

    suspend fun addLine(line: BillLineEntity): Long = db.withTransaction {
        val id = lines.insert(line)
        recompute(line.billId)
        id
    }

    suspend fun updateLine(line: BillLineEntity) = db.withTransaction {
        lines.update(line)
        recompute(line.billId)
    }

    /** Returns the removed line so the UI can offer Undo. */
    suspend fun removeLine(id: Long): BillLineEntity? = db.withTransaction {
        val l = lines.get(id) ?: return@withTransaction null
        lines.delete(id)
        recompute(l.billId)
        l
    }

    suspend fun restoreLine(line: BillLineEntity) = db.withTransaction {
        lines.upsert(line)
        recompute(line.billId)
    }

    suspend fun setDiscount(billId: Long, type: DiscountType?, value: Long) = db.withTransaction {
        val b = bills.get(billId) ?: return@withTransaction
        bills.update(b.copy(discountType = type, discountValue = if (type == null) 0 else value.coerceAtLeast(0)))
        recompute(billId)
    }

    private suspend fun recompute(billId: Long) {
        val b = bills.get(billId) ?: return
        val sub = PriceCalculator.subtotal(lines.forBill(billId).map { it.lineTotal })
        bills.update(b.copy(subtotal = sub, total = PriceCalculator.total(sub, b.discountType, b.discountValue)))
    }

    /**
     * One transaction: check flags, assign bill number, write SALE movements, update cached stock and
     * set needs_stock_entry for anything that went below zero.
     */
    suspend fun confirm(billId: Long): ConfirmResult = db.withTransaction {
        val b = bills.get(billId) ?: return@withTransaction ConfirmResult.Empty
        val ls = lines.forBill(billId)
        if (ls.isEmpty()) return@withTransaction ConfirmResult.Empty
        val checks = ls.map { LineCheck(it.flag, it.medicineId) }
        if (!BillRules.canConfirm(checks)) return@withTransaction ConfirmResult.Blocked(BillRules.needsChecking(checks))

        val now = System.currentTimeMillis()
        val billNo = (bills.maxBillNo() ?: 0) + 1
        val sub = PriceCalculator.subtotal(ls.map { it.lineTotal })
        bills.update(
            b.copy(
                status = BillStatus.CONFIRMED, billNo = billNo, confirmedAt = now, subtotal = sub,
                total = PriceCalculator.total(sub, b.discountType, b.discountValue),
            ),
        )
        val negative = mutableListOf<Long>()
        val medDao = db.medicineDao()
        for ((medId, units) in ls.groupBy { it.medicineId!! }.mapValues { (_, v) -> v.sumOf { it.qtyInStockUnits } }) {
            val m = medDao.get(medId) ?: continue
            db.stockMovementDao().insert(StockMovementEntity(medicineId = medId, change = -units, reason = MovementReason.SALE, billId = billId, createdAt = now))
            val s = StockLedger.applySale(StockState(m.stockQty, m.needsStockEntry), units)
            medDao.setStock(medId, s.quantity, s.needsStockEntry, now)
            if (s.quantity < 0) negative += medId
        }
        db.scanDao().let { scanDao -> ls.mapNotNull { it.scanId }.distinct().forEach { scanDao.attachToBill(it, billId) } }
        ConfirmResult.Confirmed(billId, billNo, negative)
    }
}
