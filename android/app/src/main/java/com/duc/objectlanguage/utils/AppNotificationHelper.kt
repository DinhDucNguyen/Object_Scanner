package com.duc.objectlanguage.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.duc.objectlanguage.R
import com.duc.objectlanguage.ui.MainActivity

object AppNotificationHelper {
    const val DAILY_CHANNEL_ID = "daily_review_reminder"
    const val MILESTONE_CHANNEL_ID = "learning_milestones"

    private const val DAILY_NOTIFICATION_ID = 1001
    private const val MILESTONE_NOTIFICATION_ID = 1002

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun showDailyReminder(context: Context, title: String, message: String) {
        if (!canPostNotifications(context)) return
        createChannel(
            context = context,
            channelId = DAILY_CHANNEL_ID,
            name = context.getString(R.string.notification_channel_daily_name),
            description = context.getString(R.string.notification_channel_daily_desc),
        )
        notifySafely(
            context,
            DAILY_NOTIFICATION_ID,
            baseBuilder(context, DAILY_CHANNEL_ID, title, message)
                .setContentIntent(reviewPendingIntent(context))
                .build()
        )
    }

    fun showMilestone(context: Context, milestone: Int) {
        if (!canPostNotifications(context)) return
        createChannel(
            context = context,
            channelId = MILESTONE_CHANNEL_ID,
            name = context.getString(R.string.notification_channel_milestone_name),
            description = context.getString(R.string.notification_channel_milestone_desc),
        )
        val title = context.getString(R.string.notification_milestone_title, milestone)
        val message = context.getString(R.string.notification_milestone_message, milestone)
        notifySafely(
            context,
            MILESTONE_NOTIFICATION_ID + milestone,
            baseBuilder(context, MILESTONE_CHANNEL_ID, title, message)
                .setContentIntent(reviewPendingIntent(context))
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    private fun notifySafely(context: Context, notificationId: Int, notification: android.app.Notification) {
        if (!canPostNotifications(context)) return
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and notify().
        }
    }

    private fun baseBuilder(
        context: Context,
        channelId: String,
        title: String,
        message: String,
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
    }

    private fun createChannel(
        context: Context,
        channelId: String,
        name: String,
        description: String,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            this.description = description
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun reviewPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_review", true)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
