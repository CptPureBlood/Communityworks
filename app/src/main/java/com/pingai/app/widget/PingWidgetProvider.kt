package com.pingai.app.widget

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.pingai.app.core.TipActions
import com.pingai.app.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PingWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.pingai.app.WIDGET_REFRESH"
        const val ACTION_ANSWER = "com.pingai.app.WIDGET_ANSWER"
        const val ACTION_SAVE = "com.pingai.app.WIDGET_SAVE"
        const val ACTION_LIKE = "com.pingai.app.WIDGET_LIKE"
        const val ACTION_DISLIKE = "com.pingai.app.WIDGET_DISLIKE"
        const val EXTRA_INDEX = "index"
        const val EXTRA_TOPIC_ID = "topic_id"
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        run(context) { WidgetUpdater.updateAll(context) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val app = context.applicationContext
        when (intent.action) {
            ACTION_REFRESH -> run(app) {
                val prefs = com.pingai.app.data.Prefs(app)
                if (com.pingai.app.core.TipService.cooldownRemainingMs() > 0L) return@run
                val pro = prefs.isPro.first()
                val noTipYet = prefs.activeTopic()?.shortInfo.isNullOrBlank()
                if (pro || noTipYet || prefs.useCredit()) {
                    WorkScheduler.runManual(app)
                } else {
                    // Ücretsizde yeni soru için hak gerekir: uygulamada reklam izleyerek kazanılır.
                    prefs.setLastError(com.pingai.app.core.LocaleHelper.wrap(app).getString(com.pingai.app.R.string.err_no_credit))
                    WidgetUpdater.updateAll(app)
                }
            }
            ACTION_ANSWER -> {
                val index = intent.getIntExtra(EXTRA_INDEX, -1)
                val topicId = intent.getStringExtra(EXTRA_TOPIC_ID)
                val fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)
                run(app) {
                    val correct = TipActions.answer(app, topicId, index)
                    if (fromNotification) {
                        app.getSystemService(NotificationManager::class.java)?.cancel(topicId.orEmpty().hashCode())
                    }
                    if (correct == null) WidgetUpdater.updateAll(app)
                    else WidgetUpdater.playFlash(app, correct)
                }
            }
            ACTION_SAVE -> run(app) { TipActions.toggleSave(app, null); WidgetUpdater.updateAll(app) }
            ACTION_LIKE -> run(app) { TipActions.feedback(app, null, true); WidgetUpdater.updateAll(app) }
            ACTION_DISLIKE -> run(app) { TipActions.feedback(app, null, false); WidgetUpdater.updateAll(app) }
        }
    }

    private fun run(context: Context, block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { block() } finally { pending.finish() }
        }
    }
}
