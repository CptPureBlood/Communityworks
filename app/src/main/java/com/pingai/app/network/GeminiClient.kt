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

/**
 * Google model adlarını sık değiştirir. Aşağıdaki listedeki modeller sırayla denenir;
 * biri bulunamazsa (404) veya kapatılmışsa bir sonrakine geçilir.
 * Güncel adlar: ai.google.dev/gemini-api/docs/deprecations
 */
class GeminiClient : AiClient {

    companion object {
        private val MODELS = listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-2.5-flash")
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(prompt: String, apiKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            var lastError: Exception = Exception("Gemini: no model available")
            for (model in MODELS) {
                try {
                    val body = JSONObject().apply {
                        put("contents", JSONArray().put(JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                        }))
                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.8)
                            put("maxOutputTokens", 1024)
                        })
                    }
                    val request = Request.Builder()
                        .url("$BASE_URL$model:generateContent")
                        .addHeader("x-goog-api-key", apiKey)
                        .post(body.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    http.newCall(request).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            val result = JSONObject(text).optJSONArray("candidates")
                                ?.optJSONObject(0)?.optJSONObject("content")
                                ?.optJSONArray("parts")?.optJSONObject(0)
                                ?.optString("text")?.trim()
                            if (!result.isNullOrBlank()) return@withContext Result.success(result)
                            lastError = Exception("Gemini $model: empty response")
                        } else {
                            val msg = runCatching {
                                JSONObject(text).getJSONObject("error").getString("message")
                            }.getOrDefault(text)
                            lastError = Exception("Gemini ${response.code} ($model): $msg")
                            // Anahtar hatası / kota: diğer modeller de aynı sonucu verir, hemen dön.
                            if (response.code == 400 || response.code == 401 ||
                                response.code == 403 || response.code == 429
                            ) return@withContext Result.failure(lastError)
                        }
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            Result.failure(lastError)
        }
}
