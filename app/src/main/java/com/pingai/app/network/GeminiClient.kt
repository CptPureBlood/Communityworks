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

/** Model adı Google tarafından değişirse (404), MODEL'i aistudio.google.com'daki güncel adla değiştir. */
class GeminiClient : AiClient {

    companion object {
        private const val MODEL = "gemini-1.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(prompt: String, apiKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    }))
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.8)
                        put("maxOutputTokens", 600)
                    })
                }
                val request = Request.Builder()
                    .url("$BASE_URL$MODEL:generateContent")
                    .addHeader("x-goog-api-key", apiKey)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Gemini ${response.code}: $text"))
                    }
                    val result = JSONObject(text).optJSONArray("candidates")
                        ?.optJSONObject(0)?.optJSONObject("content")
                        ?.optJSONArray("parts")?.optJSONObject(0)
                        ?.optString("text")?.trim()
                    if (result.isNullOrBlank()) Result.failure(Exception("Empty Gemini response"))
                    else Result.success(result)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
