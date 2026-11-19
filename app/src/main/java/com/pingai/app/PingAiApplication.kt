package com.pingai.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.pingai.app.notification.NotificationHelper

class PingAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        // Reklam SDK'sı ana thread'i yavaşlatmasın diye arka planda başlatılır.
        Thread { MobileAds.initialize(this) }.start()
    }
}
