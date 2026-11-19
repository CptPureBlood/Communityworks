package com.pingai.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils
import com.pingai.app.widget.WidgetTheme
import com.pingai.app.widget.WidgetThemes

/** Uygulama renk paleti. Widget paletlerinden türetilir; "navy" varsayılan gece mavisidir. */
class AppPalette(
    val id: String,
    val bg: Color, val card: Color, val stroke: Color,
    val text: Color, val dim: Color, val accent: Color,
    val heroStart: Color, val heroEnd: Color, val navIdle: Color,
    val pro: Boolean
) {
    val isDark get() = bg.luminance() < 0.5f
    val onAccent get() = if (accent.luminance() > 0.5f) Color(0xFF06183A) else Color.White
}

object AppThemes {
    val NAVY = AppPalette(
        "navy", Color(0xFF050C1E), Color(0xFF0A1630), Color(0xFF1B3366),
        Color.White, Color(0xFF8CA6D4), Color(0xFFBFE3FF),
        Color(0xFF0E2250), Color(0xFF0A1630), Color(0xFF6D86B8), false
    )

    private fun blend(a: Int, b: Int, r: Float) = Color(ColorUtils.blendARGB(a, b, r))

    private fun from(t: WidgetTheme) = AppPalette(
        t.id, Color(t.bg), Color(t.button), blend(t.button, t.text, 0.18f),
        Color(t.text), Color(t.dim), Color(t.accent),
        blend(t.bg, t.accent, 0.20f), Color(t.button), Color(t.dim), t.pro
    )

    /** Seçim listesi: gece mavisi + tüm widget paletleri (widget teması id'leriyle aynı). */
    val all: List<AppPalette> = listOf(NAVY) + WidgetThemes.all.map { from(it) }

    fun byId(id: String?, isPro: Boolean): AppPalette {
        val p = all.firstOrNull { it.id == id } ?: NAVY
        return if (p.pro && !isPro) NAVY else p
    }
}

/** Geçerli renkler; [current] değişince okuyan tüm ekranlar yeniden çizilir. */
object WinColors {
    var current by mutableStateOf(AppThemes.NAVY)

    val Bg get() = current.bg
    val Card get() = current.card
    val Stroke get() = current.stroke
    val Text get() = current.text
    val TextDim get() = current.dim
    val Accent get() = current.accent
    val Error get() = if (current.isDark) Color(0xFFFF99A4) else Color(0xFFC62828)
    val HeroStart get() = current.heroStart
    val HeroEnd get() = current.heroEnd
    val NavIdle get() = current.navIdle
}

@Composable
fun PingAiTheme(content: @Composable () -> Unit) {
    val p = WinColors.current
    val scheme = remember(p) {
        if (p.isDark) darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
            surfaceVariant = p.card, onSurfaceVariant = p.dim, outline = p.stroke, error = WinColors.Error
        ) else lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
            surfaceVariant = p.card, onSurfaceVariant = p.dim, outline = p.stroke, error = WinColors.Error
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
