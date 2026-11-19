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

enum class AiProvider(val id: String, val displayLabel: String, val shortLabel: String) {
    GEMINI("gemini", "Google Gemini", "Gemini"),
    OPENAI("openai", "ChatGPT (OpenAI)", "ChatGPT"),
    CLAUDE("claude", "Claude (Anthropic)", "Claude");

    companion object {
        fun fromId(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

enum class RefreshInterval(val minutes: Int, val label: String, val isPro: Boolean) {
    H12(720, "12 h", false),
    H6(360, "6 h", true),
    H4(240, "4 h", true),
    H2(120, "2 h", true),
    H1(60, "1 h", true),
    M30(30, "30 min", true),
    M15(15, "15 min", true),
    M10(10, "10 min", true)
}
