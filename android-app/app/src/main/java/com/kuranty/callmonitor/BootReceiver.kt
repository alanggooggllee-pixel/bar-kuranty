package com.kuranty.callmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts monitoring services after device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Device booted, starting RecordingWatcher")
            val serviceIntent = Intent(context, RecordingWatcher::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
