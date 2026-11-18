package com.pingai.app.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pingai.app.data.Prefs
import com.pingai.app.data.SecureKeyStore
import com.pingai.app.network.AiRepository
import com.pingai.app.notification.NotificationHelper
import com.pingai.app.widget.PingWidget
import kotlinx.coroutines.flow.first
import java.util.Locale

class RefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val keyStore = SecureKeyStore(applicationContext)
        val repository = AiRepository()

        val intervalMinutes = prefs.refreshIntervalMinutes.first()
        return try {
            val provider = prefs.provider.first()
            val apiKey = keyStore.getKey(provider)
            if (apiKey.isNullOrBlank()) {
                reportError(prefs, "No API key saved for ${provider.displayLabel}. Open Settings and paste your key.")
                return Result.failure()
            }

            val contentLangRaw = prefs.contentLanguage.first()
            val lang = if (contentLangRaw == "system") Locale.getDefault().language else contentLangRaw
            val isPro = prefs.isPro.first()
            val notificationsOn = prefs.notificationsEnabled.first()

            val allTopics = prefs.getTopicsOnce()
            val activeId = prefs.activeTopicId.first()
            val toRefresh = if (isPro) allTopics
            else allTopics.filter { it.id == activeId }.ifEmpty { allTopics.take(1) }

            var anySuccess = false
            for (topic in toRefresh) {
                val tipResult = repository.fetchShortTip(provider, apiKey, topic.name, lang)
                tipResult.onFailure { e ->
                    // Widget boşsa hatayı göster ki kullanıcı neyin yanlış olduğunu görsün.
                    if (topic.shortInfo.isBlank() || topic.shortInfo.startsWith("⚠")) {
                        prefs.upsertTopic(topic.copy(shortInfo = "⚠ " + (e.message ?: "Error").take(220)))
                    }
                }
                tipResult.onSuccess { tip ->
                    anySuccess = true
                    val now = System.currentTimeMillis()
                    val updated = topic.copy(
                        shortInfo = tip,
                        lastUpdatedEpochMs = now,
                        nextUpdateEpochMs = now + intervalMinutes * 60_000L
                    )
                    prefs.upsertTopic(updated)
                    if (notificationsOn) NotificationHelper.showTipNotification(applicationContext, updated)
                }
            }

            PingWidget().updateAll(applicationContext)
            WorkScheduler.scheduleNext(applicationContext, if (anySuccess) intervalMinutes else 5)
            if (anySuccess) Result.success() else Result.failure()
        } catch (e: Exception) {
            reportError(prefs, "${e.javaClass.simpleName}: ${e.message}")
            WorkScheduler.scheduleNext(applicationContext, 5)
            Result.failure()
        }
    }

    /** Aktif konunun bilgi alanına hatayı yazar (widget ve Ayarlar'da görünür). */
    private suspend fun reportError(prefs: Prefs, message: String) {
        try {
            val topics = prefs.getTopicsOnce()
            val activeId = prefs.activeTopicId.first()
            val topic = topics.firstOrNull { it.id == activeId } ?: topics.firstOrNull() ?: return
            if (topic.shortInfo.isBlank() || topic.shortInfo.startsWith("⚠")) {
                prefs.upsertTopic(topic.copy(shortInfo = "⚠ " + message.take(220)))
            }
            PingWidget().updateAll(applicationContext)
        } catch (_: Exception) {
        }
    }
}
