package com.duc.objectlanguage.ui.common

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import com.duc.objectlanguage.utils.LocaleHelper

fun AndroidViewModel.localizedString(@StringRes resId: Int, vararg args: Any): String {
    val context = LocaleHelper.applyLocale(getApplication<Application>())
    return if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args)
}
