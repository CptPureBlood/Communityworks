package com.pingai.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.pingai.app.notification.NotificationHelper

class PingAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
        NotificationHelper.ensureChannel(this)
    }
}
