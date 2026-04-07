package com.example.pomodoro.domain

import com.example.pomodoro.model.DayStats
import com.example.pomodoro.model.EngineState
import com.example.pomodoro.model.TickResult

class PomodoroEngine(private var state: EngineState = EngineState()) {

    fun state(): EngineState = state

    fun restore(newState: EngineState) {
        state = newState
    }

    fun applyCustomDurations(workMin: Int, breakMin: Int, longBreakMin: Int) {
        val work = maxOf(1, workMin) * 60
        val brk = maxOf(1, breakMin) * 60
        val longBrk = maxOf(1, longBreakMin) * 60

        state = state.copy(
            workTime = work,
            breakTime = brk,
            longBreakTime = longBrk
        )

        if (!state.running && state.controlMode == "automatic") {
            val d = defaultDurationForMode(state.mode)
            state = state.copy(periodDuration = d, timeLeft = d)
        }
    }

    fun setMode(mode: String) {
        state = if (state.controlMode == "automatic") {
            val duration = defaultDurationForMode(mode)
            state.copy(
                running = false,
                mode = mode,
                periodDuration = duration,
                timeLeft = duration
            )
        } else {
            state.copy(
                running = false,
                mode = mode,
                manualElapsed = 0
            )
        }
    }

    fun setControlMode(controlMode: String) {
        state = if (controlMode == "automatic") {
            val duration = defaultDurationForMode(state.mode)
            state.copy(
                running = false,
                controlMode = controlMode,
                periodDuration = duration,
                timeLeft = duration
            )
        } else {
            state.copy(
                running = false,
                controlMode = controlMode,
                manualElapsed = 0
            )
        }
    }

    fun start() {
        state = state.copy(running = true, lastTickEpochMillis = System.currentTimeMillis())
    }

    fun pause() {
        state = state.copy(running = false)
    }

    fun reset() {
        state = if (state.controlMode == "automatic") {
            val duration = defaultDurationForMode(state.mode)
            state.copy(running = false, periodDuration = duration, timeLeft = duration)
        } else {
            state.copy(running = false, manualElapsed = 0)
        }
    }

    fun processOneSecond(todayStats: DayStats): TickResult {
        var stats = todayStats
        var eventText: String? = null

        stats = if (state.mode == "work") {
            stats.copy(workTime = stats.workTime + 1)
        } else {
            stats.copy(breakTime = stats.breakTime + 1)
        }

        if (state.controlMode == "automatic") {
            val newLeft = state.timeLeft - 1
            state = state.copy(timeLeft = newLeft)
            if (newLeft <= 0) {
                val result = finishPeriodAutomatic(stats)
                stats = result.first
                eventText = result.second
            }
        } else {
            state = state.copy(manualElapsed = state.manualElapsed + 1)
        }

        state = state.copy(lastTickEpochMillis = System.currentTimeMillis())
        return TickResult(state, stats, eventText)
    }

    private fun finishPeriodAutomatic(stats: DayStats): Pair<DayStats, String?> {
        var updatedStats = stats
        return if (state.mode == "work") {
            updatedStats = updatedStats.copy(
                cycles = updatedStats.cycles + 1,
                breaks = updatedStats.breaks + 1
            )

            if (updatedStats.cycles % 4 == 0) {
                state = state.copy(
                    mode = "long_break",
                    periodDuration = state.longBreakTime,
                    timeLeft = state.longBreakTime
                )
                updatedStats to "Время длинного перерыва"
            } else {
                state = state.copy(
                    mode = "break",
                    periodDuration = state.breakTime,
                    timeLeft = state.breakTime
                )
                updatedStats to "Время перерыва"
            }
        } else {
            state = state.copy(
                mode = "work",
                periodDuration = state.workTime,
                timeLeft = state.workTime
            )
            updatedStats to "Пора снова работать"
        }
    }

    private fun defaultDurationForMode(mode: String): Int {
        return when (mode) {
            "work" -> state.workTime
            "break" -> state.breakTime
            else -> state.longBreakTime
        }
    }

    fun formatMs(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(minutes, secs)
    }

    fun formatHms(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, secs)
    }
}
