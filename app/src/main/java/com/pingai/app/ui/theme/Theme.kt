package com.pingai.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Windows 11 koyu tema renkleri. */
object WinColors {
    val Background = Color(0xFF202020)
    val Card = Color(0xFF2B2B2B)
    val Stroke = Color(0xFF3D3D3D)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFC5C5C5)
    val Accent = Color(0xFF60CDFF)
    val Error = Color(0xFFFF99A4)
}

private val WindowsDark = darkColorScheme(
    primary = WinColors.Accent,
    onPrimary = Color.Black,
    secondary = WinColors.Accent,
    background = WinColors.Background,
    onBackground = WinColors.TextPrimary,
    surface = WinColors.Background,
    onSurface = WinColors.TextPrimary,
    surfaceVariant = WinColors.Card,
    onSurfaceVariant = WinColors.TextSecondary,
    outline = Color(0xFF8A8A8A),
    outlineVariant = WinColors.Stroke,
    error = WinColors.Error
)

@Composable
fun PingAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WindowsDark, content = content)
}
