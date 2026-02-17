package com.kuranty.callmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
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
 * Foreground service that watches the MIUI call recording folder
 * for new audio files and triggers upload to Google Drive.
 */
class RecordingWatcher : Service() {

    companion object {
        private const val TAG = "RecordingWatcher"
        private const val CHANNEL_ID = "watcher_channel"
        private const val NOTIFICATION_ID = 2

        val DEFAULT_RECORDING_PATH: String =
            "${Environment.getExternalStorageDirectory()}/MIUI/sound_recorder/call_rec"
    }

    private var fileObserver: FileObserver? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startWatching()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fileObserver?.stopWatching()
        scope.cancel()
        super.onDestroy()
    }

    private fun startWatching() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val recordingPath = prefs.getString("recording_path", DEFAULT_RECORDING_PATH)
            ?: DEFAULT_RECORDING_PATH

        val dir = File(recordingPath)
        if (!dir.exists()) {
            Log.w(TAG, "Recording directory does not exist: $recordingPath")
            dir.mkdirs()
        }

        Log.i(TAG, "Watching directory: $recordingPath")

        fileObserver = object : FileObserver(dir, CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                if (!path.endsWith(".mp3") && !path.endsWith(".wav") &&
                    !path.endsWith(".m4a") && !path.endsWith(".aac")
                ) return

                val file = File(recordingPath, path)
                Log.i(TAG, "New recording detected: ${file.absolutePath}")
                uploadFile(file)
            }
        }

        fileObserver?.startWatching()

        // Also check for any existing files that haven't been uploaded
        checkExistingFiles(dir)
    }

    private fun checkExistingFiles(dir: File) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val uploadedSet = prefs.getStringSet("uploaded_files", emptySet()) ?: emptySet()

        dir.listFiles()?.filter { file ->
            file.isFile &&
            (file.name.endsWith(".mp3") || file.name.endsWith(".wav") ||
             file.name.endsWith(".m4a") || file.name.endsWith(".aac")) &&
            file.name !in uploadedSet
        }?.forEach { file ->
            Log.i(TAG, "Found unuploaded recording: ${file.name}")
            uploadFile(file)
        }
    }

    private fun uploadFile(file: File) {
        scope.launch {
            try {
                val uploader = DriveUploader(this@RecordingWatcher)
                uploader.uploadFile(file)

                // Mark as uploaded
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@RecordingWatcher)
                val uploaded = prefs.getStringSet("uploaded_files", mutableSetOf())
                    ?.toMutableSet() ?: mutableSetOf()
                uploaded.add(file.name)
                prefs.edit().putStringSet("uploaded_files", uploaded).apply()

                Log.i(TAG, "Uploaded: ${file.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed for ${file.name}", e)
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
