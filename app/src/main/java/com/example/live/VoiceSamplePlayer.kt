package com.example.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Plays a short generated Bengali voice sample for any voice in VoiceCatalog
 * using models/gemini-3.8-live.
 */
class VoiceSamplePlayer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _playingVoiceName = MutableStateFlow<String?>(null)
    val playingVoiceName: StateFlow<String?> = _playingVoiceName.asStateFlow()

    private val _sampleError = MutableStateFlow<String?>(null)
    val sampleError: StateFlow<String?> = _sampleError.asStateFlow()

    private var activeSocket: WebSocket? = null
    private var activeTrack: AudioTrack? = null

    fun playSample(
        scope: CoroutineScope,
        apiKey: String,
        voiceName: String,
        isMaleVoice: Boolean
    ) {
        stop()
        _sampleError.value = null
        _playingVoiceName.value = voiceName

        val pcmBuffer = ByteArrayOutputStream()
        val encodedKey = URLEncoder.encode(apiKey.trim(), "UTF-8")
        val request = Request.Builder()
            .url("${GeminiLiveClient.LIVE_WS_URL}?key=$encodedKey")
            .addHeader("x-goog-api-key", apiKey.trim())
            .build()

        activeSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val setup = JSONObject().put(
                    "setup",
                    JSONObject()
                        .put("model", GeminiLiveClient.MODEL_RESOURCE)
                        .put(
                            "generationConfig",
                            JSONObject()
                                .put("responseModalities", JSONArray().put("AUDIO"))
                                .put(
                                    "speechConfig",
                                    JSONObject().put(
                                        "voiceConfig",
                                        JSONObject().put(
                                            "prebuiltVoiceConfig",
                                            JSONObject().put("voiceName", voiceName)
                                        )
                                    )
                                )
                        )
                        .put(
                            "systemInstruction",
                            JSONObject().put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put(
                                        "text",
                                        if (isMaleVoice) {
                                            "তুমি বাংলায় কথা বলা একজন বন্ধুসুলভ পুরুষ এআই সহকারী। মাত্র এক লাইনে নিজের কণ্ঠের উষ্ণ নমুনা শোনাও।"
                                        } else {
                                            "তুমি আয়শা, বাংলায় কথা বলা উষ্ণ এআই সহকারী। মাত্র এক লাইনে নিজের কণ্ঠের নমুনা শোনাও।"
                                        }
                                    )
                                )
                            )
                        )
                )
                webSocket.send(setup.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(webSocket, text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleMessage(webSocket, bytes.utf8())
            }

            private fun handleMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    if (root.has("setupComplete")) {
                        val prompt = JSONObject().put(
                            "clientContent",
                            JSONObject()
                                .put(
                                    "turns",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("role", "user")
                                            .put(
                                                "parts",
                                                JSONArray().put(
                                                    JSONObject().put(
                                                        "text",
                                                        "আসসালামু আলাইকুম বা হ্যালো বলে বাংলায় এক লাইনে তোমার কণ্ঠের সংক্ষিপ্ত পরিচয় দাও।"
                                                    )
                                                )
                                            )
                                    )
                                )
                                .put("turnComplete", true)
                        )
                        webSocket.send(prompt.toString())
                        return
                    }

                    if (root.has("error")) {
                        val errMsg = root.getJSONObject("error").optString("message")
                        _sampleError.value = if (errMsg.contains("quota", true)) {
                            "ফ্রি সীমা শেষ, কিছুক্ষণ পরে চেষ্টা করুন।"
                        } else {
                            "ভয়েস নমুনা চালু করা যায়নি।"
                        }
                        stop()
                        return
                    }

                    val sc = root.optJSONObject("serverContent") ?: return
                    val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val inline = parts.getJSONObject(i).optJSONObject("inlineData")
                            if (inline != null && inline.optString("mimeType").startsWith("audio/")) {
                                val bytes = Base64.decode(inline.optString("data"), Base64.DEFAULT)
                                pcmBuffer.write(bytes)
                            }
                        }
                    }

                    if (sc.optBoolean("turnComplete", false)) {
                        webSocket.close(1000, "Sample complete")
                        val audioBytes = pcmBuffer.toByteArray()
                        scope.launch(Dispatchers.IO) {
                            playPcmBuffer(audioBytes, voiceName)
                        }
                    }
                } catch (_: Exception) {
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _sampleError.value = "ইন্টারনেট বা কী-তে সমস্যার কারণে নমুনা চালানো যায়নি।"
                _playingVoiceName.value = null
            }
        })
    }

    private fun playPcmBuffer(audioBytes: ByteArray, voiceName: String) {
        if (audioBytes.isEmpty()) {
            _playingVoiceName.value = null
            return
        }
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                LiveAudioEngine.OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(LiveAudioEngine.OUTPUT_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, 4800))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            activeTrack = track
            track.play()
            track.write(audioBytes, 0, audioBytes.size)
            val durationMs = (audioBytes.size.toLong() * 1000L) / (LiveAudioEngine.OUTPUT_SAMPLE_RATE * 2L)
            Thread.sleep(durationMs.coerceIn(300L, 12_000L))
        } catch (_: Exception) {
        } finally {
            if (_playingVoiceName.value == voiceName) {
                _playingVoiceName.value = null
            }
            try {
                activeTrack?.stop()
                activeTrack?.release()
            } catch (_: Exception) {
            }
            activeTrack = null
        }
    }

    fun stop() {
        try {
            activeSocket?.close(1000, "Stopped")
        } catch (_: Exception) {
        }
        activeSocket = null
        try {
            activeTrack?.stop()
            activeTrack?.release()
        } catch (_: Exception) {
        }
        activeTrack = null
        _playingVoiceName.value = null
    }
}
