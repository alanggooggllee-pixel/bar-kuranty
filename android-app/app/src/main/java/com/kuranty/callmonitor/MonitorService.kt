package com.kuranty.callmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager

/**
 * Persistent foreground service that manages call monitoring and recording watcher.
 * Lives independently of the Activity — survives activity destruction.
 */
class MonitorService : Service() {

    companion object {
        const val ACTION_START = "com.kuranty.callmonitor.START_MONITOR"
        const val ACTION_STOP = "com.kuranty.callmonitor.STOP_MONITOR"
        private const val TAG = "MonitorService"
        private const val CHANNEL_ID = "monitor_channel"
        private const val NOTIFICATION_ID = 3

        fun isRunning(context: android.content.Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean("monitoring_active", false)
        }
    }

    private var callStateListener: CallStateListener? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    private fun startMonitoring() {
        startForeground(NOTIFICATION_ID, buildNotification())

        // Register call state listener
        callStateListener = CallStateListener(this)
        callStateListener?.register()

        // Start recording watcher
        val watcherIntent = Intent(this, RecordingWatcher::class.java)
        startForegroundService(watcherIntent)

        // Persist state
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putBoolean("monitoring_active", true).apply()

        Log.i(TAG, "Monitoring started")
    }

    private fun stopMonitoring() {
        callStateListener?.unregister()
        callStateListener = null

        stopService(Intent(this, RecordingWatcher::class.java))
        stopService(Intent(this, OverlayService::class.java))

        PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putBoolean("monitoring_active", false).apply()

        Log.i(TAG, "Monitoring stopped")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Мониторинг звонков",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Постоянное уведомление о работе мониторинга"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Куранты — Мониторинг активен")
            .setContentText("Отслеживание звонков и запись")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
