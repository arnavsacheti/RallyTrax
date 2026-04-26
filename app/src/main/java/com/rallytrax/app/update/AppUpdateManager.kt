package com.rallytrax.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    DOWNLOADED,
    ERROR,
}

data class DownloadState(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Int = 0, // 0-100
    val errorMessage: String? = null,
)

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var currentDownloadId: Long = -1
    private var downloadedFile: File? = null
    private var receiver: BroadcastReceiver? = null

    private val downloadManager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun startDownload(url: String, versionName: String) {
        if (_state.value.status == DownloadStatus.DOWNLOADING) return

        // Clean up any previous download
        cleanup()

        val updatesDir = File(context.externalCacheDir, "updates").apply { mkdirs() }
        val fileName = "RallyTrax-v$versionName.apk"
        val destFile = File(updatesDir, fileName)
        if (destFile.exists()) destFile.delete()
        downloadedFile = destFile

        _state.value = DownloadState(status = DownloadStatus.DOWNLOADING, progress = 0)

        val request = DownloadManager.Request(url.toUri()).apply {
            setTitle("RallyTrax Update")
            setDescription("Downloading v$versionName")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationUri(Uri.fromFile(destFile))
        }

        currentDownloadId = downloadManager.enqueue(request)

        // Register completion receiver
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != currentDownloadId) return

                val query = DownloadManager.Query().setFilterById(currentDownloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    when (cursor.getInt(statusIdx)) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            _state.value = DownloadState(
                                status = DownloadStatus.DOWNLOADED,
                                progress = 100,
                            )
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = cursor.getInt(reasonIdx)
                            _state.value = DownloadState(
                                status = DownloadStatus.ERROR,
                                errorMessage = "Download failed (error $reason)",
                            )
                        }
                    }
                    cursor.close()
                }
                unregisterReceiver()
            }
        }

        // RECEIVER_EXPORTED is required (not RECEIVER_NOT_EXPORTED): the
        // DownloadManager.ACTION_DOWNLOAD_COMPLETE broadcast is sent by the
        // system but is NOT a protected broadcast, so on API 33+ a receiver
        // registered with NOT_EXPORTED would never fire. The receiver is
        // anonymous, registered dynamically, and unregistered as soon as the
        // download terminates (success or failure) — there's no manifest
        // entry, so the export only affects this transient registration.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            // Registration can fail under quota / SecurityException edge
            // cases. Drop the field reference and surface the error so we
            // don't leave a dangling BroadcastReceiver that the next
            // startDownload would clobber.
            receiver = null
            _state.value = DownloadState(
                status = DownloadStatus.ERROR,
                errorMessage = "Couldn't register download receiver: ${e.message}",
            )
        }
    }

    /**
     * Polls DownloadManager for progress updates.
     * Call this from a coroutine that loops while status == DOWNLOADING.
     */
    suspend fun pollProgress() {
        if (currentDownloadId == -1L) return
        withContext(Dispatchers.IO) {
            while (_state.value.status == DownloadStatus.DOWNLOADING) {
                val query = DownloadManager.Query().setFilterById(currentDownloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    if (bytesIdx >= 0 && totalIdx >= 0) {
                        val downloaded = cursor.getLong(bytesIdx)
                        val total = cursor.getLong(totalIdx)
                        if (total > 0) {
                            val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            _state.value = _state.value.copy(progress = pct)
                        }
                    }
                    cursor.close()
                }
                delay(300)
            }
        }
    }

    /**
     * Launch the system package installer for the downloaded APK.
     */
    fun installUpdate(activityContext: Context) {
        val file = downloadedFile ?: return
        if (!file.exists()) {
            _state.value = DownloadState(
                status = DownloadStatus.ERROR,
                errorMessage = "Downloaded file not found",
            )
            return
        }

        val uri = FileProvider.getUriForFile(
            activityContext,
            "${activityContext.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        activityContext.startActivity(intent)
    }

    fun reset() {
        cleanup()
        _state.value = DownloadState()
    }

    private fun cleanup() {
        if (currentDownloadId != -1L) {
            try {
                downloadManager.remove(currentDownloadId)
            } catch (_: Exception) {
            }
            currentDownloadId = -1
        }
        unregisterReceiver()
        downloadedFile?.let {
            if (it.exists()) it.delete()
        }
        downloadedFile = null
    }

    private fun unregisterReceiver() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        receiver = null
    }
}
