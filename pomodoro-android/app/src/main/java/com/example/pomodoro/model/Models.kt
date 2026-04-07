package com.example.pomodoro.model

import kotlinx.serialization.Serializable

@Serializable
data class TaskItem(
    val id: String,
    val text: String,
    val done: Boolean,
    val created: String
)

@Serializable
data class DayStats(
    val workTime: Int = 0,
    val breakTime: Int = 0,
    val cycles: Int = 0,
    val breaks: Int = 0,
    val tasksDone: Int = 0,
    val updatedAt: String = ""
)

@Serializable
data class EngineState(
    val running: Boolean = false,
    val mode: String = "work",
    val controlMode: String = "automatic",
    val timeLeft: Int = 25 * 60,
    val periodDuration: Int = 25 * 60,
    val manualElapsed: Int = 0,
    val workTime: Int = 25 * 60,
    val breakTime: Int = 5 * 60,
    val longBreakTime: Int = 15 * 60,
    val lastTickEpochMillis: Long = 0L
)

@Serializable
data class PersistedState(
    val engine: EngineState = EngineState(),
    val todayStats: DayStats = DayStats(),
    val statsHistory: Map<String, DayStats> = emptyMap(),
    val tasks: List<TaskItem> = emptyList()
)

data class TickResult(
    val engine: EngineState,
    val todayStats: DayStats,
    val cycleEventText: String? = null
)

data class UiState(
    val engine: EngineState = EngineState(),
    val todayStats: DayStats = DayStats(),
    val statsHistory: Map<String, DayStats> = emptyMap(),
    val tasks: List<TaskItem> = emptyList()
)
