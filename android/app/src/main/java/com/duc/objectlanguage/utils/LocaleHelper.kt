package com.duc.objectlanguage.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale


object LocaleHelper {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LOCALE = "app_locale"

    fun getSavedLocale(context: Context): String {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOCALE, "") ?: ""
        if (saved.isNotEmpty()) return normalizeLanguage(saved)

        return "vi"
    }

    fun setLocale(context: Context, lang: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCALE, normalizeLanguage(lang))
            .apply()
    }

    fun applyLocale(context: Context): Context {
        val lang = getSavedLocale(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    private fun normalizeLanguage(lang: String): String {
        return if (lang.lowercase(Locale.ROOT).startsWith("vi")) "vi" else "en"
    }
}
