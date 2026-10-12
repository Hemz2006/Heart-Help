package com.hearthelp.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hearthelp.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (context.app.prefs.monitoringEnabled.first()) {
                    MonitoringService.send(context, MonitoringService.ACTION_START)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
