package com.kuranty.callmonitor

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Periodic watchdog that checks every 5 minutes if MonitorService is running.
 * If the service was killed by MIUI or the system, restarts it automatically.
 * Uses AlarmManager which survives app process death.
 */
class WatchdogReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WatchdogReceiver"
        private const val INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WatchdogReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + INTERVAL_MS,
                INTERVAL_MS,
                pendingIntent
            )
            Log.i(TAG, "Watchdog scheduled every 5 minutes")
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (!isServiceRunning(context, MonitorService::class.java)) {
            Log.w(TAG, "MonitorService not running — restarting")
            val serviceIntent = Intent(context, MonitorService::class.java).apply {
                action = MonitorService.ACTION_START
            }
            try {
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart MonitorService: ${e.message}")
            }
        } else {
            Log.d(TAG, "MonitorService is running — OK")
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }
}
