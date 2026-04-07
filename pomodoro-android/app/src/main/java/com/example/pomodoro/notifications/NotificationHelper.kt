package com.example.pomodoro.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.pomodoro.R

object NotificationHelper {
    const val CHANNEL_TIMER = "pomodoro_timer"
    const val CHANNEL_EVENTS = "pomodoro_events"
    const val TIMER_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val timer = NotificationChannel(
                CHANNEL_TIMER,
                "Pomodoro Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Постоянное уведомление таймера"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }

            val events = NotificationChannel(
                CHANNEL_EVENTS,
                "Pomodoro Events",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Переходы между циклами"
            }

            manager.createNotificationChannel(timer)
            manager.createNotificationChannel(events)
        }
    }

    fun builder(context: Context, channelId: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
    }
}
