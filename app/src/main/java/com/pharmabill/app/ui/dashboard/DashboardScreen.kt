@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.R
import com.pharmabill.app.data.db.dao.StockMovementDao
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.repo.BillRepository
import com.pharmabill.app.data.repo.MedicineRepository
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.ui.components.EmptyState
import com.pharmabill.app.ui.components.LocalBangla
import com.pharmabill.app.ui.components.NeedsStockBadge
import com.pharmabill.app.ui.components.moneyWhole
import com.pharmabill.app.ui.components.num
import com.pharmabill.app.ui.home.startOfToday
import com.pharmabill.app.ui.theme.BigNumber
import com.pharmabill.app.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class DashboardUi(
    val todayTotal: Long = 0,
    val todayBills: Int = 0,
    val weekTotal: Long = 0,
    val weekBills: Int = 0,
    val days: List<Pair<Long, Long>> = emptyList(),
    val top: List<Pair<MedicineEntity, Long>> = emptyList(),
    val soldWithoutStock: List<MedicineEntity> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(bills: BillRepository, medicines: MedicineRepository, movements: StockMovementDao) : ViewModel() {
    private val today = startOfToday()
    private val weekStart = today - 6L * 24 * 3_600_000

    val ui = combine(
        bills.observeConfirmedSince(weekStart),
        movements.observeTopSold(weekStart, 10),
        medicines.observeAll(),
        medicines.observeNeedsStock(),
    ) { week, top, all, needs ->
        val byId = all.associateBy { it.id }
        val days = (0..6).map { i ->
            val start = weekStart + i * 24L * 3_600_000
            start to week.filter { (it.confirmedAt ?: 0) in start until start + 24L * 3_600_000 }.sumOf { it.total }
        }
        val todayList = week.filter { (it.confirmedAt ?: 0) >= today }
        DashboardUi(
            todayTotal = todayList.sumOf { it.total }, todayBills = todayList.size,
            weekTotal = week.sumOf { it.total }, weekBills = week.size, days = days,
            top = top.mapNotNull { t -> byId[t.medicineId]?.let { it to t.qty } },
            soldWithoutStock = needs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUi())
}

@Composable
fun DashboardScreen(onBack: () -> Unit, onStock: (Long) -> Unit, vm: DashboardViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val bn = LocalBangla.current
    val dayFmt = SimpleDateFormat("EEE", if (bn) Locale.forLanguageTag("bn-BD") else Locale.US)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.dashboard)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(stringResource(R.string.today), moneyWhole(ui.todayTotal), stringResource(R.string.bills_count, num(ui.todayBills)), Modifier.weight(1f))
                Stat(stringResource(R.string.this_week), moneyWhole(ui.weekTotal), stringResource(R.string.bills_count, num(ui.weekBills)), Modifier.weight(1f))
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.sales_last_7_days), style = MaterialTheme.typography.titleMedium)
                    val max = (ui.days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                    val barColor = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp)) {
                        val n = ui.days.size.coerceAtLeast(1)
                        val slot = size.width / n
                        ui.days.forEachIndexed { i, (_, v) ->
                            val h = size.height * v / max
                            drawRect(barColor, Offset(i * slot + slot * 0.15f, size.height - h), Size(slot * 0.7f, h))
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        ui.days.forEach { (d, _) -> Text(dayFmt.format(Date(d)), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.top_10_week), style = MaterialTheme.typography.titleMedium)
                    if (ui.top.isEmpty()) EmptyState(stringResource(R.string.no_sales_yet))
                    ui.top.forEachIndexed { i, (m, q) ->
                        Row {
                            Text(Fmt.digits("${i + 1}. ", bn) + m.displayName, Modifier.weight(1f))
                            Text(stringResource(if (m.kind == MedicineKind.STRIP) R.string.stock_pcs else R.string.stock_units, num(q)))
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.sold_without_stock), style = MaterialTheme.typography.titleMedium)
                    if (ui.soldWithoutStock.isEmpty()) Text(stringResource(R.string.none))
                    ui.soldWithoutStock.forEach { m ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(m.displayName, Modifier.weight(1f))
                            NeedsStockBadge()
                            Text(" " + num(m.stockQty))
                        }
                        androidx.compose.material3.TextButton(onClick = { onStock(m.id) }) { Text(stringResource(R.string.stock_entry)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(title: String, value: String, sub: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(value, style = BigNumber)
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}
