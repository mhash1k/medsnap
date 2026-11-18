@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.pharmabill.app.R
import com.pharmabill.app.data.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(private val backup: BackupManager) : ViewModel() {
    suspend fun export(uri: android.net.Uri) = runCatching { backup.export(uri) }.isSuccess
    suspend fun restore(uri: android.net.Uri) = runCatching { backup.restore(uri) }.getOrDefault(false)
}

@Composable
fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch { snackbar.showSnackbar(ctx.getString(if (vm.export(uri)) R.string.backup_done else R.string.backup_failed)) }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = {
                val name = "pharmabill_backup_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".zip"
                create.launch(name)
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Filled.Backup, null); Text(stringResource(R.string.backup_now), Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Filled.Restore, null); Text(stringResource(R.string.restore_from_file), Modifier.padding(start = 8.dp))
            }
            Text(stringResource(R.string.restore_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.restore_from_file)) },
            text = { Text(stringResource(R.string.restore_confirm)) },
            confirmButton = {
                Button(onClick = {
                    pendingRestore = null
                    scope.launch { if (!vm.restore(uri)) snackbar.showSnackbar(ctx.getString(R.string.restore_failed)) }
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
