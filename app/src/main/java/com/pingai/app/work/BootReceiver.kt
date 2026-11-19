package com.pingai.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pingai.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (Prefs(context).onboardingDone.first()) {
                    WorkScheduler.scheduleNext(context, 1, null)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
