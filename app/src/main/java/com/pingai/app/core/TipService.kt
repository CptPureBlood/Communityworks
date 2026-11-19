package com.pingai.app.core

import android.content.Context
import com.pingai.app.R
import com.pingai.app.data.Prefs
import com.pingai.app.data.SecureKeyStore
import com.pingai.app.network.AiException
import com.pingai.app.network.AiRepository
import com.pingai.app.notification.NotificationHelper
import com.pingai.app.widget.WidgetUpdater
import com.pingai.app.work.WorkScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import kotlin.math.min

data class RefreshStatus(val loading: Boolean = false, val error: String? = null)

/**
 * Bilgi çekme işinin tek sahibi. Uygulama (Şimdi yenile), WorkManager ve widget aynı kodu kullanır.
 * Her aşamanın zaman aşımı vardır; her hata kullanıcıya görünür şekilde yazılır.
 */
object TipService {

    enum class Outcome { SUCCESS, FAILED, SKIPPED }

    private const val TOTAL_TIMEOUT_MS = 45_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val _status = MutableStateFlow(RefreshStatus())
    val status: StateFlow<RefreshStatus> = _status.asStateFlow()

    /** Uygulamadan başlat: ekran kapansa/dönse de iş sürer. */
    fun launchRefresh(context: Context, notify: Boolean = false) {
        val app = context.applicationContext
        scope.launch { refreshAndReschedule(app, notify, null) }
    }

    /** Yeniler, sonra bir sonraki zamanlamayı kurar. slot: zincirdeki mevcut işin yuvası (yoksa null). */
    suspend fun refreshAndReschedule(app: Context, notify: Boolean, slot: Int?) {
        val outcome = refreshNow(app, notify)
        if (outcome == Outcome.SKIPPED) return
        val interval = Prefs(app).refreshIntervalMinutes.first()
        val delayMin = if (outcome == Outcome.SUCCESS) interval else min(interval, 15)
        WorkScheduler.scheduleNext(app, delayMin, slot)
    }

    suspend fun refreshNow(app: Context, notify: Boolean): Outcome {
        if (!mutex.tryLock()) return Outcome.SKIPPED
        val prefs = Prefs(app)
        var outcome = Outcome.FAILED
        try {
            _status.value = RefreshStatus(loading = true)
            prefs.setFetchingSince(System.currentTimeMillis())
            WidgetUpdater.updateAll(app)
            outcome = runRefresh(app, prefs, notify)
        } finally {
            withContext(NonCancellable) {
                prefs.setFetchingSince(0L)
                WidgetUpdater.updateAll(app)
                mutex.unlock()
            }
        }
        return outcome
    }

    private suspend fun runRefresh(app: Context, prefs: Prefs, notify: Boolean): Outcome {
        val loc = LocaleHelper.wrap(app)
        return try {
            withTimeout(TOTAL_TIMEOUT_MS) {
                val provider = prefs.provider.first()
                val apiKey = withContext(Dispatchers.IO) { SecureKeyStore.get(app).getKey(provider) }
                if (apiKey.isNullOrBlank()) {
                    return@withTimeout fail(prefs, loc.getString(R.string.err_no_key, provider.displayLabel))
                }

                val contentLang = LocaleHelper.resolveContentLanguage(prefs.contentLanguage.first())
                val isPro = prefs.isPro.first()
                val interval = prefs.refreshIntervalMinutes.first()
                val topics = prefs.getTopicsOnce()
                val activeId = prefs.activeTopicId.first()
                val targets = if (isPro) topics
                else topics.filter { it.id == activeId }.ifEmpty { topics.take(1) }
                if (targets.isEmpty()) {
                    return@withTimeout fail(prefs, loc.getString(R.string.widget_no_topic))
                }

                val results = coroutineScope {
                    targets.map { t ->
                        async { t to AiRepository.shortTip(provider, apiKey, t.name, contentLang, t.shortInfo) }
                    }.awaitAll()
                }

                val now = System.currentTimeMillis()
                var successes = 0
                var firstError: Throwable? = null
                for ((topic, result) in results) {
                    val tip = result.getOrNull()
                    if (tip != null) {
                        successes++
                        val next = now + interval * 60_000L
                        prefs.updateTopic(topic.id) {
                            it.copy(shortInfo = tip, lastUpdatedEpochMs = now, nextUpdateEpochMs = next)
                        }
                        if (notify) {
                            NotificationHelper.showTipNotification(
                                app, topic.copy(shortInfo = tip, lastUpdatedEpochMs = now, nextUpdateEpochMs = next)
                            )
                        }
                    } else if (firstError == null) {
                        firstError = result.exceptionOrNull()
                    }
                }

                if (successes > 0) {
                    prefs.setLastError("")
                    _status.value = RefreshStatus()
                    Outcome.SUCCESS
                } else {
                    fail(prefs, errorText(loc, firstError ?: IllegalStateException("No result")))
                }
            }
        } catch (e: TimeoutCancellationException) {
            fail(prefs, errorText(loc, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(prefs, errorText(loc, e))
        }
    }

    private suspend fun fail(prefs: Prefs, message: String): Outcome {
        prefs.setLastError(message)
        _status.value = RefreshStatus(error = message)
        return Outcome.FAILED
    }

    /** Hatayı kullanıcının dilinde, anlaşılır cümleye çevirir. */
    fun errorText(loc: Context, e: Throwable): String = when {
        e is TimeoutCancellationException || e is InterruptedIOException ->
            loc.getString(R.string.err_timeout)
        e is UnknownHostException -> loc.getString(R.string.err_network)
        e is AiException -> when (e.code) {
            429 -> loc.getString(R.string.err_rate)
            500, 502, 503, 504 -> loc.getString(R.string.err_busy)
            400, 401, 403 -> loc.getString(R.string.err_key, e.message.orEmpty())
            404 -> loc.getString(R.string.err_model)
            else -> loc.getString(R.string.err_unknown, "${e.code ?: ""} ${e.message.orEmpty()}".trim())
        }
        e is IOException -> loc.getString(R.string.err_network)
        else -> loc.getString(R.string.err_unknown, "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
    }
}
