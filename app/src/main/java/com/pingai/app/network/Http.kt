package com.pingai.app.network

import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** HTTP hatası (kod + sağlayıcının mesajı). Ağ hataları IOException olarak ayrı gelir. */
class AiException(val code: Int?, message: String, val model: String? = null) : Exception(message)

/** Kısa zaman aşımları: takılma yerine hızlı hata. */
internal val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(28, TimeUnit.SECONDS)
        .build()
}

internal fun errorMessage(body: String): String =
    runCatching { JSONObject(body).getJSONObject("error").getString("message") }
        .getOrDefault(body)
        .take(160)
