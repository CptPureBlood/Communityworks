package com.pingai.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.pingai.app.MainActivity
import com.pingai.app.R
import com.pingai.app.core.LocaleHelper
import com.pingai.app.data.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Klasik RemoteViews widget'ı: soru → cevap parlaması → bilgi. */
object WidgetUpdater {

    /** Cevap sonrası geçici parlama. factor 0..1: rengin yoğunluğu. */
    data class Flash(val factor: Float, val correct: Boolean)

    private val OPT_FRAME = intArrayOf(R.id.w_opt0, R.id.w_opt1, R.id.w_opt2, R.id.w_opt3)
    private val OPT_BG = intArrayOf(R.id.w_optbg0, R.id.w_optbg1, R.id.w_optbg2, R.id.w_optbg3)
    private val OPT_TX = intArrayOf(R.id.w_opttx0, R.id.w_opttx1, R.id.w_opttx2, R.id.w_opttx3)

    suspend fun updateAll(context: Context, flash: Flash? = null) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, PingWidgetProvider::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, build(app, flash))
    }

    /**
     * Cevap sonrası kenarlardan ortaya doğru saydamlaşan yeşil/kırmızı parlama.
     * İlk kare tam güncelleme, sonrakiler yalnızca parlama katmanını değiştiren hafif (kısmi) güncellemeler;
     * böylece kare hızı yüksek kalır.
     */
    suspend fun playFlash(context: Context, correct: Boolean) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, PingWidgetProvider::class.java))
        if (ids.isEmpty()) return
        updateAll(app, Flash(1f, correct))
        val color = if (correct) WidgetThemes.GOOD else WidgetThemes.BAD
        val frames = 16
        for (i in 1..frames) {
            val t = i / frames.toFloat()
            val factor = Math.pow((1f - t).toDouble(), 1.7).toFloat()
            val partial = RemoteViews(app.packageName, R.layout.widget_ping)
            partial.setInt(R.id.w_glow, "setImageAlpha", (factor * 255).toInt().coerceIn(0, 255))
            partial.setInt(R.id.w_glow, "setColorFilter", color)
            manager.partiallyUpdateAppWidget(ids, partial)
            delay(55)
        }
        updateAll(app, null)
    }

    private suspend fun build(app: Context, flash: Flash?): RemoteViews {
        val loc = LocaleHelper.wrap(app)
        val prefs = Prefs(app)
        val isPro = prefs.isPro.first()
        val theme = WidgetThemes.byId(prefs.widgetTheme.first(), isPro)
        val topic = prefs.activeTopic()
        val lastError = prefs.lastError.first()
        val fetchingSince = prefs.fetchingSince.first()
        val stats = prefs.stats.first()
        val saved = prefs.saved.first()
        val now = System.currentTimeMillis()
        val fetching = fetchingSince > 0L && now - fetchingSince < 60_000L

        val views = RemoteViews(app.packageName, R.layout.widget_ping)

        // Arka plan: tema rengi. Parlama ayrı bir katmanda (kenarlardan ortaya saydamlaşan).
        views.setInt(R.id.w_bg, "setColorFilter", theme.bg)
        if (flash != null) {
            views.setViewVisibility(R.id.w_glow, View.VISIBLE)
            views.setInt(R.id.w_glow, "setColorFilter", if (flash.correct) WidgetThemes.GOOD else WidgetThemes.BAD)
            views.setInt(R.id.w_glow, "setImageAlpha", (flash.factor * 255).toInt().coerceIn(0, 255))
        } else {
            views.setViewVisibility(R.id.w_glow, View.GONE)
        }
        views.setTextColor(R.id.w_sign, ColorUtils.setAlphaComponent(theme.dim, 140))

        views.setTextViewText(
            R.id.w_topic,
            (if (topic?.review == true) "🔁 " else "") + (topic?.name ?: loc.getString(R.string.app_name))
        )
        views.setTextColor(R.id.w_topic, theme.accent)
        views.setInt(R.id.w_settings, "setColorFilter", theme.dim)
        views.setInt(R.id.w_refresh, "setColorFilter", theme.dim)

        val streak = stats.effectiveStreak()
        if (streak > 0) {
            views.setTextViewText(R.id.w_streak, "🔥 $streak")
            views.setTextColor(R.id.w_streak, theme.text)
            views.setViewVisibility(R.id.w_streak, View.VISIBLE)
        } else views.setViewVisibility(R.id.w_streak, View.GONE)

        val hasTip = topic != null && topic.hasTip
        val quizPhase = topic != null && topic.hasQuiz && (topic.answeredIdx < 0 || flash != null)

        // --- Gövde ---
        val body: String
        val bodyColor: Int
        when {
            topic == null -> { body = loc.getString(R.string.widget_no_topic); bodyColor = theme.dim }
            hasTip && quizPhase -> { body = topic.question; bodyColor = theme.text }
            hasTip -> { body = topic.shortInfo; bodyColor = theme.text }
            lastError != null && !fetching -> { body = lastError; bodyColor = WidgetThemes.BAD }
            else -> { body = loc.getString(R.string.widget_loading); bodyColor = theme.dim }
        }
        views.setTextViewText(R.id.w_body, body)
        views.setTextColor(R.id.w_body, bodyColor)

        // --- Şıklar ---
        views.setViewVisibility(R.id.w_options, View.GONE)
        if (topic != null && hasTip && quizPhase) {
            views.setViewVisibility(R.id.w_options, View.VISIBLE)
            for (i in 0 until 4) {
                if (i >= topic.options.size) {
                    views.setViewVisibility(OPT_FRAME[i], View.GONE)
                    continue
                }
                views.setViewVisibility(OPT_FRAME[i], View.VISIBLE)
                views.setTextViewText(OPT_TX[i], topic.options[i])
                var btn = theme.button
                var txt = theme.text
                if (flash != null) {
                    if (i == topic.correctIdx) { btn = WidgetThemes.GOOD; txt = 0xFFFFFFFF.toInt() }
                    else if (i == topic.answeredIdx) { btn = WidgetThemes.BAD; txt = 0xFFFFFFFF.toInt() }
                }
                views.setInt(OPT_BG[i], "setColorFilter", btn)
                views.setTextColor(OPT_TX[i], txt)
                if (flash == null) {
                    views.setOnClickPendingIntent(
                        OPT_FRAME[i],
                        broadcast(app, 10 + i, PingWidgetProvider.ACTION_ANSWER) {
                            putExtra(PingWidgetProvider.EXTRA_INDEX, i)
                            putExtra(PingWidgetProvider.EXTRA_TOPIC_ID, topic.id)
                        }
                    )
                }
            }
        }

        // --- Sonuç satırı (cevaplandıktan sonra) ---
        views.setViewVisibility(R.id.w_result, View.GONE)
        val infoPhase = hasTip && !quizPhase
        if (infoPhase && topic != null && topic.hasQuiz && topic.answeredIdx >= 0) {
            val ok = topic.answeredIdx == topic.correctIdx
            val text = if (ok) loc.getString(R.string.widget_correct)
            else loc.getString(R.string.widget_wrong, topic.options.getOrNull(topic.correctIdx).orEmpty())
            views.setTextViewText(R.id.w_result, text)
            views.setTextColor(R.id.w_result, if (ok) WidgetThemes.GOOD else WidgetThemes.BAD)
            views.setViewVisibility(R.id.w_result, View.VISIBLE)
        }

        // --- Alt satır: eylem düğmeleri + geri sayım/durum ---
        val actionVis = if (infoPhase) View.VISIBLE else View.GONE
        for (id in intArrayOf(R.id.w_save, R.id.w_like, R.id.w_dislike, R.id.w_share)) {
            views.setViewVisibility(id, actionVis)
        }
        views.setViewVisibility(R.id.w_countdown, View.GONE)
        views.setViewVisibility(R.id.w_footer, View.GONE)
        var bottomVisible = infoPhase

        if (infoPhase && topic != null) {
            val isSaved = saved.any { it.id == topic.tipId }
            views.setImageViewResource(R.id.w_save, if (isSaved) R.drawable.ic_heart_filled else R.drawable.ic_heart)
            views.setInt(R.id.w_save, "setColorFilter", if (isSaved) WidgetThemes.BAD else theme.dim)
            views.setInt(R.id.w_like, "setColorFilter", if (topic.feedback == 1) theme.accent else theme.dim)
            views.setInt(R.id.w_dislike, "setColorFilter", if (topic.feedback == -1) WidgetThemes.BAD else theme.dim)
            views.setInt(R.id.w_share, "setColorFilter", theme.dim)
            views.setOnClickPendingIntent(R.id.w_save, broadcast(app, 20, PingWidgetProvider.ACTION_SAVE) {})
            views.setOnClickPendingIntent(R.id.w_like, broadcast(app, 21, PingWidgetProvider.ACTION_LIKE) {})
            views.setOnClickPendingIntent(R.id.w_dislike, broadcast(app, 22, PingWidgetProvider.ACTION_DISLIKE) {})
            views.setOnClickPendingIntent(
                R.id.w_share,
                PendingIntent.getActivity(
                    app, 23, Intent(app, ShareActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }

        val remaining = (topic?.nextUpdateEpochMs ?: 0L) - now
        when {
            fetching -> {
                views.setTextViewText(R.id.w_footer, loc.getString(R.string.widget_updating))
                views.setTextColor(R.id.w_footer, theme.dim)
                views.setViewVisibility(R.id.w_footer, View.VISIBLE)
                bottomVisible = true
            }
            hasTip && lastError != null -> {
                views.setTextViewText(R.id.w_footer, loc.getString(R.string.widget_update_failed))
                views.setTextColor(R.id.w_footer, WidgetThemes.BAD)
                views.setViewVisibility(R.id.w_footer, View.VISIBLE)
                bottomVisible = true
            }
            infoPhase && remaining > 0L -> {
                views.setChronometerCountDown(R.id.w_countdown, true)
                views.setChronometer(
                    R.id.w_countdown,
                    SystemClock.elapsedRealtime() + remaining,
                    loc.getString(R.string.widget_next_update, "%s"),
                    true
                )
                views.setTextColor(R.id.w_countdown, theme.dim)
                views.setViewVisibility(R.id.w_countdown, View.VISIBLE)
            }
        }
        views.setViewVisibility(R.id.w_bottom, if (bottomVisible) View.VISIBLE else View.GONE)

        // --- Üst düğmeler. Widget gövdesine dokunmak bir şey yapmaz. ---
        views.setViewVisibility(R.id.w_refresh, if (isPro && topic != null) View.VISIBLE else View.GONE)
        views.setOnClickPendingIntent(R.id.w_refresh, broadcast(app, 2, PingWidgetProvider.ACTION_REFRESH) {})
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

    private fun broadcast(app: Context, code: Int, action: String, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(app, PingWidgetProvider::class.java).setAction(action).apply(extras)
        return PendingIntent.getBroadcast(app, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
