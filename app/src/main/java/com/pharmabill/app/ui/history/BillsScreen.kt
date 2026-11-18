@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.pharmabill.app.ui.components.EmptyState
import com.pharmabill.app.ui.components.LocalBangla
import com.pharmabill.app.ui.components.draftTitle
import com.pharmabill.app.ui.components.moneyWhole
import com.pharmabill.app.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class BillsViewModel @Inject constructor(bills: BillRepository, settings: SettingsStore) : ViewModel() {
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
    val dayFmt = SimpleDateFormat("EEE, dd MMM yyyy", if (bn) Locale.forLanguageTag("bn-BD") else Locale.US)
    val timeFmt = SimpleDateFormat("hh:mm a", if (bn) Locale.forLanguageTag("bn-BD") else Locale.US)

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_bills)) }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.open_drafts, Fmt.number(drafts.size.toLong(), bn))) })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.history)) })
            }
            if (tab == 0) {
                LazyColumn(Modifier.weight(1f)) {
                    if (drafts.isEmpty()) item { EmptyState(stringResource(R.string.no_drafts)) }
                    items(drafts, key = { it.id }) { d ->
                        val old = System.currentTimeMillis() - d.createdAt > settings.draftReminderHours * 3_600_000L
                        BillRow(draftTitle(ctx, d, bn), timeFmt.format(Date(d.createdAt)) + if (old) " · " + stringResource(R.string.old_draft) else "", moneyWhole(d.total)) { onOpenDraft(d.id) }
                        HorizontalDivider()
                    }
                }
            } else {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(12.dp), leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text(stringResource(R.string.search_bills_hint)) }, singleLine = true)
                val filtered = confirmed.filter { b -> matches(b, query) }
                val byDay = filtered.groupBy { dayFmt.format(Date(it.confirmedAt ?: it.createdAt)) }
                LazyColumn(Modifier.weight(1f)) {
                    if (filtered.isEmpty()) item { EmptyState(stringResource(R.string.no_bills)) }
                    byDay.forEach { (day, list) ->
                        item(key = "h_$day") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(day, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(moneyWhole(list.sumOf { it.total }), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        items(list, key = { it.id }) { b ->
                            BillRow(Fmt.digits(ReceiptBuilder.billNo(b), bn) + (b.draftLabel?.let { " · $it" } ?: ""), timeFmt.format(Date(b.confirmedAt ?: b.createdAt)), moneyWhole(b.total)) { onOpenReceipt(b.id) }
                            HorizontalDivider()
                        }
                    }
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

@Composable
private fun BillRow(title: String, subtitle: String, amount: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Text(amount, style = MaterialTheme.typography.titleMedium)
    }
}
