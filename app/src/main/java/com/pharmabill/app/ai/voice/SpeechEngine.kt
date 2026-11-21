package com.pharmabill.app.ai.voice

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface SpeechEvent {
    data object Ready : SpeechEvent
    data class Partial(val text: String) : SpeechEvent
    data class Results(val alternatives: List<String>, val language: String) : SpeechEvent
    data class Error(val code: Int) : SpeechEvent
}

data class VoiceStatus(
    val recognizerAvailable: Boolean,
    /** null when the Android version cannot report offline packs (below Android 13). */
    val banglaOffline: Boolean?,
    val englishOffline: Boolean?,
)

/** Android SpeechRecognizer: bn-BD with 5 alternatives and offline preference, falls back to en-US. */
@Singleton
class SpeechEngine @Inject constructor(@ApplicationContext private val context: Context) {

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** True when the phone has a working internet connection right now. */
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Prefer Google's recognition service when it is installed: many phones (Samsung, Xiaomi, ...) ship a
     * different default service that does not handle Bangla or fails silently.
     */
    private fun preferredService(): ComponentName? = runCatching {
        val services = context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .mapNotNull { it.serviceInfo }
        val google = services.firstOrNull { it.packageName == GOOGLE_APP } ?: services.firstOrNull { it.packageName.startsWith("com.google.android") }
        google?.let { ComponentName(it.packageName, it.name) }
    }.getOrNull()

    /** Intent for the system "Speak now" dialog, the last fallback when the in-app recognizer cannot start. */
    fun systemDialogIntent(lang: String = LANG_BN): Intent? {
        val i = intent(lang, online = true).apply { removeExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS) }
        return if (i.resolveActivity(context.packageManager) != null) i else null
    }

    private fun intent(lang: String, online: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        // Forcing offline made recognition fail on phones without the Bangla offline pack, even with internet.
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !online)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
    }

    /**
     * Must be collected on the main thread. Tries, in order: Google service in Bangla, then English if Bangla is
     * missing, then the phone's default service; a fresh recognizer is retried once on busy/client/server errors.
     * Emits [SpeechEvent.Error] with [ERROR_USE_SYSTEM_DIALOG] when only the system dialog is left.
     */
    fun listen(primary: String = LANG_BN): Flow<SpeechEvent> = callbackFlow {
        var recognizer: SpeechRecognizer? = null
        var lang = primary
        var useGoogle = preferredService() != null
        var retries = 0
        val online = isOnline()

        fun start() {
            recognizer?.destroy()
            val service = if (useGoogle) preferredService() else null
            val r = try {
                if (service != null) SpeechRecognizer.createSpeechRecognizer(context, service) else SpeechRecognizer.createSpeechRecognizer(context)
            } catch (e: Exception) {
                trySend(SpeechEvent.Error(ERROR_USE_SYSTEM_DIALOG)); close(); return
            }
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { trySend(SpeechEvent.Ready) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    val languageProblem = error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE
                    val transient = error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        error == SpeechRecognizer.ERROR_SERVER || error == ERROR_SERVER_DISCONNECTED || error == ERROR_TOO_MANY_REQUESTS
                    when {
                        languageProblem && lang != LANG_EN -> { lang = LANG_EN; start() }
                        transient && retries == 0 -> { retries++; start() }
                        (transient || languageProblem) && useGoogle -> { useGoogle = false; retries = 0; lang = primary; start() }
                        transient || languageProblem -> { trySend(SpeechEvent.Error(ERROR_USE_SYSTEM_DIALOG)); close() }
                        else -> { trySend(SpeechEvent.Error(error)); close() }
                    }
                }
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (list.isEmpty()) trySend(SpeechEvent.Error(SpeechRecognizer.ERROR_NO_MATCH)) else trySend(SpeechEvent.Results(list, lang))
                    close()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { trySend(SpeechEvent.Partial(it)) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            try {
                r.startListening(intent(lang, online))
            } catch (e: Exception) {
                trySend(SpeechEvent.Error(ERROR_USE_SYSTEM_DIALOG)); close()
            }
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context) && preferredService() == null) {
            trySend(SpeechEvent.Error(ERROR_USE_SYSTEM_DIALOG))
            close()
        } else start()

        awaitClose { recognizer?.destroy() }
    }

    /** Offline pack status for Settings (Android 13+ can report installed on-device languages). */
    suspend fun status(): VoiceStatus = withContext(Dispatchers.Main) {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        if (!available || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@withContext VoiceStatus(available, null, null)
        val support = withTimeoutOrNull(3000) { checkSupport(LANG_BN) }
        val installed = support?.installedOnDeviceLanguages.orEmpty().map { it.lowercase(Locale.ROOT) }
        VoiceStatus(
            recognizerAvailable = true,
            banglaOffline = support?.let { installed.any { l -> l.startsWith("bn") } },
            englishOffline = support?.let { installed.any { l -> l.startsWith("en") } },
        )
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun checkSupport(lang: String): RecognitionSupport? = suspendCancellableCoroutine { cont ->
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        try {
            r.checkRecognitionSupport(intent(lang, online = false), ContextCompat.getMainExecutor(context), object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    r.destroy()
                    if (cont.isActive) cont.resume(recognitionSupport)
                }
                override fun onError(error: Int) {
                    r.destroy()
                    if (cont.isActive) cont.resume(null)
                }
            })
        } catch (e: Exception) {
            r.destroy()
            if (cont.isActive) cont.resume(null)
        }
    }

    /** Opens the download for the Bangla offline pack (Android 13+) or the system voice input settings. */
    fun downloadIntentOrTrigger(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val r = SpeechRecognizer.createSpeechRecognizer(context)
                r.triggerModelDownload(intent(LANG_BN, online = false))
                r.destroy()
                return null
            } catch (_: Exception) {
            }
        }
        return Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        const val LANG_BN = "bn-BD"
        const val LANG_EN = "en-US"
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
        const val ERROR_TOO_MANY_REQUESTS = 10
        const val ERROR_SERVER_DISCONNECTED = 11
        /** Our own code: the in-app recognizer cannot work on this phone, open the system voice dialog instead. */
        const val ERROR_USE_SYSTEM_DIALOG = 1001
        const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    }
}

/** Optional text-to-speech for stock and price answers. */
@Singleton
class Speaker @Inject constructor(@ApplicationContext private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, Boolean>? = null

    fun speak(text: String, bangla: Boolean) {
        val t = tts
        if (t == null) {
            pending = text to bangla
            tts = TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                pending?.let { (txt, bn) -> if (ready) say(txt, bn) }
                pending = null
            }
        } else if (ready) say(text, bangla)
    }

    private fun say(text: String, bangla: Boolean) {
        val t = tts ?: return
        val locale = if (bangla) Locale.forLanguageTag("bn-BD") else Locale.US
        val r = t.setLanguage(locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale.US)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer")
    }
}
