package com.pingai.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ClaudeClient : AiClient {

    companion object {
        private const val URL = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-haiku-5-5"
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }

    override suspend fun generate(prompt: String, apiKey: String, maxTokens: Int): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("model", MODEL)
                    .put("max_tokens", maxTokens)
                    .put(
                        "messages",
                        JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
                    )
                val req = Request.Builder()
                    .url(URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val out = JSONObject(text).optJSONArray("content")
                            ?.optJSONObject(0)?.optString("text")?.trim()
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
