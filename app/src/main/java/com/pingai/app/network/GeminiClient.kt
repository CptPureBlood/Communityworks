package com.pingai.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Model adlarını sabit yazmak yerine Google'a "bu anahtarla hangi modeller kullanılabilir?" diye
 * sorar (ListModels), en yeni Flash modelini seçer ve sırayla dener.
 */
class GeminiClient : AiClient {

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/"
        // Liste alınamazsa son çare:
        private val FALLBACK = listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-2.5-flash-lite")
        private val VERSION_RE = Regex("""^gemini-(\d+(?:\.\d+)?)-flash(-lite)?$""")
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun listCandidateModels(apiKey: String): List<String> {
        return try {
            val req = Request.Builder()
                .url("${BASE_URL}models?pageSize=200")
                .addHeader("x-goog-api-key", apiKey)
                .get().build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return emptyList()
                val arr = JSONObject(r.body?.string().orEmpty()).optJSONArray("models") ?: return emptyList()
                val found = mutableListOf<Triple<String, Double, Boolean>>() // ad, sürüm, lite mi
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val name = m.optString("name").removePrefix("models/")
                    val methods = m.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                    val canGenerate = (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                    val match = VERSION_RE.find(name) ?: continue
                    if (!canGenerate) continue
                    found += Triple(name, match.groupValues[1].toDouble(), match.groupValues[2].isNotEmpty())
                }
                // Önce lite olmayanlar, her grupta en yeni sürüm başta.
                found.sortedWith(compareBy<Triple<String, Double, Boolean>> { it.third }.thenByDescending { it.second })
                    .map { it.first }.take(4)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun friendly(code: Int, model: String, raw: String): String {
        val tr = Locale.getDefault().language == "tr"
        return when (code) {
            429 -> if (tr) "Gemini istek limiti doldu (429). Birazdan otomatik tekrar denenecek."
                   else "Gemini rate limit reached (429). Will retry automatically."
            503 -> if (tr) "Gemini şu an yoğun (503). Birazdan otomatik tekrar denenecek."
                   else "Gemini is busy (503). Will retry automatically."
            else -> "Gemini $code ($model): $raw"
        }
    }

    override suspend fun generate(prompt: String, apiKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            val models = (listCandidateModels(apiKey) + FALLBACK).distinct()
            var lastError: Exception = Exception("Gemini: no model available")
            for (model in models) {
                var attempt = 0
                while (attempt < 2) {
                    attempt++
                    var retrySameModel = false
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
                            .url("${BASE_URL}models/$model:generateContent")
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
                                val code = response.code
                                val raw = runCatching {
                                    JSONObject(text).getJSONObject("error").getString("message")
                                }.getOrDefault(text)
                                lastError = Exception(friendly(code, model, raw))
                                when (code) {
                                    // Anahtar/istek hatası: diğer modeller de aynı sonucu verir.
                                    400, 401, 403 -> return@withContext Result.failure(lastError)
                                    // Sunucu yoğun: kısa bekleyip aynı modeli bir kez daha dene.
                                    500, 503 -> retrySameModel = attempt < 2
                                    // 429 (kota) modele özeldir: sıradaki modele geç. 404 ve diğerleri de öyle.
                                    else -> {}
                                }
                            }
                        }
                    } catch (e: Exception) {
                        lastError = e
                    }
                    if (retrySameModel) delay(2500) else break
                }
            }
            Result.failure(lastError)
        }
}
