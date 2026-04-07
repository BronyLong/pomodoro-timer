package com.example.pomodoro

import android.app.Application
import com.example.pomodoro.notifications.NotificationHelper

class PomodoroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
