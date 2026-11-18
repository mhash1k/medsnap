package com.pharmabill.app

import android.app.Application
import com.pharmabill.app.data.seed.SeedImporter
import com.pharmabill.app.device.work.LowStockWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PharmaBillApp : Application() {
    @Inject lateinit var seedImporter: SeedImporter

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Imports the bundled medicine CSV into Room in the background; billing works meanwhile.
        startupScope.launch { seedImporter.importIfNeeded() }
        LowStockWorker.schedule(this)
    }
}
