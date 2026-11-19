package com.pingai.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pingai.app.ui.theme.WinColors
import kotlinx.coroutines.delay

private const val PERIOD_S = 20f      // bir halkanın doğup solması (çok yavaş)
private const val BOOST = 14f         // yeni soru gelince hız çarpanı
private const val LOADING = 4f       // soru beklenirken orta hız
private const val RINGS = 3

/**
 * Arka planda çok yavaş büyüyen ping halkaları; soru beklenirken ([loading]) orta hız. [boostKey] değişince
 * (yeni soru geldi) yaklaşık 1 saniye boyunca hızlanıp tekrar sakinleşir.
 */
@Composable
fun PingBackground(boostKey: Any?, loading: Boolean, modifier: Modifier = Modifier) {
    val base by androidx.compose.runtime.rememberUpdatedState(if (loading) LOADING else 1f)
    var phase by remember { mutableFloatStateOf(0f) }
    val speed = remember { Animatable(1f) }
    var prev by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(boostKey) {
        val old = prev
        prev = boostKey
        if (old != null && old != boostKey) {
            speed.snapTo(1f)
            speed.animateTo(BOOST, tween(150))
            delay(550)
            speed.animateTo(base, tween(300))
        }
    }
    LaunchedEffect(loading) { speed.animateTo(base, tween(400)) }
    LaunchedEffect(Unit) {
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            phase = (phase + dt * speed.value / PERIOD_S) % 1f
        }
    }

    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height * 0.30f)
        val maxR = size.width * 1.0f
        val minR = size.width * 0.10f
        val boosted = ((speed.value - LOADING) / (BOOST - LOADING)).coerceIn(0f, 1f)
        for (k in 0 until RINGS) {
            val p = (phase + k.toFloat() / RINGS) % 1f
            val r = minR + (maxR - minR) * p
            val fadeIn = (p / 0.08f).coerceAtMost(1f)
            val fadeOut = (1f - p)
            val a = (0.30f + 0.20f * boosted) * fadeIn * fadeOut * fadeOut
            val w = (2f + 5f * p).dp.toPx()
            drawCircle(
                color = WinColors.Accent.copy(alpha = a),
                radius = r, center = c,
                style = Stroke(width = w)
            )
        }
    }
}
