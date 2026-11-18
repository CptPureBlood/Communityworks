package com.pingai.app.data

import org.json.JSONObject

data class Topic(
    val id: String,
    val name: String,
    val shortInfo: String = "",
    val lastUpdatedEpochMs: Long = 0L,
    val nextUpdateEpochMs: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("shortInfo", shortInfo)
        put("lastUpdatedEpochMs", lastUpdatedEpochMs)
        put("nextUpdateEpochMs", nextUpdateEpochMs)
    }

    companion object {
        fun fromJson(obj: JSONObject): Topic = Topic(
            id = obj.getString("id"),
            name = obj.getString("name"),
            shortInfo = obj.optString("shortInfo", ""),
            lastUpdatedEpochMs = obj.optLong("lastUpdatedEpochMs", 0L),
            nextUpdateEpochMs = obj.optLong("nextUpdateEpochMs", 0L)
        )
    }
}

enum class AiProvider(val id: String, val displayLabel: String) {
    GEMINI("gemini", "Google Gemini"),
    OPENAI("openai", "ChatGPT (OpenAI)"),
    CLAUDE("claude", "Claude (Anthropic)");

    companion object {
        fun fromId(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

enum class RefreshInterval(val minutes: Int, val isPro: Boolean) {
    H12(720, false),
    H6(360, true),
    H4(240, true),
    H2(120, true),
    H1(60, true),
    M30(30, true),
    M15(15, true),
    M10(10, true)
}
