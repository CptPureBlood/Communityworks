package com.pingai.app.ui.common

import androidx.annotation.StringRes
import com.pingai.app.R
import com.pingai.app.data.Stats

data class Badge(val emoji: String, @StringRes val nameRes: Int, val earned: (Stats) -> Boolean)

val BADGES = listOf(
    Badge("🌱", R.string.badge_first) { it.answered >= 1 },
    Badge("🔥", R.string.badge_streak3) { it.best >= 3 },
    Badge("⚡", R.string.badge_streak7) { it.best >= 7 },
    Badge("👑", R.string.badge_streak30) { it.best >= 30 },
    Badge("🎯", R.string.badge_correct25) { it.correct >= 25 },
    Badge("🧠", R.string.badge_correct100) { it.correct >= 100 },
    Badge("✅", R.string.badge_cs10) { it.bestCorrect >= 10 },
    Badge("💥", R.string.badge_cs25) { it.bestCorrect >= 25 },
    Badge("🚀", R.string.badge_cs100) { it.bestCorrect >= 100 },
    Badge("📚", R.string.badge_answered50) { it.answered >= 50 },
    Badge("🏆", R.string.badge_answered100) { it.answered >= 100 },
    Badge("💎", R.string.badge_answered500) { it.answered >= 500 }
)
