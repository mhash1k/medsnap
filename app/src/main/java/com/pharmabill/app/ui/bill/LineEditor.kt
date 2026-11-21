@file:OptIn(ExperimentalLayoutApi::class)

package com.pharmabill.app.ui.bill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pharmabill.app.R
import com.pharmabill.app.ui.components.PbBtnShape
import com.pharmabill.app.data.db.entities.BillLineEntity
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.domain.pricing.BoxChipType
import com.pharmabill.app.domain.pricing.PriceCalculator
import com.pharmabill.app.domain.pricing.QuantityResolver
import com.pharmabill.app.ui.components.ChoiceChips
import com.pharmabill.app.ui.components.FlagBadge
import com.pharmabill.app.ui.components.LocalBangla
import com.pharmabill.app.ui.components.MedicinePickerDialog
import com.pharmabill.app.ui.components.QtyText
import com.pharmabill.app.ui.components.Stepper
import com.pharmabill.app.ui.components.money
import com.pharmabill.app.ui.components.num
import com.pharmabill.app.ui.components.unitLabel

/** Fix a line: pick a suggestion or search, set unit and quantity, choose a box chip. Saving makes it green. */
@Composable
fun LineEditorDialog(
    line: BillLineEntity,
    medicines: Map<Long, MedicineEntity>,
    onDismiss: () -> Unit,
    onSave: (MedicineEntity, SaleUnit, Int) -> Unit,
) {
    var med by remember { mutableStateOf(line.medicineId?.let { medicines[it] }) }
    var unit by remember { mutableStateOf(line.saleUnit) }
    var qty by remember { mutableIntStateOf(line.quantity) }
    var boxMode by remember { mutableStateOf(line.isBox) }
    var searching by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val bn = LocalBangla.current
    val suggestions = line.candidates.mapNotNull { medicines[it] }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlagBadge(line.flag, size = 24)
                Text(med?.displayName ?: line.displayName.ifBlank { stringResource(R.string.unknown_medicine) })
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                line.ocrText?.let { Text(stringResource(R.string.heard_or_saw, it), style = MaterialTheme.typography.bodySmall) }
                if (suggestions.isNotEmpty()) {
                    Text(stringResource(R.string.did_you_mean), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { s ->
                            FilterChip(selected = med?.id == s.id, onClick = {
                                med = s
                                if (s.kind.name == "UNIT") {
                                    unit = SaleUnit.UNIT
                                    boxMode = false
                                } else if (unit == SaleUnit.UNIT) {
                                    unit = SaleUnit.PIECE
                                }
                            }, label = { Text(s.displayName) }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                }
                OutlinedButton(shape = PbBtnShape, onClick = { searching = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.search)) }
                val m = med
                if (m != null) {
                    if (boxMode) {
                        Text(stringResource(R.string.box_what_taken), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuantityResolver.boxChips(m.toInfo()).forEach { chip ->
                                val label = when (chip.type) {
                                    BoxChipType.FULL_BOX -> stringResource(R.string.chip_full_box, num(chip.strips))
                                    BoxChipType.STRIPS -> stringResource(R.string.chip_strips, num(chip.strips))
                                    BoxChipType.CUSTOM -> stringResource(R.string.chip_custom)
                                }
                                AssistChip(onClick = {
                                    boxMode = false
                                    if (chip.type == BoxChipType.CUSTOM) { unit = SaleUnit.PIECE; qty = m.piecesPerStrip } else { unit = chip.unit; qty = chip.quantity }
                                }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                    } else {
                        ChoiceChips(QuantityResolver.allowedUnits(m.kind), unit, { unitLabel(it) }, { unit = it; qty = 1 })
                        Stepper(num(qty), onMinus = { qty = QuantityResolver.step(qty, -1) }, onPlus = { qty = QuantityResolver.step(qty, 1) })
                        Text(QtyText.plain(ctx, unit, qty, m.piecesPerStrip, bn) + " · " + money(PriceCalculator.linePrice(m.toInfo(), unit, qty)), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(shape = PbBtnShape, onClick = { med?.let { onSave(it, unit, qty) } }, enabled = med != null && !boxMode) { Text(stringResource(R.string.mark_checked)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (searching) {
        MedicinePickerDialog(onDismiss = { searching = false }, pickOnly = true, initialQuery = "", onPicked = { m, u, _ ->
            med = m; unit = if (boxMode && m.kind.name == "STRIP") unit else u; if (m.kind.name == "UNIT") boxMode = false
            searching = false
        })
    }
}
