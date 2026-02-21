package com.kuranty.callmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Always restarts monitoring after device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Device booted — starting monitoring")
            val serviceIntent = Intent(context, MonitorService::class.java).apply {
                action = MonitorService.ACTION_START
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
