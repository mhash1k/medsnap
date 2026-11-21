@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.R
import androidx.compose.material.icons.outlined.Info
import com.pharmabill.app.ui.components.NoticeCard
import com.pharmabill.app.ui.components.PbCardBorder
import com.pharmabill.app.ui.components.PbCardShape
import com.pharmabill.app.ui.components.SearchLauncher
import com.pharmabill.app.ui.components.pbCardColors
import com.pharmabill.app.ui.components.PbBtnShape
import com.pharmabill.app.ui.components.PbTopBar
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.repo.MedicineRepository
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.usecase.AdjustStockUseCase
import com.pharmabill.app.ui.components.ChoiceChips
import com.pharmabill.app.ui.components.LocalBangla
import com.pharmabill.app.ui.components.MedicinePickerDialog
import com.pharmabill.app.ui.components.MedicineRow
import com.pharmabill.app.ui.components.NeedsStockBadge
import com.pharmabill.app.ui.components.num
import com.pharmabill.app.ui.components.unitLabel
import com.pharmabill.app.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockEntryViewModel @Inject constructor(
    private val repo: MedicineRepository,
    private val adjust: AdjustStockUseCase,
) : ViewModel() {
    private val _selected = MutableStateFlow<MedicineEntity?>(null)
    val selected: StateFlow<MedicineEntity?> = _selected
    val needsStock = repo.observeNeedsStock().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun select(id: Long?) {
        viewModelScope.launch { _selected.value = id?.let { repo.get(it) } }
    }

    suspend fun addStock(unit: SaleUnit, qty: Int, note: String): Long? {
        val m = _selected.value ?: return null
        val s = adjust.stockIn(m.id, unit, qty, note) ?: return null
        _selected.value = repo.get(m.id)
        return s.quantity
    }

    suspend fun setCount(count: Long, note: String): Long? {
        val m = _selected.value ?: return null
        val s = adjust.setCount(m.id, count, note) ?: return null
        _selected.value = repo.get(m.id)
        return s.quantity
    }
}

@Composable
fun StockEntryScreen(medicineId: Long?, onBack: () -> Unit, vm: StockEntryViewModel = hiltViewModel()) {
    LaunchedEffect(medicineId) { vm.select(medicineId) }
    val selected by vm.selected.collectAsStateWithLifecycle()
    val needs by vm.needsStock.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val bn = LocalBangla.current

    Scaffold(
        topBar = {
            PbTopBar(
                title = { Text(stringResource(R.string.stock_entry)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 18.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SearchLauncher(selected?.displayName ?: stringResource(R.string.choose_medicine), { picking = true })
            }
            selected?.let { m ->
                item {
                    StockForm(m) { unit, qty, count, note ->
                        scope.launch {
                            val result = if (count != null) vm.setCount(count, note) else vm.addStock(unit, qty, note)
                            if (result != null) snack.showSnackbar(ctx.getString(R.string.stock_now, Fmt.number(result, bn)))
                        }
                    }
                }
            }
            if (selected == null) item {
                NoticeCard(stringResource(R.string.stock_tracked_title), stringResource(R.string.stock_tracked_text), Icons.Outlined.Info)
            }
            if (needs.isNotEmpty()) {
                item { Text(stringResource(R.string.needs_stock_list), style = MaterialTheme.typography.titleMedium) }
                items(needs, key = { it.id }) { m ->
                    Column {
                        MedicineRow(m) { vm.select(m.id) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeedsStockBadge()
                            Text(stringResource(R.string.stock_value, num(m.stockQty)), color = MaterialTheme.colorScheme.error)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (picking) {
        MedicinePickerDialog(onDismiss = { picking = false }, pickOnly = true, onPicked = { m, _, _ -> vm.select(m.id); picking = false })
    }
}

@Composable
private fun StockForm(m: MedicineEntity, onSubmit: (SaleUnit, Int, Long?, String) -> Unit) {
    val units = if (m.kind == MedicineKind.STRIP) listOf(SaleUnit.STRIP, SaleUnit.BOX, SaleUnit.PIECE) else listOf(SaleUnit.UNIT)
    var unit by remember(m.id) { mutableStateOf(units.first()) }
    var qty by remember(m.id) { mutableStateOf("") }
    var exact by remember(m.id) { mutableStateOf("") }
    var note by remember(m.id) { mutableStateOf("") }
    Card(Modifier.fillMaxWidth(), shape = PbCardShape, colors = pbCardColors(), border = PbCardBorder) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(m.displayName, style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(if (m.kind == MedicineKind.STRIP) R.string.stock_pcs else R.string.stock_units, num(m.stockQty)),
                style = MaterialTheme.typography.headlineSmall,
                color = if (m.stockQty < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (m.needsStockEntry) NeedsStockBadge()
            Text(stringResource(R.string.add_stock), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(units, unit, { unitLabel(it) }, { unit = it })
            OutlinedTextField(qty, { qty = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.quantity)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.note)) }, singleLine = true)
            Button(
                shape = PbBtnShape,
                onClick = { Fmt.parseIntLocal(qty)?.takeIf { it > 0 }?.let { onSubmit(unit, it, null, note); qty = "" } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.add_stock)) }
            HorizontalDivider()
            Text(stringResource(R.string.set_exact_count), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(exact, { exact = it }, Modifier.weight(1f), label = { Text(stringResource(if (m.kind == MedicineKind.STRIP) R.string.count_in_pieces else R.string.count_in_units)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedButton(shape = PbBtnShape, onClick = { exact.trim().let { t -> Fmt.parseIntLocal(t)?.let { onSubmit(unit, 0, it.toLong(), note); exact = "" } } }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.set))
                }
            }
        }
    }
}
