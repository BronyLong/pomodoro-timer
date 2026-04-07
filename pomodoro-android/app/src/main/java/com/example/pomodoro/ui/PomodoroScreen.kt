package com.example.pomodoro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pomodoro.domain.PomodoroEngine
import com.example.pomodoro.model.DayStats
import com.example.pomodoro.model.TaskItem
import com.example.pomodoro.viewmodel.PomodoroViewModel
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun PomodoroScreen(vm: PomodoroViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    var taskText by rememberSaveable { mutableStateOf("") }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var visibleMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            vm.refresh()
        }
    }

    val engine = remember(state.engine) { PomodoroEngine(state.engine) }
    val selectedDate = remember(selectedDateText) { LocalDate.parse(selectedDateText) }
    val visibleMonth = remember(visibleMonthText) { YearMonth.parse(visibleMonthText) }

    val timerText = if (state.engine.controlMode == "automatic") {
        engine.formatMs(state.engine.timeLeft.coerceAtLeast(0))
    } else {
        engine.formatHms(state.engine.manualElapsed)
    }

    val progress = if (state.engine.controlMode == "automatic") {
        val duration = state.engine.periodDuration
        if (duration <= 0) 0f
        else ((duration - state.engine.timeLeft).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        if (state.engine.running) (state.engine.manualElapsed % 60) / 60f else 0f
    }

    val selectedStats = state.statsHistory[selectedDate.toString()] ?: DayStats()

    fun submitTask() {
        vm.addTask(taskText)
        taskText = ""
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = when (state.engine.mode) {
                    "work" -> "РАБОТА"
                    "break" -> "ПЕРЕРЫВ"
                    else -> "ДЛИННЫЙ ПЕРЕРЫВ"
                },
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Text(
                text = timerText,
                style = MaterialTheme.typography.displayMedium
            )
        }

        item {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startService(context) }) { Text("Старт") }
                Button(onClick = { vm.pauseService(context) }) { Text("Пауза") }
                Button(onClick = { vm.resetService(context) }) { Text("Сброс") }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.setWork(context) }) { Text("Работа") }
                Button(onClick = { vm.setBreak(context) }) { Text("Перерыв") }
                Button(onClick = { vm.setLongBreak(context) }) { Text("Длинный") }
            }
        }

        item {
            Button(onClick = { vm.toggleControlMode(context) }) {
                Text(
                    if (state.engine.controlMode == "automatic") "Переключить на ручной режим"
                    else "Переключить на авто режим"
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Text(
                text = "Задачи",
                style = MaterialTheme.typography.titleLarge
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskInputField(
                    value = taskText,
                    onValueChange = { taskText = it }
                )
                Button(onClick = { submitTask() }) {
                    Text("Добавить")
                }
            }
        }

        item {
            TasksList(
                tasks = state.tasks,
                onToggle = { vm.toggleTask(it) },
                onDelete = { vm.deleteTask(it) }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Статистика за сегодня",
                    style = MaterialTheme.typography.titleLarge
                )
                Button(onClick = { showCalendar = !showCalendar }) {
                    Text(if (showCalendar) "Скрыть календарь" else "Календарь")
                }
            }
        }

        item {
            StatsBlock(
                stats = state.todayStats,
                dateLabel = "Сегодня"
            )
        }

        if (showCalendar) {
            item {
                CalendarSection(
                    visibleMonth = visibleMonth,
                    onPreviousMonth = { visibleMonthText = visibleMonth.minusMonths(1).toString() },
                    onNextMonth = { visibleMonthText = visibleMonth.plusMonths(1).toString() },
                    statsHistory = state.statsHistory,
                    selectedDate = selectedDate,
                    onSelectDate = { selectedDateText = it.toString() }
                )
            }

            item {
                StatsBlock(
                    stats = selectedStats,
                    dateLabel = selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                )
            }
        }
    }
}

@Composable
private fun RowScope.TaskInputField(
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.weight(1f),
        label = { Text("Новая задача") },
        placeholder = { Text("Например: Написать отчёт") },
        singleLine = true
    )
}

@Composable
private fun TasksList(
    tasks: List<TaskItem>,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tasks, key = { it.id }) { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(if (task.done) "✔ ${task.text}" else task.text)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onToggle(task.id) }) {
                            Text(if (task.done) "Снять" else "Готово")
                        }
                        Button(onClick = { onDelete(task.id) }) {
                            Text("Удалить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsBlock(
    stats: DayStats,
    dateLabel: String
) {
    val engine = remember { PomodoroEngine() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(dateLabel, style = MaterialTheme.typography.titleMedium)
            Text("Время работы: ${engine.formatHms(stats.workTime)}")
            Text("Время отдыха: ${engine.formatHms(stats.breakTime)}")
            Text("Циклы: ${stats.cycles}")
            Text("Перерывы: ${stats.breaks}")
            Text("Выполненные задачи: ${stats.tasksDone}")
        }
    }
}

@Composable
private fun CalendarSection(
    visibleMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    statsHistory: Map<String, DayStats>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    val monthLabel = visibleMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru")))
    val cells = remember(visibleMonth) { buildCalendarCells(visibleMonth) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onPreviousMonth) { Text("←") }
                Text(monthLabel.replaceFirstChar { it.titlecase(Locale("ru")) }, style = MaterialTheme.typography.titleMedium)
                Button(onClick = onNextMonth) { Text("→") }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                dayLabels().forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            cells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    week.forEach { date ->
                        CalendarDayCell(
                            date = date,
                            isInMonth = date.month == visibleMonth.month,
                            isSelected = date == selectedDate,
                            hasStats = statsHistory.containsKey(date.toString()),
                            onClick = { onSelectDate(date) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.CalendarDayCell(
    date: LocalDate,
    isInMonth: Boolean,
    isSelected: Boolean,
    hasStats: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        hasStats -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(42.dp)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .background(backgroundColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                color = if (isInMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                style = MaterialTheme.typography.bodyMedium
            )
            if (hasStats) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

private fun buildCalendarCells(month: YearMonth): List<LocalDate> {
    val firstDay = month.atDay(1)
    val offset = (firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val startDate = firstDay.minusDays(offset.toLong())
    return List(42) { startDate.plusDays(it.toLong()) }
}

private fun dayLabels(): List<String> {
    return listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    ).map {
        it.getDisplayName(TextStyle.SHORT, Locale("ru"))
            .replace(".", "")
            .replaceFirstChar { ch -> ch.titlecase(Locale("ru")) }
    }
}
