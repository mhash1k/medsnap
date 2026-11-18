package com.pharmabill.app.di

import android.content.Context
import androidx.room.Room
import com.pharmabill.app.ai.ocr.MlKitTextRecognizer
import com.pharmabill.app.ai.ocr.TextRecognizer
import com.pharmabill.app.data.db.AppDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides fun medicineDao(db: AppDatabase) = db.medicineDao()
    @Provides fun aliasDao(db: AppDatabase) = db.aliasDao()
    @Provides fun billDao(db: AppDatabase) = db.billDao()
    @Provides fun billLineDao(db: AppDatabase) = db.billLineDao()
    @Provides fun scanDao(db: AppDatabase) = db.scanDao()
    @Provides fun trainingSampleDao(db: AppDatabase) = db.trainingSampleDao()
    @Provides fun stockMovementDao(db: AppDatabase) = db.stockMovementDao()

    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {
    @Binds
    @Singleton
    abstract fun textRecognizer(impl: MlKitTextRecognizer): TextRecognizer
}
