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

class OpenAiClient : AiClient {

    companion object {
        private const val URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o-mini"
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
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }))
                    put("temperature", 0.8)
                    put("max_tokens", 600)
                }
                val request = Request.Builder()
                    .url(URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("OpenAI ${response.code}: $text"))
                    }
                    val result = JSONObject(text).optJSONArray("choices")
                        ?.optJSONObject(0)?.optJSONObject("message")
                        ?.optString("content")?.trim()
                    if (result.isNullOrBlank()) Result.failure(Exception("Empty OpenAI response"))
                    else Result.success(result)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
