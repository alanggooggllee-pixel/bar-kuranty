package com.kuranty.callmonitor

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import java.io.File
import java.io.FileInputStream

/**
 * Uploads audio files to a specified Google Drive folder
 * using a service account.
 */
class DriveUploader(private val context: Context) {

    companion object {
        private const val TAG = "DriveUploader"
        private const val APP_NAME = "KurantyCallMonitor"
    }

    private fun getDriveService(): Drive {
        val credentialsStream = context.assets.open("service_account.json")
        val credentials = GoogleCredentials.fromStream(credentialsStream)
            .createScoped(listOf("https://www.googleapis.com/auth/drive.file"))

        val transport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()

        return Drive.Builder(transport, jsonFactory, HttpCredentialsAdapter(credentials))
            .setApplicationName(APP_NAME)
            .build()
    }

    fun uploadFile(file: File) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val folderId = prefs.getString("drive_folder_id", null)
            ?: throw IllegalStateException("Google Drive folder ID not configured")

        val driveService = getDriveService()

        val mimeType = when {
            file.name.endsWith(".mp3") -> "audio/mpeg"
            file.name.endsWith(".wav") -> "audio/wav"
            file.name.endsWith(".m4a") -> "audio/mp4"
            file.name.endsWith(".aac") -> "audio/aac"
            else -> "audio/*"
        }

        val fileMetadata = DriveFile().apply {
            name = file.name
            parents = listOf(folderId)
        }

        val mediaContent = com.google.api.client.http.FileContent(mimeType, file)

        val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
            .setFields("id, name")
            .execute()

        Log.i(TAG, "File uploaded to Drive: ${uploadedFile.name} (id: ${uploadedFile.id})")
    }
}
