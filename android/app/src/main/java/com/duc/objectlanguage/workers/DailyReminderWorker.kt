package com.duc.objectlanguage.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.duc.objectlanguage.data.local.NotificationPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object DailyReminderWorker {

    private const val REQUEST_CODE = 1234

    fun scheduleDailyReminder(context: Context, hourOfDay: Int = 19, minute: Int = 0) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            scheduleInexact(context, alarmManager, hourOfDay, minute)
            return
        }

        val triggerAt = nextTriggerTime(hourOfDay, minute)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, buildPendingIntent(context))
    }

    fun cancelDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context))
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

    private fun scheduleInexact(context: Context, alarmManager: AlarmManager, hourOfDay: Int, minute: Int) {
        val triggerAt = nextTriggerTime(hourOfDay, minute)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, buildPendingIntent(context))
    }

    private fun nextTriggerTime(hourOfDay: Int, minute: Int): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
