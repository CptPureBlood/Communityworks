package com.pingai.app.core

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** Uygulama arayüz dili (içerik dilinden bağımsız). "system" = telefon dili. */
object LocaleHelper {
    private const val PREF = "ping_ai_locale"
    private const val KEY = "app_language"

    fun saved(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "system") ?: "system"

    fun save(context: Context, lang: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply()
    }

    /** Seçili uygulama diline göre yapılandırılmış bir Context döndürür. */
    fun wrap(context: Context): Context {
        val lang = saved(context)
        if (lang == "system") return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(lang))
        return context.createConfigurationContext(config)
    }

    /** Telefonun gerçek dili (uygulama dili seçiminden etkilenmez). */
    fun deviceLanguage(): String =
        Resources.getSystem().configuration.locales.get(0).language

    /** "system" ise telefon dilini, değilse seçili içerik dilini döndürür. */
    fun resolveContentLanguage(raw: String): String =
        if (raw == "system") deviceLanguage() else raw
}
