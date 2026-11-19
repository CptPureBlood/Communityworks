package com.pingai.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.pingDataStore by preferencesDataStore(name = "ping_ai_settings")

class Prefs(context: Context) {

    private val ds = context.applicationContext.pingDataStore

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val PROVIDER = stringPreferencesKey("provider")
        val TOPICS_JSON = stringPreferencesKey("topics_json")
        val ACTIVE_TOPIC_ID = stringPreferencesKey("active_topic_id")
        val REFRESH_INTERVAL_MIN = intPreferencesKey("refresh_interval_min")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val IS_PRO = booleanPreferencesKey("is_pro")
        val LAST_ERROR = stringPreferencesKey("last_error")
        val FETCHING_SINCE = longPreferencesKey("fetching_since")
        val STATS_JSON = stringPreferencesKey("stats_json")
        val SAVED_JSON = stringPreferencesKey("saved_json")
        val WIDGET_THEME = stringPreferencesKey("widget_theme")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start_min")
        val QUIET_END = intPreferencesKey("quiet_end_min")
        val OPTION_COUNT = intPreferencesKey("option_count")
    }

    val onboardingDone: Flow<Boolean> = ds.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    val contentLanguage: Flow<String> = ds.data.map { it[Keys.CONTENT_LANGUAGE] ?: "system" }
    val provider: Flow<AiProvider> = ds.data.map { AiProvider.fromId(it[Keys.PROVIDER]) }
    val refreshIntervalMinutes: Flow<Int> =
        ds.data.map { it[Keys.REFRESH_INTERVAL_MIN] ?: RefreshInterval.H12.minutes }
    val notificationsEnabled: Flow<Boolean> = ds.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    val isPro: Flow<Boolean> = ds.data.map { it[Keys.IS_PRO] ?: false }
    val activeTopicId: Flow<String?> = ds.data.map { it[Keys.ACTIVE_TOPIC_ID] }
    val lastError: Flow<String?> = ds.data.map { it[Keys.LAST_ERROR]?.takeIf { e -> e.isNotBlank() } }
    val fetchingSince: Flow<Long> = ds.data.map { it[Keys.FETCHING_SINCE] ?: 0L }

    val stats: Flow<Stats> = ds.data.map { Stats.parse(it[Keys.STATS_JSON]) }
    val saved: Flow<List<SavedTip>> = ds.data.map { parseSaved(it[Keys.SAVED_JSON]) }
    val widgetTheme: Flow<String> = ds.data.map { it[Keys.WIDGET_THEME] ?: "dark" }
    val quietEnabled: Flow<Boolean> = ds.data.map { it[Keys.QUIET_ENABLED] ?: true }
    val quietStartMin: Flow<Int> = ds.data.map { it[Keys.QUIET_START] ?: (23 * 60) }
    val quietEndMin: Flow<Int> = ds.data.map { it[Keys.QUIET_END] ?: (7 * 60) }
    val optionCount: Flow<Int> = ds.data.map { (it[Keys.OPTION_COUNT] ?: 2).coerceIn(2, 4) }

    val topics: Flow<List<Topic>> = ds.data.map { p -> parseTopics(p[Keys.TOPICS_JSON]) }

    private fun parseTopics(raw: String?): List<Topic> {
        val arr = JSONArray(raw ?: "[]")
        return (0 until arr.length()).map { Topic.fromJson(arr.getJSONObject(it)) }
    }

    private fun parseSaved(raw: String?): List<SavedTip> = try {
        val arr = JSONArray(raw ?: "[]")
        (0 until arr.length()).map { SavedTip.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    private fun encodeSaved(list: List<SavedTip>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    private fun encodeTopics(list: List<Topic>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    suspend fun setOnboardingDone(v: Boolean) { ds.edit { it[Keys.ONBOARDING_DONE] = v } }
    suspend fun setContentLanguage(v: String) { ds.edit { it[Keys.CONTENT_LANGUAGE] = v } }
    suspend fun setProvider(v: AiProvider) { ds.edit { it[Keys.PROVIDER] = v.id } }
    suspend fun setRefreshIntervalMinutes(v: Int) { ds.edit { it[Keys.REFRESH_INTERVAL_MIN] = v } }
    suspend fun setNotificationsEnabled(v: Boolean) { ds.edit { it[Keys.NOTIFICATIONS_ENABLED] = v } }
    suspend fun setPro(v: Boolean) { ds.edit { it[Keys.IS_PRO] = v } }
    suspend fun setActiveTopicId(v: String) { ds.edit { it[Keys.ACTIVE_TOPIC_ID] = v } }
    suspend fun setLastError(v: String) { ds.edit { it[Keys.LAST_ERROR] = v } }
    suspend fun setFetchingSince(v: Long) { ds.edit { it[Keys.FETCHING_SINCE] = v } }

    suspend fun getTopicsOnce(): List<Topic> = topics.first()

    suspend fun saveTopics(list: List<Topic>) {
        ds.edit { it[Keys.TOPICS_JSON] = encodeTopics(list) }
    }

    suspend fun upsertTopic(topic: Topic) {
        ds.edit { p ->
            val current = parseTopics(p[Keys.TOPICS_JSON]).toMutableList()
            val idx = current.indexOfFirst { it.id == topic.id }
            if (idx >= 0) current[idx] = topic else current.add(topic)
            p[Keys.TOPICS_JSON] = encodeTopics(current)
        }
    }

    /** Tek bir konuyu atomik olarak günceller (başka alanlara dokunmaz). */
    suspend fun updateTopic(id: String, transform: (Topic) -> Topic) {
        ds.edit { p ->
            val updated = parseTopics(p[Keys.TOPICS_JSON]).map { if (it.id == id) transform(it) else it }
            p[Keys.TOPICS_JSON] = encodeTopics(updated)
        }
    }

    suspend fun removeTopic(id: String) {
        ds.edit { p ->
            p[Keys.TOPICS_JSON] = encodeTopics(parseTopics(p[Keys.TOPICS_JSON]).filterNot { it.id == id })
        }
    }

    suspend fun setWidgetTheme(v: String) { ds.edit { it[Keys.WIDGET_THEME] = v } }
    suspend fun setQuietEnabled(v: Boolean) { ds.edit { it[Keys.QUIET_ENABLED] = v } }
    suspend fun setQuietStartMin(v: Int) { ds.edit { it[Keys.QUIET_START] = v } }
    suspend fun setQuietEndMin(v: Int) { ds.edit { it[Keys.QUIET_END] = v } }
    suspend fun setOptionCount(v: Int) { ds.edit { it[Keys.OPTION_COUNT] = v.coerceIn(2, 4) } }

    /** Widget'ın ve bildirimin gösterdiği konu. */
    suspend fun activeTopic(): Topic? {
        val list = getTopicsOnce()
        val id = activeTopicId.first()
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    /** Sessiz saatlerde miyiz? (bildirim verilmez, widget yine güncellenir) */
    suspend fun isQuietNow(): Boolean {
        if (!quietEnabled.first()) return false
        val s = quietStartMin.first()
        val e = quietEndMin.first()
        if (s == e) return false
        val c = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        return if (s < e) c in s until e else (c >= s || c < e)
    }

    /** İlk cevap sayılır; seri ve doğruluk güncellenir. */
    suspend fun recordAnswer(correct: Boolean) {
        ds.edit { p ->
            val st = Stats.parse(p[Keys.STATS_JSON])
            val today = java.time.LocalDate.now()
            val newStreak = when (st.lastDay) {
                today.toString() -> st.streak.coerceAtLeast(1)
                today.minusDays(1).toString() -> st.streak + 1
                else -> 1
            }
            val upd = st.copy(
                streak = newStreak,
                best = maxOf(st.best, newStreak),
                lastDay = today.toString(),
                answered = st.answered + 1,
                correct = st.correct + if (correct) 1 else 0
            )
            p[Keys.STATS_JSON] = upd.toJson()
        }
    }

    /** Kaydedilmişse çıkarır, değilse ekler. true = artık kayıtlı. */
    suspend fun toggleSaved(tip: SavedTip): Boolean {
        var nowSaved = false
        ds.edit { p ->
            val cur = parseSaved(p[Keys.SAVED_JSON]).toMutableList()
            val idx = cur.indexOfFirst { it.id == tip.id }
            if (idx >= 0) cur.removeAt(idx) else { cur.add(0, tip); nowSaved = true }
            p[Keys.SAVED_JSON] = encodeSaved(cur)
        }
        return nowSaved
    }

    suspend fun removeSaved(id: String) {
        ds.edit { p ->
            p[Keys.SAVED_JSON] = encodeSaved(parseSaved(p[Keys.SAVED_JSON]).filterNot { it.id == id })
        }
    }
}
