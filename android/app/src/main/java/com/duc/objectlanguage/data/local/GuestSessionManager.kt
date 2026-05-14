package com.duc.objectlanguage.data.local

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GuestSessionManager(context: Context) {

    private val prefs = context.getSharedPreferences("guest_prefs", Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val today get() = dateFormat.format(Date())

    private fun resetIfNewDay() {
        val savedDate = prefs.getString("scan_date", "") ?: ""
        if (savedDate != today) {
            prefs.edit().putInt("scan_count", 0).putString("scan_date", today).apply()
        }
    }

    fun canScan(): Boolean {
        resetIfNewDay()
        return prefs.getInt("scan_count", 0) < MAX_GUEST_SCANS
    }

    fun incrementScan() {
        resetIfNewDay()
        val current = prefs.getInt("scan_count", 0)
        prefs.edit().putInt("scan_count", current + 1).apply()
    }

    fun getRemainingScans(): Int {
        resetIfNewDay()
        return maxOf(0, MAX_GUEST_SCANS - prefs.getInt("scan_count", 0))
    }

    companion object {
        const val MAX_GUEST_SCANS = 5
    }
}
