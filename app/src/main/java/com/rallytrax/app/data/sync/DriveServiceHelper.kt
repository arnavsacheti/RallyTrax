package com.rallytrax.app.data.sync

import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class DriveServiceHelper(credential: GoogleAccountCredential) {

    private val driveService: Drive = Drive.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance(),
        credential,
    )
        .setApplicationName("RallyTrax")
        .build()

    suspend fun getOrCreateManifest(): SyncManifest = withContext(Dispatchers.IO) {
        retryWithBackoff {
            val existingFile = findFile(MANIFEST_FILENAME)
            if (existingFile != null) {
                val content = downloadFileContent(existingFile.id)
                SyncManifest.fromJson(content)
            } else {
                val manifest = SyncManifest()
                uploadNewFile(MANIFEST_FILENAME, manifest.toJson(), MIME_JSON)
                manifest
            }
        }
    }

    suspend fun uploadManifest(manifest: SyncManifest) = withContext(Dispatchers.IO) {
        retryWithBackoff {
            val existingFile = findFile(MANIFEST_FILENAME)
            val content = manifest.toJson()
            if (existingFile != null) {
                updateFileContent(existingFile.id, content, MIME_JSON)
            } else {
                uploadNewFile(MANIFEST_FILENAME, content, MIME_JSON)
            }
        }
    }

    suspend fun uploadSettings(json: String, manifest: SyncManifest): SyncManifest =
        withContext(Dispatchers.IO) {
            retryWithBackoff {
                val md5 = md5Hash(json)
                val fileId = if (manifest.settingsFileId != null) {
                    updateFileContent(manifest.settingsFileId, json, MIME_JSON)
                    manifest.settingsFileId
                } else {
                    val file = uploadNewFile(SETTINGS_FILENAME, json, MIME_JSON)
                    file.id
                }
                manifest.copy(
                    settingsFileId = fileId,
                    settingsMd5 = md5,
                    settingsModifiedAt = System.currentTimeMillis(),
                )
            }
        }

    suspend fun downloadSettings(manifest: SyncManifest): String? =
        withContext(Dispatchers.IO) {
            val fileId = manifest.settingsFileId ?: return@withContext null
            retryWithBackoff {
                downloadFileContent(fileId)
            }
        }

    suspend fun uploadGarageData(json: String, manifest: SyncManifest): SyncManifest =
        withContext(Dispatchers.IO) {
            retryWithBackoff {
                val md5 = md5Hash(json)
                val fileId = if (manifest.garageFileId != null) {
                    updateFileContent(manifest.garageFileId, json, MIME_JSON)
                    manifest.garageFileId
                } else {
                    val file = uploadNewFile(GARAGE_FILENAME, json, MIME_JSON)
                    file.id
                }
                manifest.copy(
                    garageFileId = fileId,
                    garageMd5 = md5,
                    garageModifiedAt = System.currentTimeMillis(),
                )
            }
        }

    suspend fun downloadGarageData(manifest: SyncManifest): String? =
        withContext(Dispatchers.IO) {
            val fileId = manifest.garageFileId ?: return@withContext null
            retryWithBackoff {
                downloadFileContent(fileId)
            }
        }

    private fun findFile(name: String): File? {
        val result = driveService.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$name' and trashed = false")
            .setFields("files(id, name, md5Checksum)")
            .setPageSize(1)
            .execute()
        return result.files?.firstOrNull()
    }

    private fun downloadFileContent(fileId: String): String {
        val outputStream = ByteArrayOutputStream()
        driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        return outputStream.toString(Charsets.UTF_8.name())
    }

    private fun uploadNewFile(name: String, content: String, mimeType: String): File {
        val metadata = File().apply {
            this.name = name
            this.parents = listOf("appDataFolder")
        }
        val mediaContent = ByteArrayContent(mimeType, content.toByteArray(Charsets.UTF_8))
        return driveService.files().create(metadata, mediaContent)
            .setFields("id")
            .execute()
    }

    private fun updateFileContent(fileId: String, content: String, mimeType: String) {
        val mediaContent = ByteArrayContent(mimeType, content.toByteArray(Charsets.UTF_8))
        driveService.files().update(fileId, null, mediaContent).execute()
    }

    companion object {
        private const val TAG = "DriveServiceHelper"
        private const val MANIFEST_FILENAME = "manifest.json"
        private const val SETTINGS_FILENAME = "settings.json"
        private const val GARAGE_FILENAME = "garage.json"
        private const val MIME_JSON = "application/json"

        fun md5Hash(input: String): String {
            val digest = MessageDigest.getInstance("MD5")
            val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        private suspend fun <T> retryWithBackoff(
            maxAttempts: Int = 3,
            initialDelayMs: Long = 1000L,
            block: () -> T,
        ): T {
            var lastException: Exception? = null
            var delayMs = initialDelayMs
            repeat(maxAttempts) { attempt ->
                try {
                    return block()
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "Attempt ${attempt + 1}/$maxAttempts failed", e)
                    if (attempt < maxAttempts - 1) {
                        delay(delayMs)
                        delayMs *= 2
                    }
                }
            }
            throw lastException!!
        }
    }
}
