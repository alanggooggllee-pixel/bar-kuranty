package com.kuranty.callmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Foreground service that shows a floating overlay and plays
 * a pre-recorded announcement that the call is being recorded.
 * Uses speakerphone at moderate volume so both parties can hear it
 * without drowning out the conversation.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "com.kuranty.callmonitor.SHOW_OVERLAY"
        const val ACTION_DISMISS = "com.kuranty.callmonitor.DISMISS_OVERLAY"
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 1

        // Volume at 40% of max — audible but not overwhelming
        private const val ANNOUNCEMENT_VOLUME_PERCENT = 0.4f

        private const val OVERLAY_TEXT =
            "Идёт запись звонка"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var previousVolume: Int = 0
    private var previousMode: Int = AudioManager.MODE_NORMAL
    private var wasSpeakerOn: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                showOverlay()
                handler.postDelayed({ playAnnouncement() }, 1500)
            }
            ACTION_DISMISS -> dismissOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        dismissOverlay()
        releaseMediaPlayer()
        super.onDestroy()
    }

    private fun playAnnouncement() {
        // Disabled: playing audio during a call interferes with Cube ACR recording.
        // The caller cannot hear device-played audio without telephony-level integration.
        // Keeping overlay as visual reminder only.
        Log.i(TAG, "Overlay shown as visual reminder (audio announcement disabled)")
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaPlayer", e)
            }
            mediaPlayer = null
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val textView = TextView(this).apply {
            text = OVERLAY_TEXT
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(32, 24, 32, 24)
            gravity = Gravity.CENTER
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
        releaseMediaPlayer()
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
