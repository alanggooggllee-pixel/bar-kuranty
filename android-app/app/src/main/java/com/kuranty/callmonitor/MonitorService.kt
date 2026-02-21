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
 * Always-on foreground service that manages call monitoring and recording watcher.
 * Cannot be stopped by the user — only by uninstalling the app.
 * Auto-starts on boot and restarts if killed by the system.
 */
class MonitorService : Service() {

    companion object {
        const val ACTION_START = "com.kuranty.callmonitor.START_MONITOR"
        private const val TAG = "MonitorService"
        private const val CHANNEL_ID = "monitor_channel"
        private const val NOTIFICATION_ID = 3
    }

    private var callStateListener: CallStateListener? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.w(TAG, "Service destroyed — scheduling restart")
        callStateListener?.unregister()
        callStateListener = null

        // Restart the service immediately
        val restartIntent = Intent(applicationContext, MonitorService::class.java).apply {
            action = ACTION_START
        }
        applicationContext.startForegroundService(restartIntent)

        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed — restarting service")
        val restartIntent = Intent(applicationContext, MonitorService::class.java).apply {
            action = ACTION_START
        }
        applicationContext.startForegroundService(restartIntent)
        super.onTaskRemoved(rootIntent)
    }

    private fun startMonitoring() {
        startForeground(NOTIFICATION_ID, buildNotification())

        // Register call state listener (if not already registered)
        if (callStateListener == null) {
            callStateListener = CallStateListener(this)
            callStateListener?.register()
        }

        // Start recording watcher
        val watcherIntent = Intent(this, RecordingWatcher::class.java)
        startForegroundService(watcherIntent)

        // Persist state
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putBoolean("monitoring_active", true).apply()

        Log.i(TAG, "Monitoring started")
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
