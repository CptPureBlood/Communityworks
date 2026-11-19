package com.pingai.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Kendi kendini zincirleyen tek seferlik işler (PeriodicWork en az 15 dk; bizde 10 dk seçeneği var).
 * İki yuva (a/b) kullanılır: çalışan iş, kendini iptal etmeden bir sonrakini diğer yuvaya kurar.
 */
object WorkScheduler {

    const val KEY_SLOT = "slot"
    private val CHAIN = listOf("ping_chain_a", "ping_chain_b")
    private const val MANUAL = "ping_manual"

    private val needNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** currentSlot == null: zinciri sıfırdan kur. Doluysa: çalışan işin yuvası, sonraki diğerine kurulur. */
    fun scheduleNext(context: Context, delayMinutes: Int, currentSlot: Int? = null) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (currentSlot == null) CHAIN.forEach { wm.cancelUniqueWork(it) }
        val nextSlot = if (currentSlot == null) 0 else 1 - currentSlot
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(needNetwork)
            .setInputData(workDataOf(KEY_SLOT to nextSlot))
            .build()
        wm.enqueueUniqueWork(CHAIN[nextSlot], ExistingWorkPolicy.REPLACE, request)
    }

    /** Widget'taki yenile düğmesi için: hemen çalışır (zincire dokunmaz, iş bitince zincir yenilenir). */
    fun runManual(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(needNetwork)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(KEY_SLOT to -1))
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP, request)
    }

    /** Zincir yoksa (ör. eski sürümden güncelleme) 1 dakika sonrasına kurar. */
    suspend fun ensureScheduled(context: Context) = withContext(Dispatchers.IO) {
        val wm = WorkManager.getInstance(context.applicationContext)
        var active = false
        for (name in CHAIN) {
            val infos = wm.getWorkInfosForUniqueWorkFlow(name).first()
            if (infos.any { !it.state.isFinished }) active = true
        }
        if (!active) scheduleNext(context, 1, null)
    }
}
