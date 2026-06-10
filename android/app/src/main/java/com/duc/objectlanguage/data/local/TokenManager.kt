package com.duc.objectlanguage.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "auth_prefs_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    var username: String?
        get() = prefs.getString("username", null)
        set(value) = prefs.edit().putString("username", value).apply()

    var userId: Int
        get() = prefs.getInt("nguoi_dung_id", 0)
        set(value) = prefs.edit().putInt("nguoi_dung_id", value).apply()

    var role: String?
        get() = prefs.getString("role", null)
        set(value) = prefs.edit().putString("role", value).apply()

    val isAdmin: Boolean get() = role?.lowercase() in setOf("admin", "quan_tri", "quan_tri_vien")

    var isGoogleAccount: Boolean
        get() = prefs.getBoolean("is_google_account", false)
        set(value) = prefs.edit().putBoolean("is_google_account", value).apply()

    val isLoggedIn: Boolean get() = !accessToken.isNullOrEmpty()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
