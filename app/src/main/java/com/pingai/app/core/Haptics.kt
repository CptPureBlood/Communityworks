package com.pingai.app.core

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

object Haptics {
    /** Doğru: tek kısa titreşim. Yanlış: çift titreşim. */
    fun answer(context: Context, correct: Boolean) {
        try {
            val vib = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
            val effect = if (correct) VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
            else VibrationEffect.createWaveform(longArrayOf(0, 70, 80, 70), -1)
            vib.vibrate(effect)
        } catch (_: Exception) {
        }
    }
}
