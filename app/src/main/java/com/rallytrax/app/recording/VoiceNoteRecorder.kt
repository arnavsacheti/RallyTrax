package com.rallytrax.app.recording

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/**
 * Simple [MediaRecorder] wrapper that captures short (max 10 s) voice clips
 * during a live recording session. Files are stored under
 * `cacheDir/voice_notes/{trackId}/note_{pointIndex}_{timestamp}.3gp`
 * so the track-detail screen can discover them by listing the directory.
 */
class VoiceNoteRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun startRecording(trackId: String, pointIndex: Int): File? {
        val dir = File(context.cacheDir, "voice_notes/$trackId")
        dir.mkdirs()
        val file = File(dir, "note_${pointIndex}_${System.currentTimeMillis()}.3gp")
        return try {
            @Suppress("DEPRECATION")
            recorder = (if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setMaxDuration(10_000) // 10 seconds max
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            currentFile = file
            file
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            null
        }
    }

    fun stopRecording(): String? {
        val r = recorder ?: return null
        return try {
            try {
                r.stop()
                currentFile?.absolutePath
            } catch (e: Exception) {
                null
            } finally {
                // release() must run even if stop() throws, and even if a
                // subsequent throw happens we still need recorder = null so a
                // re-start gets a fresh MediaRecorder instead of a half-dead one.
                try {
                    r.release()
                } catch (_: Exception) {
                    // ignore — we're tearing down anyway
                }
            }
        } finally {
            recorder = null
        }
    }

    fun isRecording(): Boolean = recorder != null
}
