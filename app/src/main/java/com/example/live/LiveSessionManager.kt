package com.example.live

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.example.control.PhoneControlCoordinator
import com.example.data.AyeshaRepository
import com.example.data.KeystoreManager
import com.example.data.SettingsRepository
import com.example.persona.AyeshaMode
import com.example.persona.SystemInstructionBuilder
import com.example.persona.VoiceCatalog
import com.example.video.LatestShortsDraftStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class CallType(val id: String, val labelBn: String) {
    AUDIO("AUDIO", "অডিও কল"),
    VIDEO("VIDEO", "ভিডিও কল")
}

data class LiveCallUiState(
    val isInCall: Boolean = false,
    val callType: CallType = CallType.AUDIO,
    val status: LiveCallStatus = LiveCallStatus.IDLE,
    val elapsedSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val audioRoute: AudioRoute = AudioRoute.SPEAKER,
    val isCameraOn: Boolean = false,
    val isFrontCamera: Boolean = true,
    val isScreenSharing: Boolean = false,
    val orbAmplitude: Float = 0f,
    val modeTitleBn: String = "সহকারী",
    val characterName: String = "আয়শা",
    val errorMessageBn: String? = null
)

/**
 * Coordinates the end-to-end live audio/video call lifecycle, tool call routing,
 * audio/video streaming, foreground service, and call history recording.
 */
class LiveSessionManager(
    private val context: Context,
    private val keystoreManager: KeystoreManager,
    private val settingsRepository: SettingsRepository,
    private val repository: AyeshaRepository,
    private val phoneControlCoordinator: PhoneControlCoordinator,
    private val latestShortsDraftStore: LatestShortsDraftStore,
    private val liveWebSearchService: LiveWebSearchService,
    private val callAutoMemorySummarizer: CallAutoMemorySummarizer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val audioEngine = LiveAudioEngine(context)
    private val videoEncoder = VideoFrameEncoder()
    private val screenShareManager = ScreenShareCaptureManager(context, videoEncoder)

    private var liveClient: GeminiLiveClient? = null
    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null
    private var activeCharacterId: String = "default"
    private val callTranscriptLines = mutableListOf<String>()

    private val _callState = MutableStateFlow(LiveCallUiState())
    val callState: StateFlow<LiveCallUiState> = _callState.asStateFlow()

    fun startCall(
        callType: CallType,
        overrideMode: AyeshaMode? = null,
        overrideCustomCharacterId: String? = null
    ) {
        if (_callState.value.isInCall) return

        scope.launch {
            val apiKey = keystoreManager.getApiKey()
            if (apiKey.isNullOrBlank()) {
                _callState.value = _callState.value.copy(
                    errorMessageBn = "প্রথমে যাচাইকৃত Gemini API কী যোগ করুন।"
                )
                return@launch
            }

            val settings = settingsRepository.settings.first()
            val mode = overrideMode ?: AyeshaMode.fromId(settings.selectedModeId)
            val customCharId = overrideCustomCharacterId ?: settings.selectedCustomCharacterId
            val customChar = if (mode == AyeshaMode.CUSTOM && !customCharId.isNullOrBlank()) {
                repository.getCustomCharacter(customCharId)
            } else {
                null
            }

            activeCharacterId = if (mode == AyeshaMode.CUSTOM && customChar != null) {
                customChar.id
            } else {
                "mode_${mode.id}"
            }

            val voiceName = if (mode == AyeshaMode.CUSTOM && customChar != null) {
                customChar.voiceName
            } else {
                settings.selectedVoice
            }
            val effectiveVoice = VoiceCatalog.findByName(voiceName)
            val memories = if (settings.memoryEnabled) {
                repository.getMemoriesForCharacter(activeCharacterId)
            } else {
                emptyList()
            }

            val instruction = SystemInstructionBuilder.build(
                settings = settings,
                mode = mode,
                customCharacter = customChar,
                memories = memories,
                effectiveVoice = effectiveVoice
            )

            val displayName = customChar?.name?.takeIf { mode == AyeshaMode.CUSTOM && it.isNotBlank() }
                ?: if (effectiveVoice.gender.name == "MALE" && mode != AyeshaMode.ISLAMIC) "আয়ান" else "আয়শা"

            phoneControlCoordinator.beginCallSession()
            videoEncoder.resetThrottle()
            synchronized(callTranscriptLines) { callTranscriptLines.clear() }

            _callState.value = LiveCallUiState(
                isInCall = true,
                callType = callType,
                status = LiveCallStatus.CONNECTING,
                elapsedSeconds = 0L,
                isMuted = false,
                audioRoute = AudioRoute.SPEAKER,
                isCameraOn = (callType == CallType.VIDEO),
                isFrontCamera = true,
                isScreenSharing = false,
                modeTitleBn = mode.titleBn,
                characterName = displayName,
                errorMessageBn = null
            )

            // Start Foreground Service
            val fgIntent = Intent(context, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_START_CALL
                putExtra(CallForegroundService.EXTRA_USE_CAMERA, callType == CallType.VIDEO)
            }
            ContextCompat.startForegroundService(context, fgIntent)

            // Connect WebSocket
            val client = GeminiLiveClient(
                scope = scope,
                onStatusChanged = { newStatus ->
                    _callState.update { it.copy(status = newStatus) }
                },
                onAudioReceived = { pcm24k ->
                    audioEngine.enqueueOutputPcm(pcm24k)
                },
                onInterrupted = {
                    audioEngine.clearPlaybackQueue()
                },
                onTranscriptReceived = { text ->
                    appendTranscriptLine("User", text)
                    phoneControlCoordinator.handleVoiceTextForSafety(text)
                },
                onModelTranscriptReceived = { text ->
                    appendTranscriptLine("Ayesha", text)
                },
                onToolCall = { name, args ->
                    handleToolCall(name, args)
                },
                onError = { errBn ->
                    _callState.update {
                        it.copy(
                            status = LiveCallStatus.ERROR,
                            errorMessageBn = errBn
                        )
                    }
                }
            )
            liveClient = client
            client.connect(
                LiveSessionConfig(
                    apiKey = apiKey,
                    voiceName = effectiveVoice.name,
                    systemInstruction = instruction,
                    includeMemoryTools = settings.memoryEnabled,
                    includePhoneControlTools = settings.phoneControlMasterEnabled,
                    includeGoogleSearch = false,
                    turnPauseMs = settings.turnPauseMs
                )
            )

            // Start PCM Audio Engine
            audioEngine.start(
                scope = scope,
                onPcmCaptured = { pcm16k ->
                    val status = _callState.value.status
                    if (status != LiveCallStatus.CONNECTING && status != LiveCallStatus.ERROR) {
                        liveClient?.sendPcmAudio(pcm16k)
                    }
                },
                onPlaybackActiveChanged = { playing ->
                    if (!playing) {
                        _callState.update {
                            if (it.status == LiveCallStatus.SPEAKING) {
                                it.copy(status = LiveCallStatus.LISTENING)
                            } else {
                                it
                            }
                        }
                    }
                }
            )

            startTimersAndAmplitudeSync()
        }
    }

    private suspend fun handleToolCall(
        name: String,
        args: Map<String, Any?>
    ): Map<String, Any?> {
        return when (name) {
            "save_memory" -> {
                val enabled = settingsRepository.settings.first().memoryEnabled
                if (!enabled) {
                    return mapOf("success" to false, "error" to "ব্যবহারকারী স্মৃতি সংরক্ষণ বন্ধ রেখেছেন।")
                }
                val key = args["key"]?.toString().orEmpty()
                val value = args["value"]?.toString().orEmpty()
                val saved = repository.saveMemory(activeCharacterId, key, value)
                if (saved) {
                    mapOf("success" to true, "message" to "স্থানীয়ভাবে সংরক্ষণ করা হয়েছে।")
                } else {
                    mapOf("success" to false, "error" to "গোপন বা সংবেদনশীল তথ্য (পাসওয়ার্ড/ওটিপি/পিন/কার্ড) সংরক্ষণ করা যাবে না।")
                }
            }
            "delete_memory" -> {
                val key = args["key"]?.toString().orEmpty()
                val deleted = repository.deleteMemoryByKey(activeCharacterId, key)
                mapOf("success" to deleted)
            }
            "web_search" -> {
                val query = args["query"]?.toString().orEmpty()
                val apiKey = keystoreManager.getApiKey().orEmpty()
                liveWebSearchService.search(apiKey, query)
            }
            "get_latest_shorts_draft" -> {
                val draft = latestShortsDraftStore.latestDraft.value
                if (draft == null) {
                    mapOf(
                        "success" to false,
                        "has_draft" to false,
                        "message" to "শর্টস ট্যাবে এখনো কোনো ভিডিও বিশ্লেষণ করা হয়নি।"
                    )
                } else {
                    val platformMaps = draft.platformPacks.mapKeys { it.key.id }.mapValues { (_, pack) ->
                        mapOf(
                            "selected_title" to pack.selectedTitle,
                            "alternative_titles" to pack.titles,
                            "description" to pack.description,
                            "hashtags" to pack.hashtags.joinToString(" ")
                        )
                    }
                    mapOf(
                        "success" to true,
                        "has_draft" to true,
                        "active_platform" to draft.activePlatform.labelBn,
                        "video_name" to draft.videoDisplayName,
                        "selected_title" to draft.selectedTitle,
                        "alternative_titles" to draft.titles,
                        "description" to draft.description,
                        "hashtags" to draft.hashtags.joinToString(" "),
                        "visual_summary" to draft.visualSummaryBn,
                        "audio_summary" to draft.audioSummaryBn,
                        "platforms" to platformMaps
                    )
                }
            }
            else -> phoneControlCoordinator.executeTool(name, args)
        }
    }

    private fun appendTranscriptLine(speaker: String, chunk: String) {
        val clean = chunk.trim()
        if (clean.isEmpty()) return
        synchronized(callTranscriptLines) {
            if (callTranscriptLines.size < 400) {
                callTranscriptLines.add("$speaker: $clean")
            }
        }
    }

    private fun startTimersAndAmplitudeSync() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _callState.value.isInCall) {
                delay(1000L)
                _callState.update { state ->
                    if (state.isInCall && state.status != LiveCallStatus.ERROR) {
                        state.copy(elapsedSeconds = state.elapsedSeconds + 1)
                    } else {
                        state
                    }
                }
            }
        }

        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            while (isActive && _callState.value.isInCall) {
                val mic = audioEngine.micAmplitude.value
                val spk = audioEngine.speakerAmplitude.value
                val combined = maxOf(mic, spk)
                _callState.update { state ->
                    if (state.orbAmplitude != combined) {
                        state.copy(orbAmplitude = combined)
                    } else {
                        state
                    }
                }
                delay(45L)
            }
        }
    }

    fun submitCameraFrame(imageProxy: ImageProxy) {
        val state = _callState.value
        if (!state.isInCall || !state.isCameraOn || state.isScreenSharing) {
            imageProxy.close()
            return
        }
        videoEncoder.processCameraProxy(imageProxy) { jpeg ->
            liveClient?.sendImageFrame(jpeg)
        }
    }

    fun attachMediaProjection(projection: MediaProjection) {
        _callState.value = _callState.value.copy(isScreenSharing = true)
        screenShareManager.start(
            projection = projection,
            onJpegFrame = { jpeg ->
                liveClient?.sendImageFrame(jpeg)
            },
            onProjectionStopped = {
                _callState.value = _callState.value.copy(isScreenSharing = false)
            }
        )
    }

    fun stopScreenShare() {
        screenShareManager.stop()
        _callState.value = _callState.value.copy(isScreenSharing = false)
    }

    fun toggleMute() {
        val muted = audioEngine.toggleMute()
        _callState.value = _callState.value.copy(isMuted = muted)
    }

    fun cycleAudioRoute() {
        val route = audioEngine.cycleAudioRoute()
        _callState.value = _callState.value.copy(audioRoute = route)
    }

    fun toggleCamera() {
        val next = !_callState.value.isCameraOn
        _callState.value = _callState.value.copy(isCameraOn = next)
        val intent = Intent(context, CallForegroundService::class.java).apply {
            action = CallForegroundService.ACTION_UPDATE_CAMERA
            putExtra(CallForegroundService.EXTRA_USE_CAMERA, next)
        }
        context.startService(intent)
    }

    fun switchCameraFacing() {
        _callState.value = _callState.value.copy(
            isFrontCamera = !_callState.value.isFrontCamera
        )
    }

    fun notifyAppBackgrounded() {
        if (!_callState.value.isInCall) return
        context.startService(
            Intent(context, CallForegroundService::class.java).setAction(
                CallForegroundService.ACTION_APP_BACKGROUNDED
            )
        )
    }

    fun notifyAppForegrounded() {
        if (!_callState.value.isInCall) return
        context.startService(
            Intent(context, CallForegroundService::class.java).setAction(
                CallForegroundService.ACTION_APP_FOREGROUNDED
            )
        )
    }

    fun clearError() {
        _callState.value = _callState.value.copy(errorMessageBn = null)
    }

    fun endCall() {
        val snapshot = _callState.value
        if (!snapshot.isInCall && liveClient == null) return

        timerJob?.cancel()
        timerJob = null
        amplitudeJob?.cancel()
        amplitudeJob = null

        phoneControlCoordinator.stopTaskNow()
        screenShareManager.stop()
        audioEngine.stop()
        liveClient?.disconnect()
        liveClient = null

        context.startService(
            Intent(context, CallForegroundService::class.java).setAction(
                CallForegroundService.ACTION_STOP_SERVICE
            )
        )

        val actions = phoneControlCoordinator.getSessionActionLogs()
        val duration = snapshot.elapsedSeconds
        val transcriptSnapshot = synchronized(callTranscriptLines) {
            val joined = callTranscriptLines.joinToString("\n")
            callTranscriptLines.clear()
            joined
        }
        val charIdSnapshot = activeCharacterId

        _callState.value = LiveCallUiState()

        if (duration > 0 || actions.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                repository.insertCallHistory(
                    callType = snapshot.callType.id,
                    durationSeconds = duration,
                    modeName = snapshot.modeTitleBn,
                    characterName = snapshot.characterName,
                    actions = actions
                )

                if (duration >= 8L && transcriptSnapshot.length >= 25) {
                    val settings = settingsRepository.settings.first()
                    val apiKey = keystoreManager.getApiKey()
                    if (settings.memoryEnabled && !apiKey.isNullOrBlank()) {
                        val existingKeys = repository.getMemoriesForCharacter(charIdSnapshot)
                            .map { it.factKey }
                        val extracted = callAutoMemorySummarizer.extractMemories(
                            apiKey = apiKey,
                            transcript = transcriptSnapshot,
                            existingKeys = existingKeys
                        )
                        extracted.forEach { fact ->
                            repository.saveMemory(charIdSnapshot, fact.key, fact.value)
                        }
                    }
                }
            }
        }
    }
}
