@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.pharmabill.app.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pharmabill.app.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.pharmabill.app.ui.components.FlagPill
import com.pharmabill.app.ui.components.PbCardBorder
import com.pharmabill.app.ui.components.PbCardShape
import com.pharmabill.app.ui.components.TextTile
import com.pharmabill.app.ui.components.pbCardColors
import com.pharmabill.app.ui.theme.Pb
import com.pharmabill.app.ui.components.PbBtnShape
import com.pharmabill.app.ui.components.PbTopBar
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.pricing.BoxChipType
import com.pharmabill.app.domain.pricing.QuantityResolver
import com.pharmabill.app.ui.components.ChoiceChips
import com.pharmabill.app.ui.components.CheckPriceBadge
import com.pharmabill.app.ui.components.FlagBadge
import com.pharmabill.app.ui.components.Loading
import com.pharmabill.app.ui.components.LocalBangla
import com.pharmabill.app.ui.components.MedicinePickerDialog
import com.pharmabill.app.ui.components.QtyText
import com.pharmabill.app.ui.components.Stepper
import com.pharmabill.app.ui.components.money
import com.pharmabill.app.ui.components.num
import com.pharmabill.app.ui.components.unitLabel
import com.pharmabill.app.ui.medicines.QuickAddDialog
import com.pharmabill.app.ui.theme.FlagColors
import kotlinx.coroutines.launch

@Composable
fun ScanScreen(onBack: () -> Unit, onRetake: () -> Unit, onAdded: (Int) -> Unit, vm: ScanViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var searchFor by remember { mutableStateOf<Int?>(null) }
    var quickAddFor by remember { mutableStateOf<Pair<Int, String?>?>(null) }

    Scaffold(topBar = {
        PbTopBar(
            title = { Text(stringResource(R.string.scan_result)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { TextButton(onClick = onRetake) { Text(stringResource(R.string.retake)) } },
        )
    }) { pad ->
        when (val s = ui) {
            ScanUi.Loading -> Column(Modifier.padding(pad).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.reading_photo), Modifier.padding(24.dp))
                Loading()
            }
            is ScanUi.Error -> Column(Modifier.padding(pad).padding(24.dp)) {
                Text(stringResource(R.string.scan_failed, s.message))
                Button(shape = PbBtnShape, onClick = onRetake) { Text(stringResource(R.string.retake)) }
            }
            is ScanUi.Ready -> {
                val canAdd = s.lines.any { it.medicine != null }
                LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { PhotoWithBoxes(s) }
                    if (s.blurry) item {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp), shape = PbCardShape, colors = pbCardColors(Pb.warnBg), border = androidx.compose.foundation.BorderStroke(1.dp, Pb.warnBorder)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.photo_blurry), Modifier.weight(1f))
                                TextButton(onClick = onRetake) { Text(stringResource(R.string.retake)) }
                            }
                        }
                    }
                    item {
                        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.scan_meta, s.output.modelVersion, num(s.output.elapsedMs)),
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.rerun(true) }) { Text(stringResource(R.string.one_medicine_mode)) }
                        }
                    }
                    items(s.lines, key = { it.key }) { line ->
                        ScanLineCard(
                            line, s,
                            onPick = { vm.pickMedicine(line.key, it) },
                            onSearch = { searchFor = line.key },
                            onQuickAdd = { quickAddFor = line.key to line.ai.ocrText },
                            onStep = { vm.step(line.key, it) },
                            onBox = { vm.setBox(line.key, it) },
                            onChip = { vm.chooseChip(line.key, it) },
                            onUnit = { vm.setUnit(line.key, it) },
                            onCheck = { vm.markChecked(line.key) },
                            onRemove = { vm.remove(line.key) },
                        )
                    }
                    item {
                        Button(
                            shape = PbBtnShape,
                            onClick = { scope.launch { onAdded(vm.addToDraft()) } },
                            enabled = canAdd,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).heightIn(min = 52.dp),
                        ) { Text(stringResource(R.string.add_to_draft)) }
                    }
                }
            }
        }
    }

    searchFor?.let { key ->
        MedicinePickerDialog(onDismiss = { searchFor = null }, pickOnly = true, onPicked = { m, _, _ -> vm.pickMedicine(key, m); searchFor = null },
            onQuickAdd = { q -> searchFor = null; quickAddFor = key to q.ifBlank { null } })
    }
    quickAddFor?.let { (key, text) ->
        QuickAddDialog(text, onDismiss = { quickAddFor = null }, onSaved = { id -> vm.quickAdded(key, id); quickAddFor = null })
    }
}

@Composable
private fun PhotoWithBoxes(s: ScanUi.Ready) {
    val img = s.image ?: return
    val bitmap = remember(img) { img.asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(16.dp)).aspectRatio(img.width.toFloat() / img.height.coerceAtLeast(1))) {
        Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        val density = LocalDensity.current
        val scale = with(density) { maxWidth.toPx() } / s.output.imageWidth.coerceAtLeast(1)
        Canvas(Modifier.fillMaxSize()) {
            s.lines.forEach { l ->
                val color = when (l.flag) { Flag.GREEN -> FlagColors.green; Flag.YELLOW -> FlagColors.yellow; Flag.RED -> FlagColors.red }
                l.ai.detections.forEach { d ->
                    drawRect(color, Offset(d.box.left * scale, d.box.top * scale), Size(d.box.width * scale, d.box.height * scale), style = Stroke(3f))
                }
                l.ai.area?.let { a ->
                    drawRect(color.copy(alpha = 0.6f), Offset(a.left * scale, a.top * scale), Size(a.width * scale, a.height * scale), style = Stroke(6f))
                }
            }
        }
    }
}

@Composable
private fun ScanLineCard(
    l: ScanLineUi,
    s: ScanUi.Ready,
    onPick: (com.pharmabill.app.data.db.entities.MedicineEntity) -> Unit,
    onSearch: () -> Unit,
    onQuickAdd: () -> Unit,
    onStep: (Int) -> Unit,
    onBox: (Boolean) -> Unit,
    onChip: (com.pharmabill.app.domain.pricing.BoxChip) -> Unit,
    onUnit: (SaleUnit) -> Unit,
    onCheck: () -> Unit,
    onRemove: () -> Unit,
) {
    val ctx = LocalContext.current
    val bn = LocalBangla.current
    val m = l.medicine
    Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp), shape = PbCardShape, colors = pbCardColors(), border = PbCardBorder) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextTile("✚")
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(m?.displayName ?: stringResource(R.string.unknown_medicine_pcs, num(l.ai.detectedCount)), style = MaterialTheme.typography.titleMedium)
                    FlagPill(l.flag, Modifier.padding(vertical = 4.dp))
                    if (m != null) Text(QtyText.plain(ctx, l.unit, l.quantity, m.piecesPerStrip, bn) + " · " + money(l.lineTotal), style = MaterialTheme.typography.bodyLarge)
                    if (m != null && !m.priceVerified) CheckPriceBadge()
                    l.ai.ocrText?.let { Text(stringResource(R.string.ocr_saw, it), style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, stringResource(R.string.remove)) }
            }
            // Top-3 picker
            val cands = l.ai.candidates.mapNotNull { s.candidates[it.id] }
            // Only ask "which one?" when the NAME is unsure; a sure name with an unchecked count shows no choices.
            if ((l.ai.nameFlag != Flag.GREEN || m == null) && cands.isNotEmpty()) {
                Text(stringResource(R.string.which_medicine), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cands.forEach { c ->
                        FilterChip(
                            selected = m?.id == c.id, onClick = { onPick(c) },
                            label = { Text(c.displayName + if (c.company.isNotBlank()) " · " + c.company else "") },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(shape = PbBtnShape, onClick = onSearch, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.search)) }
                if (m == null || l.flag == Flag.RED) OutlinedButton(shape = PbBtnShape, onClick = onQuickAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.quick_add)) }
            }
            if (m != null) {
                if (m.kind == MedicineKind.STRIP) {
                    // Strip/Box toggle
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !l.isBox, onClick = { onBox(false) }, label = { Text(stringResource(R.string.toggle_strip)) }, modifier = Modifier.heightIn(min = 48.dp))
                        FilterChip(selected = l.isBox, onClick = { onBox(true) }, label = { Text(stringResource(R.string.toggle_box)) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                if (l.isBox && !l.customPieces) {
                    Text(stringResource(R.string.box_what_taken), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuantityResolver.boxChips(m.toInfo()).forEach { chip ->
                            val label = when (chip.type) {
                                BoxChipType.FULL_BOX -> stringResource(R.string.chip_full_box, num(chip.strips))
                                BoxChipType.STRIPS -> stringResource(R.string.chip_strips, num(chip.strips))
                                BoxChipType.CUSTOM -> stringResource(R.string.chip_custom)
                            }
                            val selected = l.boxChipChosen && l.unit == chip.unit && (chip.type == BoxChipType.CUSTOM || l.quantity == chip.quantity)
                            AssistChipOrFilter(label, selected) { onChip(chip) }
                        }
                    }
                } else {
                    if (m.kind == MedicineKind.STRIP) ChoiceChips(listOf(SaleUnit.PIECE, SaleUnit.STRIP, SaleUnit.BOX), l.unit, { unitLabel(it) }, onUnit)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stepper(num(l.quantity), onMinus = { onStep(-1) }, onPlus = { onStep(1) })
                        if (l.flag == Flag.YELLOW) Button(shape = PbBtnShape, onClick = onCheck, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.mark_checked)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistChipOrFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) FilterChip(selected = true, onClick = onClick, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
    else AssistChip(onClick = onClick, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
}

