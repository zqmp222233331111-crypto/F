package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.live.CallType
import com.example.ui.components.AccessibilityDisclosureDialog
import com.example.ui.components.RelationshipNoticeDialog
import com.example.ui.components.SystemIntentUtils
import com.example.ui.screens.ApiKeyScreen
import com.example.ui.screens.AudioCallScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PermissionsSetupScreen
import com.example.ui.screens.SettingsConnectionSubScreen
import com.example.ui.screens.SettingsHomeScreen
import com.example.ui.screens.SettingsMemorySubScreen
import com.example.ui.screens.SettingsPermissionsBatterySubScreen
import com.example.ui.screens.SettingsPersonaSubScreen
import com.example.ui.screens.SettingsPhoneControlSubScreen
import com.example.ui.screens.SettingsSafetySubScreen
import com.example.ui.screens.SettingsSubPage
import com.example.ui.screens.SettingsThemeAboutSubScreen
import com.example.ui.screens.SettingsVoiceSubScreen
import com.example.ui.screens.ShortsAnalyzerScreen
import com.example.ui.screens.VideoCallScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.AyeshaTheme
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurface

enum class MainTab(
    val titleBn: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("হোম", Icons.Filled.Home, Icons.Outlined.Home, "tab_home"),
    SHORTS("শর্টস", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle, "tab_shorts"),
    HISTORY("ইতিহাস", Icons.Filled.History, Icons.Outlined.History, "tab_history"),
    SETTINGS("সেটিংস", Icons.Filled.Settings, Icons.Outlined.Settings, "tab_settings")
}

@Composable
fun AyeshaAppRoot(viewModel: AyeshaViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val phoneControlState by viewModel.phoneControlState.collectAsStateWithLifecycle()
    val apiKeyVerifyState by viewModel.apiKeyVerifyState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val customCharacters by viewModel.customCharacters.collectAsStateWithLifecycle()
    val memories by viewModel.allMemories.collectAsStateWithLifecycle()
    val callHistory by viewModel.callHistory.collectAsStateWithLifecycle()
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()
    val playingVoiceName by viewModel.playingVoiceName.collectAsStateWithLifecycle()
    val voiceSampleError by viewModel.voiceSampleError.collectAsStateWithLifecycle()
    val relationshipNoticeMode by viewModel.showRelationshipNoticeMode.collectAsStateWithLifecycle()
    val showAccessibilityDisclosure by viewModel.showAccessibilityDisclosure.collectAsStateWithLifecycle()
    val shortsAnalyzerState by viewModel.shortsAnalyzerState.collectAsStateWithLifecycle()
    val trendRadarState by viewModel.trendRadarState.collectAsStateWithLifecycle()
    val creatorAudits by viewModel.creatorAudits.collectAsStateWithLifecycle()

    val shortsCallMicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startAudioCall()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions(context)
    }

    val isKeyVerified = settings.apiKeyVerified && viewModel.hasStoredApiKey()

    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var activeSettingsSubPage by remember { mutableStateOf<SettingsSubPage?>(null) }

    AyeshaTheme(accentTheme = settings.accentTheme) {
        when {
            callState.isInCall && callState.callType == CallType.AUDIO -> {
                AudioCallScreen(
                    callState = callState,
                    phoneControlState = phoneControlState,
                    onToggleMute = { viewModel.liveSessionManager.toggleMute() },
                    onCycleAudioRoute = { viewModel.liveSessionManager.cycleAudioRoute() },
                    onStopTask = { viewModel.phoneControlCoordinator.stopTaskNow() },
                    onResolveConfirmation = { viewModel.phoneControlCoordinator.resolveConfirmation(it) },
                    onEndCall = { viewModel.liveSessionManager.endCall() }
                )
            }

            callState.isInCall && callState.callType == CallType.VIDEO -> {
                VideoCallScreen(
                    callState = callState,
                    phoneControlState = phoneControlState,
                    onSubmitCameraFrame = { viewModel.liveSessionManager.submitCameraFrame(it) },
                    onToggleCamera = { viewModel.liveSessionManager.toggleCamera() },
                    onSwitchCamera = { viewModel.liveSessionManager.switchCameraFacing() },
                    onToggleMute = { viewModel.liveSessionManager.toggleMute() },
                    onStopTask = { viewModel.phoneControlCoordinator.stopTaskNow() },
                    onResolveConfirmation = { viewModel.phoneControlCoordinator.resolveConfirmation(it) },
                    onEndCall = { viewModel.liveSessionManager.endCall() }
                )
            }

            !settings.welcomeCompleted -> {
                WelcomeScreen(
                    onStart = { ageConfirmed ->
                        viewModel.completeWelcome(ageConfirmed)
                    }
                )
            }

            !isKeyVerified -> {
                ApiKeyScreen(
                    isKeyVerified = false,
                    initialMaskedPreview = viewModel.getMaskedApiKeyPreview(),
                    verifyState = apiKeyVerifyState,
                    onVerifyAndSave = { key -> viewModel.verifyAndSaveApiKey(key) }
                )
            }

            !settings.permissionsStepCompleted -> {
                PermissionsSetupScreen(
                    permissions = permissions,
                    onRefreshPermissions = { viewModel.refreshPermissions(context) },
                    onFinish = { viewModel.completePermissionsStep() }
                )
            }

            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useNavRail = maxWidth >= 700.dp

                    val subPage = activeSettingsSubPage
                    if (currentTab == MainTab.SETTINGS && subPage != null) {
                        Scaffold(
                            containerColor = NavyDeep
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                when (subPage) {
                                    SettingsSubPage.CONNECTION -> SettingsConnectionSubScreen(
                                        isKeyVerified = isKeyVerified,
                                        maskedPreview = viewModel.getMaskedApiKeyPreview(),
                                        verifyState = apiKeyVerifyState,
                                        onVerifyAndSave = { viewModel.verifyAndSaveApiKey(it) },
                                        onDeleteKey = {
                                            activeSettingsSubPage = null
                                            viewModel.deleteApiKey()
                                        },
                                        onBack = {
                                            viewModel.resetVerifyState()
                                            activeSettingsSubPage = null
                                        }
                                    )

                                    SettingsSubPage.VOICE -> SettingsVoiceSubScreen(
                                        selectedVoice = settings.selectedVoice,
                                        playingVoiceName = playingVoiceName,
                                        sampleError = voiceSampleError,
                                        onSelectVoice = { viewModel.selectVoice(it) },
                                        onPlaySample = { viewModel.playVoiceSample(it) },
                                        onBack = {
                                            viewModel.stopVoiceSample()
                                            activeSettingsSubPage = null
                                        }
                                    )

                                    SettingsSubPage.PERSONA -> SettingsPersonaSubScreen(
                                        settings = settings,
                                        customCharacters = customCharacters,
                                        onSelectMode = { m, cId -> viewModel.selectMode(m, cId) },
                                        onSetLanguage = { viewModel.setPreferredLanguage(it) },
                                        onSetEmotionLevel = { viewModel.setEmotionLevel(it) },
                                        onSetVoiceMood = { viewModel.setVoiceMood(it) },
                                        onSetTurnPauseMs = { viewModel.setTurnPauseMs(it) },
                                        onSaveCustomCharacter = { id, n, r, cu, l, v, i, d, f, e, s, t ->
                                            viewModel.saveCustomCharacter(id, n, r, cu, l, v, i, d, f, e, s, t)
                                        },
                                        onDeleteCustomCharacter = { viewModel.deleteCustomCharacter(it) },
                                        onTestCustomCharacter = { viewModel.testCustomCharacterCall(it) },
                                        onBack = { activeSettingsSubPage = null }
                                    )

                                    SettingsSubPage.MEMORY -> SettingsMemorySubScreen(
                                        memoryEnabled = settings.memoryEnabled,
                                        memories = memories,
                                        customCharacters = customCharacters,
                                        onToggleMemory = { viewModel.setMemoryEnabled(it) },
                                        onUpdateMemory = { m, v -> viewModel.updateMemory(m, v) },
                                        onDeleteMemory = { viewModel.deleteMemory(it) },
                                        onDeleteAllMemories = { viewModel.deleteAllMemories() },
                                        onBack = { activeSettingsSubPage = null }
                                    )

                                    SettingsSubPage.PHONE_CONTROL -> SettingsPhoneControlSubScreen(
                                        settings = settings,
                                        permissions = permissions,
                                        onRefreshPermissions = { viewModel.refreshPermissions(context) },
                                        onSetMaster = { viewModel.setPhoneControlMaster(it) },
                                        onRequestAccessibilitySetup = { viewModel.requestAccessibilitySetup() },
                                        onSetAllowAppOpening = { viewModel.setAllowAppOpening(it) },
                                        onSetAllowTapping = { viewModel.setAllowTapping(it) },
                                        onSetAllowTyping = { viewModel.setAllowTyping(it) },
                                        onSetAllowKeyboardSwitch = { viewModel.setAllowKeyboardSwitch(it) },
                                        onBack = { activeSettingsSubPage = null }
                                    )

                                    SettingsSubPage.SAFETY -> SettingsSafetySubScreen(
                                        requireConfirmation = settings.requireActionConfirmation,
                                        blockedApps = blockedApps,
                                        onToggleConfirmation = { viewModel.setRequireActionConfirmation(it) },
                                        onAddBlockedApp = { pkg, name -> viewModel.addBlockedApp(pkg, name) },
                                        onRemoveBlockedApp = { viewModel.removeBlockedApp(it) },
                                        onBack = { activeSettingsSubPage = null }
                                    )

                                    SettingsSubPage.PERMISSIONS_BATTERY -> SettingsPermissionsBatterySubScreen(
                                        permissions = permissions,
                                        onRefreshPermissions = { viewModel.refreshPermissions(context) },
                                        onRequestAccessibilitySetup = { viewModel.requestAccessibilitySetup() },
                                        onBack = { activeSettingsSubPage = null }
                                    )

                                    SettingsSubPage.THEME_ABOUT -> SettingsThemeAboutSubScreen(
                                        accentTheme = settings.accentTheme,
                                        dailyGreetingEnabled = settings.dailyGreetingEnabled,
                                        onSelectTheme = { viewModel.setAccentTheme(it) },
                                        onToggleDailyGreeting = { viewModel.setDailyGreetingEnabled(it) },
                                        onDeleteAllAppData = {
                                            activeSettingsSubPage = null
                                            currentTab = MainTab.HOME
                                            viewModel.deleteAllAppData()
                                        },
                                        onBack = { activeSettingsSubPage = null }
                                    )
                                }
                            }
                        }
                    } else {
                        Scaffold(
                            containerColor = NavyDeep,
                            bottomBar = {
                                if (!useNavRail) {
                                    NavigationBar(
                                        containerColor = NavySurface
                                    ) {
                                        MainTab.entries.forEach { tab ->
                                            val selected = currentTab == tab
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = { currentTab = tab },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = tab.titleBn
                                                    )
                                                },
                                                label = { Text(tab.titleBn) },
                                                colors = NavigationBarItemDefaults.colors(
                                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                ),
                                                modifier = Modifier.testTag(tab.testTag)
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                if (useNavRail) {
                                    NavigationRail(containerColor = NavySurface) {
                                        MainTab.entries.forEach { tab ->
                                            val selected = currentTab == tab
                                            NavigationRailItem(
                                                selected = selected,
                                                onClick = { currentTab = tab },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = tab.titleBn
                                                    )
                                                },
                                                label = { Text(tab.titleBn) },
                                                modifier = Modifier.testTag(tab.testTag)
                                            )
                                        }
                                    }
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    when (currentTab) {
                                        MainTab.HOME -> HomeScreen(
                                            settings = settings,
                                            customCharacters = customCharacters,
                                            isKeyVerified = isKeyVerified,
                                            onSelectMode = { m, cId -> viewModel.selectMode(m, cId) },
                                            onSaveCustomCharacter = { id, n, r, cu, l, v, i, d, f, e, s, t, onSaved ->
                                                viewModel.saveCustomCharacter(
                                                    existingId = id,
                                                    name = n,
                                                    relationshipRole = r,
                                                    callUserAs = cu,
                                                    language = l,
                                                    voiceName = v,
                                                    interests = i,
                                                    description = d,
                                                    funLevel = f,
                                                    emotionLevel = e,
                                                    seriousnessLevel = s,
                                                    talkativenessLevel = t,
                                                    selectImmediately = true,
                                                    onSaved = onSaved
                                                )
                                            },
                                            onOpenPersonaSettings = {
                                                currentTab = MainTab.SETTINGS
                                                activeSettingsSubPage = SettingsSubPage.PERSONA
                                            },
                                            onStartAudioCall = { viewModel.startAudioCall() },
                                            onStartVideoCall = { viewModel.startVideoCall() },
                                            onOpenConnectionSettings = {
                                                currentTab = MainTab.SETTINGS
                                                activeSettingsSubPage = SettingsSubPage.CONNECTION
                                            }
                                        )

                                        MainTab.SHORTS -> ShortsAnalyzerScreen(
                                            uiState = shortsAnalyzerState,
                                            trendState = trendRadarState,
                                            creatorAudits = creatorAudits,
                                            isKeyVerified = isKeyVerified,
                                            isPhoneControlReady = settings.phoneControlMasterEnabled && permissions.isAccessibilityEnabled,
                                            onSelectSubTab = { tab -> viewModel.selectShortsSubTab(tab) },
                                            onVideoPicked = { uri -> viewModel.onShortsVideoPicked(uri) },
                                            onSelectLanguage = { lang -> viewModel.setShortsLanguage(lang) },
                                            onCreatorNoteChange = { note -> viewModel.setShortsCreatorNote(note) },
                                            onAnalyzeClick = { viewModel.analyzeSelectedShortsVideo() },
                                            onSelectPlatform = { platform -> viewModel.selectShortsPlatform(platform) },
                                            onSelectTitleOption = { title -> viewModel.selectShortsTitle(title) },
                                            onTitleEdited = { title -> viewModel.updateShortsTitle(title) },
                                            onDescriptionEdited = { desc -> viewModel.updateShortsDescription(desc) },
                                            onTrendNicheChange = { q -> viewModel.setTrendNicheQuery(q) },
                                            onTrendRegionChange = { r -> viewModel.setTrendRegion(r) },
                                            onTrendPlatformChange = { p -> viewModel.setTrendPlatformFilter(p) },
                                            onFetchTrends = { niche -> viewModel.fetchLiveTrends(niche) },
                                            onDeleteCreatorAudit = { id -> viewModel.deleteCreatorAudit(id) },
                                            onDeleteAllCreatorAudits = { viewModel.deleteAllCreatorAudits() },
                                            onStartVoiceAssistCall = {
                                                val micOk = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (micOk) {
                                                    viewModel.startAudioCall()
                                                } else {
                                                    shortsCallMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            },
                                            onOpenPhoneControlSettings = {
                                                currentTab = MainTab.SETTINGS
                                                activeSettingsSubPage = SettingsSubPage.PHONE_CONTROL
                                            },
                                            onOpenConnectionSettings = {
                                                currentTab = MainTab.SETTINGS
                                                activeSettingsSubPage = SettingsSubPage.CONNECTION
                                            }
                                        )

                                        MainTab.HISTORY -> HistoryScreen(
                                            historyItems = callHistory,
                                            onDeleteOne = { viewModel.deleteCallHistory(it) },
                                            onDeleteAll = { viewModel.deleteAllCallHistory() }
                                        )

                                        MainTab.SETTINGS -> SettingsHomeScreen(
                                            settings = settings,
                                            isKeyVerified = isKeyVerified,
                                            onOpenSubPage = { activeSettingsSubPage = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // One-time 18+ AI character disclosure dialog for relationship modes
        relationshipNoticeMode?.let { mode ->
            RelationshipNoticeDialog(
                modeName = mode.titleBn,
                onConfirm = { viewModel.confirmRelationshipNoticeAndSelect() },
                onDismiss = { viewModel.dismissRelationshipNotice() }
            )
        }

        // Required Bengali Accessibility disclosure before opening system settings
        if (showAccessibilityDisclosure) {
            AccessibilityDisclosureDialog(
                onAgree = {
                    viewModel.acceptAccessibilityDisclosure {
                        SystemIntentUtils.openAccessibilitySettings(context)
                    }
                },
                onDismiss = { viewModel.dismissAccessibilityDisclosure() }
            )
        }
    }
}
