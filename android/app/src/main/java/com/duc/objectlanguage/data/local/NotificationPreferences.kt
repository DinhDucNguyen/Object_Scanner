package com.duc.objectlanguage.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class NotificationSettings(
    val dailyReminderEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val streakAlertEnabled: Boolean = true,
    val milestoneCelebrationEnabled: Boolean = true,
)

class NotificationPreferences(private val context: Context) {

    companion object {
        private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore("notification_prefs")

        private val DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        private val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        private val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        private val STREAK_ALERT_ENABLED = booleanPreferencesKey("streak_alert_enabled")
        private val MILESTONE_CELEBRATION_ENABLED = booleanPreferencesKey("milestone_celebration_enabled")
    }

    val settings: Flow<NotificationSettings> = context.notificationDataStore.data.map { prefs ->
        NotificationSettings(
            dailyReminderEnabled = prefs[DAILY_REMINDER_ENABLED] ?: false,
            reminderHour = (prefs[REMINDER_HOUR] ?: 19).coerceIn(0, 23),
            reminderMinute = (prefs[REMINDER_MINUTE] ?: 0).coerceIn(0, 59),
            streakAlertEnabled = prefs[STREAK_ALERT_ENABLED] ?: true,
            milestoneCelebrationEnabled = prefs[MILESTONE_CELEBRATION_ENABLED] ?: true,
        )
    }

    suspend fun currentSettings(): NotificationSettings = settings.first()

    suspend fun setDailyReminderEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs ->
            prefs[DAILY_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.notificationDataStore.edit { prefs ->
            prefs[REMINDER_HOUR] = hour.coerceIn(0, 23)
            prefs[REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setStreakAlertEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs ->
            prefs[STREAK_ALERT_ENABLED] = enabled
        }
    }

    suspend fun setMilestoneCelebrationEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { prefs ->
            prefs[MILESTONE_CELEBRATION_ENABLED] = enabled
        }
    }
}
