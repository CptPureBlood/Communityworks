package com.pingai.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** API anahtarlarını telefonda şifreli saklar. */
class SecureKeyStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        "ping_ai_secure_keys",
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getKey(provider: AiProvider): String? = prefs.getString(provider.id, null)

    fun setKey(provider: AiProvider, key: String) {
        prefs.edit().putString(provider.id, key.trim()).apply()
    }
}
