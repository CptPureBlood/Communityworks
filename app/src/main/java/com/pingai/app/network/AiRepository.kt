package com.pingai.app.network

import com.pingai.app.data.AiProvider
import org.json.JSONObject
import java.util.Locale

class BadFormatException(message: String) : Exception(message)

data class Quiz(
    val title: String,
    val question: String,
    /** Şıklar karıştırılmış; correctIdx doğru olanın yeri. */
    val options: List<String>,
    val correctIdx: Int,
    val info: String
)

object AiRepository {

    private val clients: Map<AiProvider, AiClient> = mapOf(
        AiProvider.GEMINI to GeminiClient(),
        AiProvider.OPENAI to OpenAiClient(),
        AiProvider.CLAUDE to ClaudeClient()
    )

    private fun languageName(code: String): String =
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }

    private fun clean(text: String): String = text.replace("**", "").trim()

    /**
     * TEK istekte hem soru hem bilgi. Kısa prompt, kısa JSON çıktı: az token.
     * Doğru şık modelden ayrı alınır, şıkları uygulama karıştırır (modeller hep aynı yere koyma eğiliminde).
     */
    suspend fun quiz(
        provider: AiProvider,
        apiKey: String,
        topic: String,
        languageCode: String,
        optionCount: Int,
        recent: List<String>,
        avoid: List<String>,
        liked: List<String>
    ): Result<Quiz> {
        val wrong = (optionCount - 1).coerceIn(1, 3)
        val extra = buildString {
            if (recent.isNotEmpty()) append("\nAlready used (pick something else): ${recent.take(6).joinToString("; ")}.")
            if (avoid.isNotEmpty()) append("\nUser disliked: ${avoid.take(4).joinToString("; ")}.")
            if (liked.isNotEmpty()) append("\nUser liked: ${liked.take(3).joinToString("; ")}.")
        }
        val prompt = """
            Topic: "$topic". Language: ${languageName(languageCode)}.
            Write one memorable fact and a quiz question about it. JSON only:
            {"t":"2-5 word title","q":"question, must not reveal the answer","c":"correct answer, max 5 words","w":[$wrong plausible wrong answers, max 5 words each],"i":"the fact in max 3 sentences, explains the answer"}$extra
        """.trimIndent()
        val raw = clients.getValue(provider).generate(prompt, apiKey, 1024)
        return raw.mapCatching { parseQuiz(it, wrong) }
    }

    private fun parseQuiz(text: String, wrongCount: Int): Quiz {
        val s = text.indexOf('{')
        val e = text.lastIndexOf('}')
        if (s < 0 || e <= s) throw BadFormatException("no json")
        val o = JSONObject(text.substring(s, e + 1))
        val q = clean(o.optString("q"))
        val c = clean(o.optString("c"))
        val info = clean(o.optString("i"))
        val arr = o.optJSONArray("w")
        val wrong = if (arr == null) emptyList() else
            (0 until arr.length()).map { clean(arr.optString(it)) }
                .filter { it.isNotBlank() && !it.equals(c, ignoreCase = true) }.distinct()
        if (q.isBlank() || c.isBlank() || info.isBlank() || wrong.isEmpty()) throw BadFormatException("incomplete")
        val options = (wrong.take(wrongCount) + c).shuffled()
        return Quiz(clean(o.optString("t")).ifBlank { q.take(30) }, q, options, options.indexOf(c), info)
    }

    /** Bildirime tıklanınca açılan 8-12 cümlelik detaylı anlatım. */
    suspend fun detailed(
        provider: AiProvider,
        apiKey: String,
        topic: String,
        shortTip: String,
        languageCode: String
    ): Result<String> {
        val prompt = """
            You are a knowledgeable tutor. Topic: "$topic".
            Earlier you gave this short tip: "$shortTip"
            Now explain this tip and its topic in more depth so the reader truly understands it.
            RULES:
            - Write in ${languageName(languageCode)}.
            - Length: between 8 and 12 sentences.
            - Clear and educational, with concrete examples where useful.
            - No markdown, no bullet points, just flowing prose.
            - Output only the explanation itself.
        """.trimIndent()
        return clients.getValue(provider).generate(prompt, apiKey, 2048).map { clean(it) }
    }
}
