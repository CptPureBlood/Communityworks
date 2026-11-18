package com.pingai.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClaudeClient : AiClient {

    companion object {
        private const val URL = "https://api.anthropic.com/v1/messages"
        // Model adı eskirse docs.claude.com'dan güncel adı alıp değiştir.
        private const val MODEL = "claude-haiku-5-5"
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(prompt: String, apiKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("model", MODEL)
                    put("max_tokens", 600)
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }))
                }
                val request = Request.Builder()
                    .url(URL)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", ANTHROPIC_VERSION)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Claude ${response.code}: $text"))
                    }
                    val result = JSONObject(text).optJSONArray("content")
                        ?.optJSONObject(0)?.optString("text")?.trim()
                    if (result.isNullOrBlank()) Result.failure(Exception("Empty Claude response"))
                    else Result.success(result)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
