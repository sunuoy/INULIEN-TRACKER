package com.example.data.api

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GoogleDriveService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"

    fun invalidateToken(context: Context, token: String) {
        try {
            val accountManager = android.accounts.AccountManager.get(context)
            accountManager.invalidateAuthToken("com.google", token)
            Log.d("GoogleDriveService", "Successfully invalidated cached auth token")
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "Error invalidating token: ${e.message}")
        }
    }

    /**
     * Searches for a file named "glucolog_backup.json" in the user's Google Drive.
     * Returns the file ID if found, otherwise null.
     */
    suspend fun findBackupFile(accessToken: String): String? {
        val url = "$DRIVE_FILES_URL?q=name='glucolog_backup.json' and trashed=false&fields=files(id, name)"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GoogleDriveService", "Find backup call unsuccessful: ${response.code} ${response.message}")
                    return null
                }
                val bodyStr = response.body?.string() ?: return null
                val json = JSONObject(bodyStr)
                val files = json.optJSONArray("files") ?: return null
                if (files.length() > 0) {
                    val fileObj = files.getJSONObject(0)
                    return fileObj.optString("id")
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "Error finding backup file in Drive: ${e.message}", e)
        }
        return null
    }

    /**
     * Uploads the backup JSON data to Google Drive.
     * If an existing file ID is provided, it updates that file; otherwise, it creates a new file.
     * Returns true if successful.
     */
    suspend fun uploadBackupFile(accessToken: String, jsonData: String, existingFileId: String?): Boolean {
        return if (existingFileId != null) {
            updateBackupFile(accessToken, jsonData, existingFileId)
        } else {
            createNewBackupFile(accessToken, jsonData)
        }
    }

    private fun createNewBackupFile(accessToken: String, jsonData: String): Boolean {
        // Multi-part or simple upload. Simple metadata-content upload using multipart approach:
        // Or simple: POST to upload URL with query parameters.
        val boundary = "BackupBoundaryXYZ"
        val mediaType = "multipart/related; boundary=$boundary".toMediaType()

        val metadata = JSONObject()
        metadata.put("name", "glucolog_backup.json")
        metadata.put("mimeType", "application/json")

        val requestBodyStr = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/json\r\n\r\n")
            append(jsonData)
            append("\r\n--$boundary--\r\n")
        }

        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_URL?uploadType=multipart")
            .header("Authorization", "Bearer $accessToken")
            .post(requestBodyStr.toRequestBody(mediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("GoogleDriveService", "Successfully created new glucolog_backup.json on Google Drive")
                    return true
                } else {
                    Log.e("GoogleDriveService", "Failed to create new file: ${response.code} ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "Error during file creation: ${e.message}", e)
        }
        return false
    }

    private fun updateBackupFile(accessToken: String, jsonData: String, fileId: String): Boolean {
        // PATCH call to update the file content on Google Drive
        val mediaType = "application/json".toMediaType()
        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_URL/$fileId?uploadType=media")
            .header("Authorization", "Bearer $accessToken")
            .patch(jsonData.toRequestBody(mediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("GoogleDriveService", "Successfully updated glucolog_backup.json with ID $fileId on Google Drive")
                    return true
                } else {
                    Log.e("GoogleDriveService", "Failed to update backup file: ${response.code} ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "Error during file update: ${e.message}", e)
        }
        return false
    }

    /**
     * Downloads the backup JSON string from Google Drive using the provided file ID.
     */
    suspend fun downloadBackupFile(accessToken: String, fileId: String): String? {
        val url = "$DRIVE_FILES_URL/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("GoogleDriveService", "Successfully downloaded file content")
                    return response.body?.string()
                } else {
                    Log.e("GoogleDriveService", "Failed to download backup file: ${response.code} ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveService", "Error downloading file from Drive: ${e.message}", e)
        }
        return null
    }
}
