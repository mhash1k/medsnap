package com.pharmabill.app.device.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pharmabill.app.MainActivity
import com.pharmabill.app.R
import com.pharmabill.app.data.db.AppDatabase
import com.pharmabill.app.data.prefs.LanguageMirror
import com.pharmabill.app.util.LocaleHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerEntryPoint {
    fun database(): AppDatabase
}

/** Twice a day: notify when medicines are under their low-stock threshold or below zero. */
class LowStockWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java).database()
        val count = db.medicineDao().lowStockCount()
        if (count > 0) notify(count)
        return Result.success()
    }

    private fun notify(count: Int) {
        val ctx = LocaleHelper.wrap(applicationContext, LanguageMirror.get(applicationContext))
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel_stock), NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "low_stock"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_low_stock_title))
            .setContentText(ctx.resources.getQuantityString(R.plurals.notif_low_stock_text, count, count))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, n)
    }

    companion object {
        private const val CHANNEL = "low_stock"
        private const val NOTIF_ID = 1001

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<LowStockWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("low_stock_check", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
