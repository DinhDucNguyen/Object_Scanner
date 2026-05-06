package com.duc.objectlanguage.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LOCALE  = "app_locale"

    /**
     * Trả về ngôn ngữ đang được lưu. Nếu user chưa chọn (lần đầu mở app),
     * trả về ngôn ngữ hệ thống ("vi" hoặc "en").
     */
    fun getSavedLocale(context: Context): String {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOCALE, "") ?: ""
        if (saved.isNotEmpty()) return saved
        // Dùng ngôn ngữ hệ thống, chỉ hỗ trợ vi / en
        return if (Locale.getDefault().language == "vi") "vi" else "en"
    }

    fun setLocale(context: Context, lang: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOCALE, lang).apply()
    }

    /**
     * Tạo Context với locale đã lưu.
     * Gọi từ attachBaseContext() của Application và Activity.
     */
    fun applyLocale(context: Context): Context {
        val lang   = getSavedLocale(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
