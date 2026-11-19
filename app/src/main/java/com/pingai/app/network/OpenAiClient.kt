package com.pingai.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenAiClient : AiClient {

    companion object {
        private const val URL = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o-mini"
    }

    override suspend fun generate(prompt: String, apiKey: String, maxTokens: Int): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("model", MODEL)
                    .put(
                        "messages",
                        JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
                    )
                    .put("temperature", 0.9)
                    .put("max_tokens", maxTokens)
                val req = Request.Builder()
                    .url(URL)
                    .header("Authorization", "Bearer $apiKey")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val out = JSONObject(text).optJSONArray("choices")
                            ?.optJSONObject(0)?.optJSONObject("message")
                            ?.optString("content")?.trim()
                        if (out.isNullOrBlank()) Result.failure<String>(AiException(null, "Empty response"))
                        else Result.success(out)
                    } else {
                        Result.failure<String>(AiException(resp.code, errorMessage(text), MODEL))
                    }
                }
            } catch (e: Exception) {
                Result.failure<String>(e)
            }
        }
}
