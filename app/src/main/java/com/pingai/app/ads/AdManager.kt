package com.pingai.app.ads

/**
 * REKLAM KURULUMU: admob.google.com'da hesap aç, birimleri oluştur, App ID'yi AndroidManifest.xml'e,
 * birim ID'lerini aşağıya yapıştır. Şu an hepsi Google TEST ID'si (gerçek gelir getirmez).
 */
object AdManager {
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    /** Ana şalter: false yapılırsa uygulamada hiçbir reklam gösterilmez (tek satırla kapatma). */
    const val MASTER_SWITCH = true
}
