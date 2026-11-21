package com.pharmabill.app.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pharmabill.app.R
import com.pharmabill.app.ai.voice.SpeechEngine
import com.pharmabill.app.ai.voice.SpeechEvent
import com.pharmabill.app.ai.voice.Speaker
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.prefs.SettingsStore
import com.pharmabill.app.data.repo.BillRepository
import com.pharmabill.app.data.repo.MatcherProvider
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.domain.pricing.PriceCalculator
import com.pharmabill.app.domain.usecase.VoiceCommandRunner
import com.pharmabill.app.domain.usecase.VoiceOutcome
import com.pharmabill.app.ui.components.InfoDialog
import com.pharmabill.app.ui.components.LocalBangla
import com.pharmabill.app.ui.components.QtyText
import com.pharmabill.app.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Shared by Home and Bill: listening state, outcomes, undo and spoken answers. */
class VoiceHelper(
    private val speech: SpeechEngine,
    private val runner: VoiceCommandRunner,
    private val bills: BillRepository,
    private val speaker: Speaker,
    private val settings: SettingsStore,
    private val matcher: MatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening
    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial
    private val _events = Channel<VoiceOutcome>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var job: Job? = null

    fun toggle() {
        if (_listening.value) stop() else start()
    }

    private fun start() {
        job?.cancel()
        _partial.value = ""
        _listening.value = true
        job = scope.launch {
            try {
                speech.listen().collect { ev ->
                    when (ev) {
                        is SpeechEvent.Partial -> _partial.value = ev.text
                        is SpeechEvent.Results -> {
                            _listening.value = false
                            _partial.value = ev.alternatives.firstOrNull().orEmpty()
                            _events.send(runner.run(ev.alternatives))
                        }
                        is SpeechEvent.Error -> _events.send(VoiceOutcome.Failed(ev.code))
                        SpeechEvent.Ready -> {}
                    }
                }
            } finally {
                _listening.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        _listening.value = false
    }

    fun undo(o: VoiceOutcome) {
        scope.launch {
            when (o) {
                is VoiceOutcome.Added -> o.lines.forEach { bills.removeLine(it.id) }
                is VoiceOutcome.Removed -> bills.restoreLine(o.line)
                is VoiceOutcome.Changed -> bills.restoreLine(o.before)
                else -> {}
            }
        }
    }

    fun speakIfEnabled(text: String) {
        scope.launch {
            val s = settings.current()
            if (s.speakAnswers) speaker.speak(text, s.isBangla)
        }
    }

    /** System "Speak now" dialog, used when the in-app recognizer cannot run on this phone. */
    fun systemIntent(): android.content.Intent? = speech.systemDialogIntent()

    /** Results coming back from the system dialog go through the same command runner. */
    fun handleResults(alternatives: List<String>) {
        if (alternatives.isEmpty()) return
        scope.launch {
            _partial.value = alternatives.first()
            _events.send(runner.run(alternatives))
        }
    }

    fun piecesPerStrip(id: Long?): Int = id?.let { matcher.medicine(it)?.piecesPerStrip } ?: 1
}

@Singleton
class VoiceHelperFactory @Inject constructor(
    private val speech: SpeechEngine,
    private val runner: VoiceCommandRunner,
    private val bills: BillRepository,
    private val speaker: Speaker,
    private val settings: SettingsStore,
    private val matcher: MatcherProvider,
) {
    fun create(scope: CoroutineScope) = VoiceHelper(speech, runner, bills, speaker, settings, matcher, scope)
}

/**
 * Returns the click handler for any "voice" button: asks for the microphone permission first,
 * then starts/stops listening. Use this instead of calling [VoiceHelper.toggle] directly.
 */
@Composable
fun rememberVoiceToggle(helper: VoiceHelper): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) helper.toggle() }
    return {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) helper.toggle()
        else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
fun MicButton(listening: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, size: Int = 64) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) onToggle() }
    FilledIconButton(
        onClick = {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) onToggle()
            else launcher.launch(Manifest.permission.RECORD_AUDIO)
        },
        modifier = modifier.size(size.dp),
        colors = if (listening) IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error) else IconButtonDefaults.filledIconButtonColors(),
    ) {
        Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic, stringResource(if (listening) R.string.stop_listening else R.string.speak), Modifier.size((size / 2).dp))
    }
}

/** Shows a toast-like snackbar with Undo for adds/removes/changes and an answer card for questions. */
@Composable
fun VoiceOutcomeEffects(
    helper: VoiceHelper,
    snackbar: SnackbarHostState,
    onFinish: (Long) -> Unit,
    onNewDraft: (Long) -> Unit = {},
) {
    val ctx = LocalContext.current
    val bn = LocalBangla.current
    var card by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    val systemDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            helper.handleResults(res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS).orEmpty())
        }
    }
    LaunchedEffect(helper) {
        helper.events.collect { o ->
            fun qty(unit: com.pharmabill.app.domain.model.SaleUnit, q: Int, medId: Long?) = QtyText.plain(ctx, unit, q, helper.piecesPerStrip(medId), bn)
            when (o) {
                is VoiceOutcome.Added -> {
                    val text = ctx.getString(R.string.voice_added, o.lines.joinToString(", ") { "${it.displayName} × ${qty(it.saleUnit, it.quantity, it.medicineId)}" })
                    if (snackbar.showSnackbar(text, ctx.getString(R.string.undo), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) helper.undo(o)
                }
                is VoiceOutcome.Removed -> {
                    if (snackbar.showSnackbar(ctx.getString(R.string.removed_item, o.line.displayName), ctx.getString(R.string.undo), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) helper.undo(o)
                }
                is VoiceOutcome.Changed -> {
                    val t = ctx.getString(R.string.voice_changed, o.after.displayName, qty(o.after.saleUnit, o.after.quantity, o.after.medicineId))
                    if (snackbar.showSnackbar(t, ctx.getString(R.string.undo), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) helper.undo(o)
                }
                is VoiceOutcome.Stock -> {
                    val m = o.medicine
                    val line = ctx.getString(if (m.kind == MedicineKind.STRIP) R.string.stock_pcs else R.string.stock_units, Fmt.number(m.stockQty, bn))
                    card = m.displayName to listOf(line)
                    helper.speakIfEnabled("${m.displayName}, $line")
                }
                is VoiceOutcome.Price -> {
                    val lines = priceLines(ctx, o.medicine, bn)
                    card = o.medicine.displayName to lines
                    helper.speakIfEnabled("${o.medicine.displayName}, ${lines.joinToString(", ")}")
                }
                is VoiceOutcome.Finish -> onFinish(o.billId)
                is VoiceOutcome.NewDraft -> {
                    onNewDraft(o.billId)
                    snackbar.showSnackbar(ctx.getString(R.string.voice_new_draft, Fmt.digits(o.billId.toString(), bn)))
                }
                is VoiceOutcome.NotInBill -> snackbar.showSnackbar(ctx.getString(R.string.voice_not_in_bill, o.name))
                is VoiceOutcome.NotUnderstood -> snackbar.showSnackbar(ctx.getString(R.string.voice_not_understood, o.text))
                is VoiceOutcome.Failed -> if (o.errorCode == SpeechEngine.ERROR_USE_SYSTEM_DIALOG) {
                    val i = helper.systemIntent()
                    if (i != null) runCatching { systemDialog.launch(i) }.onFailure { snackbar.showSnackbar(ctx.getString(R.string.voice_err_unavailable)) }
                    else snackbar.showSnackbar(ctx.getString(R.string.voice_err_unavailable))
                } else snackbar.showSnackbar(
                    ctx.getString(
                        when (o.errorCode) {
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechEngine.ERROR_LANGUAGE_UNAVAILABLE -> R.string.voice_err_network
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_err_nomatch
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_err_permission
                            SpeechRecognizer.ERROR_CLIENT -> R.string.voice_err_unavailable
                            else -> R.string.voice_err_generic
                        },
                    ),
                )
            }
        }
    }
    card?.let { (title, lines) -> InfoDialog(title, lines) { card = null } }
}

fun priceLines(ctx: android.content.Context, m: MedicineEntity, bn: Boolean): List<String> {
    val info = m.toInfo()
    return if (m.kind == MedicineKind.STRIP) listOf(
        ctx.getString(R.string.price_per_piece, Fmt.taka(PriceCalculator.piecePrice(info), bn)),
        ctx.getString(R.string.price_per_strip, Fmt.taka(m.stripPrice, bn)),
        ctx.getString(R.string.price_per_box, Fmt.taka(PriceCalculator.boxPrice(info), bn)),
    ) else listOf(ctx.getString(R.string.price_per_unit, Fmt.taka(m.unitPrice, bn)))
}
