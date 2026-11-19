package com.pingai.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API anahtarlarını telefonda şifreli saklar. Oluşturma ağır olduğu için tembel (lazy) başlar:
 * ilk erişimi arka plan thread'inde yap. Şifreli depo açılamazsa (bozuk anahtar vb.)
 * sessizce takılmak yerine düz depoya düşer; kullanıcı anahtarı yeniden girer.
 */
class SecureKeyStore(context: Context) {

    private val app = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                app,
                "ping_ai_secure_keys",
                MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            app.getSharedPreferences("ping_ai_keys_fallback", Context.MODE_PRIVATE)
        }
    }

    companion object {
        @Volatile private var instance: SecureKeyStore? = null

        fun get(context: Context): SecureKeyStore =
            instance ?: synchronized(this) {
                instance ?: SecureKeyStore(context.applicationContext).also { instance = it }
            }
    }

    fun getKey(provider: AiProvider): String? = prefs.getString(provider.id, null)

    fun setKey(provider: AiProvider, key: String) {
        prefs.edit().putString(provider.id, key.trim()).apply()
    }
}
