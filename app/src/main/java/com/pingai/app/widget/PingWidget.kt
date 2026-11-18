package com.pingai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
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

private data class WidgetStrings(
    val noTopic: String,
    val loading: String,
    val nextUpdateFormat: String
)

class PingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = Prefs(context)
        val topics = prefs.topics.first()
        val activeId = prefs.activeTopicId.first()
        val isPro = prefs.isPro.first()
        val topic = topics.firstOrNull { it.id == activeId } ?: topics.firstOrNull()

        val strings = WidgetStrings(
            noTopic = context.getString(R.string.widget_no_topic),
            loading = context.getString(R.string.widget_loading),
            nextUpdateFormat = context.getString(R.string.widget_next_update)
        )

        provideContent { WidgetContent(topic, isPro, strings) }
    }

    @Composable
    private fun WidgetContent(topic: Topic?, isPro: Boolean, strings: WidgetStrings) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF1E1B2E))
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = "Ping AI",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF9C90F5)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                if (isPro) {
                    Text(
                        text = "↻",
                        modifier = GlanceModifier.clickable(actionRunCallback<RefreshActionCallback>()),
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 18.sp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (topic == null) {
                Text(
                    text = strings.noTopic,
                    style = TextStyle(color = ColorProvider(Color(0xFFB8B3D6)), fontSize = 14.sp)
                )
            } else {
                Text(
                    text = topic.name,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFB8B3D6)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = topic.shortInfo.ifBlank { strings.loading },
                    maxLines = 6,
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 15.sp)
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = countdownLabel(topic.nextUpdateEpochMs, strings),
                    style = TextStyle(color = ColorProvider(Color(0xFF7C6CF0)), fontSize = 10.sp)
                )
            }
        }
    }

    private fun countdownLabel(nextUpdateEpochMs: Long, strings: WidgetStrings): String {
        if (nextUpdateEpochMs <= 0L) return ""
        val remainingMs = (nextUpdateEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val hours = TimeUnit.MILLISECONDS.toHours(remainingMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60
        val timeStr = if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')} h" else "$minutes min"
        return String.format(strings.nextUpdateFormat, timeStr)
    }
}

class PingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PingWidget()
}
