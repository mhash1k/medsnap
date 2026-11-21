@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.medicines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pharmabill.app.R
import com.pharmabill.app.ui.components.PbBtnShape
import com.pharmabill.app.ui.components.PbTopBar
import com.pharmabill.app.ai.ocr.TextNormalizer
import com.pharmabill.app.data.db.entities.MedicineEntity
import com.pharmabill.app.data.repo.MedicineRepository
import com.pharmabill.app.domain.model.MedicineForm
import com.pharmabill.app.domain.model.MedicineKind
import com.pharmabill.app.ui.components.ChoiceChips
import com.pharmabill.app.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MedicineFormState(
    val id: Long = 0,
    val brand: String = "",
    val generic: String = "",
    val strength: String = "",
    val form: MedicineForm = MedicineForm.TABLET,
    val company: String = "",
    val piecesPerStrip: String = "10",
    val stripsPerBox: String = "1",
    val stripPrice: String = "",
    val unitPrice: String = "",
    val piecePriceOverride: String = "",
    val boxPriceOverride: String = "",
    val lowStock: String = "",
    val banglaName: String = "",
    val aliases: String = "",
    val error: Boolean = false,
    val loaded: Boolean = false,
) {
    val kind: MedicineKind get() = form.defaultKind
}

@HiltViewModel
class MedicineEditViewModel @Inject constructor(private val repo: MedicineRepository) : ViewModel() {
    private val _state = MutableStateFlow(MedicineFormState())
    val state: StateFlow<MedicineFormState> = _state
    private var original: MedicineEntity? = null
    private var initialised = false

    fun init(id: Long?, prefillOcr: String?) {
        if (initialised) return
        initialised = true
        viewModelScope.launch {
            val m = id?.takeIf { it > 0 }?.let { repo.get(it) }
            if (m != null) {
                original = m
                val aliases = repo.aliases(m.id).joinToString(", ") { it.alias }
                _state.value = MedicineFormState(
                    id = m.id, brand = m.brand, generic = m.generic, strength = m.strength, form = m.form, company = m.company,
                    piecesPerStrip = m.piecesPerStrip.toString(), stripsPerBox = m.stripsPerBox.toString(),
                    stripPrice = if (m.stripPrice > 0) Fmt.paisaToPlain(m.stripPrice) else "",
                    unitPrice = if (m.unitPrice > 0) Fmt.paisaToPlain(m.unitPrice) else "",
                    piecePriceOverride = m.piecePriceOverride?.let { Fmt.paisaToPlain(it) } ?: "",
                    boxPriceOverride = m.boxPriceOverride?.let { Fmt.paisaToPlain(it) } ?: "",
                    lowStock = m.lowStockThreshold.toString(), banglaName = m.banglaName.orEmpty(), aliases = aliases, loaded = true,
                )
            } else {
                _state.value = prefill(prefillOcr).copy(loaded = true)
            }
        }
    }

    /** Quick-add pre-filled from OCR text: brand words and the first strength. */
    private fun prefill(ocr: String?): MedicineFormState {
        if (ocr.isNullOrBlank()) return MedicineFormState()
        val brand = TextNormalizer.brandTokens(ocr).take(2).joinToString(" ") { it.lowercase().replaceFirstChar(Char::titlecase) }
        val s = TextNormalizer.strengths(ocr).firstOrNull()?.let { "${it.value} ${it.unit.lowercase()}" }.orEmpty()
        val u = ocr.uppercase()
        val form = when {
            "SYRUP" in u || "SUSPENSION" in u -> MedicineForm.SYRUP
            "INJECTION" in u || "VIAL" in u -> MedicineForm.INJECTION
            "CREAM" in u || "OINTMENT" in u -> MedicineForm.CREAM
            "DROP" in u -> MedicineForm.DROPS
            "CAPSULE" in u -> MedicineForm.CAPSULE
            else -> MedicineForm.TABLET
        }
        return MedicineFormState(brand = brand, strength = s, form = form)
    }

    fun update(f: (MedicineFormState) -> MedicineFormState) = _state.update { f(it).copy(error = false) }

    suspend fun save(): Long? {
        val s = _state.value
        val brand = s.brand.trim()
        val pps = Fmt.parseIntLocal(s.piecesPerStrip) ?: if (s.kind == MedicineKind.STRIP) 0 else 1
        val strip = Fmt.parseTaka(s.stripPrice) ?: 0
        val unit = Fmt.parseTaka(s.unitPrice) ?: 0
        val valid = brand.isNotEmpty() && if (s.kind == MedicineKind.STRIP) pps > 0 && strip > 0 else unit > 0
        if (!valid) {
            _state.update { it.copy(error = true) }
            return null
        }
        val o = original
        val e = (o ?: MedicineEntity(brand = brand, isUserAdded = true)).copy(
            brand = brand, generic = s.generic.trim(), strength = s.strength.trim(), form = s.form, kind = s.kind,
            company = s.company.trim(), piecesPerStrip = if (s.kind == MedicineKind.STRIP) pps else 1,
            stripsPerBox = (Fmt.parseIntLocal(s.stripsPerBox) ?: 1).coerceAtLeast(1),
            stripPrice = if (s.kind == MedicineKind.STRIP) strip else 0,
            unitPrice = if (s.kind == MedicineKind.UNIT) unit else 0,
            piecePriceOverride = Fmt.parseTaka(s.piecePriceOverride)?.takeIf { it > 0 },
            boxPriceOverride = Fmt.parseTaka(s.boxPriceOverride)?.takeIf { it > 0 },
            lowStockThreshold = Fmt.parseIntLocal(s.lowStock)?.toLong() ?: (if (s.kind == MedicineKind.STRIP) pps * 2L else 2L),
            banglaName = s.banglaName.trim().ifEmpty { null },
            priceVerified = true,
        )
        return repo.save(e, s.aliases.split(',', '|'))
    }
}

@Composable
fun MedicineFormFields(s: MedicineFormState, onChange: ((MedicineFormState) -> MedicineFormState) -> Unit) {
    val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val int = KeyboardOptions(keyboardType = KeyboardType.Number)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(s.brand, { v -> onChange { it.copy(brand = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.f_brand)) }, isError = s.error && s.brand.isBlank(), singleLine = true)
        OutlinedTextField(s.strength, { v -> onChange { it.copy(strength = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.f_strength)) }, singleLine = true)
        OutlinedTextField(s.generic, { v -> onChange { it.copy(generic = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.f_generic)) }, singleLine = true)
        OutlinedTextField(s.company, { v -> onChange { it.copy(company = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.f_company)) }, singleLine = true)
        Text(stringResource(R.string.f_form), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(MedicineForm.entries, s.form, { formLabel(it) }, { f -> onChange { it.copy(form = f) } })
        if (s.kind == MedicineKind.STRIP) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(s.piecesPerStrip, { v -> onChange { it.copy(piecesPerStrip = v) } }, Modifier.weight(1f), label = { Text(stringResource(R.string.f_pieces_per_strip)) }, keyboardOptions = int, singleLine = true)
                OutlinedTextField(s.stripsPerBox, { v -> onChange { it.copy(stripsPerBox = v) } }, Modifier.weight(1f), label = { Text(stringResource(R.string.f_strips_per_box)) }, keyboardOptions = int, singleLine = true)
            }
            OutlinedTextField(s.stripPrice, { v -> onChange { it.copy(stripPrice = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.f_strip_price)) }, keyboardOptions = num, isError = s.error, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(s.piecePriceOverride, { v -> onChange { it.copy(piecePriceOverride = v) } }, Modifier.weight(1f), label = { Text(stringResource(R.string.f_piece_override)) }, keyboardOptions = num, singleLine = true)
                OutlinedTextField(s.boxPriceOverride, { v -> onChange { it.copy(boxPriceOverride = v) } }, Modifier.weight(1f), label = { Text(stringResource(R.string.f_box_override)) }, keyboardOptions = num, singleLine = true)
            }
        } else {
            OutlinedTextField(s.unitPrice, { v -> onChange { it.copy(unitPrice = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.f_unit_price)) }, keyboardOptions = num, isError = s.error, singleLine = true)
        }
        OutlinedTextField(s.lowStock, { v -> onChange { it.copy(lowStock = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.f_low_stock)) }, keyboardOptions = int, singleLine = true)
        OutlinedTextField(s.banglaName, { v -> onChange { it.copy(banglaName = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.f_bangla_name)) }, singleLine = true)
        OutlinedTextField(s.aliases, { v -> onChange { it.copy(aliases = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.f_aliases)) }, singleLine = true)
        if (s.error) Text(stringResource(R.string.f_error), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun formLabel(f: MedicineForm): String = stringResource(
    when (f) {
        MedicineForm.TABLET -> R.string.form_tablet
        MedicineForm.CAPSULE -> R.string.form_capsule
        MedicineForm.SYRUP -> R.string.form_syrup
        MedicineForm.INJECTION -> R.string.form_injection
        MedicineForm.CREAM -> R.string.form_cream
        MedicineForm.DROPS -> R.string.form_drops
        MedicineForm.OTHER -> R.string.form_other
    },
)

@Composable
fun MedicineEditScreen(id: Long?, onBack: () -> Unit, vm: MedicineEditViewModel = hiltViewModel()) {
    LaunchedEffect(id) { vm.init(id, null) }
    val s by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Scaffold(topBar = {
        PbTopBar(
            title = { Text(stringResource(if (id == null || id == 0L) R.string.add_medicine else R.string.edit_medicine)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (s.loaded) {
                MedicineFormFields(s) { f -> vm.update(f) }
                Button(shape = PbBtnShape, onClick = { scope.launch { if (vm.save() != null) onBack() } }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

/** Unknown medicine: form pre-filled from OCR; Save adds the medicine and returns its id in one step. */
@Composable
fun QuickAddDialog(ocrText: String?, onDismiss: () -> Unit, onSaved: (Long) -> Unit, vm: MedicineEditViewModel = hiltViewModel(key = "quickadd_${ocrText.hashCode()}")) {
    LaunchedEffect(ocrText) { vm.init(null, ocrText) }
    val s by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row {
                    Text(stringResource(R.string.quick_add_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                }
                if (!ocrText.isNullOrBlank()) Text(stringResource(R.string.ocr_saw, ocrText), style = MaterialTheme.typography.bodySmall)
                if (s.loaded) {
                    MedicineFormFields(s) { f -> vm.update(f) }
                    Button(shape = PbBtnShape, onClick = { scope.launch { vm.save()?.let(onSaved) } }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.save_and_add))
                    }
                }
            }
        }
    }
}
