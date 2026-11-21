@file:OptIn(ExperimentalLayoutApi::class)

package com.pharmabill.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharmabill.app.R
import com.pharmabill.app.data.db.entities.BillEntity
import com.pharmabill.app.domain.model.Flag
import com.pharmabill.app.domain.model.SaleUnit
import com.pharmabill.app.ui.theme.FlagColors
import com.pharmabill.app.util.Fmt

/** True when the UI language is Bangla (numerals follow it). */
val LocalBangla = compositionLocalOf { false }

@Composable
fun money(paisa: Long): String = Fmt.taka(paisa, LocalBangla.current)

@Composable
fun moneyWhole(paisa: Long): String = Fmt.takaWhole(paisa, LocalBangla.current)

@Composable
fun num(n: Long): String = Fmt.number(n, LocalBangla.current)

@Composable
fun num(n: Int): String = Fmt.number(n.toLong(), LocalBangla.current)

/** Quantity text like "1 strip + 4 pcs". */
object QtyText {
    fun plain(c: Context, unit: SaleUnit, qty: Int, piecesPerStrip: Int, bangla: Boolean): String {
        fun d(n: Int) = Fmt.digits(n.toString(), bangla)
        return when (unit) {
            SaleUnit.PIECE -> {
                val pps = piecesPerStrip.coerceAtLeast(1)
                if (pps > 1 && qty >= pps) {
                    val strips = qty / pps
                    val rest = qty % pps
                    if (rest == 0) c.getString(R.string.qty_strips_as_pcs, d(strips), d(qty))
                    else c.getString(R.string.qty_strip_plus_pcs, d(strips), d(rest))
                } else c.getString(R.string.qty_pcs, d(qty))
            }
            SaleUnit.STRIP -> c.getString(R.string.qty_strips, d(qty))
            SaleUnit.BOX -> c.getString(R.string.qty_boxes, d(qty))
            SaleUnit.UNIT -> c.getString(R.string.qty_units, d(qty))
        }
    }
}

@Composable
fun unitLabel(u: SaleUnit): String = stringResource(
    when (u) {
        SaleUnit.PIECE -> R.string.unit_piece
        SaleUnit.STRIP -> R.string.unit_strip
        SaleUnit.BOX -> R.string.unit_box
        SaleUnit.UNIT -> R.string.unit_unit
    },
)

/** Flag colour always comes with a symbol: check, question mark or cross. */
@Composable
fun FlagBadge(flag: Flag, modifier: Modifier = Modifier, size: Int = 32) {
    val (color, glyph, desc) = when (flag) {
        Flag.GREEN -> Triple(FlagColors.green, "✓", stringResource(R.string.flag_green))
        Flag.YELLOW -> Triple(FlagColors.yellow, "?", stringResource(R.string.flag_yellow))
        Flag.RED -> Triple(FlagColors.red, "✕", stringResource(R.string.flag_red))
    }
    Box(
        modifier
            .size(size.dp)
            .background(color, CircleShape)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = if (flag == Flag.YELLOW) Color(0xFF3D2A00) else Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.55f).sp)
    }
}

/** Flag as a soft pill: "✓ Checked", "? Check", "✕ Fix". */
@Composable
fun FlagPill(flag: Flag, modifier: Modifier = Modifier) {
    when (flag) {
        Flag.GREEN -> PillBadge("✓ " + stringResource(R.string.flag_pill_green), Tone.GREEN, modifier)
        Flag.YELLOW -> PillBadge("? " + stringResource(R.string.flag_pill_yellow), Tone.YELLOW, modifier)
        Flag.RED -> PillBadge("✕ " + stringResource(R.string.flag_pill_red), Tone.RED, modifier)
    }
}

@Composable
fun CheckPriceBadge(modifier: Modifier = Modifier) = PillBadge(stringResource(R.string.check_price), Tone.YELLOW, modifier)

@Composable
fun NeedsStockBadge(modifier: Modifier = Modifier) = PillBadge(stringResource(R.string.needs_stock_entry), Tone.RED, modifier)

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp), colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(containerColor = com.pharmabill.app.ui.theme.Pb.surface2, contentColor = com.pharmabill.app.ui.theme.Pb.tonalInk)) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.decrease))
        }
        Text(value, Modifier.width(72.dp).padding(horizontal = 4.dp), style = MaterialTheme.typography.titleLarge, maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp), colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(containerColor = com.pharmabill.app.ui.theme.Pb.surface2, contentColor = com.pharmabill.app.ui.theme.Pb.tonalInk)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.increase))
        }
    }
}

fun draftTitle(c: Context, b: BillEntity, bangla: Boolean): String {
    val n = c.getString(R.string.draft_number, Fmt.digits(b.id.toString(), bangla))
    return if (b.draftLabel.isNullOrBlank()) n else "$n · ${b.draftLabel}"
}

/** Row of open-draft pills; tap to switch the active one. */
@Composable
fun DraftChipsBar(
    drafts: List<BillEntity>,
    activeId: Long?,
    reminderHours: Int,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = androidx.compose.ui.platform.LocalContext.current
    val bn = LocalBangla.current
    val now = System.currentTimeMillis()
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        drafts.forEach { d ->
            val old = now - d.createdAt > reminderHours * 3_600_000L
            DraftPill(
                draftTitle(c, d, bn) + (if (d.total > 0) " · " + Fmt.takaWhole(d.total, bn) else "") + if (old) " ⏰" else "",
                active = d.id == activeId,
            ) { onSelect(d.id) }
        }
        DraftPill("+ " + stringResource(R.string.new_draft), active = false, onClick = onNew)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    EmptyBlock(text, modifier = modifier)
}

@Composable
fun InfoDialog(title: String, lines: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { Text(it, style = MaterialTheme.typography.titleMedium) }
            }
        },
    )
}

@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }, modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
