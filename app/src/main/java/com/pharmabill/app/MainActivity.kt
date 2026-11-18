package com.pharmabill.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.activity.viewModels
import com.pharmabill.app.data.prefs.AppSettings
import com.pharmabill.app.data.prefs.LanguageMirror
import com.pharmabill.app.data.prefs.SettingsStore
import com.pharmabill.app.ui.components.LocalBangla
import com.pharmabill.app.ui.components.Loading
import com.pharmabill.app.ui.nav.AppNav
import com.pharmabill.app.ui.theme.PharmaTheme
import com.pharmabill.app.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(store: SettingsStore) : ViewModel() {
    val settings: StateFlow<AppSettings?> = store.settings.map<AppSettings, AppSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, LanguageMirror.get(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val open = intent?.getStringExtra(EXTRA_OPEN)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            PharmaTheme {
                val s = settings
                if (s == null) Loading() else {
                    CompositionLocalProvider(LocalBangla provides (LanguageMirror.get(this) == AppSettings.LANG_BN)) {
                        AppNav(setupDone = s.setupDone, openOnStart = open, onLanguageChanged = { recreate() })
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}
