package com.pingai.app.network

import com.pingai.app.data.AiProvider
import java.util.Locale

object AiRepository {

    private val clients: Map<AiProvider, AiClient> = mapOf(
        AiProvider.GEMINI to GeminiClient(),
        AiProvider.OPENAI to OpenAiClient(),
        AiProvider.CLAUDE to ClaudeClient()
    )

    private fun languageName(code: String): String =
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }

    private fun clean(text: String): String = text.replace("**", "").trim()

    /** Widget ve bildirim için en fazla 3 cümlelik bilgi. */
    suspend fun shortTip(
        provider: AiProvider,
        apiKey: String,
        topic: String,
        languageCode: String,
        previousTip: String
    ): Result<String> {
        val avoid = if (previousTip.isBlank()) "" else
            "\n- Do NOT repeat this earlier tip, choose a different fact: \"${previousTip.take(300)}\""
        val prompt = """
            You are a concise micro-tutor. Topic: "$topic".
            Give ONE useful, memorable piece of knowledge about this topic.
            RULES:
            - Write in ${languageName(languageCode)}.
            - Maximum 3 sentences in total.
            - Be concrete and memorable (a fact, a rule, or a mnemonic), not generic fluff.
            - No title, no markdown, no bullet points.
            - Output only the tip itself.$avoid
        """.trimIndent()
        return clients.getValue(provider).generate(prompt, apiKey, 1024).map { clean(it) }
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
