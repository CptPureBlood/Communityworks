package com.pingai.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object WinColors {
    val Bg = Color(0xFF202020)
    val Card = Color(0xFF2B2B2B)
    val Stroke = Color(0xFF3D3D3D)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xFFC5C5C5)
    val Accent = Color(0xFF60CDFF)
    val Error = Color(0xFFFF99A4)
    val HeroStart = Color(0xFF15405A)
    val HeroEnd = Color(0xFF262626)
}

private val scheme = darkColorScheme(
    primary = WinColors.Accent,
    onPrimary = Color(0xFF00212F),
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
