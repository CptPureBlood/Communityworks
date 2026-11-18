package com.pingai.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.pingai.app.data.Prefs
import com.pingai.app.work.WorkScheduler
import kotlinx.coroutines.flow.first

class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (Prefs(context).isPro.first()) WorkScheduler.runNow(context)
    }
}
