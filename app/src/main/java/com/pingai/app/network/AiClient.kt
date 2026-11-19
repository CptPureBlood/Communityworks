package com.pingai.app.network

interface AiClient {
    suspend fun generate(prompt: String, apiKey: String, maxTokens: Int): Result<String>
}
