package com.kuranty.callmonitor

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.UserCredentials
import org.json.JSONObject
import java.io.File

/**
 * Uploads audio files to a specified Google Drive folder
 * using OAuth2 user credentials (refresh token).
 */
class DriveUploader(private val context: Context) {

    companion object {
        private const val TAG = "DriveUploader"
        private const val APP_NAME = "KurantyCallMonitor"
        private const val QUOTA_PROJECT = "gen-lang-client-0199868904"
    }

    private fun getDriveService(): Drive {
        val credentialsStream = context.assets.open("oauth_credentials.json")
        val json = JSONObject(credentialsStream.bufferedReader().readText())

        val credentials = UserCredentials.newBuilder()
            .setClientId(json.getString("client_id"))
            .setClientSecret(json.getString("client_secret"))
            .setRefreshToken(json.getString("refresh_token"))
            .setQuotaProjectId(QUOTA_PROJECT)
            .build()

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
            file.name.endsWith(".amr") -> "audio/amr"
            file.name.endsWith(".3gp") -> "audio/3gpp"
            file.name.endsWith(".ogg") -> "audio/ogg"
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
