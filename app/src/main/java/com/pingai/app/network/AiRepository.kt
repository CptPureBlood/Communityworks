package com.pingai.app.network

import com.pingai.app.data.AiProvider

class AiRepository {

    private val clients: Map<AiProvider, AiClient> = mapOf(
        AiProvider.GEMINI to GeminiClient(),
        AiProvider.OPENAI to OpenAiClient(),
        AiProvider.CLAUDE to ClaudeClient()
    )

    private fun languageName(code: String): String = when (code) {
        "tr" -> "Turkish"
        "en" -> "English"
        "de" -> "German"
        "fr" -> "French"
        "es" -> "Spanish"
        else -> "English"
    }

    suspend fun fetchShortTip(
        provider: AiProvider, apiKey: String, topic: String, contentLanguageCode: String
    ): Result<String> {
        val prompt = """
            You are a concise micro-tutor. Topic: "$topic".
            Give ONE piece of useful, memorable knowledge about this topic.
            STRICT RULES:
            - Write in ${languageName(contentLanguageCode)}.
            - Maximum 3 sentences total.
            - Be concrete and memorable (a fact, a rule, or a mnemonic), not generic fluff.
            - No title, no markdown, no bullet points.
            - Output only the tip itself.
        """.trimIndent()
        return clients.getValue(provider).generate(prompt, apiKey)
    }

    suspend fun fetchDetailedExplanation(
        provider: AiProvider, apiKey: String, topic: String, shortTip: String, contentLanguageCode: String
    ): Result<String> {
        val prompt = """
            You are a knowledgeable tutor. Topic: "$topic".
            Earlier you gave this short tip: "$shortTip"
            Now explain this tip and its topic in more depth so the reader truly understands it.
            STRICT RULES:
            - Write in ${languageName(contentLanguageCode)}.
            - Length: between 8 and 12 sentences.
            - Clear, educational, with concrete examples where useful.
            - No markdown, no bullet points, just flowing prose.
            - Output only the explanation itself.
        """.trimIndent()
        return clients.getValue(provider).generate(prompt, apiKey)
    }
}
