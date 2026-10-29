package dev.itszain.termuxfilemanager

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppLocale {
    @JvmStatic fun wrap(context: Context): Context {
        val language = AiSecureStore.settings(context).getString("language", "system")
        if(language == null || language == "system") return context
        val locale = Locale.forLanguageTag(language)
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) })
    }
}
