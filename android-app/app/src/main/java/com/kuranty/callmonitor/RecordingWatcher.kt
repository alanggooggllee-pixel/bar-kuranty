package com.kuranty.callmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that watches the call recording folder
 * for new audio files and triggers upload to Google Drive.
 * Uses both FileObserver and periodic polling for reliability.
 */
class RecordingWatcher : Service() {

    companion object {
        private const val TAG = "RecordingWatcher"
        private const val CHANNEL_ID = "watcher_channel"
        private const val NOTIFICATION_ID = 2
        private const val POLL_INTERVAL_MS = 15_000L // 15 seconds

        val DEFAULT_RECORDING_PATH: String =
            "${Environment.getExternalStorageDirectory()}/Documents/CubeCallRecorder/All"
    }

    private var fileObserver: FileObserver? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var recordingPath: String = DEFAULT_RECORDING_PATH

    private val pollRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "Polling for new recordings...")
            val dir = File(recordingPath)
            if (dir.exists()) {
                checkExistingFiles(dir)
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startWatching()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        fileObserver?.stopWatching()
        scope.cancel()
        super.onDestroy()
    }

    private fun startWatching() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        recordingPath = prefs.getString("recording_path", DEFAULT_RECORDING_PATH)
            ?: DEFAULT_RECORDING_PATH

        val dir = File(recordingPath)
        if (!dir.exists()) {
            Log.w(TAG, "Recording directory does not exist: $recordingPath")
            dir.mkdirs()
        }

        Log.i(TAG, "Watching directory: $recordingPath")

        // FileObserver for instant detection
        fileObserver = object : FileObserver(dir, CLOSE_WRITE or MOVED_TO or CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                if (!isAudioFile(path)) return

                val file = File(recordingPath, path)
                Log.i(TAG, "FileObserver detected: ${file.absolutePath} (event=$event)")

                // Delay slightly to ensure file is fully written
                handler.postDelayed({
                    if (file.exists() && file.length() > 0) {
                        uploadFileIfNew(file)
                    }
                }, 3000)
            }
        }

        fileObserver?.startWatching()

        // Check existing files immediately
        checkExistingFiles(dir)

        // Start periodic polling as backup
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    private fun isAudioFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".wav") ||
               lower.endsWith(".m4a") || lower.endsWith(".aac") ||
               lower.endsWith(".amr") || lower.endsWith(".3gp") ||
               lower.endsWith(".ogg")
    }

    private fun checkExistingFiles(dir: File) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val uploadedSet = prefs.getStringSet("uploaded_files", emptySet()) ?: emptySet()

        val files = dir.listFiles()
        Log.d(TAG, "Directory ${dir.absolutePath} contains ${files?.size ?: 0} files")

        files?.filter { file ->
            file.isFile && isAudioFile(file.name) && file.name !in uploadedSet && file.length() > 0
        }?.forEach { file ->
            Log.i(TAG, "Found unuploaded recording: ${file.name} (${file.length()} bytes)")
            uploadFileIfNew(file)
        }
    }

    private fun uploadFileIfNew(file: File) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val uploadedSet = prefs.getStringSet("uploaded_files", emptySet()) ?: emptySet()

        if (file.name in uploadedSet) return

        scope.launch {
            try {
                Log.i(TAG, "Uploading: ${file.name} (${file.length()} bytes)")
                val uploader = DriveUploader(this@RecordingWatcher)
                uploader.uploadFile(file)

                // Mark as uploaded
                val currentUploaded = PreferenceManager.getDefaultSharedPreferences(this@RecordingWatcher)
                    .getStringSet("uploaded_files", mutableSetOf())
                    ?.toMutableSet() ?: mutableSetOf()
                currentUploaded.add(file.name)
                PreferenceManager.getDefaultSharedPreferences(this@RecordingWatcher)
                    .edit().putStringSet("uploaded_files", currentUploaded).apply()

                Log.i(TAG, "Successfully uploaded: ${file.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed for ${file.name}: ${e.message}", e)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Мониторинг записей",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Куранты — Мониторинг записей")
            .setContentText("Отслеживание новых записей звонков")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .build()
    }
}
