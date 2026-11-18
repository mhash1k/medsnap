@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import com.pharmabill.app.R

@Composable
fun MoreScreen(onDashboard: () -> Unit, onLowStock: () -> Unit, onStock: () -> Unit, onSettings: () -> Unit, onBackup: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_more)) }) }) { pad ->
        Column(Modifier.padding(pad)) {
            Item(Icons.Filled.Dashboard, stringResource(R.string.dashboard), onDashboard)
            Item(Icons.Filled.Warning, stringResource(R.string.low_stock), onLowStock)
            Item(Icons.Filled.Inventory, stringResource(R.string.stock_entry), onStock)
            Item(Icons.Filled.Settings, stringResource(R.string.settings), onSettings)
            Item(Icons.Filled.Backup, stringResource(R.string.backup_restore), onBackup)
        }
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, leadingContent = { Icon(icon, null) }, modifier = Modifier.clickable(onClick = onClick))
    HorizontalDivider()
}
