@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.R
import com.pharmabill.app.data.db.entities.BillEntity
import com.pharmabill.app.data.prefs.AppSettings
import com.pharmabill.app.data.prefs.SettingsStore
import com.pharmabill.app.data.repo.BillRepository
import com.pharmabill.app.device.pdf.ReceiptBuilder
import com.pharmabill.app.domain.usecase.DraftManager
import com.pharmabill.app.ui.components.EmptyBlock
import com.pharmabill.app.ui.components.IconTile
import com.pharmabill.app.ui.components.ItemRow
import com.pharmabill.app.ui.components.LocalBangla
import com.pharmabill.app.ui.components.PageHeader
import com.pharmabill.app.ui.components.PbButton
import com.pharmabill.app.ui.components.PbCard
import com.pharmabill.app.ui.components.PillBadge
import com.pharmabill.app.ui.components.PriceColumn
import com.pharmabill.app.ui.components.SearchBox
import com.pharmabill.app.ui.components.SegmentedTabs
import com.pharmabill.app.ui.components.Tone
import com.pharmabill.app.ui.components.groupItem
import com.pharmabill.app.ui.components.draftTitle
import com.pharmabill.app.ui.components.moneyWhole
import com.pharmabill.app.ui.theme.Pb
import com.pharmabill.app.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class BillsViewModel @Inject constructor(bills: BillRepository, settings: SettingsStore, private val draftManager: DraftManager) : ViewModel() {
    suspend fun newDraft(): Long = draftManager.newDraft()
    val drafts = bills.observeDrafts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val confirmed = bills.observeConfirmed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
}

/** Bills tab: open drafts, and confirmed bills by date with search, reprint and share. */
@Composable
fun BillsScreen(onOpenDraft: (Long) -> Unit, onOpenReceipt: (Long) -> Unit, vm: BillsViewModel = hiltViewModel()) {
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    val confirmed by vm.confirmed.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current
    val bn = LocalBangla.current
    val scope = rememberCoroutineScope()
    val dayFmt = SimpleDateFormat("EEE, dd MMM yyyy", if (bn) Locale.forLanguageTag("bn-BD") else Locale.US)
    val timeFmt = SimpleDateFormat("hh:mm a", if (bn) Locale.forLanguageTag("bn-BD") else Locale.US)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(Pb.bg).padding(horizontal = 18.dp, vertical = 10.dp)) {
                PbButton(stringResource(R.string.create_new_bill), { scope.launch { onOpenDraft(vm.newDraft()) } }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)) {
            item {
                PageHeader(stringResource(R.string.tab_bills), stringResource(R.string.bills_sub))
                SegmentedTabs(
                    listOf(stringResource(R.string.drafts_n, Fmt.number(drafts.size.toLong(), bn)), stringResource(R.string.completed)),
                    tab, { tab = it }, Modifier.padding(bottom = 14.dp),
                )
            }
            if (tab == 0) {
                if (drafts.isEmpty()) item {
                    PbCard { EmptyBlock(stringResource(R.string.no_drafts), stringResource(R.string.no_drafts_sub), Icons.Outlined.Receipt) }
                }
                itemsIndexed(drafts, key = { _, d -> d.id }) { i, d ->
                    val old = System.currentTimeMillis() - d.createdAt > settings.draftReminderHours * 3_600_000L
                    ItemRow(
                        title = draftTitle(ctx, d, bn),
                        meta = timeFmt.format(Date(d.createdAt)),
                        modifier = Modifier.groupItem(i == 0, i == drafts.lastIndex),
                        leading = { IconTile(Icons.Outlined.EditNote, bg = Pb.medIconBg, tint = Pb.medIcon, size = 42.dp, radius = 14.dp, iconSize = 21.dp) },
                        below = { if (old) PillBadge(stringResource(R.string.old_draft), Tone.YELLOW, Modifier.padding(top = 6.dp)) },
                        trailing = { PriceColumn(moneyWhole(d.total), stringResource(R.string.continue_arrow), subColor = Pb.primary) },
                        last = i == drafts.lastIndex,
                        onClick = { onOpenDraft(d.id) },
                    )
                }
            } else {
                item { SearchBox(query, { query = it }, stringResource(R.string.search_bills_hint), Modifier.padding(bottom = 12.dp)) }
                val filtered = confirmed.filter { b -> matches(b, query) }
                val byDay = filtered.groupBy { dayFmt.format(Date(it.confirmedAt ?: it.createdAt)) }
                if (filtered.isEmpty()) item {
                    PbCard { EmptyBlock(stringResource(R.string.no_bills), icon = Icons.Outlined.Receipt) }
                }
                byDay.forEach { (day, list) ->
                    item(key = "h_$day") {
                        Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 8.dp)) {
                            Text(day.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = Pb.muted, modifier = Modifier.weight(1f))
                            Text(moneyWhole(list.sumOf { it.total }), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Pb.ink)
                        }
                    }
                    itemsIndexed(list, key = { _, b -> b.id }) { i, b ->
                        ItemRow(
                            title = Fmt.digits(ReceiptBuilder.billNo(b), bn),
                            meta = listOfNotNull(b.draftLabel, timeFmt.format(Date(b.confirmedAt ?: b.createdAt))).joinToString(" · "),
                            modifier = Modifier.groupItem(i == 0, i == list.lastIndex),
                            leading = { IconTile(Icons.Outlined.Receipt, bg = Pb.medIconBg, tint = Pb.medIcon, size = 42.dp, radius = 14.dp, iconSize = 20.dp) },
                            trailing = { PriceColumn(moneyWhole(b.total), stringResource(R.string.view)) },
                            last = i == list.lastIndex,
                            onClick = { onOpenReceipt(b.id) },
                        )
                    }
                    item(key = "s_$day") { Spacer(Modifier.height(6.dp)) }
                }
            }
        }
    }
}

private fun matches(b: BillEntity, q: String): Boolean {
    val t = q.trim().map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("")
    if (t.isEmpty()) return true
    return ReceiptBuilder.billNo(b).contains(t, ignoreCase = true) || (b.draftLabel?.contains(t, ignoreCase = true) == true) ||
        (b.billNo?.toString() == t.trimStart('0'))
}

