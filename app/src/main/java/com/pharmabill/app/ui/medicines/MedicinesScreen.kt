@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.medicines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.pharmabill.app.ui.components.DraftPill
import com.pharmabill.app.ui.components.EmptyBlock
import com.pharmabill.app.ui.components.ItemRow
import com.pharmabill.app.ui.components.PageHeader
import com.pharmabill.app.ui.components.PbButton
import com.pharmabill.app.ui.components.PbCard
import com.pharmabill.app.ui.components.PillBadge
import com.pharmabill.app.ui.components.PriceColumn
import com.pharmabill.app.ui.components.RoundIconButton
import com.pharmabill.app.ui.components.SearchBox
import com.pharmabill.app.ui.components.SegmentedTabs
import com.pharmabill.app.ui.components.TextTile
import com.pharmabill.app.ui.components.Tone
import com.pharmabill.app.ui.components.groupItem
import com.pharmabill.app.ui.components.money
import com.pharmabill.app.ui.components.num
import com.pharmabill.app.ui.theme.Pb
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(Pb.bg).padding(horizontal = 18.dp, vertical = 10.dp)) {
                PbButton(stringResource(R.string.add_medicine), { onEdit(null) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)) {
            item {
                PageHeader(stringResource(R.string.tab_medicines), stringResource(R.string.medicines_sub), trailing = {
                    Row {
                        RoundIconButton(Icons.Outlined.Inventory2, stringResource(R.string.stock_entry)) { onStock(null) }
                        RoundIconButton(Icons.Filled.Add, stringResource(R.string.add_medicine)) { onEdit(null) }
                    }
                })
                SearchBox(ui.query, vm::setQuery, stringResource(R.string.search_hint), Modifier.padding(bottom = 12.dp))
                SegmentedTabs(
                    listOf(stringResource(R.string.filter_all), stringResource(R.string.filter_low), stringResource(R.string.filter_negative)),
                    ui.stock.ordinal, { vm.setStock(StockFilter.entries[it]) },
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MedicineForm.entries.forEach { f -> DraftPill(formLabel(f), ui.form == f) { vm.setForm(f) } }
                }
            }
            if (ui.items.isEmpty()) item {
                PbCard { EmptyBlock(stringResource(R.string.no_results), stringResource(R.string.no_results_sub), Icons.Outlined.Inventory2) }
            }
            itemsIndexed(ui.items, key = { _, m -> m.id }) { i, m ->
                MedicineListRow(m, onClick = { onEdit(m.id) }, onStock = { onStock(m.id) }, modifier = Modifier.groupItem(i == 0, i == ui.items.lastIndex), last = i == ui.items.lastIndex)
            }
        }
    }
}

/** Glyph for the medicine tile, by form. */
fun formGlyph(f: MedicineForm): String = when (f) {
    MedicineForm.SYRUP, MedicineForm.DROPS -> "◒"
    MedicineForm.CAPSULE -> "◖"
    MedicineForm.CREAM -> "◍"
    MedicineForm.INJECTION -> "⟟"
    else -> "✚"
}

@Composable
fun MedicineListRow(m: MedicineEntity, onClick: () -> Unit, onStock: () -> Unit, modifier: Modifier = Modifier, last: Boolean = false) {
    ItemRow(
        title = m.brand,
        titleSuffix = m.strength,
        meta = listOf(formLabel(m.form), m.generic, m.company).filter { it.isNotBlank() }.joinToString(" · "),
        modifier = modifier,
        leading = { TextTile(formGlyph(m.form)) },
        below = {
            if (!m.priceVerified || m.needsStockEntry) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!m.priceVerified) PillBadge(stringResource(R.string.check_price), Tone.YELLOW)
                    if (m.needsStockEntry) PillBadge(stringResource(R.string.needs_stock_entry), Tone.RED)
                }
            }
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.clickable(onClick = onStock).padding(start = 4.dp)) {
                PriceColumn(
                    stringResource(if (m.kind == MedicineKind.STRIP) R.string.n_pcs else R.string.n_units, num(m.stockQty)),
                    stringResource(if (m.stockQty <= 0) R.string.out_of_stock else if (m.isLowStock) R.string.low_stock else R.string.in_stock),
                    valueColor = if (m.stockQty < 0) Pb.red else Pb.ink,
                    subColor = if (m.isLowStock) Pb.red else Pb.muted,
                )
                Text(
                    if (m.kind == MedicineKind.STRIP) money(m.stripPrice) + stringResource(R.string.per_strip) else money(m.unitPrice),
                    Modifier.padding(top = 3.dp), fontSize = 11.sp, color = Pb.muted, maxLines = 1,
                )
            }
        },
        last = last,
        onClick = onClick,
    )
}
