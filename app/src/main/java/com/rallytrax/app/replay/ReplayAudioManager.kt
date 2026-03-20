package com.rallytrax.app.replay

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Manages TTS playback of pace note call-outs with:
 * - Clipped, urgent cadence (pitch ≥ 1.1, rate ≥ 1.2)
 * - Chime earcon ~500ms before each TTS call
 * - Audio ducking for other media
 * - Mute toggle and volume control
 */
class ReplayAudioManager(
    private val context: Context,
) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var toneGenerator: ToneGenerator? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false

    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    private val _volume = MutableStateFlow(0.8f) // 0.0 to 1.0
    val volume = _volume.asStateFlow()

    private var pendingCallText: String? = null

    fun initialize(rate: Float = 1.25f, pitch: Float = 1.15f) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.apply {
                    language = Locale.US
                    setPitch(pitch)
                    setSpeechRate(rate)

                    setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            releaseAudioFocus()
                        }
                        override fun onError(utteranceId: String?) {
                            releaseAudioFocus()
                        }
                    })
                }
                ttsReady = true

                // If a call was queued before TTS was ready, play it now
                pendingCallText?.let { speak(it) }
                pendingCallText = null
            }
        }

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (_: Exception) {
            // ToneGenerator may fail on some devices
        }
    }

    fun speak(text: String) {
        if (_isMuted.value) return
        if (!ttsReady) {
            pendingCallText = text
            return
        }

        // Request audio focus with ducking
        requestAudioFocus()

        // Play chime first, then speak after ~500ms
        playChime()

        handler.postDelayed({
            val params = android.os.Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, _volume.value)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
        }, 500L)
    }

    fun speakFinish() {
        if (_isMuted.value) return
        requestAudioFocus()
        playChime()
        handler.postDelayed({
            val params = android.os.Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, _volume.value)
            tts?.speak("Finish!", TextToSpeech.QUEUE_FLUSH, params, "finish")
        }, 500L)
    }

    private fun playChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) {
            // Silently fail if tone can't play
        }
    }

    private fun requestAudioFocus() {
        if (hasFocus) return

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        hasFocus = false
                    }
                }
            }
            .build()

        val result = audioManager.requestAudioFocus(focusRequest!!)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun releaseAudioFocus() {
        focusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
        hasFocus = false
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        if (muted) {
            tts?.stop()
        }
    }

    fun setVolume(vol: Float) {
        _volume.value = vol.coerceIn(0f, 1f)
    }

    fun stop() {
        tts?.stop()
        releaseAudioFocus()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        toneGenerator?.release()
        toneGenerator = null
        releaseAudioFocus()
        handler.removeCallbacksAndMessages(null)
    }
}
