package com.duc.objectlanguage.utils

import android.content.Context
import com.duc.objectlanguage.R

object PasswordValidator {
    private const val MIN_PASSWORD_LENGTH = 8

    fun validate(context: Context, password: String): String? {
        return when {
            password.length < MIN_PASSWORD_LENGTH -> context.getString(R.string.profile_password_min_length)
            password.none { it.isLowerCase() } -> context.getString(R.string.profile_password_lowercase_required)
            password.none { it.isUpperCase() } -> context.getString(R.string.profile_password_uppercase_required)
            password.none { it.isDigit() } -> context.getString(R.string.profile_password_digit_required)
            else -> null
        }
    }
}
