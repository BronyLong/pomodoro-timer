package com.example.pomodoro.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.pomodoro.data.PomodoroRepository
import com.example.pomodoro.model.UiState
import com.example.pomodoro.service.PomodoroForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PomodoroViewModel(
    private val app: Application,
    private val repository: PomodoroRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val state = repository.loadState()
            _uiState.value = UiState(
                engine = state.engine,
                todayStats = state.todayStats,
                statsHistory = state.statsHistory,
                tasks = state.tasks
            )
        }
    }

    fun startService(context: Context) = PomodoroForegroundService.send(context, PomodoroForegroundService.ACTION_START)
    fun pauseService(context: Context) = PomodoroForegroundService.send(context, PomodoroForegroundService.ACTION_PAUSE)
    fun resetService(context: Context) = PomodoroForegroundService.send(context, PomodoroForegroundService.ACTION_RESET)
    fun setWork(context: Context) = PomodoroForegroundService.send(context, PomodoroForegroundService.ACTION_WORK)
    fun setBreak(context: Context) = PomodoroForegroundService.send(context, PomodoroForegroundService.ACTION_BREAK)
    fun setLongBreak(context: Context) = PomodoroForegroundService.send(context, PomodoroForegroundService.ACTION_LONG_BREAK)
    fun toggleControlMode(context: Context) = PomodoroForegroundService.send(context, PomodoroForegroundService.ACTION_TOGGLE_MODE)

    fun addTask(text: String) {
        viewModelScope.launch {
            repository.addTask(text)
            refresh()
        }
    }

    fun toggleTask(id: String) {
        viewModelScope.launch {
            repository.toggleTask(id)
            refresh()
        }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch {
            repository.deleteTask(id)
            refresh()
        }
    }

    companion object {
        fun provideFactory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PomodoroViewModel(app, PomodoroRepository(app.applicationContext)) as T
            }
        }
    }
}
