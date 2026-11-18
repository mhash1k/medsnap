@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.receipt

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.R
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.prefs.SettingsStore
import com.pharmabill.app.data.repo.BillRepository
import com.pharmabill.app.data.repo.MatcherProvider
import com.pharmabill.app.device.pdf.Receipt
import com.pharmabill.app.device.pdf.ReceiptBuilder
import com.pharmabill.app.device.pdf.ReceiptLayout
import com.pharmabill.app.device.pdf.ReceiptPdfGenerator
import com.pharmabill.app.device.print.PrinterManager
import com.pharmabill.app.domain.usecase.DraftManager
import com.pharmabill.app.ui.components.Loading
import com.pharmabill.app.ui.components.NeedsStockBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ReceiptUi(
    val receipt: Receipt,
    val preview: Bitmap,
    val billNo: String,
    val negative: List<MedicineEntity>,
    val printerName: String?,
)

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val bills: BillRepository,
    private val settings: SettingsStore,
    private val matcher: MatcherProvider,
    private val printer: PrinterManager,
    private val drafts: DraftManager,
) : ViewModel() {
    private val billId: Long = savedState.get<Long>("billId") ?: 0
    private val _ui = MutableStateFlow<ReceiptUi?>(null)
    val ui: StateFlow<ReceiptUi?> = _ui
    private var printerAddress: String? = null

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val bill = bills.bill(billId) ?: return@launch
            val lines = bills.lines(billId)
            val s = settings.current()
            printerAddress = s.printerAddress
            val snap = matcher.current()
            val r = ReceiptBuilder.build(context, s, bill, lines) { id -> id?.let { snap.medicines[it]?.piecesPerStrip } ?: 1 }
            val bmp = withContext(Dispatchers.Default) { ReceiptLayout.bitmap(r) }
            val negative = lines.mapNotNull { it.medicineId }.distinct().mapNotNull { snap.medicines[it] }.filter { it.needsStockEntry || it.stockQty < 0 }
            _ui.value = ReceiptUi(r, bmp, ReceiptBuilder.billNo(bill), negative, s.printerName)
        }
    }

    fun hasPrinter() = printerAddress != null
    fun printPermissions() = printer.requiredPermissions()

    suspend fun print(): Result<Unit> {
        val u = _ui.value ?: return Result.failure(IllegalStateException())
        val addr = printerAddress ?: return Result.failure(IllegalStateException("no printer"))
        return printer.print(addr, u.preview)
    }

    suspend fun pdf(): Intent? {
        val u = _ui.value ?: return null
        val file = withContext(Dispatchers.IO) { ReceiptPdfGenerator.generate(context, u.receipt, u.billNo) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun newBill(): Long = drafts.newDraft()
}

@Composable
fun ReceiptScreen(onBack: () -> Unit, onNewBill: (Long) -> Unit, onStock: (Long) -> Unit, vm: ReceiptViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun doPrint() {
        scope.launch {
            val r = vm.print()
            snackbar.showSnackbar(ctx.getString(if (r.isSuccess) R.string.printed else R.string.print_failed))
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) doPrint()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.receipt)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val u = ui
        if (u == null) Loading(Modifier.padding(pad)) else {
            Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(u.preview.asImageBitmap(), stringResource(R.string.receipt), Modifier.width(300.dp).border(1.dp, Color.LightGray))
                if (u.negative.isNotEmpty()) {
                    Text(stringResource(R.string.sold_without_stock), style = MaterialTheme.typography.titleMedium)
                    u.negative.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(m.displayName)
                            NeedsStockBadge()
                            OutlinedButton(onClick = { onStock(m.id) }) { Text(stringResource(R.string.stock_entry)) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val perms = vm.printPermissions()
                            if (perms.isEmpty() || perms.all { androidx.core.content.ContextCompat.checkSelfPermission(ctx, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) doPrint()
                            else permLauncher.launch(perms)
                        },
                        enabled = vm.hasPrinter(),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) { Icon(Icons.Filled.Print, null); Text(stringResource(R.string.print), Modifier.padding(start = 6.dp)) }
                    Button(onClick = {
                        scope.launch { vm.pdf()?.let { ctx.startActivity(Intent.createChooser(it, ctx.getString(R.string.share_pdf))) } }
                    }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Icon(Icons.Filled.Share, null); Text(stringResource(R.string.share_pdf), Modifier.padding(start = 6.dp)) }
                }
                if (!vm.hasPrinter()) Text(stringResource(R.string.no_printer_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { scope.launch { onNewBill(vm.newBill()) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.new_bill)) }
            }
        }
    }
}
