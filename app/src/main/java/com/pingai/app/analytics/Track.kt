package com.pingai.app.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.pingai.app.R
import com.pingai.app.core.LocaleHelper
import com.pingai.app.data.Prefs
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Anonim kullanım istatistikleri (Firebase Analytics). Yalnızca kategori düzeyinde olaylar gider:
 * kullanıcının yazdığı serbest konu metni, API anahtarı veya cevap içerikleri ASLA gönderilmez.
 * Ayarlar'daki anahtarla kapatılabilir.
 */
object Track {
    private const val SP = "ping_ai_flags"
    private const val KEY = "analytics_on"
    private var fa: FirebaseAnalytics? = null

    private val CATS = listOf(
        "history" to R.string.cat_history, "law" to R.string.cat_law, "fun_facts" to R.string.cat_fun_facts,
        "science" to R.string.cat_science, "geography" to R.string.cat_geography, "technology" to R.string.cat_technology,
        "health" to R.string.cat_health, "languages" to R.string.cat_languages, "maritime" to R.string.cat_maritime,
        "business" to R.string.cat_business, "space" to R.string.cat_space, "psychology" to R.string.cat_psychology
    )
    private val LOCALES = listOf("en", "tr", "de", "es", "fr", "pt", "ru")
    private var catMap: Map<String, String>? = null

    fun isOn(c: Context): Boolean =
        c.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun init(c: Context) {
        val app = c.applicationContext
        val f = FirebaseAnalytics.getInstance(app)
        fa = f
        f.setAnalyticsCollectionEnabled(isOn(app))
    }

    fun setOn(c: Context, on: Boolean) {
        val app = c.applicationContext
        app.getSharedPreferences(SP, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        (fa ?: FirebaseAnalytics.getInstance(app).also { fa = it }).setAnalyticsCollectionEnabled(on)
    }

    fun event(c: Context, name: String, vararg params: Pair<String, String>) {
        if (!isOn(c)) return
        val f = fa ?: return
        val b = Bundle()
        params.forEach { b.putString(it.first, it.second) }
        f.logEvent(name, b)
    }

    fun topicEvent(c: Context, name: String, topicName: String, vararg extra: Pair<String, String>) {
        if (!isOn(c)) return
        event(c, name, "category" to category(c, topicName), *extra)
    }

    /** Serbest metin konular "custom" olur; hazır kategoriler 7 dilde de tanınır. */
    fun category(c: Context, topicName: String): String {
        val map = catMap ?: buildMap<String, String> {
            val app = c.applicationContext
            for (tag in LOCALES) {
                val cfg = android.content.res.Configuration(app.resources.configuration)
                cfg.setLocale(Locale.forLanguageTag(tag))
                val loc = app.createConfigurationContext(cfg)
                CATS.forEach { (id, res) -> put(loc.getString(res).trim().lowercase(), id) }
            }
        }.also { catMap = it }
        return map[topicName.trim().lowercase()] ?: "custom"
    }

    fun setUiLang(c: Context, code: String) {
        val v = if (code == "system") LocaleHelper.deviceLanguage() else code
        fa?.setUserProperty("ui_lang", v.take(36))
    }

    /** Kullanılan arayüz ve içerik dilini kullanıcı özelliği olarak işler. */
    suspend fun refreshLanguages(c: Context) {
        val app = c.applicationContext
        val f = fa ?: return
        val ui = LocaleHelper.saved(app).let { if (it == "system") LocaleHelper.deviceLanguage() else it }
        val content = LocaleHelper.resolveContentLanguage(Prefs(app).contentLanguage.first())
        f.setUserProperty("ui_lang", ui.take(36))
        f.setUserProperty("content_lang", content.take(36))
    }
}
