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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.pharmabill.app.ui.components.CardTitle
import com.pharmabill.app.ui.components.EmptyBlock
import com.pharmabill.app.ui.components.ItemRow
import com.pharmabill.app.ui.components.PagePadding
import com.pharmabill.app.ui.components.PbCard
import com.pharmabill.app.ui.components.PbTextButton
import com.pharmabill.app.ui.components.PillBadge
import com.pharmabill.app.ui.components.StatCard
import com.pharmabill.app.ui.components.TextTile
import com.pharmabill.app.ui.components.Tone
import com.pharmabill.app.ui.theme.Pb
import com.pharmabill.app.ui.components.PbTopBar
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
        PbTopBar(
            title = { Text(stringResource(R.string.dashboard)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(moneyWhole(ui.todayTotal), stringResource(R.string.todays_sales), Modifier.weight(1f).fillMaxHeight(), foot = stringResource(R.string.bills_count, num(ui.todayBills)), footColor = Pb.green)
                StatCard(moneyWhole(ui.weekTotal), stringResource(R.string.this_week), Modifier.weight(1f).fillMaxHeight(), foot = stringResource(R.string.bills_count, num(ui.weekBills)))
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val avg = if (ui.weekBills > 0) ui.weekTotal / ui.weekBills else 0L
                StatCard(moneyWhole(avg), stringResource(R.string.average_bill), Modifier.weight(1f).fillMaxHeight(), foot = stringResource(R.string.this_week))
                StatCard(
                    num(ui.soldWithoutStock.size), stringResource(R.string.sold_without_stock), Modifier.weight(1f).fillMaxHeight(),
                    foot = stringResource(R.string.needs_stock_entry), valueColor = if (ui.soldWithoutStock.isNotEmpty()) Pb.red else Pb.ink,
                )
            }
            PbCard(Modifier.padding(top = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardTitle(stringResource(R.string.sales_last_7_days), Modifier.weight(1f), sub = stringResource(R.string.daily_revenue))
                    PillBadge(stringResource(R.string.this_week), Tone.BLUE)
                }
                val max = (ui.days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(150.dp).padding(top = 15.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Bottom) {
                    ui.days.forEachIndexed { i, (d, v) ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            val frac = (v.toFloat() / max).coerceIn(0.03f, 1f)
                            if (frac < 1f) Spacer(Modifier.weight(1f - frac))
                            Box(
                                Modifier.fillMaxWidth().weight(frac)
                                    .background(if (i == ui.days.lastIndex) Pb.primary else Color(0xFFCFE0FB), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp)),
                            )
                            Text(dayFmt.format(Date(d)), Modifier.padding(top = 7.dp), fontSize = 11.sp, color = Pb.muted, maxLines = 1)
                        }
                    }
                }
            }
            PbCard {
                CardTitle(stringResource(R.string.top_10_week))
                if (ui.top.isEmpty()) EmptyBlock(stringResource(R.string.no_sales_yet), icon = Icons.Outlined.BarChart)
                ui.top.forEachIndexed { i, (m, q) ->
                    ItemRow(
                        title = m.displayName,
                        meta = stringResource(if (m.kind == MedicineKind.STRIP) R.string.n_pcs_sold else R.string.n_units_sold, num(q)),
                        leading = { TextTile(Fmt.digits("${i + 1}", bn)) },
                        trailing = { PillBadge(Fmt.digits("#${i + 1}", bn), Tone.BLUE) },
                        last = i == ui.top.lastIndex,
                    )
                }
            }
            PbCard {
                CardTitle(stringResource(R.string.sold_without_stock), sub = if (ui.soldWithoutStock.isEmpty()) stringResource(R.string.none) else null)
                ui.soldWithoutStock.forEachIndexed { i, m ->
                    ItemRow(
                        title = m.displayName,
                        meta = stringResource(R.string.stock_now_n, num(m.stockQty)),
                        leading = { TextTile("✚") },
                        trailing = { PbTextButton(stringResource(R.string.restock), { onStock(m.id) }) },
                        last = i == ui.soldWithoutStock.lastIndex,
                    )
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
