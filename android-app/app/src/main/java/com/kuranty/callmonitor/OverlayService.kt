package com.kuranty.callmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager

/**
 * Foreground service that shows a floating overlay reminder
 * when a phone call begins, reminding the hostess to announce
 * that the call is being recorded.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "com.kuranty.callmonitor.SHOW_OVERLAY"
        const val ACTION_DISMISS = "com.kuranty.callmonitor.DISMISS_OVERLAY"
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 1

        private const val DEFAULT_REMINDER_TEXT =
            "⚠️ Напомните гостю:\n«Звонок записывается для улучшения качества обслуживания»"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_DISMISS -> dismissOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        dismissOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val reminderText = prefs.getString("reminder_text", DEFAULT_REMINDER_TEXT)
            ?: DEFAULT_REMINDER_TEXT

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val textView = TextView(this).apply {
            text = reminderText
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(32, 24, 32, 24)
        }

        overlayView = LinearLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC1565C0"))
            gravity = Gravity.CENTER
            addView(textView)
            setOnClickListener { dismissOverlay() }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 100
        }

        try {
            windowManager?.addView(overlayView, params)
            Log.i(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    private fun dismissOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove overlay", e)
            }
            overlayView = null
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Напоминание о записи звонка",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Куранты — Мониторинг звонков")
            .setContentText("Отслеживание звонков активно")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .build()
    }
}
