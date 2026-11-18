package com.pharmabill.app.data.repo

import com.pharmabill.app.data.db.dao.ScanDao
import com.pharmabill.app.data.db.dao.TrainingSampleDao
import com.pharmabill.app.data.db.entities.ScanEntity
import com.pharmabill.app.data.db.entities.TrainingSampleEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    private val scans: ScanDao,
    private val samples: TrainingSampleDao,
) {
    suspend fun insert(s: ScanEntity): Long = scans.insert(s)
    suspend fun get(id: Long) = scans.get(id)
    suspend fun addSample(t: TrainingSampleEntity) = samples.insert(t)
    fun observeImageCount() = scans.observeWithImageCount()
    fun observeSampleCount() = samples.observeCount()
}
