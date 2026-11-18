package com.pingai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF7C6CF0)

private val DarkColors = darkColorScheme(
    primary = Purple, background = Color(0xFF1E1B2E), surface = Color(0xFF1E1B2E)
)
private val LightColors = lightColorScheme(
    primary = Purple, background = Color(0xFFF7F6FC), surface = Color(0xFFF7F6FC)
)

@Composable
fun PingAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
