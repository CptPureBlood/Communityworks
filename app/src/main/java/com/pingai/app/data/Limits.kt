package com.pingai.app.data

/** Günlük sınırlar ve ödüller. */
object Limits {
    const val FREE_DAILY = 10
    const val PRO_DAILY = 500
    const val REWARD_ADS_PER_DAY = 5
    const val CREDITS_PER_AD = 2
    const val DAILY_GOAL = 5
}

/** Bugünün sayaçları (gün değişince sıfırlanır). */
data class Daily(val used: Int = 0, val credits: Int = 0, val adsWatched: Int = 0, val answered: Int = 0)
