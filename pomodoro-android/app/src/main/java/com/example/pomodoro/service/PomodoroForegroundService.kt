package com.example.pomodoro.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.pomodoro.MainActivity
import com.example.pomodoro.data.PomodoroRepository
import com.example.pomodoro.domain.PomodoroEngine
import com.example.pomodoro.model.PersistedState
import com.example.pomodoro.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PomodoroForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.example.pomodoro.START"
        const val ACTION_PAUSE = "com.example.pomodoro.PAUSE"
        const val ACTION_RESET = "com.example.pomodoro.RESET"
        const val ACTION_WORK = "com.example.pomodoro.WORK"
        const val ACTION_BREAK = "com.example.pomodoro.BREAK"
        const val ACTION_LONG_BREAK = "com.example.pomodoro.LONG_BREAK"
        const val ACTION_TOGGLE_MODE = "com.example.pomodoro.TOGGLE_MODE"

        fun send(context: Context, action: String) {
            val intent = Intent(context, PomodoroForegroundService::class.java).apply {
                this.action = action
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: PomodoroRepository
    private lateinit var engine: PomodoroEngine
    private var persistedState = PersistedState()
    private var tickerJob: Job? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        repository = PomodoroRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            ensureInitialized()
            ensureForeground()
            handleAction(intent?.action)
            persistEngineAndStats()
            updateNotification()
            ensureTicker()
        }
        return START_STICKY
    }

    private suspend fun ensureInitialized() {
        if (!::engine.isInitialized) {
            persistedState = repository.loadState()
            engine = PomodoroEngine(persistedState.engine)
        } else {
            persistedState = repository.loadState()
            engine.restore(persistedState.engine)
        }
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        val notification = buildTimerNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.TIMER_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.TIMER_ID, notification)
        }
        foregroundStarted = true
    }

    private fun ensureTicker() {
        if (tickerJob != null) return
        tickerJob = scope.launch {
            while (true) {
                delay(1000)
                if (!::engine.isInitialized) continue
                val currentState = engine.state()
                if (!currentState.running) continue

                val latestState = repository.loadState()
                val tick = engine.processOneSecond(latestState.todayStats)
                repository.saveEngineAndStats(tick.engine, tick.todayStats)
                persistedState = repository.loadState()
                updateNotification()
                tick.cycleEventText?.let { showEventNotification(it) }
            }
        }
    }

    private fun handleAction(action: String?) {
        when (action) {
            ACTION_START -> engine.start()
            ACTION_PAUSE -> engine.pause()
            ACTION_RESET -> engine.reset()
            ACTION_WORK -> engine.setMode("work")
            ACTION_BREAK -> engine.setMode("break")
            ACTION_LONG_BREAK -> engine.setMode("long_break")
            ACTION_TOGGLE_MODE -> {
                val next = if (engine.state().controlMode == "automatic") "manual" else "automatic"
                engine.setControlMode(next)
            }
            null -> Unit
        }
    }

    private suspend fun persistEngineAndStats() {
        persistedState = repository.loadState().copy(engine = engine.state())
        repository.saveEngineAndStats(persistedState.engine, persistedState.todayStats)
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NotificationHelper.TIMER_ID, buildTimerNotification())
    }

    private fun buildTimerNotification(): Notification {
        val state = if (::engine.isInitialized) engine.state() else persistedState.engine
        val formatter = PomodoroEngine(state)
        val timerText = if (state.controlMode == "automatic") {
            formatter.formatMs(state.timeLeft.coerceAtLeast(0))
        } else {
            formatter.formatHms(state.manualElapsed)
        }

        val modeText = when (state.mode) {
            "work" -> "Работа"
            "break" -> "Перерыв"
            else -> "Длинный перерыв"
        }

        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationHelper.builder(this, NotificationHelper.CHANNEL_TIMER)
            .setContentTitle("Pomodoro • $modeText")
            .setContentText(timerText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$modeText\n$timerText"))
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setLocalOnly(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                0,
                if (state.running) "Пауза" else "Старт",
                actionPendingIntent(if (state.running) ACTION_PAUSE else ACTION_START, 2)
            )
            .addAction(0, "Сброс", actionPendingIntent(ACTION_RESET, 3))
            .build()
    }

    private fun showEventNotification(text: String) {
        val notification = NotificationHelper.builder(this, NotificationHelper.CHANNEL_EVENTS)
            .setContentTitle("Pomodoro")
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PomodoroForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
