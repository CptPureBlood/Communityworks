package com.pingai.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pingai.app.core.TipService

class RefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val slot = inputData.getInt(WorkScheduler.KEY_SLOT, -1)
        TipService.refreshAndReschedule(
            applicationContext,
            notify = true,
            slot = if (slot >= 0) slot else null
        )
        return Result.success()
    }
}
