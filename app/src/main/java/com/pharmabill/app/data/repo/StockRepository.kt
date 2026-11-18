package com.pharmabill.app.data.repo

import androidx.room.withTransaction
import com.pharmabill.app.data.db.AppDatabase
import com.pharmabill.app.data.db.entities.StockMovementEntity
import com.pharmabill.app.domain.model.MovementReason
import com.pharmabill.app.domain.stock.StockLedger
import com.pharmabill.app.domain.stock.StockState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepository @Inject constructor(private val db: AppDatabase) {

    fun observeMovements(medicineId: Long) = db.stockMovementDao().observeForMedicine(medicineId, 50)

    /** Stock-in: +units, reconciles negative stock and clears the badge at zero or above. */
    suspend fun stockIn(medicineId: Long, units: Long, note: String?): StockState? = change(medicineId, units, MovementReason.STOCK_IN, note)

    /** Manual correction to an exact count. */
    suspend fun setCount(medicineId: Long, newCount: Long, note: String?): StockState? = db.withTransaction {
        val m = db.medicineDao().get(medicineId) ?: return@withTransaction null
        val delta = newCount - m.stockQty
        if (delta == 0L) return@withTransaction StockState(m.stockQty, m.needsStockEntry)
        changeInTx(medicineId, delta, MovementReason.ADJUST, note)
    }

    private suspend fun change(medicineId: Long, delta: Long, reason: MovementReason, note: String?): StockState? =
        db.withTransaction { changeInTx(medicineId, delta, reason, note) }

    private suspend fun changeInTx(medicineId: Long, delta: Long, reason: MovementReason, note: String?): StockState? {
        val m = db.medicineDao().get(medicineId) ?: return null
        val now = System.currentTimeMillis()
        db.stockMovementDao().insert(StockMovementEntity(medicineId = medicineId, change = delta, reason = reason, note = note?.ifBlank { null }, createdAt = now))
        val s = StockLedger.apply(StockState(m.stockQty, m.needsStockEntry), delta)
        db.medicineDao().setStock(medicineId, s.quantity, s.needsStockEntry, now)
        return s
    }
}
