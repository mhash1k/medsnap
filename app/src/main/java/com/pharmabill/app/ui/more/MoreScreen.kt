package com.pharmabill.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.BuildConfig
import com.pharmabill.app.R
import com.pharmabill.app.data.prefs.AppSettings
import com.pharmabill.app.data.prefs.SettingsStore
import com.pharmabill.app.ui.components.BrandMark
import com.pharmabill.app.ui.components.CardTitle
import com.pharmabill.app.ui.components.Eyebrow
import com.pharmabill.app.ui.components.MoreGroup
import com.pharmabill.app.ui.components.MoreItem
import com.pharmabill.app.ui.components.PageHeader
import com.pharmabill.app.ui.components.PagePadding
import com.pharmabill.app.ui.components.PbCard
import com.pharmabill.app.ui.components.PillBadge
import com.pharmabill.app.ui.components.Tone
import com.pharmabill.app.ui.theme.Pb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(store: SettingsStore) : ViewModel() {
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
}

@Composable
fun MoreScreen(
    onDashboard: () -> Unit,
    onLowStock: () -> Unit,
    onStock: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onBackup: () -> Unit,
    vm: MoreViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding)) {
        PageHeader(stringResource(R.string.tab_more), stringResource(R.string.more_sub))
        PbCard(container = Pb.infoBg, border = Pb.infoBorder, onClick = onSettings) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandMark(44.dp)
                CardTitle(
                    settings.shopName.ifBlank { stringResource(R.string.app_name) },
                    Modifier.weight(1f),
                    sub = settings.shopAddress.ifBlank { stringResource(R.string.owner_account) },
                )
                PillBadge(stringResource(R.string.offline), Tone.BLUE)
            }
        }
        Eyebrow(stringResource(R.string.manage))
        MoreGroup {
            MoreItem(Icons.Outlined.BarChart, stringResource(R.string.dashboard), stringResource(R.string.dashboard_desc), onDashboard)
            MoreItem(Icons.Outlined.WarningAmber, stringResource(R.string.low_stock), stringResource(R.string.low_stock_desc), onLowStock)
            MoreItem(Icons.Outlined.Inventory2, stringResource(R.string.stock_entry), stringResource(R.string.stock_entry_desc), onStock)
            MoreItem(Icons.Outlined.Receipt, stringResource(R.string.sales_history), stringResource(R.string.sales_history_desc), onHistory, last = true)
        }
        Eyebrow(stringResource(R.string.preferences))
        MoreGroup {
            MoreItem(Icons.Outlined.Settings, stringResource(R.string.settings), stringResource(R.string.settings_desc), onSettings)
            MoreItem(Icons.Outlined.CloudUpload, stringResource(R.string.backup_restore), stringResource(R.string.backup_desc), onBackup, last = true)
        }
        Text(
            stringResource(R.string.app_name) + " · " + stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), fontSize = 11.sp, color = Pb.muted, textAlign = TextAlign.Center,
        )
    }
}
