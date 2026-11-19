package com.pingai.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

data class Topic(
    val id: String,
    val name: String,
    /** Bilgi metni (quiz cevaplandıktan sonra gösterilen kısım). */
    val shortInfo: String = "",
    val lastUpdatedEpochMs: Long = 0L,
    val nextUpdateEpochMs: Long = 0L,
    val title: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctIdx: Int = 0,
    val answeredIdx: Int = -1,
    /** 1 = beğendi, -1 = beğenmedi, 0 = yok */
    val feedback: Int = 0,
    val recent: List<String> = emptyList(),
    val avoid: List<String> = emptyList(),
    val liked: List<String> = emptyList(),
    /** Daha önce yanlış bilinen sorunun tekrarı (token harcamaz). */
    val review: Boolean = false
) {
    val hasTip: Boolean get() = shortInfo.isNotBlank()
    val hasQuiz: Boolean get() = question.isNotBlank() && options.size >= 2
    /** Soru açık ve henüz cevaplanmadı. */
    val awaitingAnswer: Boolean get() = hasTip && hasQuiz && answeredIdx < 0
    val tipId: String get() = "$id:$lastUpdatedEpochMs"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("shortInfo", shortInfo)
        put("lastUpdatedEpochMs", lastUpdatedEpochMs)
        put("nextUpdateEpochMs", nextUpdateEpochMs)
        put("title", title)
        put("question", question)
        put("options", JSONArray(options))
        put("correctIdx", correctIdx)
        put("answeredIdx", answeredIdx)
        put("feedback", feedback)
        put("recent", JSONArray(recent))
        put("avoid", JSONArray(avoid))
        put("liked", JSONArray(liked))
        put("review", review)
    }

    companion object {
        private fun strings(a: JSONArray?): List<String> =
            if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

        fun fromJson(obj: JSONObject): Topic = Topic(
            id = obj.getString("id"),
            name = obj.getString("name"),
            shortInfo = obj.optString("shortInfo", ""),
            lastUpdatedEpochMs = obj.optLong("lastUpdatedEpochMs", 0L),
            nextUpdateEpochMs = obj.optLong("nextUpdateEpochMs", 0L),
            title = obj.optString("title", ""),
            question = obj.optString("question", ""),
            options = strings(obj.optJSONArray("options")),
            correctIdx = obj.optInt("correctIdx", 0),
            answeredIdx = obj.optInt("answeredIdx", -1),
            feedback = obj.optInt("feedback", 0),
            recent = strings(obj.optJSONArray("recent")),
            avoid = strings(obj.optJSONArray("avoid")),
            liked = strings(obj.optJSONArray("liked")),
            review = obj.optBoolean("review", false)
        )
    }
}

data class SavedTip(
    val id: String,
    val topic: String,
    val question: String,
    val answer: String,
    val info: String,
    val savedAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("topic", topic); put("question", question)
        put("answer", answer); put("info", info); put("savedAt", savedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = SavedTip(
            o.getString("id"), o.optString("topic"), o.optString("question"),
            o.optString("answer"), o.optString("info"), o.optLong("savedAt")
        )
    }
}

data class MissedQ(
    val topicId: String,
    val topicName: String,
    val title: String,
    val question: String,
    val options: List<String>,
    val correctIdx: Int,
    val info: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("topicId", topicId); put("topicName", topicName); put("title", title)
        put("question", question); put("options", JSONArray(options))
        put("correctIdx", correctIdx); put("info", info)
    }

    companion object {
        fun fromJson(o: JSONObject): MissedQ {
            val a = o.optJSONArray("options")
            return MissedQ(
                o.optString("topicId"), o.optString("topicName"), o.optString("title"),
                o.optString("question"),
                if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) },
                o.optInt("correctIdx"), o.optString("info")
            )
        }
    }
}

data class Stats(
    val streak: Int = 0,
    val best: Int = 0,
    val lastDay: String = "",
    val answered: Int = 0,
    val correct: Int = 0,
    /** Seri korumanın kullanıldığı ay (yyyy-MM). */
    val freezeMonth: String = "",
    /** Üst üste doğru cevap sayısı. */
    val correctStreak: Int = 0,
    val bestCorrect: Int = 0
) {
    fun freezeAvailable(): Boolean = freezeMonth != YearMonth.now().toString()

    /** Dün/bugün cevap varsa seri sürer; iki gün önceyse ve koruma hakkı varsa yine sürer. */
    fun effectiveStreak(): Int {
        val today = LocalDate.now()
        return when {
            lastDay == today.toString() || lastDay == today.minusDays(1).toString() -> streak
            lastDay == today.minusDays(2).toString() && freezeAvailable() -> streak
            else -> 0
        }
    }

    val accuracyPercent: Int get() = if (answered == 0) 0 else (correct * 100) / answered

    fun toJson(): String = JSONObject().apply {
        put("streak", streak); put("best", best); put("lastDay", lastDay)
        put("answered", answered); put("correct", correct); put("freezeMonth", freezeMonth)
        put("correctStreak", correctStreak); put("bestCorrect", bestCorrect)
    }.toString()

    companion object {
        fun parse(raw: String?): Stats = try {
            if (raw.isNullOrBlank()) Stats() else JSONObject(raw).let {
                Stats(it.optInt("streak"), it.optInt("best"), it.optString("lastDay"),
                    it.optInt("answered"), it.optInt("correct"), it.optString("freezeMonth"),
                    it.optInt("correctStreak"), it.optInt("bestCorrect"))
            }
        } catch (e: Exception) { Stats() }
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
