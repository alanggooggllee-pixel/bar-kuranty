package com.kuranty.callmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restarts monitoring after device reboot if it was active before.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (MonitorService.isRunning(context)) {
                Log.i("BootReceiver", "Device booted, restarting monitoring")
                val serviceIntent = Intent(context, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_START
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
