package com.pingai.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.pingai.app.ads.AdGate
import com.pingai.app.analytics.Track
import com.pingai.app.data.Prefs
import com.pingai.app.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PingAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        Track.init(this)
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch { Prefs(this@PingAiApplication).isPro.collect { AdGate.isPro = it } }
        scope.launch { Track.refreshLanguages(this@PingAiApplication) }
        // Reklam SDK'sı ana thread'i yavaşlatmasın diye arka planda başlatılır; hazır olunca reklamlar önceden yüklenir.
        Thread { MobileAds.initialize(this) { AdGate.preload(this) } }.start()
    }
}
