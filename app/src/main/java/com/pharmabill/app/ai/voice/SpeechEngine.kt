package com.pharmabill.app.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
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

    private fun intent(lang: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    /** Must be collected on the main thread. */
    fun listen(primary: String = LANG_BN): Flow<SpeechEvent> = callbackFlow {
        var recognizer: SpeechRecognizer? = null
        var lang = primary

        fun start() {
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { trySend(SpeechEvent.Ready) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    val languageProblem = error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE
                    if (languageProblem && lang != LANG_EN) {
                        lang = LANG_EN
                        start()
                    } else {
                        trySend(SpeechEvent.Error(error))
                        close()
                    }
                }
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    trySend(SpeechEvent.Results(list, lang))
                    close()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { trySend(SpeechEvent.Partial(it)) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            r.startListening(intent(lang))
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechEvent.Error(SpeechRecognizer.ERROR_CLIENT))
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
            r.checkRecognitionSupport(intent(lang), ContextCompat.getMainExecutor(context), object : RecognitionSupportCallback {
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
                r.triggerModelDownload(intent(LANG_BN))
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
