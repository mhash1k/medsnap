@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.lowstock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.R
import com.pharmabill.app.data.repo.MedicineRepository
import com.pharmabill.app.ui.components.EmptyState
import com.pharmabill.app.ui.medicines.MedicineListRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LowStockViewModel @Inject constructor(repo: MedicineRepository) : ViewModel() {
    val items = repo.observeLowStock().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun LowStockScreen(onBack: () -> Unit, onStock: (Long) -> Unit, vm: LowStockViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.low_stock)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item { Text(stringResource(R.string.low_stock_hint), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            items(items, key = { it.id }) { m ->
                MedicineListRow(m, onClick = { onStock(m.id) }, onStock = { onStock(m.id) })
                HorizontalDivider()
            }
            if (items.isEmpty()) item { EmptyState(stringResource(R.string.no_low_stock)) }
        }
    }
}
