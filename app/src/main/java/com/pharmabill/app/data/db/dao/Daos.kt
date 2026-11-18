package com.pharmabill.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pharmabill.app.data.db.entities.AppMetaEntity
import com.pharmabill.app.data.db.entities.BillEntity
import com.pharmabill.app.data.db.entities.BillLineEntity
import com.pharmabill.app.data.db.entities.MedicineAliasEntity
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.db.entities.ScanEntity
import com.pharmabill.app.data.db.entities.StockMovementEntity
import com.pharmabill.app.data.db.entities.TrainingSampleEntity
import com.pharmabill.app.data.db.entities.UserEntity
import kotlinx.coroutines.flow.Flow

data class MedicineQty(val medicineId: Long, val qty: Long)

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicine ORDER BY brand COLLATE NOCASE, strength")
    fun observeAll(): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicine")
    suspend fun getAll(): List<MedicineEntity>

    @Query("SELECT * FROM medicine WHERE id = :id")
    suspend fun get(id: Long): MedicineEntity?

    @Query("SELECT * FROM medicine WHERE id = :id")
    fun observe(id: Long): Flow<MedicineEntity?>

    @Query("SELECT * FROM medicine WHERE brand = :brand AND strength = :strength AND form = :form LIMIT 1")
    suspend fun find(brand: String, strength: String, form: String): MedicineEntity?

    @Insert
    suspend fun insert(m: MedicineEntity): Long

    @Update
    suspend fun update(m: MedicineEntity)

    @Query("SELECT * FROM medicine WHERE stock_qty < 0 OR stock_qty < low_stock_threshold ORDER BY stock_qty")
    fun observeLowStock(): Flow<List<MedicineEntity>>

    @Query("SELECT COUNT(*) FROM medicine WHERE stock_qty < 0 OR stock_qty < low_stock_threshold")
    fun observeLowStockCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM medicine WHERE stock_qty < 0 OR stock_qty < low_stock_threshold")
    suspend fun lowStockCount(): Int

    @Query("SELECT * FROM medicine WHERE needs_stock_entry = 1 ORDER BY stock_qty")
    fun observeNeedsStock(): Flow<List<MedicineEntity>>

    @Query("SELECT COUNT(*) FROM medicine WHERE needs_stock_entry = 1")
    fun observeNeedsStockCount(): Flow<Int>

    @Query("UPDATE medicine SET stock_qty = :qty, needs_stock_entry = :needs, updated_at = :now WHERE id = :id")
    suspend fun setStock(id: Long, qty: Long, needs: Boolean, now: Long)
}

@Dao
interface AliasDao {
    @Query("SELECT * FROM medicine_alias")
    fun observeAll(): Flow<List<MedicineAliasEntity>>

    @Query("SELECT * FROM medicine_alias WHERE medicine_id = :medicineId")
    suspend fun forMedicine(medicineId: Long): List<MedicineAliasEntity>

    @Query("SELECT COUNT(*) FROM medicine_alias WHERE medicine_id = :medicineId AND alias = :alias")
    suspend fun count(medicineId: Long, alias: String): Int

    @Insert
    suspend fun insert(a: MedicineAliasEntity): Long

    @Query("DELETE FROM medicine_alias WHERE medicine_id = :medicineId AND source = 'MANUAL'")
    suspend fun deleteManual(medicineId: Long)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bill WHERE status = 'DRAFT' ORDER BY created_at")
    fun observeDrafts(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bill WHERE status = 'CONFIRMED' ORDER BY confirmed_at DESC")
    fun observeConfirmed(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bill WHERE status = 'CONFIRMED' AND confirmed_at >= :from ORDER BY confirmed_at")
    fun observeConfirmedSince(from: Long): Flow<List<BillEntity>>

    @Query("SELECT * FROM bill WHERE id = :id")
    suspend fun get(id: Long): BillEntity?

    @Query("SELECT * FROM bill WHERE id = :id")
    fun observe(id: Long): Flow<BillEntity?>

    @Insert
    suspend fun insert(b: BillEntity): Long

    @Update
    suspend fun update(b: BillEntity)

    @Query("DELETE FROM bill WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT MAX(bill_no) FROM bill")
    suspend fun maxBillNo(): Long?
}

@Dao
interface BillLineDao {
    @Query("SELECT * FROM bill_line WHERE bill_id = :billId ORDER BY id")
    fun observeForBill(billId: Long): Flow<List<BillLineEntity>>

    @Query("SELECT * FROM bill_line WHERE bill_id = :billId ORDER BY id")
    suspend fun forBill(billId: Long): List<BillLineEntity>

    @Query("SELECT * FROM bill_line WHERE id = :id")
    suspend fun get(id: Long): BillLineEntity?

    @Insert
    suspend fun insert(l: BillLineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(l: BillLineEntity): Long

    @Update
    suspend fun update(l: BillLineEntity)

    @Query("DELETE FROM bill_line WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bill_line WHERE bill_id = :billId")
    suspend fun deleteForBill(billId: Long)
}

@Dao
interface ScanDao {
    @Insert
    suspend fun insert(s: ScanEntity): Long

    @Update
    suspend fun update(s: ScanEntity)

    @Query("SELECT * FROM scan WHERE id = :id")
    suspend fun get(id: Long): ScanEntity?

    @Query("SELECT * FROM scan ORDER BY id")
    suspend fun all(): List<ScanEntity>

    @Query("SELECT * FROM scan WHERE exported = 0 AND image_path IS NOT NULL ORDER BY id")
    suspend fun unexportedWithImage(): List<ScanEntity>

    @Query("SELECT * FROM scan WHERE exported = 1 AND image_path IS NOT NULL")
    suspend fun exportedWithImage(): List<ScanEntity>

    @Query("UPDATE scan SET exported = 1 WHERE id IN (:ids)")
    suspend fun markExported(ids: List<Long>)

    @Query("UPDATE scan SET image_path = NULL WHERE id = :id")
    suspend fun clearImage(id: Long)

    @Query("UPDATE scan SET bill_id = :billId WHERE id = :id")
    suspend fun attachToBill(id: Long, billId: Long)

    @Query("SELECT COUNT(*) FROM scan WHERE image_path IS NOT NULL")
    fun observeWithImageCount(): Flow<Int>
}

@Dao
interface TrainingSampleDao {
    @Insert
    suspend fun insert(t: TrainingSampleEntity): Long

    @Query("SELECT * FROM training_sample WHERE scan_id IN (:scanIds)")
    suspend fun forScans(scanIds: List<Long>): List<TrainingSampleEntity>

    @Query("UPDATE training_sample SET exported = 1 WHERE scan_id IN (:scanIds)")
    suspend fun markExported(scanIds: List<Long>)

    @Query("SELECT COUNT(*) FROM training_sample")
    fun observeCount(): Flow<Int>
}

@Dao
interface StockMovementDao {
    @Insert
    suspend fun insert(m: StockMovementEntity): Long

    @Query("SELECT * FROM stock_movement WHERE medicine_id = :medicineId ORDER BY created_at DESC LIMIT :limit")
    fun observeForMedicine(medicineId: Long, limit: Int): Flow<List<StockMovementEntity>>

    @Query("SELECT COALESCE(SUM(change), 0) FROM stock_movement WHERE medicine_id = :medicineId")
    suspend fun sumForMedicine(medicineId: Long): Long

    @Query(
        "SELECT medicine_id AS medicineId, -SUM(change) AS qty FROM stock_movement " +
            "WHERE reason = 'SALE' AND created_at >= :since GROUP BY medicine_id ORDER BY qty DESC LIMIT :limit"
    )
    fun observeTopSold(since: Long, limit: Int): Flow<List<MedicineQty>>
}

@Dao
interface UserDao {
    @Query("SELECT COUNT(*) FROM user")
    suspend fun count(): Int

    @Insert
    suspend fun insert(u: UserEntity): Long
}

@Dao
interface AppMetaDao {
    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: AppMetaEntity)
}
