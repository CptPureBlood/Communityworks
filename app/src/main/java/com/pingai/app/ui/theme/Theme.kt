package com.pingai.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object WinColors {
    val Bg = Color(0xFF050C1E)
    val Card = Color(0xFF0A1630)
    val Stroke = Color(0xFF1B3366)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xFF8CA6D4)
    val Accent = Color(0xFFBFE3FF)
    val Error = Color(0xFFFF99A4)
    val HeroStart = Color(0xFF0E2250)
    val NavIdle = Color(0xFF6D86B8)
    val HeroEnd = Color(0xFF0A1630)
}

private val scheme = darkColorScheme(
    primary = WinColors.Accent,
    onPrimary = Color(0xFF06183A),
    background = WinColors.Bg,
    onBackground = WinColors.Text,
    surface = WinColors.Bg,
    onSurface = WinColors.Text,
    surfaceVariant = WinColors.Card,
    onSurfaceVariant = WinColors.TextDim,
    outline = WinColors.Stroke,
    error = WinColors.Error
)

@Composable
fun PingAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
