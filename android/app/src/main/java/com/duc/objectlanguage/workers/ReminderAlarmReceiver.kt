package com.duc.objectlanguage.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.local.NotificationPreferences
import com.duc.objectlanguage.data.local.StreakDataStore
import com.duc.objectlanguage.data.local.TokenManager
import com.duc.objectlanguage.data.repository.AppRepository
import com.duc.objectlanguage.utils.AppNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val settings = NotificationPreferences(appContext).currentSettings()
            if (!settings.dailyReminderEnabled) return@launch
            if (!AppNotificationHelper.canPostNotifications(appContext)) return@launch

            val tokenManager = TokenManager(appContext)
            if (!tokenManager.isLoggedIn) return@launch

            val streakStore = StreakDataStore(appContext)
            val hasReviewedToday = streakStore.hasReviewedToday()
            val stats = runCatching {
                AppRepository(tokenManager).getStats().getOrNull()
            }.getOrNull()

            val dueToday = stats?.dueToday ?: 0

            // Nếu đã ôn rồi thì không nhắc streak nữa
            val showStreak = settings.streakAlertEnabled && !hasReviewedToday
            val currentStreak = streakStore.currentStreak.first()
            val (title, message) = buildNotificationContent(
                appContext, currentStreak, if (dueToday > 0) dueToday else null, showStreak
            )
            AppNotificationHelper.showDailyReminder(appContext, title, message)

            // Lên lịch lại cho ngày hôm sau
            DailyReminderWorker.scheduleDailyReminder(
                appContext, settings.reminderHour, settings.reminderMinute
            )
        }
    }

    private fun buildNotificationContent(
        context: Context,
        streak: Int,
        dueToday: Int?,
        includeStreak: Boolean,
    ): Pair<String, String> {
        val title = if (dueToday != null && dueToday > 0) {
            context.resources.getQuantityString(R.plurals.notification_due_title, dueToday, dueToday)
        } else {
            context.getString(R.string.notification_generic_title)
        }
        val message = when {
            dueToday != null && dueToday > 0 && includeStreak && streak > 0 ->
                context.getString(R.string.notification_due_streak_message, dueToday, streak)
            dueToday != null && dueToday > 0 ->
                context.getString(R.string.notification_due_message, dueToday)
            includeStreak && streak > 0 ->
                context.getString(R.string.notification_generic_streak_message, streak)
            else ->
                context.getString(R.string.notification_generic_message)
        }
        return title to message
    }
}
