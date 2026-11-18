package com.pharmabill.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pharmabill.app.data.db.dao.AliasDao
import com.pharmabill.app.data.db.dao.AppMetaDao
import com.pharmabill.app.data.db.dao.BillDao
import com.pharmabill.app.data.db.dao.BillLineDao
import com.pharmabill.app.data.db.dao.MedicineDao
import com.pharmabill.app.data.db.dao.ScanDao
import com.pharmabill.app.data.db.dao.StockMovementDao
import com.pharmabill.app.data.db.dao.TrainingSampleDao
import com.pharmabill.app.data.db.dao.UserDao
import com.pharmabill.app.data.db.entities.AppMetaEntity
import com.pharmabill.app.data.db.entities.BillEntity
import com.pharmabill.app.data.db.entities.BillLineEntity
import com.pharmabill.app.data.db.entities.MedicineAliasEntity
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.db.entities.ScanEntity
import com.pharmabill.app.data.db.entities.StockMovementEntity
import com.pharmabill.app.data.db.entities.TrainingSampleEntity
import com.pharmabill.app.data.db.entities.UserEntity

@Database(
    entities = [
        MedicineEntity::class, MedicineAliasEntity::class, BillEntity::class, BillLineEntity::class,
        ScanEntity::class, TrainingSampleEntity::class, StockMovementEntity::class, UserEntity::class,
        AppMetaEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun aliasDao(): AliasDao
    abstract fun billDao(): BillDao
    abstract fun billLineDao(): BillLineDao
    abstract fun scanDao(): ScanDao
    abstract fun trainingSampleDao(): TrainingSampleDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun userDao(): UserDao
    abstract fun appMetaDao(): AppMetaDao

    companion object {
        const val NAME = "pharmabill.db"
    }
}
