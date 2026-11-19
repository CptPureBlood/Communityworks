package com.pingai.app.network

import android.content.Context
import com.pingai.app.data.Prefs
import com.pingai.app.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** GitHub'daki son sürüme bakar. Repo gizliyse (ya da ağ yoksa) sessizce hiçbir şey yapmaz. */
object UpdateChecker {

    private const val URL = "https://api.github.com/repos/CptPureBlood/Communityworks/releases/latest"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    fun installedVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (e: Exception) { "0" }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** notify=true: yeni sürüm bulunursa (bir kez) bildirim gösterir. */
    suspend fun check(context: Context, notify: Boolean, force: Boolean = false) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.updateLastCheck.first() < CHECK_EVERY_MS) return
        try {
            val body = withContext(Dispatchers.IO) {
                val req = Request.Builder().url(URL).header("Accept", "application/vnd.github+json").build()
                httpClient.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            } ?: return
            prefs.setUpdateLastCheck(now)
            val o = JSONObject(body)
            val label = o.optString("name").ifBlank { o.optString("tag_name") }
            val version = Regex("""v(\d+(?:\.\d+)*)""").find(label)?.groupValues?.get(1) ?: return
            val url = o.optString("html_url")
            if (isNewer(version, installedVersion(app))) {
                prefs.setUpdateInfo(version, url)
                if (notify && prefs.updateNotified.first() != version &&
                    prefs.notificationsEnabled.first() && !prefs.isQuietNow()
                ) {
                    NotificationHelper.showUpdateNotification(app, version, url)
                    prefs.setUpdateNotified(version)
                }
            } else {
                prefs.setUpdateInfo("", "")
            }
        } catch (_: Exception) {
        }
    }
}
