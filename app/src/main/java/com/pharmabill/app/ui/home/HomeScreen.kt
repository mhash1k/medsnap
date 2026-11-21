@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.pharmabill.app.device.pdf.ReceiptBuilder
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.LineSource
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.usecase.AddLineUseCase
import com.pharmabill.app.domain.usecase.DraftManager
import com.pharmabill.app.ui.components.ActionPrimary
import com.pharmabill.app.ui.components.ActionSmall
import com.pharmabill.app.ui.components.CardTitle
import com.pharmabill.app.ui.components.DatePill
import com.pharmabill.app.ui.components.DraftPill
import com.pharmabill.app.ui.components.EmptyBlock
import com.pharmabill.app.ui.components.IconTile
import com.pharmabill.app.ui.components.ItemRow
import com.pharmabill.app.ui.components.ListCard
import com.pharmabill.app.ui.components.LocalBangla
import com.pharmabill.app.ui.components.MedicinePickerDialog
import com.pharmabill.app.ui.components.PageHeader
import com.pharmabill.app.ui.components.PagePadding
import com.pharmabill.app.ui.components.PbCard
import com.pharmabill.app.ui.components.PbTextButton
import com.pharmabill.app.ui.components.PillBadge
import com.pharmabill.app.ui.components.PriceColumn
import com.pharmabill.app.ui.components.SectionHeader
import com.pharmabill.app.ui.components.SlimProgress
import com.pharmabill.app.ui.components.StatCard
import com.pharmabill.app.ui.components.Tone
import com.pharmabill.app.ui.components.VoiceHero
import com.pharmabill.app.ui.components.draftTitle
import com.pharmabill.app.ui.components.moneyWhole
import com.pharmabill.app.ui.components.num
import com.pharmabill.app.ui.theme.Pb
import com.pharmabill.app.ui.voice.VoiceHelperFactory
import com.pharmabill.app.ui.voice.VoiceOutcomeEffects
import com.pharmabill.app.ui.voice.rememberVoiceToggle
import com.pharmabill.app.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

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
    val medicineCount = medicines.observeAll().map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val recent: StateFlow<List<BillEntity>> = bills.observeConfirmed().map { it.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun select(id: Long) { viewModelScope.launch { draftManager.setActive(id) } }
    fun newDraft() { viewModelScope.launch { draftManager.newDraft() } }
    suspend fun newDraftId(): Long = draftManager.newDraft()
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
    onAllBills: () -> Unit,
    onOpenReceipt: (Long) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    val sales by vm.todaySales.collectAsStateWithLifecycle()
    val low by vm.lowStock.collectAsStateWithLifecycle()
    val needs by vm.needsStock.collectAsStateWithLifecycle()
    val medCount by vm.medicineCount.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val listening by vm.voice.listening.collectAsStateWithLifecycle()
    val partial by vm.voice.partial.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val bn = LocalBangla.current
    var picking by remember { mutableStateOf(false) }
    val voiceToggle = rememberVoiceToggle(vm.voice)

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    VoiceOutcomeEffects(vm.voice, snackbar, onFinish = { onOpenBill(it, true) })

    val locale = if (bn) Locale.forLanguageTag("bn-BD") else Locale.US
    val today = remember(bn) { SimpleDateFormat("EEE, d MMM", locale).format(Date()) }
    val timeFmt = remember(bn) { SimpleDateFormat("hh:mm a", locale) }
    val greeting = stringResource(
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.greet_morning
            in 12..16 -> R.string.greet_afternoon
            else -> R.string.greet_evening
        },
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding),
        ) {
            PageHeader("$greeting 👋", stringResource(R.string.home_sub), trailing = { DatePill(today) })

            VoiceHero(
                title = stringResource(if (listening) R.string.listening else R.string.voice_billing),
                subtitle = partial.ifBlank { stringResource(R.string.voice_hint) },
                listening = listening,
                onClick = voiceToggle,
                icon = if (listening) Icons.Filled.Stop else Icons.Outlined.Mic,
            )
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionPrimary(
                    Icons.Outlined.PhotoCamera, stringResource(R.string.scan_medicines), stringResource(R.string.scan_medicines_sub),
                    onClick = { scope.launch { onTakePhoto(vm.ensureDraft()) } },
                    modifier = Modifier.weight(1.15f).fillMaxHeight(),
                )
                Column(Modifier.weight(0.85f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ActionSmall(Icons.Filled.Search, stringResource(R.string.find_medicine), stringResource(R.string.find_medicine_sub), { picking = true }, Modifier.weight(1f))
                    ActionSmall(Icons.Filled.Add, stringResource(R.string.new_bill), stringResource(R.string.new_bill_sub), {
                        scope.launch { onOpenBill(vm.newDraftId(), false) }
                    }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(14.dp))

            SectionHeader(stringResource(R.string.open_bills), count = num(drafts.size), action = stringResource(R.string.view_all), onAction = onAllBills)
            if (drafts.isEmpty()) {
                Text(stringResource(R.string.no_drafts), fontSize = 12.sp, color = Pb.muted, modifier = Modifier.padding(vertical = 6.dp))
            } else {
                val now = System.currentTimeMillis()
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    drafts.forEach { d ->
                        val old = now - d.createdAt > settings.draftReminderHours * 3_600_000L
                        DraftPill(
                            draftTitle(ctx, d, bn) + (if (d.total > 0) " · " + Fmt.takaWhole(d.total, bn) else "") + if (old) " ⏰" else "",
                            active = d.id == settings.activeDraftId,
                        ) { vm.select(d.id); onOpenBill(d.id, false) }
                    }
                }
            }
            Spacer(Modifier.height(15.dp))

            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    moneyWhole(sales.first), stringResource(R.string.todays_sales), Modifier.weight(1f).fillMaxHeight(),
                    icon = Icons.Outlined.BarChart,
                    foot = stringResource(R.string.bills_count, num(sales.second)), footColor = Pb.green,
                )
                StatCard(
                    num(medCount), stringResource(R.string.medicine_items), Modifier.weight(1f).fillMaxHeight(),
                    icon = Icons.Outlined.Inventory2, foot = stringResource(R.string.in_inventory),
                )
            }
            Spacer(Modifier.height(13.dp))

            PbCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardTitle(stringResource(R.string.inventory_overview), Modifier.weight(1f), sub = stringResource(R.string.inventory_overview_sub))
                    PbTextButton(stringResource(R.string.review), onLowStock)
                }
                val health = if (medCount == 0) 1f else 1f - low.toFloat() / medCount
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(num(low), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (low > 0) Pb.red else Pb.ink)
                        Text(stringResource(R.string.need_restocking), fontSize = 11.sp, color = Pb.muted)
                    }
                    Column(Modifier.weight(2f)) {
                        Row {
                            Text(stringResource(R.string.stock_health), fontSize = 11.sp, color = Pb.muted, modifier = Modifier.weight(1f))
                            Text(num((health * 100).roundToInt()) + "%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        SlimProgress(health, Modifier.padding(top = 8.dp), color = if (health < 0.6f) Pb.yellow else Pb.primary)
                    }
                }
                if (needs > 0) {
                    HorizontalDivider(Modifier.padding(vertical = 13.dp), color = Pb.line)
                    Row(Modifier.fillMaxWidth().clickable(onClick = onNeedsStock), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillBadge(num(needs), Tone.RED, dot = true)
                        Text(stringResource(R.string.needs_stock_entry), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.fix_now), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Pb.primary)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            SectionHeader(stringResource(R.string.recent_sales), action = stringResource(R.string.see_history), onAction = onAllBills)
            ListCard(Modifier.padding(top = 6.dp)) {
                if (recent.isEmpty()) {
                    EmptyBlock(stringResource(R.string.no_sales_yet), icon = Icons.Outlined.Receipt)
                } else {
                    recent.forEachIndexed { i, b ->
                        ItemRow(
                            title = Fmt.digits(ReceiptBuilder.billNo(b), bn) + (b.draftLabel?.let { " · $it" } ?: ""),
                            meta = timeFmt.format(Date(b.confirmedAt ?: b.createdAt)),
                            leading = { IconTile(Icons.Outlined.Receipt, bg = Pb.medIconBg, tint = Pb.medIcon, size = 42.dp, radius = 14.dp, iconSize = 20.dp) },
                            trailing = { PriceColumn(moneyWhole(b.total)) },
                            last = i == recent.lastIndex,
                            onClick = { onOpenReceipt(b.id) },
                        )
                    }
                }
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
