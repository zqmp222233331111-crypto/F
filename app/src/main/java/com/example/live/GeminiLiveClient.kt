package com.example.live

import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class LiveCallStatus(val labelBn: String) {
    IDLE("প্রস্তুত"),
    CONNECTING("সংযোগ হচ্ছে"),
    RECONNECTING("পুনরায় সংযোগ হচ্ছে"),
    LISTENING("শুনছি"),
    THINKING("ভাবছি"),
    SPEAKING("বলছি"),
    ERROR("সংযোগ বিচ্ছিন্ন")
}

data class LiveSessionConfig(
    val apiKey: String,
    val voiceName: String,
    val systemInstruction: String,
    val includeMemoryTools: Boolean,
    val includePhoneControlTools: Boolean,
    val includeGoogleSearch: Boolean,
    val turnPauseMs: Int = 900
)

/**
 * Gemini Live API WebSocket client for models/gemini-3.8-live.
 * - Audio response modality only (NO thinkingConfig/thinkingLevel, NO affective dialog)
 * - Context window compression + session resumption + silent GoAway reconnect
 * - Barge-in interruption handling & tool calling
 */
class GeminiLiveClient(
    private val scope: CoroutineScope,
    private val onStatusChanged: (LiveCallStatus) -> Unit,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onTranscriptReceived: (String) -> Unit,
    private val onToolCall: suspend (name: String, args: Map<String, Any?>) -> Map<String, Any?>,
    private val onError: (String) -> Unit
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var currentConfig: LiveSessionConfig? = null
    private var resumptionHandle: String? = null
    private var setupTimeoutJob: Job? = null

    @Volatile
    private var userClosed = false

    @Volatile
    private var silentReconnectPending = false

    @Volatile
    private var hasSentInitialGreeting = false

    @Volatile
    private var setupCompleted = false

    @Volatile
    private var socketGeneration = 0L

    fun connect(config: LiveSessionConfig) {
        userClosed = false
        currentConfig = config
        resumptionHandle = null
        hasSentInitialGreeting = false
        setupCompleted = false
        onStatusChanged(LiveCallStatus.CONNECTING)
        openSocket(config, isReconnect = false)
    }

    private fun openSocket(config: LiveSessionConfig, isReconnect: Boolean) {
        silentReconnectPending = false
        setupCompleted = false
        val generation = ++socketGeneration
        setupTimeoutJob?.cancel()
        setupTimeoutJob = scope.launch {
            delay(15_000L)
            if (!userClosed && !setupCompleted && generation == socketGeneration) {
                onStatusChanged(LiveCallStatus.ERROR)
                onError("সার্ভারের সাথে সংযোগ হতে সময় বেশি লাগছে। ইন্টারনেট পরীক্ষা করে আবার কল করুন।")
                try {
                    webSocket?.close(1000, "Setup timeout")
                } catch (_: Exception) {
                }
            }
        }

        val encodedKey = URLEncoder.encode(config.apiKey.trim(), "UTF-8")
        val request = Request.Builder()
            .url("$LIVE_WS_URL?key=$encodedKey")
            .addHeader("x-goog-api-key", config.apiKey.trim())
            .build()

        val previous = webSocket
        webSocket = null
        previous?.close(1000, "Replacing socket")

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (generation != socketGeneration) return
                sendSetupMessage(webSocket, config, resumptionHandle)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (generation != socketGeneration) return
                handleServerMessage(webSocket, text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (generation != socketGeneration) return
                handleServerMessage(webSocket, bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (userClosed || generation != socketGeneration) return
                setupCompleted = false
                setupTimeoutJob?.cancel()
                val code = response?.code
                val msg = when {
                    code == 401 || code == 403 ->
                        "API কী-টি সঠিক নয় বা বাতিল হয়েছে। অনুগ্রহ করে সেটিংস থেকে নতুন কী দিন।"
                    code == 429 ->
                        "ফ্রি সীমা শেষ, কিছুক্ষণ পরে চেষ্টা করুন।"
                    else ->
                        "নেটওয়ার্ক সংযোগ বিচ্ছিন্ন হয়েছে। ইন্টারনেট পরীক্ষা করে আবার কল করুন।"
                }
                if (resumptionHandle != null && code == null && !isReconnect) {
                    onStatusChanged(LiveCallStatus.RECONNECTING)
                    openSocket(config, isReconnect = true)
                } else {
                    onStatusChanged(LiveCallStatus.ERROR)
                    onError(msg)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (userClosed || generation != socketGeneration) return
                if (!silentReconnectPending && code != 1000) {
                    setupTimeoutJob?.cancel()
                    handleSocketCloseReason(code, reason)
                }
                try {
                    webSocket.close(code, reason)
                } catch (_: Exception) {
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (userClosed || generation != socketGeneration) return
                setupCompleted = false
                setupTimeoutJob?.cancel()
                if (silentReconnectPending) {
                    currentConfig?.let {
                        onStatusChanged(LiveCallStatus.RECONNECTING)
                        openSocket(it, isReconnect = true)
                    }
                    return
                }
                if (code != 1000) {
                    handleSocketCloseReason(code, reason)
                }
            }
        })
    }

    private fun handleSocketCloseReason(code: Int, reason: String) {
        val lower = reason.lowercase()
        val bnError = when {
            lower.contains("quota") || lower.contains("rate") || lower.contains("exhausted") ->
                "ফ্রি সীমা শেষ, কিছুক্ষণ পরে চেষ্টা করুন।"
            lower.contains("api key") || lower.contains("permission") || lower.contains("unauthorized") ->
                "API কী-টি অনুমোদিত নয়। দয়া করে যাচাই করুন।"
            reason.isNotBlank() ->
                "কল সংযোগ বিচ্ছিন্ন হয়েছে ($code): $reason"
            else -> "কল সংযোগ বিচ্ছিন্ন হয়েছে।"
        }
        onStatusChanged(LiveCallStatus.ERROR)
        onError(bnError)
    }

    private fun sendSetupMessage(
        ws: WebSocket,
        config: LiveSessionConfig,
        handle: String?
    ) {
        val generationConfig = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put(
                "speechConfig",
                JSONObject().put(
                    "voiceConfig",
                    JSONObject().put(
                        "prebuiltVoiceConfig",
                        JSONObject().put("voiceName", config.voiceName)
                    )
                )
            )
        // Intentionally NO thinkingConfig / thinkingLevel and NO affective dialog

        val setup = JSONObject()
            .put("model", MODEL_RESOURCE)
            .put("generationConfig", generationConfig)
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", config.systemInstruction))
                )
            )
            .put(
                "contextWindowCompression",
                JSONObject().put("slidingWindow", JSONObject())
            )
            .put(
                "sessionResumption",
                JSONObject().apply {
                    if (!handle.isNullOrBlank()) put("handle", handle)
                }
            )
            .put("inputAudioTranscription", JSONObject())
            .put(
                "realtimeInputConfig",
                JSONObject().put(
                    "automaticActivityDetection",
                    JSONObject()
                        .put("disabled", false)
                        .put("silenceDurationMs", config.turnPauseMs.coerceIn(500, 2000))
                )
            )

        val tools = LiveToolDeclarations.buildToolsArray(
            includeMemoryTools = config.includeMemoryTools,
            includePhoneControlTools = config.includePhoneControlTools,
            includeGoogleSearch = config.includeGoogleSearch
        )
        if (tools.length() > 0) {
            setup.put("tools", tools)
        }

        val payload = JSONObject().put("setup", setup)
        ws.send(payload.toString())
    }

    fun sendPcmAudio(pcm16kBytes: ByteArray) {
        if (!setupCompleted) return
        val ws = webSocket ?: return
        val base64 = Base64.encodeToString(pcm16kBytes, Base64.NO_WRAP)
        val msg = JSONObject().put(
            "realtimeInput",
            JSONObject().put(
                "audio",
                JSONObject()
                    .put("mimeType", "audio/pcm;rate=16000")
                    .put("data", base64)
            )
        )
        ws.send(msg.toString())
    }

    fun sendImageFrame(jpegBytes: ByteArray) {
        if (!setupCompleted) return
        val ws = webSocket ?: return
        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val msg = JSONObject().put(
            "realtimeInput",
            JSONObject().put(
                "video",
                JSONObject()
                    .put("mimeType", "image/jpeg")
                    .put("data", base64)
            )
        )
        ws.send(msg.toString())
    }

    private fun handleServerMessage(ws: WebSocket, rawJson: String) {
        try {
            val root = JSONObject(rawJson)

            if (root.has("setupComplete")) {
                setupCompleted = true
                setupTimeoutJob?.cancel()
                onStatusChanged(LiveCallStatus.LISTENING)
                if (!hasSentInitialGreeting) {
                    hasSentInitialGreeting = true
                    val greetPayload = JSONObject().put(
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
                                                    "কলটি মাত্র রিসিভ হয়েছে। কোনো যান্ত্রিক ভূমিকা ছাড়াই এখনই তোমার সক্রিয় চরিত্র ও আবেগের ধরন অনুযায়ী একদম জীবন্ত, আন্তরিক মানুষের মতো সংক্ষেপে কথা শুরু করো।"
                                                )
                                            )
                                        )
                                )
                            )
                            .put("turnComplete", true)
                    )
                    ws.send(greetPayload.toString())
                }
                return
            }

            if (root.has("sessionResumptionUpdate")) {
                val update = root.getJSONObject("sessionResumptionUpdate")
                val newHandle = update.optString("newHandle")
                if (newHandle.isNotBlank()) {
                    resumptionHandle = newHandle
                }
            }

            if (root.has("goAway")) {
                // Trigger silent reconnect on close or immediately swap with resumption handle
                silentReconnectPending = true
                currentConfig?.let {
                    onStatusChanged(LiveCallStatus.RECONNECTING)
                    openSocket(it, isReconnect = true)
                }
                return
            }

            if (root.has("error")) {
                val errObj = root.getJSONObject("error")
                val errMsg = errObj.optString("message")
                val status = errObj.optString("status")
                val code = errObj.optInt("code", 0)
                val bnMsg = when {
                    code == 429 || status.contains("RESOURCE_EXHAUSTED", true) ||
                        errMsg.contains("quota", true) || errMsg.contains("rate", true) ->
                        "ফ্রি সীমা শেষ, কিছুক্ষণ পরে চেষ্টা করুন।"
                    code == 401 || code == 403 || errMsg.contains("API key", true) ->
                        "API কী-টি সঠিক নয় বা অনুমোদিত নয়।"
                    else -> "সার্ভার ত্রুটি হয়েছে। অনুগ্রহ করে আবার চেষ্টা করুন।"
                }
                onStatusChanged(LiveCallStatus.ERROR)
                onError(bnMsg)
                return
            }

            if (root.has("serverContent")) {
                val sc = root.getJSONObject("serverContent")

                if (sc.optBoolean("interrupted", false)) {
                    onInterrupted()
                    onStatusChanged(LiveCallStatus.LISTENING)
                }

                if (sc.has("inputTranscription")) {
                    val text = sc.getJSONObject("inputTranscription").optString("text")
                    if (text.isNotBlank()) {
                        onTranscriptReceived(text)
                    }
                }

                if (sc.has("modelTurn")) {
                    val parts = sc.getJSONObject("modelTurn").optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inline = part.getJSONObject("inlineData")
                                val mime = inline.optString("mimeType")
                                val data = inline.optString("data")
                                if (mime.startsWith("audio/") && data.isNotBlank()) {
                                    val bytes = Base64.decode(data, Base64.DEFAULT)
                                    onStatusChanged(LiveCallStatus.SPEAKING)
                                    onAudioReceived(bytes)
                                }
                            }
                        }
                    }
                }

                if (sc.optBoolean("turnComplete", false)) {
                    onStatusChanged(LiveCallStatus.LISTENING)
                }
            }

            if (root.has("toolCall")) {
                onStatusChanged(LiveCallStatus.THINKING)
                val toolCall = root.getJSONObject("toolCall")
                val calls = toolCall.optJSONArray("functionCalls") ?: return
                scope.launch(Dispatchers.IO) {
                    val responses = JSONArray()
                    for (i in 0 until calls.length()) {
                        val fnCall = calls.getJSONObject(i)
                        val id = fnCall.optString("id")
                        val name = fnCall.optString("name")
                        val argsObj = fnCall.optJSONObject("args") ?: JSONObject()
                        val argsMap = jsonObjectToMap(argsObj)
                        val resultMap = try {
                            onToolCall(name, argsMap)
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to (e.message ?: "ত্রুটি হয়েছে"))
                        }
                        responses.put(
                            JSONObject()
                                .put("id", id)
                                .put("name", name)
                                .put("response", mapToJsonObject(resultMap))
                        )
                    }
                    val toolResp = JSONObject().put(
                        "toolResponse",
                        JSONObject().put("functionResponses", responses)
                    )
                    webSocket?.send(toolResp.toString())
                }
            }
        } catch (_: Exception) {
        }
    }

    fun disconnect() {
        userClosed = true
        silentReconnectPending = false
        setupCompleted = false
        setupTimeoutJob?.cancel()
        setupTimeoutJob = null
        socketGeneration++
        try {
            webSocket?.close(1000, "Call ended")
        } catch (_: Exception) {
        }
        webSocket = null
        onStatusChanged(LiveCallStatus.IDLE)
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.opt(k)
            map[k] = when (v) {
                JSONObject.NULL -> null
                is JSONObject -> jsonObjectToMap(v)
                else -> v
            }
        }
        return map
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            when (v) {
                null -> obj.put(k, JSONObject.NULL)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    obj.put(k, mapToJsonObject(v as Map<String, Any?>))
                }
                is List<*> -> {
                    val arr = JSONArray()
                    v.forEach { item ->
                        if (item is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            arr.put(mapToJsonObject(item as Map<String, Any?>))
                        } else {
                            arr.put(item)
                        }
                    }
                    obj.put(k, arr)
                }
                else -> obj.put(k, v)
            }
        }
        return obj
    }

    companion object {
        const val MODEL_RESOURCE = "models/gemini-3.8-live"
        const val LIVE_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }
}
