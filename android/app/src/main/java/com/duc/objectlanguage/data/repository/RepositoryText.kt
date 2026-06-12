package com.duc.objectlanguage.data.repository

import android.content.Context
import androidx.annotation.StringRes
import com.duc.objectlanguage.utils.LocaleHelper

internal object RepositoryText {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(@StringRes resId: Int, vararg args: Any): String {
        val localizedContext = LocaleHelper.applyLocale(appContext)
        return if (args.isEmpty()) {
            localizedContext.getString(resId)
        } else {
            localizedContext.getString(resId, *args)
        }
    }

    fun language(): String = LocaleHelper.getSavedLocale(appContext)
}
