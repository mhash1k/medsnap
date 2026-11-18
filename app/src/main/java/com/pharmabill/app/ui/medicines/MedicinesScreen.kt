@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.medicines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.R
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.repo.MatcherProvider
import com.pharmabill.app.data.repo.MedicineRepository
import com.pharmabill.app.domain.model.MedicineForm
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.ui.components.CheckPriceBadge
import com.pharmabill.app.ui.components.EmptyState
import com.pharmabill.app.ui.components.NeedsStockBadge
import com.pharmabill.app.ui.components.money
import com.pharmabill.app.ui.components.num
import com.pharmabill.app.ui.theme.FlagColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class StockFilter { ALL, LOW, NEGATIVE }

data class MedicinesUi(
    val query: String = "",
    val form: MedicineForm? = null,
    val stock: StockFilter = StockFilter.ALL,
    val items: List<MedicineEntity> = emptyList(),
)

@HiltViewModel
class MedicinesViewModel @Inject constructor(repo: MedicineRepository, private val matcher: MatcherProvider) : ViewModel() {
    private val query = MutableStateFlow("")
    private val form = MutableStateFlow<MedicineForm?>(null)
    private val stock = MutableStateFlow(StockFilter.ALL)

    val ui: StateFlow<MedicinesUi> = combine(repo.observeAll(), query, form, stock, matcher.snapshot) { all, q, f, st, snap ->
        val base = if (q.isBlank() || snap == null) all else snap.matcher.search(q, 500).mapNotNull { id -> all.firstOrNull { it.id == id } }
        val items = base.filter { f == null || it.form == f }.filter {
            when (st) {
                StockFilter.ALL -> true
                StockFilter.LOW -> it.isLowStock
                StockFilter.NEGATIVE -> it.stockQty < 0
            }
        }
        MedicinesUi(q, f, st, items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MedicinesUi())

    fun setQuery(q: String) { query.value = q }
    fun setForm(f: MedicineForm?) { form.value = if (form.value == f) null else f }
    fun setStock(s: StockFilter) { stock.value = s }
}

@Composable
fun MedicinesScreen(onEdit: (Long?) -> Unit, onStock: (Long?) -> Unit, vm: MedicinesViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_medicines)) },
                actions = { IconButton(onClick = { onStock(null) }) { Icon(Icons.Filled.Inventory, stringResource(R.string.stock_entry)) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(null) }) { Icon(Icons.Filled.Add, stringResource(R.string.add_medicine)) }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                ui.query, vm::setQuery, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text(stringResource(R.string.search_hint)) }, singleLine = true,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StockFilter.entries.forEach { s ->
                    FilterChip(ui.stock == s, { vm.setStock(s) }, label = {
                        Text(stringResource(when (s) { StockFilter.ALL -> R.string.filter_all; StockFilter.LOW -> R.string.filter_low; StockFilter.NEGATIVE -> R.string.filter_negative }))
                    })
                }
                MedicineForm.entries.forEach { f -> FilterChip(ui.form == f, { vm.setForm(f) }, label = { Text(formLabel(f)) }) }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(ui.items, key = { it.id }) { m ->
                    MedicineListRow(m, onClick = { onEdit(m.id) }, onStock = { onStock(m.id) })
                    HorizontalDivider()
                }
                if (ui.items.isEmpty()) item { EmptyState(stringResource(R.string.no_results)) }
            }
        }
    }
}

@Composable
fun MedicineListRow(m: MedicineEntity, onClick: () -> Unit, onStock: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(m.displayName, style = MaterialTheme.typography.titleMedium)
            Text(listOf(formLabel(m.form), m.generic, m.company).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!m.priceVerified) CheckPriceBadge()
                if (m.needsStockEntry) NeedsStockBadge()
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.clickable(onClick = onStock).padding(start = 8.dp)) {
            Text(if (m.kind == MedicineKind.STRIP) money(m.stripPrice) + stringResource(R.string.per_strip) else money(m.unitPrice))
            Text(
                stringResource(if (m.kind == MedicineKind.STRIP) R.string.stock_pcs else R.string.stock_units, num(m.stockQty)),
                color = if (m.isLowStock) FlagColors.red else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
