package com.pingai.app.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.pingai.app.analytics.Track

enum class RewardResult { GRANTED, NOT_READY, DISMISSED }

/**
 * Tüm reklam kuralları tek yerde (yalnızca ana thread'den çağrılır):
 * - Pro'da hiç reklam yok. Ayarlardaki "Test reklamları" anahtarı veya MASTER_SWITCH ile kapanır.
 * - Açılıştan sonraki ilk 60 sn reklam yok; iki geçiş reklamı arası en az 4 dk; günde en fazla 8.
 * - Quiz sırasında reklam yok (reklam yalnızca kullanıcı bir işlem yaptıktan sonra, işlemin önünde çıkar).
 * - Ödüllü reklam kullanıcı isteğiyle çıkar, bu kurallara tabi değildir.
 */
object AdGate {
    private const val SP = "ping_ai_flags"
    private const val KEY_ON = "ads_on"
    private const val KEY_DAY = "ad_day"
    private const val KEY_CNT = "ad_cnt"
    private const val FIRST_QUIET_MS = 60_000L
    private const val MIN_GAP_MS = 4 * 60_000L
    private const val DAILY_CAP = 8
    private const val RETURN_AFTER_MS = 30 * 60_000L

    @Volatile var isPro: Boolean = false

    private val sessionStart = SystemClock.elapsedRealtime()
    private var lastShown = 0L
    private var leftAt = 0L
    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)

    fun userSwitch(c: Context): Boolean = sp(c).getBoolean(KEY_ON, true)
    fun setUserSwitch(c: Context, on: Boolean) {
        sp(c).edit().putBoolean(KEY_ON, on).apply()
        if (on) preload(c)
    }

    /** Reklam onayı (UMP/GDPR): onay gerekiyorsa verilmeden reklam istenmez. */
    fun consentOk(c: Context): Boolean =
        UserMessagingPlatform.getConsentInformation(c.applicationContext).canRequestAds()

    fun adsOn(c: Context): Boolean = AdManager.MASTER_SWITCH && !isPro && userSwitch(c) && consentOk(c)

    /** Açılışta onay bilgisini günceller; gerekiyorsa onay formunu gösterir. */
    fun requestConsent(a: Activity) {
        if (isPro || !AdManager.MASTER_SWITCH) return
        val info = UserMessagingPlatform.getConsentInformation(a)
        info.requestConsentInfoUpdate(a, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(a) { _ ->
                if (info.canRequestAds()) preload(a)
            }
        }, { _ -> if (info.canRequestAds()) preload(a) })
    }

    fun privacyOptionsRequired(c: Context): Boolean =
        UserMessagingPlatform.getConsentInformation(c.applicationContext).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(a: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(a) { _ -> if (consentOk(a)) preload(a) }
    }

    fun preload(c: Context) {
        if (!adsOn(c)) return
        val app = c.applicationContext
        if (interstitial == null && !interstitialLoading) {
            interstitialLoading = true
            InterstitialAd.load(app, AdManager.INTERSTITIAL_AD_UNIT_ID, AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad; interstitialLoading = false }
                    override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null; interstitialLoading = false }
                })
        }
        if (rewarded == null && !rewardedLoading) {
            rewardedLoading = true
            RewardedAd.load(app, AdManager.REWARDED_AD_UNIT_ID, AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) { rewarded = ad; rewardedLoading = false }
                    override fun onAdFailedToLoad(e: LoadAdError) { rewarded = null; rewardedLoading = false }
                })
        }
    }

    private fun todayCount(c: Context): Int {
        val today = java.time.LocalDate.now().toString()
        val p = sp(c)
        return if (p.getString(KEY_DAY, "") == today) p.getInt(KEY_CNT, 0) else 0
    }

    private fun bumpCount(c: Context) {
        val today = java.time.LocalDate.now().toString()
        sp(c).edit().putString(KEY_DAY, today).putInt(KEY_CNT, todayCount(c) + 1).apply()
    }

    private fun canShow(c: Context): Boolean {
        if (!adsOn(c)) return false
        val now = SystemClock.elapsedRealtime()
        if (now - sessionStart < FIRST_QUIET_MS) return false
        if (lastShown != 0L && now - lastShown < MIN_GAP_MS) return false
        return todayCount(c) < DAILY_CAP
    }

    /** Kurallar izin veriyorsa geçiş reklamını gösterir, kapanınca onDone çalışır; yoksa onDone hemen çalışır. */
    fun showThen(a: Activity, reason: String, category: String? = null, onDone: () -> Unit) {
        val ad = interstitial
        if (ad == null || !canShow(a)) {
            preload(a)
            onDone()
            return
        }
        interstitial = null
        lastShown = SystemClock.elapsedRealtime()
        bumpCount(a)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { preload(a); onDone() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { preload(a); onDone() }
        }
        Track.event(a, "ad_shown", "type" to "interstitial", "reason" to reason, "category" to (category ?: "none"))
        ad.show(a)
    }

    /** Ödüllü reklam. Reklamlar kapalıysa (test) ödül doğrudan verilir. */
    fun showRewarded(a: Activity, category: String? = null, onResult: (RewardResult) -> Unit) {
        if (!adsOn(a)) { onResult(RewardResult.GRANTED); return }
        val ad = rewarded
        if (ad == null) { preload(a); onResult(RewardResult.NOT_READY); return }
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(a)
                onResult(if (earned) RewardResult.GRANTED else RewardResult.DISMISSED)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { preload(a); onResult(RewardResult.NOT_READY) }
        }
        Track.event(a, "ad_shown", "type" to "rewarded", "reason" to "refresh", "category" to (category ?: "none"))
        ad.show(a) { earned = true }
    }

    /** Uygulamaya 30 dakikadan uzun aradan sonra dönülünce (kurallar uygunsa) tek reklam. */
    fun onStop() { leftAt = SystemClock.elapsedRealtime() }

    fun onStart(a: Activity) {
        val left = leftAt
        if (left != 0L && SystemClock.elapsedRealtime() - left >= RETURN_AFTER_MS) {
            showThen(a, "app_return") { }
        }
        leftAt = 0L
    }
}
