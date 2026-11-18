@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.R
import com.pharmabill.app.data.db.entities.BillEntity
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.prefs.AppSettings
import com.pharmabill.app.data.prefs.SettingsStore
import com.pharmabill.app.data.repo.BillRepository
import com.pharmabill.app.data.repo.MedicineRepository
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.LineSource
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.usecase.AddLineUseCase
import com.pharmabill.app.domain.usecase.DraftManager
import com.pharmabill.app.ui.components.DraftChipsBar
import com.pharmabill.app.ui.components.MedicinePickerDialog
import com.pharmabill.app.ui.components.moneyWhole
import com.pharmabill.app.ui.components.num
import com.pharmabill.app.ui.theme.BigNumber
import com.pharmabill.app.ui.theme.FlagColors
import com.pharmabill.app.ui.voice.MicButton
import com.pharmabill.app.ui.voice.VoiceHelperFactory
import com.pharmabill.app.ui.voice.VoiceOutcomeEffects
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bills: BillRepository,
    medicines: MedicineRepository,
    private val draftManager: DraftManager,
    private val addLine: AddLineUseCase,
    settings: SettingsStore,
    voiceFactory: VoiceHelperFactory,
) : ViewModel() {
    val voice = voiceFactory.create(viewModelScope)
    val settings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val drafts: StateFlow<List<BillEntity>> = bills.observeDrafts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val todaySales: StateFlow<Pair<Long, Int>> = bills.observeConfirmedSince(startOfToday())
        .map { list -> list.sumOf { it.total } to list.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L to 0)
    val lowStock = medicines.observeLowStockCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val needsStock = medicines.observeNeedsStockCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun select(id: Long) { viewModelScope.launch { draftManager.setActive(id) } }
    fun newDraft() { viewModelScope.launch { draftManager.newDraft() } }
    suspend fun ensureDraft(): Long = draftManager.ensureActive()

    suspend fun add(m: MedicineEntity, unit: SaleUnit, qty: Int): Long? {
        val billId = draftManager.ensureActive()
        return addLine(billId, m.id, unit, qty, Flag.GREEN, LineSource.MANUAL)
    }

    fun undoAdd(lineId: Long) { viewModelScope.launch { bills.removeLine(lineId) } }
}

@Composable
fun HomeScreen(
    onTakePhoto: (Long) -> Unit,
    onOpenBill: (Long, Boolean) -> Unit,
    onLowStock: () -> Unit,
    onNeedsStock: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    val sales by vm.todaySales.collectAsStateWithLifecycle()
    val low by vm.lowStock.collectAsStateWithLifecycle()
    val needs by vm.needsStock.collectAsStateWithLifecycle()
    val listening by vm.voice.listening.collectAsStateWithLifecycle()
    val partial by vm.voice.partial.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    VoiceOutcomeEffects(vm.voice, snackbar, onFinish = { onOpenBill(it, true) })

    Scaffold(
        topBar = { TopAppBar(title = { Text(settings.shopName.ifBlank { stringResource(R.string.app_name) }) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DraftChipsBar(
                drafts, settings.activeDraftId, settings.draftReminderHours,
                onSelect = { vm.select(it); onOpenBill(it, false) }, onNew = vm::newDraft,
            )
            OutlinedButton(
                onClick = { picking = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp),
            ) {
                Icon(Icons.Filled.Search, null)
                Text(stringResource(R.string.search_hint), Modifier.padding(start = 8.dp).weight(1f))
            }
            Button(
                onClick = { scope.launch { onTakePhoto(vm.ensureDraft()) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(140.dp),
            ) {
                Icon(Icons.Filled.CameraAlt, null, Modifier.size(48.dp))
                Text(stringResource(R.string.take_photo), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MicButton(listening, vm.voice::toggle, size = 72)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (listening) R.string.listening else R.string.voice_hint), style = MaterialTheme.typography.titleMedium)
                    if (partial.isNotBlank()) Text(partial, style = MaterialTheme.typography.bodyMedium)
                }
            }
            settings.activeDraftId?.let { id ->
                OutlinedButton(onClick = { onOpenBill(id, false) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.open_active_draft, num(id)))
                }
            }
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.todays_sales), style = MaterialTheme.typography.titleMedium)
                    Text(moneyWhole(sales.first), style = BigNumber)
                    Text(stringResource(R.string.bills_count, num(sales.second)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BadgeCard(stringResource(R.string.low_stock), num(low), if (low > 0) FlagColors.yellow else null, Modifier.weight(1f), onLowStock)
                BadgeCard(stringResource(R.string.needs_stock_entry), num(needs), if (needs > 0) FlagColors.red else null, Modifier.weight(1f), onNeedsStock)
            }
        }
    }

    if (picking) {
        MedicinePickerDialog(onDismiss = { picking = false }, onPicked = { m, u, q ->
            picking = false
            scope.launch {
                val id = vm.add(m, u, q)
                if (id != null && snackbar.showSnackbar(ctx.getString(R.string.added_item, m.displayName), ctx.getString(R.string.undo), duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoAdd(id)
            }
        })
    }
}

@Composable
private fun BadgeCard(title: String, value: String, color: androidx.compose.ui.graphics.Color?, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick),
        colors = if (color != null) CardDefaults.cardColors(containerColor = color.copy(alpha = 0.18f)) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(value, style = BigNumber)
        }
    }
}
