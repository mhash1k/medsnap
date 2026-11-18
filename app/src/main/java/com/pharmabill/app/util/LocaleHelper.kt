package com.pharmabill.app.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    fun wrap(base: Context, lang: String): Context {
        val locale = Locale.forLanguageTag(if (lang == "bn") "bn-BD" else "en")
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
