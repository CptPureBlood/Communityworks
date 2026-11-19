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

    val topics: Flow<List<Topic>> = ds.data.map { p -> parseTopics(p[Keys.TOPICS_JSON]) }

    private fun parseTopics(raw: String?): List<Topic> {
        val arr = JSONArray(raw ?: "[]")
        return (0 until arr.length()).map { Topic.fromJson(arr.getJSONObject(it)) }
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
}
