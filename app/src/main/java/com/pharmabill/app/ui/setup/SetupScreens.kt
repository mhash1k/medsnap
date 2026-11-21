package com.pharmabill.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pharmabill.app.R
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.unit.sp
import com.pharmabill.app.ui.components.BrandMark
import com.pharmabill.app.ui.components.PageHeader
import com.pharmabill.app.ui.components.PagePadding
import com.pharmabill.app.ui.theme.Pb
import com.pharmabill.app.ui.components.PbBtnShape
import com.pharmabill.app.data.prefs.AppSettings
import com.pharmabill.app.data.prefs.SettingsStore
import com.pharmabill.app.data.seed.SeedImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(private val store: SettingsStore, seed: SeedImporter) : ViewModel() {
    val seedDone = seed.done
    suspend fun setLanguage(l: String) = store.setLanguage(l)
    suspend fun finish(name: String, address: String, phone: String) {
        store.saveShop(name, address, phone)
        store.setSetupDone()
    }
}

/** Screen 1: Bangla / English. */
@Composable
fun LanguageScreen(onChosen: () -> Unit, vm: SetupViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(64.dp)
        Text("PharmaBill", style = MaterialTheme.typography.headlineMedium)
        Text("ভাষা বেছে নিন / Choose language", fontSize = 14.sp, color = Pb.muted)
        Button(shape = PbBtnShape, onClick = { scope.launch { vm.setLanguage(AppSettings.LANG_BN); onChosen() } }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("বাংলা", style = MaterialTheme.typography.titleLarge)
        }
        FilledTonalButton(shape = PbBtnShape, onClick = { scope.launch { vm.setLanguage(AppSettings.LANG_EN); onChosen() } }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("English", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Screen 2: shop name, address and phone, printed on receipts. */
@Composable
fun ShopSetupScreen(onDone: () -> Unit, vm: SetupViewModel = hiltViewModel()) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val seedDone by vm.seedDone.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(stringResource(R.string.shop_setup), stringResource(R.string.shop_setup_hint))
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.shop_name)) }, singleLine = true)
        OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.shop_address)) }, singleLine = true)
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.shop_phone)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        Text(stringResource(if (seedDone) R.string.seed_ready else R.string.seed_importing), style = MaterialTheme.typography.bodySmall)
        Button(shape = PbBtnShape, onClick = { scope.launch { vm.finish(name, address, phone); onDone() } }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.start_billing))
        }
    }
}
