package com.duc.objectlanguage.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "daily_reminder_work"

        fun scheduleDailyReminder(context: Context, hourOfDay: Int = 19, minute: Int = 0) {
            enqueueReminder(context, hourOfDay, minute, ExistingWorkPolicy.REPLACE)
        }

        private fun scheduleNextDailyReminder(context: Context, hourOfDay: Int, minute: Int) {
            enqueueReminder(context, hourOfDay, minute, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }

        private fun enqueueReminder(
            context: Context,
            hourOfDay: Int,
            minute: Int,
            policy: ExistingWorkPolicy,
        ) {
            val currentTime = System.currentTimeMillis()
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                if (timeInMillis <= currentTime) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val workRequest = OneTimeWorkRequestBuilder<DailyReminderWorker>()
                .setInitialDelay(calendar.timeInMillis - currentTime, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                policy,
                workRequest,
            )
        }

        fun cancelDailyReminder(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }

        fun syncWithPreferences(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val settings = NotificationPreferences(appContext).currentSettings()
                if (settings.dailyReminderEnabled) {
                    scheduleDailyReminder(appContext, settings.reminderHour, settings.reminderMinute)
                } else {
                    cancelDailyReminder(appContext)
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        val settings = NotificationPreferences(applicationContext).currentSettings()
        if (!settings.dailyReminderEnabled) return Result.success()
        scheduleNextDailyReminder(applicationContext, settings.reminderHour, settings.reminderMinute)

        if (!AppNotificationHelper.canPostNotifications(applicationContext)) return Result.success()

        val tokenManager = TokenManager(applicationContext)
        if (!tokenManager.isLoggedIn) return Result.success()

        val streakStore = StreakDataStore(applicationContext)
        if (streakStore.hasReviewedToday()) return Result.success()

        val stats = AppRepository(tokenManager).getStats().getOrNull()
        if (stats != null && stats.dueToday <= 0) return Result.success()

        val currentStreak = streakStore.currentStreak.first()
        val (title, message) = getNotificationContent(
            streak = currentStreak,
            dueToday = stats?.dueToday,
            includeStreak = settings.streakAlertEnabled,
        )
        AppNotificationHelper.showDailyReminder(applicationContext, title, message)

        return Result.success()
    }

    private fun getNotificationContent(
        streak: Int,
        dueToday: Int?,
        includeStreak: Boolean,
    ): Pair<String, String> {
        val title = if (dueToday != null && dueToday > 0) {
            applicationContext.resources.getQuantityString(
                R.plurals.notification_due_title,
                dueToday,
                dueToday,
            )
        } else {
            applicationContext.getString(R.string.notification_generic_title)
        }

        val message = when {
            dueToday != null && dueToday > 0 && includeStreak && streak > 0 ->
                applicationContext.getString(R.string.notification_due_streak_message, dueToday, streak)
            dueToday != null && dueToday > 0 ->
                applicationContext.getString(R.string.notification_due_message, dueToday)
            includeStreak && streak > 0 ->
                applicationContext.getString(R.string.notification_generic_streak_message, streak)
            else ->
                applicationContext.getString(R.string.notification_generic_message)
        }

        return title to message
    }
}
