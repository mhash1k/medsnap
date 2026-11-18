package com.pharmabill.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pharmabill.app.domain.flags.FlagThresholds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

const val SETTINGS_STORE_NAME = "settings"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_STORE_NAME)

data class AppSettings(
    val language: String = LANG_EN,
    val setupDone: Boolean = false,
    val shopName: String = "",
    val shopAddress: String = "",
    val shopPhone: String = "",
    val printerAddress: String? = null,
    val printerName: String? = null,
    val thresholds: FlagThresholds = FlagThresholds(),
    val oneMedicineMode: Boolean = false,
    val collectTrainingData: Boolean = true,
    val speakAnswers: Boolean = false,
    val draftReminderHours: Int = 12,
    val activeDraftId: Long? = null,
) {
    val isBangla: Boolean get() = language == LANG_BN

    companion object {
        const val LANG_EN = "en"
        const val LANG_BN = "bn"
    }
}

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val language = stringPreferencesKey("language")
        val setupDone = booleanPreferencesKey("setup_done")
        val shopName = stringPreferencesKey("shop_name")
        val shopAddress = stringPreferencesKey("shop_address")
        val shopPhone = stringPreferencesKey("shop_phone")
        val printerAddress = stringPreferencesKey("printer_address")
        val printerName = stringPreferencesKey("printer_name")
        val green = doublePreferencesKey("th_green")
        val yellow = doublePreferencesKey("th_yellow")
        val lead = doublePreferencesKey("th_lead")
        val modelConf = doublePreferencesKey("th_model_conf")
        val oneMedicine = booleanPreferencesKey("one_medicine_mode")
        val collect = booleanPreferencesKey("collect_training_data")
        val speak = booleanPreferencesKey("speak_answers")
        val reminderHours = intPreferencesKey("draft_reminder_hours")
        val activeDraft = longPreferencesKey("active_draft_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = FlagThresholds()
        AppSettings(
            language = p[K.language] ?: AppSettings.LANG_EN,
            setupDone = p[K.setupDone] ?: false,
            shopName = p[K.shopName] ?: "",
            shopAddress = p[K.shopAddress] ?: "",
            shopPhone = p[K.shopPhone] ?: "",
            printerAddress = p[K.printerAddress],
            printerName = p[K.printerName],
            thresholds = FlagThresholds(
                green = p[K.green] ?: d.green,
                yellow = p[K.yellow] ?: d.yellow,
                minLead = p[K.lead] ?: d.minLead,
                modelConfidence = p[K.modelConf] ?: d.modelConfidence,
            ),
            oneMedicineMode = p[K.oneMedicine] ?: false,
            collectTrainingData = p[K.collect] ?: true,
            speakAnswers = p[K.speak] ?: false,
            draftReminderHours = p[K.reminderHours] ?: 12,
            activeDraftId = p[K.activeDraft]?.takeIf { it > 0 },
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[K.language] = lang }
        LanguageMirror.save(context, lang)
    }

    suspend fun saveShop(name: String, address: String, phone: String) {
        context.dataStore.edit {
            it[K.shopName] = name.trim()
            it[K.shopAddress] = address.trim()
            it[K.shopPhone] = phone.trim()
        }
    }

    suspend fun setSetupDone() { context.dataStore.edit { it[K.setupDone] = true } }

    suspend fun setPrinter(address: String?, name: String?) {
        context.dataStore.edit {
            if (address == null) { it.remove(K.printerAddress); it.remove(K.printerName) } else {
                it[K.printerAddress] = address
                it[K.printerName] = name ?: address
            }
        }
    }

    suspend fun setThresholds(t: FlagThresholds) {
        context.dataStore.edit {
            it[K.green] = t.green; it[K.yellow] = t.yellow; it[K.lead] = t.minLead; it[K.modelConf] = t.modelConfidence
        }
    }

    suspend fun setOneMedicineMode(v: Boolean) { context.dataStore.edit { it[K.oneMedicine] = v } }
    suspend fun setCollectTrainingData(v: Boolean) { context.dataStore.edit { it[K.collect] = v } }
    suspend fun setSpeakAnswers(v: Boolean) { context.dataStore.edit { it[K.speak] = v } }
    suspend fun setDraftReminderHours(v: Int) { context.dataStore.edit { it[K.reminderHours] = v.coerceIn(1, 72) } }

    suspend fun setActiveDraft(id: Long?) {
        context.dataStore.edit { if (id == null) it.remove(K.activeDraft) else it[K.activeDraft] = id }
    }
}

/** Plain SharedPreferences copy of the language so the Activity can apply it synchronously at start. */
object LanguageMirror {
    private const val FILE = "lang_mirror"
    private const val KEY = "lang"

    fun get(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, AppSettings.LANG_EN) ?: AppSettings.LANG_EN

    fun save(context: Context, lang: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply()
    }
}
