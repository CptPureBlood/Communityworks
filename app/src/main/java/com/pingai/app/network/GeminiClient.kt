package com.pingai.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Model adlarını sabit yazmak yerine Google'a "bu anahtarla hangi modeller var?" diye sorar.
 * Hızlı olduğu için önce -lite modelleri, sonra normal Flash modelleri denenir.
 * Çalışan model bellekte tutulur; sonraki istekler tek çağrıyla biter.
 */
class GeminiClient : AiClient {

    private sealed interface Outcome {
        class Ok(val text: String) : Outcome
        class Retry(val error: Exception) : Outcome
        class Fatal(val error: Exception) : Outcome
    }

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/"
        private val FALLBACK = listOf(
            "gemini-flash-lite-latest", "gemini-flash-latest", "gemini-2.5-flash-lite", "gemini-2.5-flash"
        )
        private val NAME_RE = Regex("""^gemini-(\d+(?:\.\d+)?)-flash(-lite)?$""")

        @Volatile
        private var cachedModel: String? = null
    }

    override suspend fun generate(prompt: String, apiKey: String, maxTokens: Int): Result<String> =
        withContext(Dispatchers.IO) {
            var last: Exception = AiException(null, "No Gemini model available")

            val cached = cachedModel
            if (cached != null) {
                when (val r = call(cached, prompt, apiKey, maxTokens)) {
                    is Outcome.Ok -> return@withContext Result.success(r.text)
                    is Outcome.Fatal -> return@withContext Result.failure<String>(r.error)
                    is Outcome.Retry -> {
                        last = r.error
                        cachedModel = null
                    }
                }
            }

            val candidates = (listModels(apiKey) + FALLBACK).distinct().filter { it != cached }.take(5)
            for (model in candidates) {
                when (val r = call(model, prompt, apiKey, maxTokens)) {
                    is Outcome.Ok -> {
                        cachedModel = model
                        return@withContext Result.success(r.text)
                    }
                    is Outcome.Fatal -> return@withContext Result.failure<String>(r.error)
                    is Outcome.Retry -> last = r.error
                }
            }
            Result.failure<String>(last)
        }

    private fun listModels(apiKey: String): List<String> = try {
        val req = Request.Builder()
            .url("${BASE}models?pageSize=200")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
        httpClient.newCall(req).execute().use { r ->
            if (!r.isSuccessful) {
                emptyList()
            } else {
                val arr = JSONObject(r.body?.string().orEmpty()).optJSONArray("models") ?: JSONArray()
                val found = ArrayList<Triple<String, Double, Boolean>>()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val name = m.optString("name").removePrefix("models/")
                    val match = NAME_RE.find(name) ?: continue
                    val methods = m.optJSONArray("supportedGenerationMethods") ?: continue
                    val canGenerate = (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                    if (!canGenerate) continue
                    found.add(Triple(name, match.groupValues[1].toDouble(), match.groupValues[2].isNotEmpty()))
                }
                // Önce lite (hızlı), her grupta en yeni sürüm başta.
                found.sortedWith(
                    compareByDescending<Triple<String, Double, Boolean>> { it.third }.thenByDescending { it.second }
                ).map { it.first }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun call(model: String, prompt: String, apiKey: String, maxTokens: Int): Outcome = try {
        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
            put(
                "generationConfig",
                JSONObject().put("temperature", 0.9).put("maxOutputTokens", maxTokens)
            )
        }
        val req = Request.Builder()
            .url("${BASE}models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val out = extractText(text)
                if (out != null) Outcome.Ok(out)
                else Outcome.Retry(AiException(null, "Empty response ($model)", model))
            } else {
                val ex = AiException(resp.code, errorMessage(text), model)
                // Anahtar/istek hatası tüm modellerde aynıdır: hemen bırak. 404/429/5xx: sıradaki modele geç.
                if (resp.code == 400 || resp.code == 401 || resp.code == 403) Outcome.Fatal(ex)
                else Outcome.Retry(ex)
            }
        }
    } catch (e: IOException) {
        Outcome.Fatal(e)
    } catch (e: Exception) {
        Outcome.Retry(e)
    }

    private fun extractText(json: String): String? {
        val parts = JSONObject(json).optJSONArray("candidates")
            ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
        return sb.toString().trim().ifEmpty { null }
    }
}
