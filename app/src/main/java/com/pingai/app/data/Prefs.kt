package com.pingai.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.dataStore by preferencesDataStore(name = "ping_ai_settings")

class Prefs(context: Context) {

    private val ds = context.applicationContext.dataStore

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val PROVIDER = stringPreferencesKey("provider")
        val TOPICS_JSON = stringPreferencesKey("topics_json")
        val ACTIVE_TOPIC_ID = stringPreferencesKey("active_topic_id")
        val REFRESH_INTERVAL_MIN = intPreferencesKey("refresh_interval_min")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val IS_PRO = booleanPreferencesKey("is_pro")
    }

    val onboardingDone: Flow<Boolean> = ds.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    val contentLanguage: Flow<String> = ds.data.map { it[Keys.CONTENT_LANGUAGE] ?: "system" }
    val provider: Flow<AiProvider> = ds.data.map { AiProvider.fromId(it[Keys.PROVIDER]) }
    val refreshIntervalMinutes: Flow<Int> =
        ds.data.map { it[Keys.REFRESH_INTERVAL_MIN] ?: RefreshInterval.H12.minutes }
    val notificationsEnabled: Flow<Boolean> = ds.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    val isPro: Flow<Boolean> = ds.data.map { it[Keys.IS_PRO] ?: false }
    val activeTopicId: Flow<String?> = ds.data.map { it[Keys.ACTIVE_TOPIC_ID] }

    val topics: Flow<List<Topic>> = ds.data.map { prefs ->
        val arr = JSONArray(prefs[Keys.TOPICS_JSON] ?: "[]")
        (0 until arr.length()).map { Topic.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun setOnboardingDone(v: Boolean) { ds.edit { it[Keys.ONBOARDING_DONE] = v } }
    suspend fun setContentLanguage(v: String) { ds.edit { it[Keys.CONTENT_LANGUAGE] = v } }
    suspend fun setProvider(v: AiProvider) { ds.edit { it[Keys.PROVIDER] = v.id } }
    suspend fun setRefreshIntervalMinutes(v: Int) { ds.edit { it[Keys.REFRESH_INTERVAL_MIN] = v } }
    suspend fun setNotificationsEnabled(v: Boolean) { ds.edit { it[Keys.NOTIFICATIONS_ENABLED] = v } }
    suspend fun setPro(v: Boolean) { ds.edit { it[Keys.IS_PRO] = v } }
    suspend fun setActiveTopicId(v: String) { ds.edit { it[Keys.ACTIVE_TOPIC_ID] = v } }

    suspend fun getTopicsOnce(): List<Topic> = topics.first()

    suspend fun saveTopics(list: List<Topic>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        ds.edit { it[Keys.TOPICS_JSON] = arr.toString() }
    }

    suspend fun upsertTopic(topic: Topic) {
        val current = getTopicsOnce().toMutableList()
        val idx = current.indexOfFirst { it.id == topic.id }
        if (idx >= 0) current[idx] = topic else current.add(topic)
        saveTopics(current)
    }

    suspend fun removeTopic(id: String) {
        saveTopics(getTopicsOnce().filterNot { it.id == id })
    }
}
