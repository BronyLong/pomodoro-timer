package com.example.pomodoro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B6FE8),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFB8A8F8),
    onSecondary = Color(0xFF1A1333),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF3F2F7),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF3F2F7),
    surfaceVariant = Color(0xFF2A2733),
    onSurfaceVariant = Color(0xFFD8D0E8),
    outline = Color(0xFF8B839B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6F4BD8),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFE8E0FF),
    onSecondary = Color(0xFF24174F),
    background = Color(0xFFF7F5FB),
    onBackground = Color(0xFF18151F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18151F),
    surfaceVariant = Color(0xFFF0EBFA),
    onSurfaceVariant = Color(0xFF4B445A),
    outline = Color(0xFFC9C1D8)
)

@Composable
fun PomodoroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
