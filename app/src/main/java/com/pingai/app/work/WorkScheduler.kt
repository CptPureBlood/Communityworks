package com.pingai.app.work

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/** 10 dk seçeneği için kendi kendini zincirleyen OneTimeWorkRequest (Periodic min. 15 dk). */
object WorkScheduler {

    private const val UNIQUE_WORK_NAME = "ping_ai_refresh_work"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleNext(context: Context, delayMinutes: Int) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request
        )
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request
        )
    }
}
