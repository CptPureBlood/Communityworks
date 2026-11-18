package com.pingai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pingai.app.MainActivity
import com.pingai.app.R
import com.pingai.app.data.Prefs
import com.pingai.app.data.Topic
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// Windows 11 koyu tema renkleri
private val BgColor = Color(0xFF202020)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFC5C5C5)
private val Accent = Color(0xFF60CDFF)
private val ErrorColor = Color(0xFFFF99A4)

private data class WidgetStrings(
    val noTopic: String,
    val loading: String,
    val nextUpdateFormat: String,
    val settings: String,
    val refresh: String
)

private data class TextFit(val fontSp: Int, val maxLines: Int)

/** Widget boyutuna ve metin uzunluğuna göre sığan en büyük yazı boyutunu seçer. */
private fun fitText(text: String, widthDp: Float, heightDp: Float): TextFit {
    val padding = 14f * 2
    val header = 32f
    val footer = 18f
    val gaps = 8f
    val availW = (widthDp - padding).coerceAtLeast(40f)
    val availH = (heightDp - padding - header - footer - gaps).coerceAtLeast(24f)
    val candidates = listOf(26, 24, 22, 20, 18, 16, 15, 14, 13, 12)
    for (fs in candidates) {
        val lineH = fs * 1.3f
        val lines = floor(availH / lineH).toInt()
        if (lines < 1) continue
        val charsPerLine = availW / (fs * 0.52f)
        val needed = ceil(text.length / charsPerLine * 1.15f).toInt()
        if (needed <= lines) return TextFit(fs, lines)
    }
    val fs = candidates.last()
    return TextFit(fs, max(1, floor(availH / (fs * 1.3f)).toInt()))
}

class PingWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = Prefs(context)
        val topics = prefs.topics.first()
        val activeId = prefs.activeTopicId.first()
        val isPro = prefs.isPro.first()
        val topic = topics.firstOrNull { it.id == activeId } ?: topics.firstOrNull()

        val strings = WidgetStrings(
            noTopic = context.getString(R.string.widget_no_topic),
            loading = context.getString(R.string.widget_loading),
            nextUpdateFormat = context.getString(R.string.widget_next_update),
            settings = context.getString(R.string.settings_title),
            refresh = context.getString(R.string.widget_refresh)
        )

        provideContent { WidgetContent(topic, isPro, strings) }
    }

    @Composable
    private fun WidgetContent(topic: Topic?, isPro: Boolean, strings: WidgetStrings) {
        val size = LocalSize.current
        val wDp = size.width.value
        val hDp = size.height.value
        val narrow = wDp < 170f

        val rawTip = when {
            topic == null -> strings.noTopic
            topic.shortInfo.isBlank() -> strings.loading
            else -> topic.shortInfo
        }
        val isError = rawTip.startsWith("⚠")
        val fit = fitText(rawTip, wDp, hDp)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(BgColor)
                .cornerRadius(20.dp)
                .padding(14.dp)
        ) {
            // Üst satır: konu adı + (Pro) yenile + ayarlar
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(32.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = topic?.name ?: "Ping AI",
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = ColorProvider(Accent),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (isPro && topic != null) {
                    IconButton(R.drawable.ic_refresh, strings.refresh, actionRunCallback<RefreshActionCallback>())
                }
                IconButton(R.drawable.ic_settings, strings.settings, actionStartActivity<MainActivity>())
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = rawTip,
                maxLines = fit.maxLines,
                style = TextStyle(
                    color = ColorProvider(if (isError) ErrorColor else TextPrimary),
                    fontSize = fit.fontSp.sp
                )
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            if (topic != null && topic.nextUpdateEpochMs > 0L) {
                Text(
                    text = countdownLabel(topic.nextUpdateEpochMs, strings, narrow),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 11.sp)
                )
            }
        }
    }

    @Composable
    private fun IconButton(iconRes: Int, description: String, action: Action) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            colorFilter = ColorFilter.tint(ColorProvider(TextSecondary)),
            modifier = GlanceModifier.size(32.dp).padding(6.dp).clickable(action)
        )
    }

    private fun countdownLabel(nextUpdateEpochMs: Long, strings: WidgetStrings, narrow: Boolean): String {
        val remainingMs = (nextUpdateEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val hours = TimeUnit.MILLISECONDS.toHours(remainingMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60
        val timeStr = if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')} h" else "$minutes min"
        return if (narrow) timeStr else String.format(strings.nextUpdateFormat, timeStr)
    }
}

class PingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PingWidget()
}
