package com.pingai.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.pingai.app.MainActivity
import com.pingai.app.R
import com.pingai.app.core.LocaleHelper
import com.pingai.app.data.Prefs
import kotlinx.coroutines.flow.first

/** Klasik RemoteViews widget'ı: yazı boyutunu sistem (autoSize) ayarlar, geri sayım canlı akar. */
object WidgetUpdater {

    private val TEXT_PRIMARY = Color.parseColor("#FFFFFF")
    private val TEXT_SECONDARY = Color.parseColor("#C5C5C5")
    private val ERROR = Color.parseColor("#FF99A4")

    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, PingWidgetProvider::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, build(app))
    }

    private suspend fun build(app: Context): RemoteViews {
        val loc = LocaleHelper.wrap(app)
        val prefs = Prefs(app)
        val topics = prefs.topics.first()
        val activeId = prefs.activeTopicId.first()
        val isPro = prefs.isPro.first()
        val lastError = prefs.lastError.first()
        val fetchingSince = prefs.fetchingSince.first()
        val now = System.currentTimeMillis()
        val fetching = fetchingSince > 0L && now - fetchingSince < 60_000L
        val topic = topics.firstOrNull { it.id == activeId } ?: topics.firstOrNull()

        val views = RemoteViews(app.packageName, R.layout.widget_ping)
        views.setTextViewText(R.id.w_topic, topic?.name ?: loc.getString(R.string.app_name))

        // Gövde metni
        val hasTip = topic != null && topic.shortInfo.isNotBlank()
        val body: String
        val bodyColor: Int
        when {
            topic == null -> {
                body = loc.getString(R.string.widget_no_topic); bodyColor = TEXT_SECONDARY
            }
            hasTip -> {
                body = topic.shortInfo; bodyColor = TEXT_PRIMARY
            }
            lastError != null && !fetching -> {
                body = lastError; bodyColor = ERROR
            }
            else -> {
                body = loc.getString(R.string.widget_loading); bodyColor = TEXT_SECONDARY
            }
        }
        views.setTextViewText(R.id.w_body, body)
        views.setTextColor(R.id.w_body, bodyColor)

        // Alt satır: güncelleniyor / hata / canlı geri sayım
        val remaining = (topic?.nextUpdateEpochMs ?: 0L) - now
        views.setViewVisibility(R.id.w_footer, View.GONE)
        views.setViewVisibility(R.id.w_countdown, View.GONE)
        when {
            fetching -> {
                views.setTextViewText(R.id.w_footer, loc.getString(R.string.widget_updating))
                views.setViewVisibility(R.id.w_footer, View.VISIBLE)
            }
            hasTip && lastError != null -> {
                views.setTextViewText(R.id.w_footer, loc.getString(R.string.widget_update_failed))
                views.setViewVisibility(R.id.w_footer, View.VISIBLE)
            }
            hasTip && remaining > 0L -> {
                views.setChronometerCountDown(R.id.w_countdown, true)
                views.setChronometer(
                    R.id.w_countdown,
                    SystemClock.elapsedRealtime() + remaining,
                    loc.getString(R.string.widget_next_update, "%s"),
                    true
                )
                views.setViewVisibility(R.id.w_countdown, View.VISIBLE)
            }
        }

        // Düğmeler: yenile (yalnızca Pro) ve ayarlar. Widget'ın gövdesine dokunmak bir şey yapmaz.
        views.setViewVisibility(
            R.id.w_refresh,
            if (isPro && topic != null) View.VISIBLE else View.GONE
        )
        views.setOnClickPendingIntent(
            R.id.w_refresh,
            PendingIntent.getBroadcast(
                app, 2,
                Intent(app, PingWidgetProvider::class.java).setAction(PingWidgetProvider.ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        views.setOnClickPendingIntent(
            R.id.w_settings,
            PendingIntent.getActivity(
                app, 1,
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        return views
    }
}
