package com.pharmabill.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.pharmabill.app.data.db.AppDatabase
import com.pharmabill.app.data.db.entities.AppMetaEntity
import com.pharmabill.app.data.prefs.SETTINGS_STORE_NAME
import com.pharmabill.app.data.seed.SeedImporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Backup = one .zip with the Room database and the settings file. Restore replaces both and restarts the app. */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private val dbFile: File get() = context.getDatabasePath(AppDatabase.NAME)
    private val prefsFile: File get() = File(context.filesDir, "datastore/$SETTINGS_STORE_NAME.preferences_pb")

    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        db.appMetaDao().put(AppMetaEntity(SeedImporter.KEY_LAST_BACKUP, System.currentTimeMillis().toString()))
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val out = context.contentResolver.openOutputStream(target) ?: error("Cannot open file")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DB_ENTRY)); dbFile.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            if (prefsFile.exists()) {
                zip.putNextEntry(ZipEntry(PREFS_ENTRY)); prefsFile.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }

    /** Returns false if the file is not a PharmaBill backup. On success the process restarts. */
    suspend fun restore(source: Uri): Boolean = withContext(Dispatchers.IO) {
        val tmpDir = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var hasDb = false
        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (e.name == DB_ENTRY || e.name == PREFS_ENTRY) {
                        File(tmpDir, e.name).outputStream().use { zip.copyTo(it) }
                        if (e.name == DB_ENTRY) hasDb = true
                    }
                    e = zip.nextEntry
                }
            }
        } ?: return@withContext false
        val restoredDb = File(tmpDir, DB_ENTRY)
        if (!hasDb || !isSqlite(restoredDb)) return@withContext false

        db.close()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        restoredDb.copyTo(dbFile, overwrite = true)
        File(tmpDir, PREFS_ENTRY).takeIf { it.exists() }?.let { prefsFile.parentFile?.mkdirs(); it.copyTo(prefsFile, overwrite = true) }
        tmpDir.deleteRecursively()
        restart()
        true
    }

    private fun isSqlite(f: File): Boolean = f.length() > 100 && f.inputStream().use { s ->
        val header = ByteArray(15); s.read(header); String(header) == "SQLite format 3"
    }

    private fun restart() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (intent != null) context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    companion object {
        const val DB_ENTRY = "pharmabill.db"
        const val PREFS_ENTRY = "settings.preferences_pb"
    }
}
