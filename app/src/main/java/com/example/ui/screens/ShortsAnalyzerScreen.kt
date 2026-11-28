package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.CreatorAuditEntity
import com.example.ui.components.AyeshaBackground
import com.example.ui.components.AyeshaCard
import com.example.ui.components.AyeshaIcons
import com.example.ui.components.formatDurationBn
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceHigh
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TealSuccess
import com.example.video.ShortsAnalyzerUiState
import com.example.video.ShortsOutputLanguage
import com.example.video.ShortsPlatform
import com.example.video.ShortsSubTab
import com.example.video.TrendPlatformFilter
import com.example.video.TrendRadarUiState
import com.example.video.TrendRegion
import kotlinx.coroutines.launch

@Composable
fun ShortsAnalyzerScreen(
    uiState: ShortsAnalyzerUiState,
    trendState: TrendRadarUiState,
    creatorAudits: List<CreatorAuditEntity>,
    isKeyVerified: Boolean,
    isPhoneControlReady: Boolean,
    onSelectSubTab: (ShortsSubTab) -> Unit,
    onVideoPicked: (Uri) -> Unit,
    onClearPendingVideo: () -> Unit,
    onSelectLanguage: (ShortsOutputLanguage) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onSelectPlatform: (ShortsPlatform) -> Unit,
    onSelectTitleOption: (String) -> Unit,
    onTitleEdited: (String) -> Unit,
    onDescriptionEdited: (String) -> Unit,
    onTrendNicheChange: (String) -> Unit,
    onTrendRegionChange: (TrendRegion) -> Unit,
    onTrendPlatformChange: (TrendPlatformFilter) -> Unit,
    onFetchTrends: (String?) -> Unit,
    onDeleteCreatorAudit: (Long) -> Unit,
    onDeleteAllCreatorAudits: () -> Unit,
    onStartVoiceAssistCall: () -> Unit,
    onStartVideoAssistCall: () -> Unit,
    onOpenPhoneControlSettings: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val chatListState = rememberLazyListState()
    var chatDraftText by rememberSaveable { mutableStateOf("") }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onVideoPicked(uri)
        }
    }

    fun showToastMsg(msg: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    val isBusy = uiState.isAnalyzing || uiState.isChatResponding
    LaunchedEffect(uiState.chatMessages.size, uiState.isAnalyzing, uiState.isChatResponding) {
        val targetIndex = (uiState.chatMessages.size + (if (isBusy) 1 else 0) - 1).coerceAtLeast(0)
        if (uiState.chatMessages.isNotEmpty() || isBusy) {
            chatListState.animateScrollToItem(targetIndex)
        }
    }

    AyeshaBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header with WhatsApp/imo-style Audio Call & Video Call icons in the top-right corner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "শর্টস ও ক্রিয়েটার কোচ",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "চ্যাট কোচ • ভিডিও ডক্টর • লাইভ ট্রেন্ড",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = TealSuccess.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, TealSuccess.copy(alpha = 0.5f))
                            ) {
                                IconButton(
                                    onClick = {
                                        if (!isKeyVerified) onOpenConnectionSettings()
                                        else onStartVoiceAssistCall()
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("shorts_header_audio_call")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "কোচের সাথে অডিও কল",
                                        tint = TealSuccess
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                            ) {
                                IconButton(
                                    onClick = {
                                        if (!isKeyVerified) onOpenConnectionSettings()
                                        else onStartVideoAssistCall()
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("shorts_header_video_call")
                                ) {
                                    Icon(
                                        imageVector = AyeshaIcons.Videocam,
                                        contentDescription = "কোচের সাথে ভিডিও কল",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // 3-SubTab Switcher (কোচ চ্যাট | ট্রেন্ড রাডার | গ্রোথ লগ)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(ShortsSubTab.entries) { _, subTab ->
                            val selected = uiState.activeSubTab == subTab
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectSubTab(subTab) },
                                label = { Text(subTab.titleBn) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onBackground
                                ),
                                modifier = Modifier.testTag("shorts_subtab_${subTab.id}")
                            )
                        }
                    }

                    if (!isKeyVerified) {
                        AyeshaCard(highlightBorder = RoseDanger.copy(alpha = 0.7f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = RoseDanger)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("API কী যাচাই বাকি", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "ক্রিয়েটার কোচ ও ভিডিও বিশ্লেষণের জন্য প্রথমে Gemini API কী যোগ করুন।",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = onOpenConnectionSettings,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("shorts_setup_key_button")
                            ) {
                                Text("API কী সেট করুন")
                            }
                        }
                    }

                    when (uiState.activeSubTab) {
                        ShortsSubTab.TRENDS -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TrendRadarTabContent(
                                    uiState = trendState,
                                    detectedNicheHint = uiState.result?.diagnostics?.detectedCategoryBn.orEmpty(),
                                    isKeyVerified = isKeyVerified,
                                    onNicheChange = onTrendNicheChange,
                                    onSelectRegion = onTrendRegionChange,
                                    onSelectPlatformFilter = onTrendPlatformChange,
                                    onFetchTrends = { onFetchTrends(null) },
                                    onStartCoachCall = onStartVoiceAssistCall,
                                    onOpenConnectionSettings = onOpenConnectionSettings,
                                    onShowMessage = { showToastMsg(it) }
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        ShortsSubTab.GROWTH_LOG -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CreatorGrowthLogTabContent(
                                    audits = creatorAudits,
                                    onDeleteAudit = onDeleteCreatorAudit,
                                    onDeleteAllAudits = onDeleteAllCreatorAudits,
                                    onStartCoachCall = onStartVoiceAssistCall
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        ShortsSubTab.DOCTOR -> {
                            // Interactive Chat Thread
                            LazyColumn(
                                state = chatListState,
                                contentPadding = PaddingValues(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .testTag("shorts_chat_list")
                            ) {
                                if (uiState.chatMessages.isEmpty()) {
                                    item(key = "welcome_coach") {
                                        CreatorCoachWelcomeBlock(
                                            onQuickPrompt = { prompt ->
                                                if (!isKeyVerified) onOpenConnectionSettings()
                                                else onSendChatMessage(prompt)
                                            },
                                            onPickVideo = {
                                                videoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                )
                                            }
                                        )
                                    }
                                }

                                items(uiState.chatMessages, key = { it.id }) { msg ->
                                    CreatorCoachChatBubbleItem(
                                        message = msg,
                                        activeLatestResult = uiState.result,
                                        selectedVideoUriFallback = uiState.selectedVideoUri,
                                        isPhoneControlReady = isPhoneControlReady,
                                        onSelectSubTab = onSelectSubTab,
                                        onTrendNicheChange = onTrendNicheChange,
                                        onFetchTrends = onFetchTrends,
                                        onSelectPlatform = onSelectPlatform,
                                        onSelectTitleOption = onSelectTitleOption,
                                        onTitleEdited = onTitleEdited,
                                        onDescriptionEdited = onDescriptionEdited,
                                        onStartVoiceAssistCall = onStartVoiceAssistCall,
                                        onOpenPhoneControlSettings = onOpenPhoneControlSettings,
                                        onShowMessage = { showToastMsg(it) }
                                    )
                                }

                                if (isBusy) {
                                    item(key = "coach_typing_indicator") {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = NavySurfaceHigh,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    text = if (uiState.isAnalyzing) {
                                                        uiState.progressMessageBn.ifBlank { "ভিডিও বিশ্লেষণ চলছে..." }
                                                    } else {
                                                        "আয়শা কোচ পরামর্শ লিখছেন..."
                                                    },
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Pending attached video strip (shown right above composer bar when a video is selected)
                            if (uiState.selectedVideoUri != null) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = NavySurfaceHigh,
                                    border = BorderStroke(1.dp, TealSuccess.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = AyeshaIcons.Movie,
                                                contentDescription = null,
                                                tint = TealSuccess,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = uiState.selectedVideoName.ifBlank { "বাছাইকৃত ভিডিও" },
                                                    style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 1
                                                )
                                                val sizeMb = uiState.selectedVideoSizeBytes / (1024f * 1024f)
                                                val durSec = (uiState.selectedVideoDurationMs / 1000L).coerceAtLeast(0L)
                                                Text(
                                                    text = buildString {
                                                        if (durSec > 0) append("${formatDurationBn(durSec)} • ")
                                                        append("%.1f MB".format(sizeMb))
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(
                                                onClick = onClearPendingVideo,
                                                enabled = !isBusy,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .testTag("shorts_clear_video_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "ভিডিও সরান",
                                                    tint = RoseDanger
                                                )
                                            }
                                        }

                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            itemsIndexed(ShortsOutputLanguage.entries) { _, lang ->
                                                val selected = uiState.outputLanguage == lang
                                                FilterChip(
                                                    selected = selected,
                                                    onClick = { if (!isBusy) onSelectLanguage(lang) },
                                                    label = { Text(lang.labelBn, style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.testTag("shorts_lang_${lang.id}")
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Bottom Chat Composer Bar (+ Video Attach | Text Input | Send)
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = NavySurface,
                                border = BorderStroke(1.dp, NavyBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            videoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                            )
                                        },
                                        enabled = !isBusy,
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = NavySurfaceHigh,
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("shorts_pick_video_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "ভিডিও যোগ করুন")
                                    }

                                    OutlinedTextField(
                                        value = chatDraftText,
                                        onValueChange = { chatDraftText = it.take(400) },
                                        enabled = !isBusy,
                                        placeholder = {
                                            Text(
                                                text = if (uiState.selectedVideoUri != null) {
                                                    "বাড়তি নির্দেশনা লিখুন বা সরাসরি পাঠান..."
                                                } else {
                                                    "ভিডিও, স্ক্রিপ্ট বা হুক নিয়ে লিখুন..."
                                                },
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        },
                                        maxLines = 3,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("shorts_chat_input")
                                    )

                                    val canSend = !isBusy && (chatDraftText.isNotBlank() || uiState.selectedVideoUri != null)
                                    IconButton(
                                        onClick = {
                                            if (!isKeyVerified) {
                                                onOpenConnectionSettings()
                                            } else {
                                                val outgoing = chatDraftText.trim()
                                                chatDraftText = ""
                                                onSendChatMessage(outgoing)
                                            }
                                        },
                                        enabled = canSend,
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (canSend) MaterialTheme.colorScheme.primary else NavySurfaceHigh,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("shorts_send_chat_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "বার্তা পাঠান")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
        }
    }
}
