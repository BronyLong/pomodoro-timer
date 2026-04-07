package com.example.pomodoro.data

import android.content.Context
import com.example.pomodoro.model.DayStats
import com.example.pomodoro.model.EngineState
import com.example.pomodoro.model.PersistedState
import com.example.pomodoro.model.TaskItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class PomodoroRepository(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val stateFile: File = File(context.filesDir, "pomodoro_state.json")
    private val mutex = Mutex()

    private fun todayKey(): String = LocalDate.now().toString()

    private fun nowIso(): String = LocalDateTime.now().toString()

    private fun normalizeState(state: PersistedState): PersistedState {
        val today = todayKey()
        val mergedHistory = state.statsHistory.toMutableMap()

        if (mergedHistory[today] == null) {
            val baseToday = if (
                state.todayStats != DayStats() &&
                state.todayStats.updatedAt.isNotBlank() &&
                mergedHistory.isEmpty()
            ) {
                state.todayStats.copy(updatedAt = nowIso())
            } else {
                DayStats(updatedAt = nowIso())
            }
            mergedHistory[today] = baseToday
        }

        val normalizedToday = mergedHistory[today] ?: DayStats(updatedAt = nowIso())
        return state.copy(
            todayStats = normalizedToday,
            statsHistory = mergedHistory.toMap()
        )
    }

    suspend fun loadState(): PersistedState = withContext(Dispatchers.IO) {
        mutex.withLock {
            val raw = if (!stateFile.exists()) {
                PersistedState()
            } else {
                runCatching {
                    json.decodeFromString<PersistedState>(stateFile.readText(UTF_8))
                }.getOrElse {
                    PersistedState()
                }
            }

            val normalized = normalizeState(raw)
            if (normalized != raw) {
                stateFile.writeText(json.encodeToString(PersistedState.serializer(), normalized), UTF_8)
            }
            normalized
        }
    }

    suspend fun saveState(state: PersistedState) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val today = todayKey()
            val mergedHistory = state.statsHistory.toMutableMap()
            mergedHistory[today] = state.todayStats.copy(updatedAt = nowIso())
            val normalized = state.copy(
                todayStats = mergedHistory[today] ?: DayStats(updatedAt = nowIso()),
                statsHistory = mergedHistory.toMap()
            )
            stateFile.writeText(json.encodeToString(PersistedState.serializer(), normalized), UTF_8)
        }
    }

    suspend fun saveEngineAndStats(engine: EngineState, stats: DayStats) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = if (stateFile.exists()) {
                runCatching {
                    json.decodeFromString<PersistedState>(stateFile.readText(UTF_8))
                }.getOrElse { PersistedState() }
            } else {
                PersistedState()
            }

            val normalized = normalizeState(current)
            val today = todayKey()
            val mergedHistory = normalized.statsHistory.toMutableMap()
            val updatedStats = stats.copy(updatedAt = nowIso())
            mergedHistory[today] = updatedStats

            val updated = normalized.copy(
                engine = engine,
                todayStats = updatedStats,
                statsHistory = mergedHistory.toMap()
            )

            stateFile.writeText(json.encodeToString(PersistedState.serializer(), updated), UTF_8)
        }
    }

    suspend fun addTask(text: String) = withContext(Dispatchers.IO) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return@withContext

        mutex.withLock {
            val current = loadOrDefaultUnsafe()
            val task = TaskItem(
                id = UUID.randomUUID().toString(),
                text = normalizedText,
                done = false,
                created = nowIso()
            )
            val updated = current.copy(tasks = current.tasks + task)
            writeUnsafe(updated)
        }
    }

    suspend fun toggleTask(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = loadOrDefaultUnsafe()
            val oldTask = current.tasks.firstOrNull { it.id == id } ?: return@withLock
            val updatedTasks = current.tasks.map {
                if (it.id == id) it.copy(done = !it.done) else it
            }

            val updatedStats = if (!oldTask.done) {
                current.todayStats.copy(
                    tasksDone = current.todayStats.tasksDone + 1,
                    updatedAt = nowIso()
                )
            } else {
                current.todayStats.copy(
                    tasksDone = maxOf(0, current.todayStats.tasksDone - 1),
                    updatedAt = nowIso()
                )
            }

            val mergedHistory = current.statsHistory.toMutableMap()
            mergedHistory[todayKey()] = updatedStats

            val updated = current.copy(
                tasks = updatedTasks,
                todayStats = updatedStats,
                statsHistory = mergedHistory.toMap()
            )
            writeUnsafe(updated)
        }
    }

    suspend fun deleteTask(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = loadOrDefaultUnsafe()
            val updated = current.copy(
                tasks = current.tasks.filterNot { it.id == id }
            )
            writeUnsafe(updated)
        }
    }

    private fun loadOrDefaultUnsafe(): PersistedState {
        val raw = if (stateFile.exists()) {
            runCatching {
                json.decodeFromString<PersistedState>(stateFile.readText(UTF_8))
            }.getOrElse { PersistedState() }
        } else {
            PersistedState()
        }
        return normalizeState(raw)
    }

    private fun writeUnsafe(state: PersistedState) {
        stateFile.writeText(json.encodeToString(PersistedState.serializer(), state), UTF_8)
    }
}
