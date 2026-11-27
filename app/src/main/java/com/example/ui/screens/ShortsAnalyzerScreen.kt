package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.AyeshaBackground
import com.example.ui.components.AyeshaCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatDurationBn
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavySurfaceHigh
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TealSuccess
import com.example.video.ShortsAnalyzerUiState
import com.example.video.ShortsOutputLanguage
import com.example.video.ShortsPlatform
import com.example.video.YouTubeLaunchHelper
import kotlinx.coroutines.launch

@Composable
fun ShortsAnalyzerScreen(
    uiState: ShortsAnalyzerUiState,
    isKeyVerified: Boolean,
    isPhoneControlReady: Boolean,
    onVideoPicked: (Uri) -> Unit,
    onSelectLanguage: (ShortsOutputLanguage) -> Unit,
    onCreatorNoteChange: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onSelectPlatform: (ShortsPlatform) -> Unit,
    onSelectTitleOption: (String) -> Unit,
    onTitleEdited: (String) -> Unit,
    onDescriptionEdited: (String) -> Unit,
    onStartVoiceAssistCall: () -> Unit,
    onOpenPhoneControlSettings: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

    AyeshaBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 580.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "শর্টস ও রিলস স্টুডিও",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "এক ভিডিও থেকে YouTube Shorts, FB Reels ও TikTok-এর টাইটেল ও হ্যাশট্যাগ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusBadge(
                        label = "৩ প্ল্যাটফর্ম",
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // API Key gate if missing
                if (!isKeyVerified) {
                    AyeshaCard(highlightBorder = RoseDanger.copy(alpha = 0.7f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = RoseDanger)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("API কী যাচাই বাকি", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "ভিডিও বিশ্লেষণের জন্য প্রথমে Gemini API কী যোগ করুন।",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
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

                // Step 1: Video Picker & Options Card
                AyeshaCard(highlightBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "১. গ্যালারি থেকে ভিডিও বাছাই করুন",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    if (uiState.selectedVideoUri != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = NavySurfaceHigh,
                            border = BorderStroke(1.dp, TealSuccess.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = TealSuccess
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = uiState.selectedVideoName.ifBlank { "বাছাইকৃত ভিডিও" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    val sizeMb = uiState.selectedVideoSizeBytes / (1024f * 1024f)
                                    val durSec = (uiState.selectedVideoDurationMs / 1000L).coerceAtLeast(0L)
                                    Text(
                                        text = buildString {
                                            if (durSec > 0) append("দৈর্ঘ্য: ${formatDurationBn(durSec)} • ")
                                            append("%.1f MB".format(sizeMb))
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    OutlinedButton(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        enabled = !uiState.isAnalyzing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("shorts_pick_video_button")
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (uiState.selectedVideoUri == null) "গ্যালারি থেকে ভিডিও বাছাই করুন"
                            else "অন্য ভিডিও বাছাই করুন"
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "টাইটেল ও ক্যাপশনের ভাষা",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(ShortsOutputLanguage.entries) { _, lang ->
                            val selected = uiState.outputLanguage == lang
                            FilterChip(
                                selected = selected,
                                onClick = { if (!uiState.isAnalyzing) onSelectLanguage(lang) },
                                label = { Text(lang.labelBn) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier.testTag("shorts_lang_${lang.id}")
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.creatorNote,
                        onValueChange = onCreatorNoteChange,
                        enabled = !uiState.isAnalyzing,
                        label = { Text("বাড়তি কোনো নির্দেশনা? (ঐচ্ছিক)") },
                        placeholder = { Text("যেমন: চ্যানেলের ধরন, বা কোনো বিশেষ হ্যাশট্যাগ") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shorts_creator_note_input")
                    )

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (!isKeyVerified) {
                                onOpenConnectionSettings()
                            } else {
                                onAnalyzeClick()
                            }
                        },
                        enabled = !uiState.isAnalyzing && uiState.selectedVideoUri != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("shorts_analyze_button")
                    ) {
                        if (uiState.isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(uiState.progressMessageBn.ifBlank { "বিশ্লেষণ চলছে..." })
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("ভিডিও বিশ্লেষণ ও ৩ প্যাক তৈরি করুন")
                        }
                    }

                    AnimatedVisibility(visible = !uiState.errorMessageBn.isNullOrBlank()) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            StatusBadge(
                                label = uiState.errorMessageBn.orEmpty(),
                                color = RoseDanger
                            )
                        }
                    }
                }

                // Step 2: 3-Platform Pack Result + Direct Actions
                val result = uiState.result
                if (result != null) {
                    val activePlatform = result.activePlatform
                    val activePack = result.activePack

                    AyeshaCard(highlightBorder = TealSuccess.copy(alpha = 0.55f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "২. ৩ প্ল্যাটফর্মের তৈরি প্যাক",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            StatusBadge(label = "প্রস্তুত", color = TealSuccess)
                        }
                        if (result.videoDisplayName.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "ভিডিও: ${result.videoDisplayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Visual & Audio breakdown
                        if (result.visualSummaryBn.isNotBlank() || result.audioSummaryBn.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = NavySurfaceHigh,
                                border = BorderStroke(1.dp, NavyBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (result.visualSummaryBn.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "দৃশ্য: ${result.visualSummaryBn}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    if (result.audioSummaryBn.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = Icons.Default.GraphicEq,
                                                contentDescription = null,
                                                tint = TealSuccess,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "অডিও: ${result.audioSummaryBn}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3-Platform Switcher
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "প্ল্যাটফর্ম বেছে নিন:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(ShortsPlatform.entries) { _, platform ->
                                val selected = activePlatform == platform
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectPlatform(platform) },
                                    label = { Text(platform.shortTabBn) },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TealSuccess.copy(alpha = 0.24f),
                                        selectedLabelColor = MaterialTheme.colorScheme.onBackground
                                    ),
                                    modifier = Modifier.testTag("shorts_platform_${platform.id}")
                                )
                            }
                        }

                        // Title suggestions for active platform
                        if (activePack.titles.size > 1) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "${activePlatform.labelBn} টাইটেল অপশন (${activePack.titles.size}টি):",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                activePack.titles.forEachIndexed { index, option ->
                                    val active = activePack.selectedTitle == option
                                    OutlinedButton(
                                        onClick = { onSelectTitleOption(option) },
                                        border = BorderStroke(
                                            1.dp,
                                            if (active) MaterialTheme.colorScheme.primary else NavyBorder
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (active) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                            } else NavySurfaceHigh
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("shorts_title_option_$index")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (active) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = option,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = activePack.selectedTitle,
                            onValueChange = onTitleEdited,
                            label = { Text("${activePlatform.shortTabBn} টাইটেল (সম্পাদনাযোগ্য)") },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("shorts_editable_title")
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                YouTubeLaunchHelper.copyToClipboard(
                                    context,
                                    "${activePlatform.labelBn} Title",
                                    activePack.selectedTitle
                                )
                                showToastMsg("${activePlatform.shortTabBn} টাইটেল কপি হয়েছে!")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("shorts_copy_title_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("টাইটেল কপি করুন")
                        }

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = activePack.description,
                            onValueChange = onDescriptionEdited,
                            label = { Text("${activePlatform.shortTabBn} ডেসক্রিপশন ও হ্যাশট্যাগ") },
                            minLines = 5,
                            maxLines = 10,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("shorts_editable_description")
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    YouTubeLaunchHelper.copyToClipboard(
                                        context,
                                        "${activePlatform.labelBn} Description",
                                        activePack.description
                                    )
                                    showToastMsg("ডেসক্রিপশন ও হ্যাশট্যাগ কপি হয়েছে!")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("shorts_copy_desc_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("ডেসক্রিপশন")
                            }
                            OutlinedButton(
                                onClick = {
                                    val combined = "${activePack.selectedTitle}\n\n${activePack.description}"
                                    YouTubeLaunchHelper.copyToClipboard(context, "Caption All", combined)
                                    showToastMsg("টাইটেল ও ডেসক্রিপশন একসাথে কপি হয়েছে!")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("shorts_copy_all_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("সব কপি")
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "৩. সরাসরি ${activePlatform.labelBn}-এ পাঠান",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))

                        val effectiveVideoUri = uiState.selectedVideoUri ?: result.videoUriString
                        Button(
                            onClick = {
                                val textToCopy = if (activePlatform == ShortsPlatform.YOUTUBE_SHORTS) {
                                    activePack.selectedTitle
                                } else {
                                    "${activePack.selectedTitle}\n\n${activePack.description}"
                                }
                                YouTubeLaunchHelper.copyToClipboard(
                                    context,
                                    activePlatform.labelBn,
                                    textToCopy
                                )
                                val parsed = effectiveVideoUri.takeIf { it.isNotBlank() }?.let {
                                    runCatching { Uri.parse(it) }.getOrNull()
                                }
                                if (parsed == null) {
                                    showToastMsg("ভিডিও ফাইল পুনরায় বাছাই করুন (ক্যাপশন কপি করা হয়েছে)।")
                                } else {
                                    val launched = YouTubeLaunchHelper.shareVideoToPlatform(
                                        context,
                                        parsed,
                                        activePlatform
                                    )
                                    if (launched) {
                                        showToastMsg("লেখা কপি করে ${activePlatform.labelBn}-এ ভিডিও পাঠানো হয়েছে!")
                                    } else {
                                        showToastMsg("${activePlatform.labelBn} খোলা যায়নি; ভিডিও পুনরায় বাছাই করে চেষ্টা করুন।")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("shorts_send_to_platform_button")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("ভিডিওসহ ${activePlatform.shortTabBn}-এ পাঠান")
                        }

                        if (activePlatform == ShortsPlatform.YOUTUBE_SHORTS) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    YouTubeLaunchHelper.copyToClipboard(
                                        context,
                                        "Shorts Description",
                                        activePack.description
                                    )
                                    val opened = YouTubeLaunchHelper.openYouTubeStudio(context)
                                    if (opened) {
                                        showToastMsg("ডেসক্রিপশন কপি করে YouTube Studio খোলা হয়েছে!")
                                    } else {
                                        showToastMsg("ডেসক্রিপশন কপি হয়েছে! ফোনে YouTube Studio অ্যাপ ইনস্টল করা নেই।")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("shorts_open_yt_studio_button")
                            ) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("ডেসক্রিপশন কপি ও YouTube Studio খুলুন")
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        if (isPhoneControlReady) {
                            Button(
                                onClick = onStartVoiceAssistCall,
                                colors = ButtonDefaults.buttonColors(containerColor = TealSuccess),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("shorts_call_ayesha_fill_button")
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("আয়শাকে কল করে বসাতে বলুন")
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "টিপস: আপলোড স্ক্রিনে গিয়ে কল থেকে বলুন — \"আয়শা, শেষ ভিডিওটার টাইটেল আর ডেসক্রিপশন এখানে বসাও\"।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AmberWarning.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "আয়শাকে দিয়ে নিজেই YouTube/FB/TikTok-এ টাইপ করাতে চাইলে ফোন নিয়ন্ত্রণ ও অ্যাকসেসিবিলিটি চালু করুন।",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = onOpenPhoneControlSettings,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("shorts_enable_phone_control_button")
                                    ) {
                                        Text("ফোন নিয়ন্ত্রণ চালু করুন")
                                    }
                                }
                            }
                        }
                    }
                }

                SnackbarHost(hostState = snackbarHostState)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
