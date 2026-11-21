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
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.pharmabill.app.ui.components.CardTitle
import com.pharmabill.app.ui.components.IconTile
import com.pharmabill.app.ui.components.PagePadding
import com.pharmabill.app.ui.components.PbButton
import com.pharmabill.app.ui.components.PbCard
import com.pharmabill.app.ui.components.PbSecondaryButton
import com.pharmabill.app.ui.theme.Pb
import com.pharmabill.app.ui.components.PbBtnShape
import com.pharmabill.app.ui.components.PbTopBar
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
            PbTopBar(
                title = { Text(stringResource(R.string.backup_restore)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            PbCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconTile(Icons.Outlined.CloudUpload, bg = Pb.surface2, tint = Color(0xFF7B8796), size = 54.dp, radius = 18.dp, iconSize = 26.dp)
                    Text(stringResource(R.string.data_on_device), Modifier.padding(top = 12.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.backup_hint), Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Pb.muted, textAlign = TextAlign.Center)
                }
                PbButton(stringResource(R.string.backup_now), {
                    val name = "pharmabill_backup_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".zip"
                    create.launch(name)
                }, Modifier.fillMaxWidth().padding(top = 16.dp), icon = Icons.Filled.Backup)
            }
            PbCard {
                CardTitle(stringResource(R.string.restore_from_file), sub = stringResource(R.string.restore_hint))
                PbSecondaryButton(stringResource(R.string.restore_from_file), {
                    open.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }, Modifier.fillMaxWidth().padding(top = 14.dp), icon = Icons.Filled.Restore)
            }
        }
    }
    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.restore_from_file)) },
            text = { Text(stringResource(R.string.restore_confirm)) },
            confirmButton = {
                Button(shape = PbBtnShape, onClick = {
                    pendingRestore = null
                    scope.launch { if (!vm.restore(uri)) snackbar.showSnackbar(ctx.getString(R.string.restore_failed)) }
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
