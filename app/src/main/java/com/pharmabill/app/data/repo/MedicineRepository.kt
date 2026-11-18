package com.pharmabill.app.data.repo

import com.pharmabill.app.data.db.dao.AliasDao
import com.pharmabill.app.data.db.dao.MedicineDao
import com.pharmabill.app.data.db.dao.StockMovementDao
import com.pharmabill.app.data.db.entities.MedicineAliasEntity
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.domain.model.AliasSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicineRepository @Inject constructor(
    private val dao: MedicineDao,
    private val aliasDao: AliasDao,
    private val movementDao: StockMovementDao,
    private val matcher: MatcherProvider,
) {
    fun observeAll(): Flow<List<MedicineEntity>> = dao.observeAll()
    fun observe(id: Long): Flow<MedicineEntity?> = dao.observe(id)
    fun observeLowStock() = dao.observeLowStock()
    fun observeLowStockCount() = dao.observeLowStockCount()
    fun observeNeedsStock() = dao.observeNeedsStock()
    fun observeNeedsStockCount() = dao.observeNeedsStockCount()

    suspend fun get(id: Long): MedicineEntity? = dao.get(id)

    suspend fun search(query: String, limit: Int = 30): List<MedicineEntity> {
        val snap = matcher.current()
        return snap.matcher.search(query, limit).mapNotNull { snap.medicines[it] }
    }

    /** Insert or update. Editing a price marks it verified, which clears the "check price" badge. */
    suspend fun save(m: MedicineEntity, aliases: List<String>? = null): Long {
        val id = if (m.id == 0L) dao.insert(m.copy(updatedAt = System.currentTimeMillis())) else {
            dao.update(m.copy(updatedAt = System.currentTimeMillis())); m.id
        }
        if (aliases != null) {
            aliasDao.deleteManual(id)
            aliases.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach {
                aliasDao.insert(MedicineAliasEntity(medicineId = id, alias = it, source = AliasSource.MANUAL))
            }
        }
        return id
    }

    suspend fun aliases(medicineId: Long): List<MedicineAliasEntity> = aliasDao.forMedicine(medicineId)

    /** Learned spelling: added when staff corrects a voice or OCR match. */
    suspend fun learnAlias(medicineId: Long, alias: String, source: AliasSource) {
        val a = alias.trim().lowercase()
        if (a.length < 2 || a.length > 40) return
        val m = dao.get(medicineId) ?: return
        if (a == m.brand.lowercase()) return
        if (aliasDao.count(medicineId, a) == 0) aliasDao.insert(MedicineAliasEntity(medicineId = medicineId, alias = a, source = source))
    }

    /** Ten most sold medicines this week; falls back to the first medicines when there are no sales yet. */
    fun observeFrequent(since: Long): Flow<List<MedicineEntity>> =
        combine(movementDao.observeTopSold(since, 10), dao.observeAll()) { top, all ->
            val byId = all.associateBy { it.id }
            val sold = top.mapNotNull { byId[it.medicineId] }
            if (sold.size >= 10) sold else (sold + all.filter { m -> sold.none { it.id == m.id } }.take(10 - sold.size))
        }
}
