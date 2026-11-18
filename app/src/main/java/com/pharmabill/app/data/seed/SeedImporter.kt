package com.pharmabill.app.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.pharmabill.app.data.db.AppDatabase
import com.pharmabill.app.data.db.entities.AppMetaEntity
import com.pharmabill.app.data.db.entities.MedicineAliasEntity
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.db.entities.UserEntity
import com.pharmabill.app.domain.model.AliasSource
import com.pharmabill.app.domain.model.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports assets/seed/medicines.csv once per seed version. A newer seed merges by (brand, strength, form):
 * it adds missing medicines and refreshes descriptive fields, but never overwrites prices the owner edited
 * (price_verified) or any medicine the owner added.
 */
@Singleton
class SeedImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done

    suspend fun importIfNeeded() {
        try {
            val meta = db.appMetaDao()
            if (db.userDao().count() == 0) db.userDao().insert(UserEntity(name = "Owner", role = UserRole.OWNER))
            val current = meta.get(KEY_SEED_VERSION)?.toIntOrNull() ?: 0
            if (current >= SEED_VERSION) return
            val rows = context.assets.open("seed/medicines.csv").bufferedReader(Charsets.UTF_8).use { SeedCsv.parse(it.readLines()) }
            db.withTransaction {
                val dao = db.medicineDao()
                for (r in rows) {
                    val existing = dao.find(r.brand, r.strength, r.form.name)
                    if (existing == null) {
                        val id = dao.insert(
                            MedicineEntity(
                                brand = r.brand, generic = r.generic, strength = r.strength, form = r.form, kind = r.kind,
                                company = r.company, piecesPerStrip = r.piecesPerStrip, stripsPerBox = r.stripsPerBox,
                                stripPrice = r.stripPrice, unitPrice = r.unitPrice, banglaName = r.banglaName,
                                lowStockThreshold = if (r.kind.name == "STRIP") r.piecesPerStrip.toLong() * 2 else 2,
                                priceVerified = r.priceVerified,
                            ),
                        )
                        r.voiceAliases.forEach { db.aliasDao().insert(MedicineAliasEntity(medicineId = id, alias = it, source = AliasSource.MANUAL)) }
                    } else if (!existing.isUserAdded) {
                        val keepPrices = existing.priceVerified
                        dao.update(
                            existing.copy(
                                generic = r.generic, company = r.company, banglaName = existing.banglaName ?: r.banglaName,
                                piecesPerStrip = r.piecesPerStrip, stripsPerBox = r.stripsPerBox,
                                stripPrice = if (keepPrices) existing.stripPrice else r.stripPrice,
                                unitPrice = if (keepPrices) existing.unitPrice else r.unitPrice,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                        r.voiceAliases.forEach {
                            if (db.aliasDao().count(existing.id, it) == 0) {
                                db.aliasDao().insert(MedicineAliasEntity(medicineId = existing.id, alias = it, source = AliasSource.MANUAL))
                            }
                        }
                    }
                }
                meta.put(AppMetaEntity(KEY_SEED_VERSION, SEED_VERSION.toString()))
            }
        } finally {
            _done.value = true
        }
    }

    companion object {
        const val SEED_VERSION = 1
        const val KEY_SEED_VERSION = "seed_version"
        const val KEY_LAST_BACKUP = "last_backup"
    }
}
