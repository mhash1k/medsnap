@file:OptIn(ExperimentalLayoutApi::class)

package com.pharmabill.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.R
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.repo.MedicineRepository
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.pricing.PriceCalculator
import com.pharmabill.app.domain.pricing.QuantityResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MedicinePickerViewModel @Inject constructor(private val repo: MedicineRepository) : ViewModel() {
    private val _results = MutableStateFlow<List<MedicineEntity>>(emptyList())
    val results: StateFlow<List<MedicineEntity>> = _results

    val frequent: StateFlow<List<MedicineEntity>> = repo.observeFrequent(System.currentTimeMillis() - 7L * 24 * 3_600_000)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun search(q: String) {
        viewModelScope.launch { _results.value = if (q.isBlank()) emptyList() else repo.search(q) }
    }
}

/**
 * Manual add: search by brand, generic or Bangla spelling plus chips for this week's top 10.
 * With [pickOnly] it returns just the medicine (used to fix a flagged line).
 */
@Composable
fun MedicinePickerDialog(
    onDismiss: () -> Unit,
    onPicked: (MedicineEntity, SaleUnit, Int) -> Unit,
    pickOnly: Boolean = false,
    onQuickAdd: ((String) -> Unit)? = null,
    initialQuery: String = "",
    vm: MedicinePickerViewModel = hiltViewModel(),
) {
    var query by remember { mutableStateOf(initialQuery) }
    var chosen by remember { mutableStateOf<MedicineEntity?>(null) }
    val results by vm.results.collectAsState()
    val frequent by vm.frequent.collectAsState()
    LaunchedEffect(query) { vm.search(query) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.add_medicine), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                }
                val c = chosen
                if (c == null || pickOnly) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                    )
                    if (query.isBlank()) {
                        Text(stringResource(R.string.frequent_this_week), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            frequent.forEach { m ->
                                AssistChip(onClick = { if (pickOnly) { onPicked(m, QuantityResolver.defaultUnit(m.kind), 1) } else { chosen = m } }, label = { Text(m.displayName) }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                    }
                    LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                        items(results, key = { it.id }) { m ->
                            MedicineRow(m) { if (pickOnly) { onPicked(m, QuantityResolver.defaultUnit(m.kind), 1) } else { chosen = m } }
                            HorizontalDivider()
                        }
                        if (query.isNotBlank() && results.isEmpty()) {
                            item { EmptyState(stringResource(R.string.no_results)) }
                        }
                    }
                    if (onQuickAdd != null) {
                        OutlinedButton(onClick = { onQuickAdd(query) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.quick_add_new))
                        }
                    }
                } else {
                    UnitQuantityPicker(c, onBack = { chosen = null }, onAdd = { u, q -> onPicked(c, u, q) })
                }
            }
        }
    }
}

@Composable
fun MedicineRow(m: MedicineEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.displayName, style = MaterialTheme.typography.titleMedium)
            Text(listOf(m.generic, m.company).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (m.kind == MedicineKind.STRIP) money(m.stripPrice) + stringResource(R.string.per_strip) else money(m.unitPrice), style = MaterialTheme.typography.bodyMedium)
            if (!m.priceVerified) CheckPriceBadge()
        }
    }
}

@Composable
fun UnitQuantityPicker(m: MedicineEntity, onBack: () -> Unit, onAdd: (SaleUnit, Int) -> Unit) {
    val units = QuantityResolver.allowedUnits(m.kind)
    var unit by remember { mutableStateOf(QuantityResolver.defaultUnit(m.kind)) }
    var qty by remember { mutableIntStateOf(1) }
    val info = m.toInfo()
    val ctx = LocalContext.current
    val bn = LocalBangla.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(m.displayName, style = MaterialTheme.typography.headlineSmall)
        if (!m.priceVerified) CheckPriceBadge()
        ChoiceChips(units, unit, { unitLabel(it) }, { unit = it })
        Stepper(num(qty), onMinus = { qty = QuantityResolver.step(qty, -1) }, onPlus = { qty = QuantityResolver.step(qty, 1) })
        Text(QtyText.plain(ctx, unit, qty, m.piecesPerStrip, bn), style = MaterialTheme.typography.bodyLarge)
        Text(money(PriceCalculator.linePrice(info, unit, qty)), style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.back)) }
            Button(onClick = { onAdd(unit, qty) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.add_to_bill)) }
        }
    }
}
