@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.BuildConfig
import com.pharmabill.app.R
import com.pharmabill.app.ui.components.PbBtnShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.pharmabill.app.ui.components.CardTitle
import com.pharmabill.app.ui.components.PagePadding
import com.pharmabill.app.ui.components.PbCard
import com.pharmabill.app.ui.theme.Pb
import com.pharmabill.app.ui.components.PbTopBar
import com.pharmabill.app.ai.count.CountingEngineHolder
import com.pharmabill.app.ai.voice.SpeechEngine
import com.pharmabill.app.ai.voice.VoiceStatus
import com.pharmabill.app.data.prefs.AppSettings
import com.pharmabill.app.data.prefs.SettingsStore
import com.pharmabill.app.data.repo.ScanRepository
import com.pharmabill.app.dataset.YoloExporter
import com.pharmabill.app.device.print.PrinterDevice
import com.pharmabill.app.device.print.PrinterManager
import com.pharmabill.app.domain.flags.FlagThresholds
import com.pharmabill.app.ui.components.ChoiceChips
import com.pharmabill.app.ui.components.LocalBangla
import com.pharmabill.app.ui.components.num
import com.pharmabill.app.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SettingsStore,
    private val printers: PrinterManager,
    private val engines: CountingEngineHolder,
    private val speech: SpeechEngine,
    private val exporter: YoloExporter,
    scans: ScanRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val modelVersion = engines.version
    val photoCount = scans.observeImageCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val sampleCount = scans.observeSampleCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    private val _devices = MutableStateFlow<List<PrinterDevice>>(emptyList())
    val devices: StateFlow<List<PrinterDevice>> = _devices
    private val _voice = MutableStateFlow<VoiceStatus?>(null)
    val voice: StateFlow<VoiceStatus?> = _voice

    init { refreshVoice() }

    fun refreshVoice() { viewModelScope.launch { _voice.value = runCatching { speech.status() }.getOrNull() } }
    fun printerPermissions() = printers.requiredPermissions()
    fun loadDevices() { _devices.value = printers.pairedDevices() }
    fun choosePrinter(d: PrinterDevice?) { viewModelScope.launch { store.setPrinter(d?.address, d?.name) } }
    fun saveShop(n: String, a: String, p: String) { viewModelScope.launch { store.saveShop(n, a, p) } }
    fun setThresholds(t: FlagThresholds) { viewModelScope.launch { store.setThresholds(t) } }
    fun setOneMedicine(v: Boolean) { viewModelScope.launch { store.setOneMedicineMode(v) } }
    fun setCollect(v: Boolean) { viewModelScope.launch { store.setCollectTrainingData(v) } }
    fun setSpeak(v: Boolean) { viewModelScope.launch { store.setSpeakAnswers(v) } }
    fun setReminder(h: Int) { viewModelScope.launch { store.setDraftReminderHours(h) } }
    suspend fun setLanguage(l: String) = store.setLanguage(l)
    fun voiceDownload(): Intent? = speech.downloadIntentOrTrigger()
    val isDebugOverride: Boolean get() = engines.isDebugOverride

    suspend fun export(): Pair<Intent?, Int> {
        val r = exporter.export()
        val f = r.file ?: return null to 0
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } to r.images
    }

    suspend fun clearPhotos(): Int = exporter.clearExportedPhotos()
    suspend fun loadModel(model: Uri, meta: Uri?) = engines.loadFromStorage(model, meta)
    suspend fun resetModel() = engines.resetToBundled()
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onBackup: () -> Unit, onLanguageChanged: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val model by vm.modelVersion.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val voice by vm.voice.collectAsStateWithLifecycle()
    val photos by vm.photoCount.collectAsStateWithLifecycle()
    val samples by vm.sampleCount.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val bn = LocalBangla.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.loadDevices() }
    var pendingModel by remember { mutableStateOf<Uri?>(null) }
    val metaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { meta ->
        val m = pendingModel ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = vm.loadModel(m, meta)
            snackbar.showSnackbar(ctx.getString(if (ok) R.string.model_loaded else R.string.model_load_failed))
        }
    }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingModel = uri; metaPicker.launch(arrayOf("application/json", "text/plain", "*/*")) }
    }

    Scaffold(
        topBar = {
            PbTopBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Section(stringResource(R.string.shop_info)) {
                var name by remember(s.shopName) { mutableStateOf(s.shopName) }
                var addr by remember(s.shopAddress) { mutableStateOf(s.shopAddress) }
                var phone by remember(s.shopPhone) { mutableStateOf(s.shopPhone) }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.shop_name)) }, singleLine = true)
                OutlinedTextField(addr, { addr = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.shop_address)) }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.shop_phone)) }, singleLine = true)
                Button(shape = PbBtnShape, onClick = { vm.saveShop(name, addr, phone); scope.launch { snackbar.showSnackbar(ctx.getString(R.string.saved)) } }) { Text(stringResource(R.string.save)) }
            }
            Section(stringResource(R.string.language)) {
                ChoiceChips(listOf(AppSettings.LANG_BN, AppSettings.LANG_EN), s.language, { if (it == AppSettings.LANG_BN) "বাংলা" else "English" }, { l ->
                    if (l != s.language) scope.launch { vm.setLanguage(l); onLanguageChanged() }
                })
            }
            Section(stringResource(R.string.printer)) {
                Text(s.printerName?.let { stringResource(R.string.printer_selected, it) } ?: stringResource(R.string.printer_none))
                OutlinedButton(shape = PbBtnShape, onClick = {
                    val p = vm.printerPermissions()
                    if (p.isEmpty()) vm.loadDevices() else btLauncher.launch(p)
                }) { Text(stringResource(R.string.find_paired_printers)) }
                devices.forEach { d ->
                    TextButton(onClick = { vm.choosePrinter(d) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("${d.name}  (${d.address})") }
                }
                if (s.printerAddress != null) TextButton(onClick = { vm.choosePrinter(null) }) { Text(stringResource(R.string.forget_printer)) }
                Text(stringResource(R.string.printer_pair_hint), style = MaterialTheme.typography.bodySmall)
            }
            Section(stringResource(R.string.flag_thresholds)) {
                val t = s.thresholds
                ThresholdSlider(stringResource(R.string.th_green), t.green, 0.5f..1f) { vm.setThresholds(t.copy(green = it)) }
                ThresholdSlider(stringResource(R.string.th_yellow), t.yellow, 0.3f..0.9f) { vm.setThresholds(t.copy(yellow = it)) }
                ThresholdSlider(stringResource(R.string.th_lead), t.minLead, 0f..0.3f) { vm.setThresholds(t.copy(minLead = it)) }
                ThresholdSlider(stringResource(R.string.th_model), t.modelConfidence, 0.2f..0.95f) { vm.setThresholds(t.copy(modelConfidence = it)) }
                TextButton(onClick = { vm.setThresholds(FlagThresholds()) }) { Text(stringResource(R.string.reset_defaults)) }
            }
            Section(stringResource(R.string.scanning)) {
                Toggle(stringResource(R.string.one_medicine_mode), stringResource(R.string.one_medicine_mode_hint), s.oneMedicineMode, vm::setOneMedicine)
                Toggle(stringResource(R.string.collect_training_data), stringResource(R.string.collect_training_hint, num(photos), num(samples)), s.collectTrainingData, vm::setCollect)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(shape = PbBtnShape, onClick = {
                        scope.launch {
                            val (intent, n) = vm.export()
                            if (intent == null) snackbar.showSnackbar(ctx.getString(R.string.export_nothing))
                            else ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.export_dataset_count, Fmt.number(n.toLong(), bn))))
                        }
                    }) { Text(stringResource(R.string.export_dataset)) }
                    OutlinedButton(shape = PbBtnShape, onClick = { scope.launch { val n = vm.clearPhotos(); snackbar.showSnackbar(ctx.getString(R.string.photos_cleared, Fmt.number(n.toLong(), bn))) } }) {
                        Text(stringResource(R.string.clear_exported_photos))
                    }
                }
                Text(stringResource(R.string.model_version, model), style = MaterialTheme.typography.titleSmall)
                if (BuildConfig.DEBUG) {
                    Text(stringResource(R.string.debug_loader_hint), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(shape = PbBtnShape, onClick = { modelPicker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.load_model_from_storage)) }
                        if (vm.isDebugOverride) TextButton(onClick = { scope.launch { vm.resetModel() } }) { Text(stringResource(R.string.use_bundled_model)) }
                    }
                }
            }
            Section(stringResource(R.string.voice)) {
                val v = voice
                Text(
                    when {
                        v == null -> stringResource(R.string.voice_checking)
                        !v.recognizerAvailable -> stringResource(R.string.voice_unavailable)
                        v.banglaOffline == true -> stringResource(R.string.voice_bn_offline_ok)
                        v.banglaOffline == false -> stringResource(R.string.voice_bn_offline_missing)
                        else -> stringResource(R.string.voice_unknown_offline)
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = PbBtnShape, onClick = { vm.voiceDownload()?.let { runCatching { ctx.startActivity(it) } } ?: scope.launch { snackbar.showSnackbar(ctx.getString(R.string.voice_download_started)) } }) {
                        Text(stringResource(R.string.voice_download_pack))
                    }
                    TextButton(onClick = vm::refreshVoice) { Text(stringResource(R.string.refresh)) }
                }
                Toggle(stringResource(R.string.speak_answers), null, s.speakAnswers, vm::setSpeak)
            }
            Section(stringResource(R.string.drafts)) {
                Text(stringResource(R.string.draft_reminder_hours, num(s.draftReminderHours)))
                Slider(value = s.draftReminderHours.toFloat(), onValueChange = { vm.setReminder(it.toInt()) }, valueRange = 1f..48f, steps = 46)
            }
            Section(stringResource(R.string.backup_restore)) {
                Button(shape = PbBtnShape, onClick = onBackup) { Text(stringResource(R.string.backup_restore)) }
            }
            Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), Modifier.fillMaxWidth(), fontSize = 11.sp, color = Pb.muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    PbCard {
        CardTitle(title)
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Pb.ink)
            subtitle?.let { Text(it, Modifier.padding(top = 3.dp), fontSize = 12.sp, color = Pb.muted) }
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun ThresholdSlider(label: String, value: Double, range: ClosedFloatingPointRange<Float>, onChange: (Double) -> Unit) {
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    Text("$label: " + String.format(Locale.US, "%.2f", local))
    Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local.toDouble()) }, valueRange = range)
}

