package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AyeshaApplication
import com.example.control.AyeshaAccessibilityService
import com.example.control.AyeshaInputMethodService
import com.example.control.PhoneControlUiState
import com.example.data.AccentTheme
import com.example.data.KeyVerificationResult
import com.example.data.UserSettings
import com.example.data.VoiceMood
import com.example.data.db.BlockedAppEntity
import com.example.data.db.CallHistoryEntity
import com.example.data.db.CustomCharacterEntity
import com.example.data.db.MemoryEntity
import com.example.live.CallType
import com.example.live.DailyGreetingScheduler
import com.example.live.LiveCallUiState
import com.example.persona.AyeshaMode
import com.example.persona.VoiceCatalog
import com.example.persona.VoiceOption
import com.example.video.ShortsAnalyzeOutcome
import com.example.video.ShortsAnalyzerUiState
import com.example.video.ShortsOutputLanguage
import com.example.video.ShortsPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class ApiKeyVerifyUiState {
    data object Idle : ApiKeyVerifyUiState()
    data object Verifying : ApiKeyVerifyUiState()
    data object Verified : ApiKeyVerifyUiState()
    data class Error(val messageBn: String) : ApiKeyVerifyUiState()
}

data class PermissionSnapshot(
    val hasMic: Boolean = false,
    val hasCamera: Boolean = false,
    val hasNotifications: Boolean = false,
    val hasOverlay: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isAyeshaKeyboardEnabled: Boolean = false
)

class AyeshaViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as AyeshaApplication).container
    private val settingsRepo = container.settingsRepository
    private val repo = container.repository
    private val keystore = container.keystoreManager
    private val verifier = container.apiKeyVerifier
    val liveSessionManager = container.liveSessionManager
    val phoneControlCoordinator = container.phoneControlCoordinator
    private val voiceSamplePlayer = container.voiceSamplePlayer
    private val shortsVideoAnalyzer = container.shortsVideoAnalyzer
    private val latestShortsDraftStore = container.latestShortsDraftStore

    val settings: StateFlow<UserSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val callState: StateFlow<LiveCallUiState> = liveSessionManager.callState
    val phoneControlState: StateFlow<PhoneControlUiState> = phoneControlCoordinator.uiState
    val playingVoiceName: StateFlow<String?> = voiceSamplePlayer.playingVoiceName
    val voiceSampleError: StateFlow<String?> = voiceSamplePlayer.sampleError

    val allMemories: StateFlow<List<MemoryEntity>> = repo.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customCharacters: StateFlow<List<CustomCharacterEntity>> = repo.customCharacters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callHistory: StateFlow<List<CallHistoryEntity>> = repo.callHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedApps: StateFlow<List<BlockedAppEntity>> = repo.blockedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _apiKeyVerifyState = MutableStateFlow<ApiKeyVerifyUiState>(ApiKeyVerifyUiState.Idle)
    val apiKeyVerifyState: StateFlow<ApiKeyVerifyUiState> = _apiKeyVerifyState.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionSnapshot())
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    private val _showRelationshipNoticeMode = MutableStateFlow<AyeshaMode?>(null)
    val showRelationshipNoticeMode: StateFlow<AyeshaMode?> = _showRelationshipNoticeMode.asStateFlow()

    private val _showAccessibilityDisclosure = MutableStateFlow(false)
    val showAccessibilityDisclosure: StateFlow<Boolean> = _showAccessibilityDisclosure.asStateFlow()

    private val _shortsAnalyzerState = MutableStateFlow(
        ShortsAnalyzerUiState(result = latestShortsDraftStore.latestDraft.value)
    )
    val shortsAnalyzerState: StateFlow<ShortsAnalyzerUiState> = _shortsAnalyzerState.asStateFlow()

    fun hasStoredApiKey(): Boolean = keystore.hasApiKey()

    fun getMaskedApiKeyPreview(): String {
        val key = keystore.getApiKey() ?: return ""
        return if (key.length <= 8) "••••••••" else "${key.take(4)}••••••••${key.takeLast(4)}"
    }

    fun getCurrentRawKey(): String = keystore.getApiKey().orEmpty()

    fun completeWelcome(age18Confirmed: Boolean) {
        if (!age18Confirmed) return
        viewModelScope.launch {
            settingsRepo.setWelcomeCompleted(true)
        }
    }

    fun completePermissionsStep() {
        viewModelScope.launch {
            settingsRepo.setPermissionsStepCompleted(true)
        }
    }

    fun verifyAndSaveApiKey(rawKey: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _apiKeyVerifyState.value = ApiKeyVerifyUiState.Verifying
            when (val result = verifier.verify(rawKey)) {
                is KeyVerificationResult.Success -> {
                    keystore.saveApiKey(rawKey.trim())
                    settingsRepo.setApiKeyVerified(true)
                    _apiKeyVerifyState.value = ApiKeyVerifyUiState.Verified
                    onSuccess()
                }
                is KeyVerificationResult.InvalidKey -> {
                    _apiKeyVerifyState.value = ApiKeyVerifyUiState.Error(result.messageBn)
                }
                is KeyVerificationResult.NoInternet -> {
                    _apiKeyVerifyState.value = ApiKeyVerifyUiState.Error(result.messageBn)
                }
                is KeyVerificationResult.QuotaReached -> {
                    _apiKeyVerifyState.value = ApiKeyVerifyUiState.Error(result.messageBn)
                }
                is KeyVerificationResult.ServerError -> {
                    _apiKeyVerifyState.value = ApiKeyVerifyUiState.Error(result.messageBn)
                }
            }
        }
    }

    fun resetVerifyState() {
        _apiKeyVerifyState.value = ApiKeyVerifyUiState.Idle
    }

    fun deleteApiKey() {
        viewModelScope.launch {
            keystore.clearApiKey()
            settingsRepo.setApiKeyVerified(false)
            _apiKeyVerifyState.value = ApiKeyVerifyUiState.Idle
        }
    }

    fun selectMode(mode: AyeshaMode, customCharId: String? = null) {
        val current = settings.value
        if (mode.isRelationshipMode && !current.relationshipNoticeShown) {
            _showRelationshipNoticeMode.value = mode
            return
        }
        viewModelScope.launch {
            settingsRepo.setSelectedMode(mode.id, customCharId)
        }
    }

    fun confirmRelationshipNoticeAndSelect() {
        val mode = _showRelationshipNoticeMode.value ?: return
        _showRelationshipNoticeMode.value = null
        viewModelScope.launch {
            settingsRepo.markRelationshipNoticeShown()
            settingsRepo.setSelectedMode(mode.id)
        }
    }

    fun dismissRelationshipNotice() {
        _showRelationshipNoticeMode.value = null
    }

    fun selectVoice(voiceName: String) {
        viewModelScope.launch { settingsRepo.setSelectedVoice(voiceName) }
    }

    fun playVoiceSample(voice: VoiceOption) {
        val apiKey = keystore.getApiKey() ?: return
        if (playingVoiceName.value == voice.name) {
            voiceSamplePlayer.stop()
        } else {
            voiceSamplePlayer.playSample(
                scope = viewModelScope,
                apiKey = apiKey,
                voiceName = voice.name,
                isMaleVoice = voice.gender == com.example.persona.VoiceGender.MALE
            )
        }
    }

    fun stopVoiceSample() {
        voiceSamplePlayer.stop()
    }

    fun setPreferredLanguage(lang: String) {
        viewModelScope.launch { settingsRepo.setPreferredLanguage(lang) }
    }

    fun setEmotionLevel(level: Float) {
        viewModelScope.launch { settingsRepo.setEmotionLevel(level) }
    }

    fun setVoiceMood(mood: VoiceMood) {
        viewModelScope.launch { settingsRepo.setVoiceMood(mood) }
    }

    fun setTurnPauseMs(ms: Int) {
        viewModelScope.launch { settingsRepo.setTurnPauseMs(ms) }
    }

    fun saveCustomCharacter(
        existingId: String?,
        name: String,
        relationshipRole: String,
        callUserAs: String,
        language: String,
        voiceName: String,
        interests: String,
        description: String,
        funLevel: Float,
        emotionLevel: Float,
        seriousnessLevel: Float,
        talkativenessLevel: Float,
        selectImmediately: Boolean = true,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = existingId ?: "custom_${UUID.randomUUID().toString().take(8)}"
            val entity = CustomCharacterEntity(
                id = id,
                name = name.trim().ifEmpty { "আয়শা" },
                relationshipRole = relationshipRole.trim().ifEmpty { "বন্ধু" },
                callUserAs = callUserAs.trim(),
                language = language.trim().ifEmpty { "বাংলাদেশি বাংলা" },
                voiceName = VoiceCatalog.findByName(voiceName).name,
                interests = interests.trim(),
                description = description.trim().take(com.example.ui.components.MAX_CUSTOM_DESCRIPTION_CHARS),
                funLevel = funLevel,
                emotionLevel = emotionLevel,
                seriousnessLevel = seriousnessLevel,
                talkativenessLevel = talkativenessLevel
            )
            repo.saveCustomCharacter(entity)
            if (selectImmediately) {
                settingsRepo.setSelectedMode(AyeshaMode.CUSTOM.id, id)
            }
            onSaved()
        }
    }

    fun deleteCustomCharacter(id: String) {
        viewModelScope.launch {
            repo.deleteCustomCharacter(id)
            if (settings.value.selectedCustomCharacterId == id) {
                settingsRepo.setSelectedMode(AyeshaMode.ASSISTANT.id)
            }
        }
    }

    fun testCustomCharacterCall(characterId: String) {
        viewModelScope.launch {
            settingsRepo.setSelectedMode(AyeshaMode.CUSTOM.id, characterId)
            liveSessionManager.startCall(
                callType = CallType.AUDIO,
                overrideMode = AyeshaMode.CUSTOM,
                overrideCustomCharacterId = characterId
            )
        }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setMemoryEnabled(enabled) }
    }

    fun updateMemory(memory: MemoryEntity, newValue: String) {
        viewModelScope.launch { repo.updateMemory(memory, newValue) }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch { repo.deleteMemoryById(id) }
    }

    fun deleteAllMemories() {
        viewModelScope.launch { repo.deleteAllMemories() }
    }

    fun setPhoneControlMaster(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setPhoneControlMaster(enabled) }
    }

    fun requestAccessibilitySetup() {
        _showAccessibilityDisclosure.value = true
    }

    fun dismissAccessibilityDisclosure() {
        _showAccessibilityDisclosure.value = false
    }

    fun acceptAccessibilityDisclosure(onOpenSettings: () -> Unit) {
        _showAccessibilityDisclosure.value = false
        viewModelScope.launch {
            settingsRepo.setAccessibilityDisclosureAccepted(true)
            onOpenSettings()
        }
    }

    fun setAllowAppOpening(allowed: Boolean) {
        viewModelScope.launch { settingsRepo.setAllowAppOpening(allowed) }
    }

    fun setAllowTapping(allowed: Boolean) {
        viewModelScope.launch { settingsRepo.setAllowTapping(allowed) }
    }

    fun setAllowTyping(allowed: Boolean) {
        viewModelScope.launch { settingsRepo.setAllowTyping(allowed) }
    }

    fun setAllowKeyboardSwitch(allowed: Boolean) {
        viewModelScope.launch { settingsRepo.setAllowKeyboardSwitch(allowed) }
    }

    fun setRequireActionConfirmation(required: Boolean) {
        viewModelScope.launch { settingsRepo.setRequireActionConfirmation(required) }
    }

    fun addBlockedApp(packageName: String, displayName: String) {
        viewModelScope.launch { repo.addBlockedApp(packageName, displayName) }
    }

    fun removeBlockedApp(packageName: String) {
        viewModelScope.launch { repo.removeBlockedApp(packageName) }
    }

    fun setAccentTheme(theme: AccentTheme) {
        viewModelScope.launch { settingsRepo.setAccentTheme(theme) }
    }

    fun setDailyGreetingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setDailyGreetingEnabled(enabled)
            DailyGreetingScheduler.setEnabled(getApplication(), enabled)
        }
    }

    fun deleteCallHistory(id: Long) {
        viewModelScope.launch { repo.deleteCallHistory(id) }
    }

    fun deleteAllCallHistory() {
        viewModelScope.launch { repo.deleteAllCallHistory() }
    }

    fun deleteAllAppData() {
        viewModelScope.launch {
            liveSessionManager.endCall()
            DailyGreetingScheduler.setEnabled(getApplication(), false)
            repo.deleteAllAppData()
            latestShortsDraftStore.clear()
            _shortsAnalyzerState.value = ShortsAnalyzerUiState()
            _apiKeyVerifyState.value = ApiKeyVerifyUiState.Idle
        }
    }

    fun onShortsVideoPicked(uri: Uri) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                shortsVideoAnalyzer.inspectVideo(getApplication(), uri)
            }
            _shortsAnalyzerState.update {
                it.copy(
                    selectedVideoUri = uri.toString(),
                    selectedVideoName = info.displayName,
                    selectedVideoSizeBytes = info.sizeBytes,
                    selectedVideoDurationMs = info.durationMs,
                    errorMessageBn = null
                )
            }
        }
    }

    fun setShortsLanguage(language: ShortsOutputLanguage) {
        _shortsAnalyzerState.update { it.copy(outputLanguage = language) }
    }

    fun setShortsCreatorNote(note: String) {
        _shortsAnalyzerState.update { it.copy(creatorNote = note.take(300)) }
    }

    fun analyzeSelectedShortsVideo() {
        val current = _shortsAnalyzerState.value
        val uriString = current.selectedVideoUri ?: return
        if (current.isAnalyzing) return

        val apiKey = keystore.getApiKey()
        if (apiKey.isNullOrBlank()) {
            _shortsAnalyzerState.update {
                it.copy(errorMessageBn = "প্রথমে যাচাইকৃত Gemini API কী যোগ করুন।")
            }
            return
        }

        val parsedUri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        viewModelScope.launch {
            _shortsAnalyzerState.update {
                it.copy(
                    isAnalyzing = true,
                    progressMessageBn = "প্রস্তুত করা হচ্ছে...",
                    errorMessageBn = null
                )
            }
            val outcome = shortsVideoAnalyzer.analyzeVideo(
                context = getApplication(),
                apiKey = apiKey,
                videoUri = parsedUri,
                language = current.outputLanguage,
                creatorNote = current.creatorNote,
                onProgress = { msg ->
                    _shortsAnalyzerState.update { it.copy(progressMessageBn = msg) }
                }
            )
            when (outcome) {
                is ShortsAnalyzeOutcome.Success -> {
                    latestShortsDraftStore.save(outcome.result)
                    _shortsAnalyzerState.update {
                        it.copy(
                            isAnalyzing = false,
                            progressMessageBn = "",
                            errorMessageBn = null,
                            result = outcome.result
                        )
                    }
                }
                is ShortsAnalyzeOutcome.Failure -> {
                    _shortsAnalyzerState.update {
                        it.copy(
                            isAnalyzing = false,
                            progressMessageBn = "",
                            errorMessageBn = outcome.messageBn
                        )
                    }
                }
            }
        }
    }

    fun selectShortsPlatform(platform: ShortsPlatform) {
        latestShortsDraftStore.selectPlatform(platform)
        _shortsAnalyzerState.update { state ->
            state.copy(result = latestShortsDraftStore.latestDraft.value ?: state.result?.copy(activePlatform = platform))
        }
    }

    fun selectShortsTitle(title: String) {
        latestShortsDraftStore.updateSelectedTitle(title)
        _shortsAnalyzerState.update { state ->
            state.copy(result = latestShortsDraftStore.latestDraft.value ?: state.result)
        }
    }

    fun updateShortsTitle(title: String) {
        latestShortsDraftStore.updateSelectedTitle(title)
        _shortsAnalyzerState.update { state ->
            state.copy(result = latestShortsDraftStore.latestDraft.value ?: state.result)
        }
    }

    fun updateShortsDescription(description: String) {
        latestShortsDraftStore.updateDescription(description)
        _shortsAnalyzerState.update { state ->
            state.copy(result = latestShortsDraftStore.latestDraft.value ?: state.result)
        }
    }

    fun refreshPermissions(context: Context) {
        val mic = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val cam = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val notif = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val overlay = Settings.canDrawOverlays(context)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(context.packageName)
        val a11yOk = AyeshaAccessibilityService.isEnabledInSystemSettings(context)
        val imeOk = AyeshaInputMethodService.isEnabledInSystemSettings(context)

        _permissions.value = PermissionSnapshot(
            hasMic = mic,
            hasCamera = cam,
            hasNotifications = notif,
            hasOverlay = overlay,
            ignoringBatteryOptimizations = batteryOk,
            isAccessibilityEnabled = a11yOk,
            isAyeshaKeyboardEnabled = imeOk
        )
    }

    fun startAudioCall() {
        liveSessionManager.startCall(CallType.AUDIO)
    }

    fun startVideoCall() {
        liveSessionManager.startCall(CallType.VIDEO)
    }
}
