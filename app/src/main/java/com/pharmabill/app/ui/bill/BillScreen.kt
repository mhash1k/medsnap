@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.bill

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pharmabill.app.R
import com.pharmabill.app.data.db.entities.BillLineEntity
import com.pharmabill.app.data.repo.ConfirmResult
import com.pharmabill.app.domain.model.DiscountType
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.pricing.PriceCalculator
import com.pharmabill.app.ui.components.DraftChipsBar
import com.pharmabill.app.ui.components.EmptyState
import com.pharmabill.app.ui.components.FlagBadge
import com.pharmabill.app.ui.components.LocalBangla
import com.pharmabill.app.ui.components.MedicinePickerDialog
import com.pharmabill.app.ui.components.QtyText
import com.pharmabill.app.ui.components.draftTitle
import com.pharmabill.app.ui.components.money
import com.pharmabill.app.ui.components.moneyWhole
import com.pharmabill.app.ui.components.num
import com.pharmabill.app.ui.theme.BigNumber
import com.pharmabill.app.ui.voice.MicButton
import com.pharmabill.app.ui.voice.VoiceOutcomeEffects
import com.pharmabill.app.util.Fmt
import kotlinx.coroutines.launch

@Composable
fun BillScreen(
    openConfirm: Boolean,
    onBack: () -> Unit,
    onPhoto: (Long) -> Unit,
    onConfirmed: (Long) -> Unit,
    onSwitch: (Long) -> Unit,
    vm: BillViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val drafts by vm.openDrafts.collectAsStateWithLifecycle()
    val listening by vm.voice.listening.collectAsStateWithLifecycle()
    val partial by vm.voice.partial.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val bn = LocalBangla.current
    var editing by remember { mutableStateOf<BillLineEntity?>(null) }
    var picking by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(openConfirm) }
    var labelOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    VoiceOutcomeEffects(vm.voice, snackbar, onFinish = { id -> if (id == vm.billId) confirmOpen = true else onSwitch(id) }, onNewDraft = onSwitch)

    val bill = ui.bill

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bill?.let { draftTitle(ctx, it, bn) } ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { labelOpen = true }) { Icon(Icons.Filled.Edit, stringResource(R.string.customer_label)) }
                    IconButton(onClick = { deleteOpen = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete_draft)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.total), style = MaterialTheme.typography.labelLarge)
                            Text(moneyWhole(bill?.total ?: 0), style = BigNumber)
                        }
                        Button(onClick = { confirmOpen = true }, enabled = ui.lines.isNotEmpty(), modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(
                                if (ui.needsChecking > 0) stringResource(R.string.items_need_checking, num(ui.needsChecking))
                                else stringResource(R.string.confirm),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            DraftChipsBar(drafts, vm.billId, settings.draftReminderHours, onSelect = { if (it != vm.billId) onSwitch(it) }, onNew = { scope.launch { onSwitch(vm.newDraft()) } })
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MicButton(listening, vm.voice::toggle, size = 56)
                FilledTonalButton(onClick = { onPhoto(vm.billId) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Icons.Filled.CameraAlt, null); Text(stringResource(R.string.add_photo), Modifier.padding(start = 6.dp))
                }
                FilledTonalButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 56.dp).weight(1f)) {
                    Icon(Icons.Filled.Search, null); Text(stringResource(R.string.search), Modifier.padding(start = 6.dp))
                }
            }
            if (listening || partial.isNotBlank()) Text(if (listening && partial.isBlank()) stringResource(R.string.listening) else partial, Modifier.padding(horizontal = 16.dp))
            LazyColumn(Modifier.weight(1f)) {
                if (ui.lines.isEmpty()) item { EmptyState(stringResource(R.string.bill_empty)) }
                items(ui.lines, key = { it.id }) { l ->
                    LineRow(l, ui.medicines[l.medicineId]?.piecesPerStrip ?: 1, onClick = { editing = l }, onRemove = {
                        scope.launch {
                            val removed = vm.remove(l.id)
                            if (removed != null && snackbar.showSnackbar(ctx.getString(R.string.removed_item, removed.displayName), ctx.getString(R.string.undo), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) vm.restore(removed)
                        }
                    })
                    HorizontalDivider()
                }
                if (bill != null && bill.subtotal > 0) item {
                    Column(Modifier.padding(16.dp)) {
                        Row { Text(stringResource(R.string.subtotal), Modifier.weight(1f)); Text(money(bill.subtotal)) }
                        val d = PriceCalculator.discountAmount(bill.subtotal, bill.discountType, bill.discountValue)
                        if (d > 0) Row { Text(stringResource(R.string.discount), Modifier.weight(1f)); Text("-" + money(d)) }
                    }
                }
            }
        }
    }

    editing?.let { line ->
        LineEditorDialog(line, ui.medicines, onDismiss = { editing = null }, onSave = { m, u, q -> vm.applyFix(line, m, u, q); editing = null })
    }
    if (picking) {
        MedicinePickerDialog(onDismiss = { picking = false }, onPicked = { m, u, q ->
            picking = false
            scope.launch {
                val id = vm.add(m, u, q)
                if (id != null && snackbar.showSnackbar(ctx.getString(R.string.added_item, m.displayName), ctx.getString(R.string.undo), duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoAdd(id)
            }
        })
    }
    if (confirmOpen && bill != null) {
        ConfirmDialog(
            subtotal = bill.subtotal, type = bill.discountType, value = bill.discountValue, total = bill.total,
            needsChecking = ui.needsChecking, hasLines = ui.lines.isNotEmpty(),
            onDiscount = vm::setDiscount,
            onDismiss = { confirmOpen = false },
            onConfirm = {
                scope.launch {
                    when (val r = vm.confirm()) {
                        is ConfirmResult.Confirmed -> { confirmOpen = false; onConfirmed(r.billId) }
                        is ConfirmResult.Blocked -> snackbar.showSnackbar(ctx.getString(R.string.items_need_checking, Fmt.number(r.needsChecking.toLong(), bn)))
                        ConfirmResult.Empty -> snackbar.showSnackbar(ctx.getString(R.string.bill_empty))
                    }
                }
            },
        )
    }
    if (labelOpen && bill != null) {
        var text by remember { mutableStateOf(bill.draftLabel.orEmpty()) }
        AlertDialog(
            onDismissRequest = { labelOpen = false },
            title = { Text(stringResource(R.string.customer_label)) },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text(stringResource(R.string.customer_label_hint)) }) },
            confirmButton = { Button(onClick = { vm.setLabel(text); labelOpen = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { labelOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text(stringResource(R.string.delete_draft)) },
            text = { Text(stringResource(R.string.delete_draft_confirm)) },
            confirmButton = { Button(onClick = { scope.launch { vm.deleteDraft(); deleteOpen = false; onBack() } }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun LineRow(l: BillLineEntity, piecesPerStrip: Int, onClick: () -> Unit, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    val bn = LocalBangla.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FlagBadge(l.flag)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(l.displayName.ifBlank { stringResource(R.string.unknown_medicine) }, style = MaterialTheme.typography.titleMedium)
            Text(QtyText.plain(ctx, l.saleUnit, l.quantity, piecesPerStrip, bn), style = MaterialTheme.typography.bodyMedium)
            if (l.flag != Flag.GREEN) Text(stringResource(if (l.isBox) R.string.tap_choose_box else R.string.tap_to_check), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text(money(l.lineTotal), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, stringResource(R.string.remove)) }
    }
}

@Composable
private fun ConfirmDialog(
    subtotal: Long,
    type: DiscountType?,
    value: Long,
    total: Long,
    needsChecking: Int,
    hasLines: Boolean,
    onDiscount: (DiscountType?, Long) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var dType by remember { mutableStateOf(type ?: DiscountType.PERCENT) }
    var text by remember {
        mutableStateOf(
            when (type) {
                DiscountType.PERCENT -> Fmt.paisaToPlain(value).removeSuffix(".00")
                DiscountType.FLAT -> Fmt.paisaToPlain(value).removeSuffix(".00")
                null -> ""
            },
        )
    }
    fun push(t: DiscountType, s: String) {
        val v = Fmt.parseTaka(s) ?: 0
        onDiscount(if (v > 0) t else null, v) // percent: 5 -> 500 basis points; flat: Taka -> paisa (both x100)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_bill)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row { Text(stringResource(R.string.subtotal), Modifier.weight(1f)); Text(money(subtotal)) }
                Text(stringResource(R.string.discount), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(dType == DiscountType.PERCENT, { dType = DiscountType.PERCENT; push(dType, text) }, label = { Text("%") }, modifier = Modifier.heightIn(min = 48.dp))
                    FilterChip(dType == DiscountType.FLAT, { dType = DiscountType.FLAT; push(dType, text) }, label = { Text("৳") }, modifier = Modifier.heightIn(min = 48.dp))
                    OutlinedTextField(text, { text = it; push(dType, it) }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Row { Text(stringResource(R.string.total), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); Text(moneyWhole(total), style = MaterialTheme.typography.titleLarge) }
                if (needsChecking > 0) Text(stringResource(R.string.items_need_checking, num(needsChecking)), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = needsChecking == 0 && hasLines, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (needsChecking > 0) stringResource(R.string.items_need_checking, num(needsChecking)) else stringResource(R.string.confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
