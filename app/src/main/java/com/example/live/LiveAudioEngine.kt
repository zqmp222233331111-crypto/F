package com.example.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

enum class AudioRoute {
    EARPIECE,
    SPEAKER,
    BLUETOOTH
}

/**
 * Handles real-time 16 kHz mono PCM input (with AEC + NS) and 24 kHz mono PCM output
 * with instant queue clearing on server interruption and audio route switching.
 */
class LiveAudioEngine(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var audioTrack: AudioTrack? = null

    private var recordJob: Job? = null
    private var playbackJob: Job? = null
    private var pcmQueue = Channel<ByteArray>(capacity = Channel.UNLIMITED)

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    private val _speakerAmplitude = MutableStateFlow(0f)
    val speakerAmplitude: StateFlow<Float> = _speakerAmplitude.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _audioRoute = MutableStateFlow(AudioRoute.SPEAKER)
    val audioRoute: StateFlow<AudioRoute> = _audioRoute.asStateFlow()

    @Volatile
    private var isPlayingOutput = false

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onPcmCaptured: (ByteArray) -> Unit,
        onPlaybackActiveChanged: (Boolean) -> Unit
    ) {
        stop()
        setupAudioFocusAndRoute(AudioRoute.SPEAKER)

        // Output: 24 kHz, 16-bit PCM mono
        val outBufSize = maxOf(
            AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ),
            4800
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(outBufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        audioTrack = track

        pcmQueue = Channel(capacity = Channel.UNLIMITED)
        playbackJob = scope.launch(Dispatchers.IO) {
            for (chunk in pcmQueue) {
                if (!isActive) break
                if (!isPlayingOutput) {
                    isPlayingOutput = true
                    onPlaybackActiveChanged(true)
                }
                _speakerAmplitude.value = computeRmsNormalized(chunk)
                try {
                    track.write(chunk, 0, chunk.size)
                } catch (_: Exception) {
                }
                if (pcmQueue.isEmpty) {
                    _speakerAmplitude.value = 0f
                }
            }
            _speakerAmplitude.value = 0f
            isPlayingOutput = false
            onPlaybackActiveChanged(false)
        }

        // Input: 16 kHz, 16-bit PCM mono
        val minInBuf = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val inBufSize = maxOf(minInBuf * 2, 3200)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            inBufSize
        )

        if (recorder.state == AudioRecord.STATE_INITIALIZED) {
            val sessionId = recorder.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
            recorder.startRecording()
            audioRecord = recorder

            recordJob = scope.launch(Dispatchers.IO) {
                val frameBytes = ByteArray(1280) // 40ms @ 16kHz 16-bit mono
                while (isActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = recorder.read(frameBytes, 0, frameBytes.size)
                    if (read > 0) {
                        if (_isMuted.value) {
                            _micAmplitude.value = 0f
                        } else {
                            val slice = frameBytes.copyOf(read)
                            _micAmplitude.value = computeRmsNormalized(slice)
                            onPcmCaptured(slice)
                        }
                    }
                }
            }
        }
    }

    fun enqueueOutputPcm(pcm24kBytes: ByteArray) {
        pcmQueue.trySend(pcm24kBytes)
    }

    /**
     * Immediately halts current playback and clears queued audio frames when server reports interruption.
     */
    fun clearPlaybackQueue() {
        while (pcmQueue.tryReceive().isSuccess) {
            // Drain all pending chunks
        }
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (_: Exception) {
        }
        isPlayingOutput = false
        _speakerAmplitude.value = 0f
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        if (muted) _micAmplitude.value = 0f
    }

    fun toggleMute(): Boolean {
        val next = !_isMuted.value
        setMuted(next)
        return next
    }

    fun cycleAudioRoute(): AudioRoute {
        val hasBluetooth = hasBluetoothAudioDevice()
        val next = when (_audioRoute.value) {
            AudioRoute.SPEAKER -> if (hasBluetooth) AudioRoute.BLUETOOTH else AudioRoute.EARPIECE
            AudioRoute.BLUETOOTH -> AudioRoute.EARPIECE
            AudioRoute.EARPIECE -> AudioRoute.SPEAKER
        }
        setupAudioFocusAndRoute(next)
        return next
    }

    @Suppress("DEPRECATION")
    private fun setupAudioFocusAndRoute(route: AudioRoute) {
        _audioRoute.value = route
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val target = when (route) {
                    AudioRoute.BLUETOOTH -> devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
                    AudioRoute.SPEAKER -> devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    AudioRoute.EARPIECE -> devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ||
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    }
                }
                if (target != null) {
                    audioManager.setCommunicationDevice(target)
                } else {
                    audioManager.isSpeakerphoneOn = (route == AudioRoute.SPEAKER)
                }
            } else {
                when (route) {
                    AudioRoute.BLUETOOTH -> {
                        audioManager.isSpeakerphoneOn = false
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                    }
                    AudioRoute.SPEAKER -> {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                        audioManager.isSpeakerphoneOn = true
                    }
                    AudioRoute.EARPIECE -> {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                        audioManager.isSpeakerphoneOn = false
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun hasBluetoothAudioDevice(): Boolean {
        return try {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            outputs.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    fun stop() {
        recordJob?.cancel()
        recordJob = null
        playbackJob?.cancel()
        playbackJob = null

        try {
            pcmQueue.close()
        } catch (_: Exception) {
        }

        try {
            echoCanceler?.release()
        } catch (_: Exception) {
        }
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {
        }
        noiseSuppressor = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                audioManager.isSpeakerphoneOn = false
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }

        _micAmplitude.value = 0f
        _speakerAmplitude.value = 0f
        _isMuted.value = false
        isPlayingOutput = false
    }

    private fun computeRmsNormalized(pcmBytes: ByteArray): Float {
        if (pcmBytes.size < 2) return 0f
        var sumSquares = 0.0
        val samples = pcmBytes.size / 2
        var i = 0
        while (i + 1 < pcmBytes.size) {
            val low = pcmBytes[i].toInt() and 0xFF
            val high = pcmBytes[i + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toDouble()
            sumSquares += sample * sample
            i += 2
        }
        val rms = sqrt(sumSquares / samples.coerceAtLeast(1))
        return (rms / 9000.0).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        const val INPUT_SAMPLE_RATE = 16000
        const val OUTPUT_SAMPLE_RATE = 24000
    }
}
