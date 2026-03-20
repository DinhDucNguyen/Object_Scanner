package com.duc.objectlanguage.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.duc.objectlanguage.ui.MainActivity
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.local.StreakDataStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for sending daily review reminders
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "daily_review_reminder"
        private const val CHANNEL_NAME = "Daily Review Reminders"
        private const val WORK_NAME = "daily_reminder_work"
        
        /**
         * Schedule daily reminder at specific time
         */
        fun scheduleDailyReminder(context: Context, hourOfDay: Int = 19, minute: Int = 0) {
            val currentTime = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                
                // If time has passed today, schedule for tomorrow
                if (timeInMillis <= currentTime) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }
            
            val delay = calendar.timeInMillis - currentTime
            
            val workRequest = PeriodicWorkRequestBuilder<DailyReminderWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
        
        /**
         * Cancel daily reminder
         */
        fun cancelDailyReminder(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
    
    override suspend fun doWork(): Result {
        val streakDataStore = StreakDataStore(applicationContext)
        
        // Check if user has already reviewed today
        val hasReviewedToday = streakDataStore.hasReviewedToday()
        
        if (!hasReviewedToday) {
            // Get current streak
            val currentStreak = streakDataStore.currentStreak.first()
            
            // Send notification
            sendNotification(currentStreak)
        }
        
        return Result.success()
    }
    
    /**
     * Send notification reminder
     */
    private fun sendNotification(currentStreak: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) 
            as NotificationManager
        
        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to maintain your daily learning streak"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Intent to open app when notification clicked
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_review", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Build notification with dynamic content
        val (title, message) = getNotificationContent(currentStreak)
        
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Generate notification content based on streak
     */
    private fun getNotificationContent(streak: Int): Pair<String, String> {
        return when {
            streak == 0 -> Pair(
                "Time to start learning! 🌟",
                "Begin your daily review streak today. Your vocabulary is waiting!"
            )
            streak == 1 -> Pair(
                "Day 2 awaits! 🔥",
                "Don't break your 1-day streak. Quick review to keep the momentum!"
            )
            streak in 2..6 -> Pair(
                "Keep your ${streak}-day streak alive! 💪",
                "You're building a great habit. Just a few minutes of review today!"
            )
            streak in 7..13 -> Pair(
                "Week ${streak/7} going strong! ⭐",
                "${streak} days of consistent learning. Don't break the chain!"
            )
            streak in 14..29 -> Pair(
                "Amazing ${streak}-day streak! 🚀",
                "You're on fire! Quick review to maintain your impressive progress."
            )
            streak >= 30 -> Pair(
                "${streak} days of excellence! 🏆",
                "Your dedication is legendary. Keep the streak alive!"
            )
            else -> Pair(
                "Daily review reminder 📚",
                "Time for your daily vocabulary practice!"
            )
        }
    }
}
