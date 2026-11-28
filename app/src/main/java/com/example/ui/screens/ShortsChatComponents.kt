package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.AyeshaCard
import com.example.ui.components.AyeshaIcons
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceHigh
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TealSuccess
import com.example.video.CreatorChatMessage
import com.example.video.ShortsAnalysisResult
import com.example.video.ShortsPlatform
import com.example.video.ShortsSubTab
import com.example.video.YouTubeLaunchHelper

@Composable
fun CreatorCoachWelcomeBlock(
    onQuickPrompt: (String) -> Unit,
    onPickVideo: () -> Unit
) {
    val starterPrompts = listOf(
        "৩ সেকেন্ডের ভাইরাল হুক লিখে দাও",
        "TikTok ক্যাপশন ও হ্যাশট্যাগ সাজিয়ে দাও",
        "আমার শর্টস স্ক্রিপ্ট ভালো করে দাও",
        "শর্টস ভিডিওতে ভিউ ড্রপ কমানোর উপায় বলো"
    )

    AyeshaCard(highlightBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AyeshaIcons.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "আয়শা ক্রিয়েটার কোচ চ্যাটে স্বাগতম",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "এখানে চ্যাটে লিখে বা নিচের `+` বাটনে ভিডিও দিয়ে স্ক্রিপ্ট, হুক, টাইটেল, ক্যাপশন ও ৩ প্ল্যাটফর্ম প্যাক তৈরি করে নিতে পারেন। চাইলে ওপরে কল আইকন থেকে সরাসরি অডিও বা ভিডিও কলেও কথা বলতে পারেন।",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onPickVideo,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("shorts_welcome_pick_video")
        ) {
            Icon(AyeshaIcons.VideoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("ভিডিও অ্যাটাচ করে বিশ্লেষণ করুন")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "দ্রুত শুরু করতে একটি প্রশ্ন বেছে নিন:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            starterPrompts.forEachIndexed { idx, prompt ->
                OutlinedButton(
                    onClick = { onQuickPrompt(prompt) },
                    border = BorderStroke(1.dp, NavyBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = NavySurfaceHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shorts_starter_prompt_$idx")
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun CreatorCoachChatBubbleItem(
    message: CreatorChatMessage,
    activeLatestResult: ShortsAnalysisResult?,
    selectedVideoUriFallback: String?,
    isPhoneControlReady: Boolean,
    onSelectSubTab: (ShortsSubTab) -> Unit,
    onTrendNicheChange: (String) -> Unit,
    onFetchTrends: (String?) -> Unit,
    onSelectPlatform: (ShortsPlatform) -> Unit,
    onSelectTitleOption: (String) -> Unit,
    onTitleEdited: (String) -> Unit,
    onDescriptionEdited: (String) -> Unit,
    onStartVoiceAssistCall: () -> Unit,
    onOpenPhoneControlSettings: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current

    if (message.isUser) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!message.attachedVideoName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NavySurfaceHigh,
                            border = BorderStroke(1.dp, TealSuccess.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AyeshaIcons.Movie,
                                    contentDescription = null,
                                    tint = TealSuccess,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = message.attachedVideoName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    if (!message.attachedVideoMeta.isNullOrBlank()) {
                                        Text(
                                            text = message.attachedVideoMeta,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (message.textBn.isNotBlank()) {
                        Text(
                            text = message.textBn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = if (message.isError) RoseDanger.copy(alpha = 0.14f) else NavySurface,
                border = BorderStroke(
                    1.dp,
                    if (message.isError) RoseDanger.copy(alpha = 0.55f) else NavyBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(
                            label = if (message.isError) "সমস্যা" else "আয়শা কোচ",
                            color = if (message.isError) RoseDanger else TealSuccess
                        )
                        TextButton(
                            onClick = {
                                YouTubeLaunchHelper.copyToClipboard(context, "Coach Reply", message.textBn)
                                onShowMessage("উত্তর কপি হয়েছে!")
                            },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(AyeshaIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("কপি", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        text = message.textBn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // If this coach reply includes a video analysis report, render the interactive cards inline in chat
            val msgResult = message.analysisResult
            if (msgResult != null) {
                val effectiveResult = if (
                    activeLatestResult != null &&
                    activeLatestResult.analyzedAtMillis == msgResult.analyzedAtMillis
                ) {
                    activeLatestResult
                } else {
                    msgResult
                }

                effectiveResult.diagnostics?.let { diag ->
                    VideoDoctorDiagnosticsCard(
                        diagnostics = diag,
                        onCheckTrendsForNiche = { niche ->
                            onSelectSubTab(ShortsSubTab.TRENDS)
                            onTrendNicheChange(niche)
                            onFetchTrends(niche)
                        },
                        onShowMessage = onShowMessage
                    )
                }

                ShortsPlatformPackCard(
                    result = effectiveResult,
                    selectedVideoUriFallback = selectedVideoUriFallback,
                    isPhoneControlReady = isPhoneControlReady,
                    onSelectPlatform = onSelectPlatform,
                    onSelectTitleOption = onSelectTitleOption,
                    onTitleEdited = onTitleEdited,
                    onDescriptionEdited = onDescriptionEdited,
                    onStartVoiceAssistCall = onStartVoiceAssistCall,
                    onOpenPhoneControlSettings = onOpenPhoneControlSettings,
                    onShowMessage = onShowMessage
                )
            }
        }
    }
}

@Composable
fun ShortsPlatformPackCard(
    result: ShortsAnalysisResult,
    selectedVideoUriFallback: String?,
    isPhoneControlReady: Boolean,
    onSelectPlatform: (ShortsPlatform) -> Unit,
    onSelectTitleOption: (String) -> Unit,
    onTitleEdited: (String) -> Unit,
    onDescriptionEdited: (String) -> Unit,
    onStartVoiceAssistCall: () -> Unit,
    onOpenPhoneControlSettings: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val activePlatform = result.activePlatform
    val activePack = result.activePack
    val isYouTube = activePlatform == ShortsPlatform.YOUTUBE_SHORTS

    AyeshaCard(highlightBorder = TealSuccess.copy(alpha = 0.55f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "৩ প্ল্যাটফর্মের তৈরি প্যাক",
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

        if (result.visualSummaryBn.isNotBlank() || result.audioSummaryBn.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NavySurfaceHigh,
                border = BorderStroke(1.dp, NavyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (result.visualSummaryBn.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = AyeshaIcons.Visibility,
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
                                imageVector = AyeshaIcons.GraphicEq,
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

        Spacer(Modifier.height(12.dp))
        Text("প্ল্যাটফর্ম বেছে নিন:", style = MaterialTheme.typography.labelLarge)
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

        if (activePack.titles.size > 1) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (isYouTube) {
                    "${activePlatform.labelBn} টাইটেল অপশন (${activePack.titles.size}টি):"
                } else {
                    "${activePlatform.labelBn} ক্যাপশন হুক অপশন (${activePack.titles.size}টি):"
                },
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
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
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

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = activePack.selectedTitle,
            onValueChange = onTitleEdited,
            label = {
                Text(
                    if (isYouTube) {
                        "${activePlatform.shortTabBn} টাইটেল (সম্পাদনাযোগ্য)"
                    } else {
                        "${activePlatform.shortTabBn} মূল ক্যাপশন / হুক লাইন (সম্পাদনাযোগ্য)"
                    }
                )
            },
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
                    if (isYouTube) "${activePlatform.labelBn} Title" else "${activePlatform.labelBn} Hook",
                    activePack.selectedTitle
                )
                onShowMessage(
                    if (isYouTube) "${activePlatform.shortTabBn} টাইটেল কপি হয়েছে!"
                    else "${activePlatform.shortTabBn} হুক লাইন কপি হয়েছে!"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("shorts_copy_title_button")
        ) {
            Icon(AyeshaIcons.ContentCopy, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (isYouTube) "টাইটেল কপি করুন" else "হুক লাইন কপি করুন")
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = activePack.description,
            onValueChange = onDescriptionEdited,
            label = {
                Text(
                    if (isYouTube) {
                        "${activePlatform.shortTabBn} ডেসক্রিপশন ও হ্যাশট্যাগ"
                    } else {
                        "${activePlatform.shortTabBn} ক্যাপশন ও হ্যাশট্যাগ"
                    }
                )
            },
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
                        if (isYouTube) "${activePlatform.labelBn} Description" else "${activePlatform.labelBn} Caption",
                        activePack.description
                    )
                    onShowMessage(
                        if (isYouTube) "ডেসক্রিপশন ও হ্যাশট্যাগ কপি হয়েছে!"
                        else "${activePlatform.shortTabBn} ক্যাপশন ও হ্যাশট্যাগ কপি হয়েছে!"
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("shorts_copy_desc_button")
            ) {
                Icon(AyeshaIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isYouTube) "ডেসক্রিপশন" else "ক্যাপশন ও ট্যাগ")
            }
            OutlinedButton(
                onClick = {
                    val combined = "${activePack.selectedTitle}\n\n${activePack.description}"
                    YouTubeLaunchHelper.copyToClipboard(context, "Caption All", combined)
                    onShowMessage(
                        if (isYouTube) "টাইটেল ও ডেসক্রিপশন একসাথে কপি হয়েছে!"
                        else "${activePlatform.shortTabBn}-এর পূর্ণ ক্যাপশন (হুক + ট্যাগ) একসাথে কপি হয়েছে!"
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("shorts_copy_all_button")
            ) {
                Icon(AyeshaIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isYouTube) "সব কপি" else "পূর্ণ ক্যাপশন")
            }
        }

        Spacer(Modifier.height(14.dp))
        val effectiveVideoUri = selectedVideoUriFallback ?: result.videoUriString
        Button(
            onClick = {
                val textToCopy = if (isYouTube) {
                    activePack.selectedTitle
                } else {
                    "${activePack.selectedTitle}\n\n${activePack.description}"
                }
                YouTubeLaunchHelper.copyToClipboard(context, activePlatform.labelBn, textToCopy)
                val parsed = effectiveVideoUri.takeIf { it.isNotBlank() }?.let {
                    runCatching { Uri.parse(it) }.getOrNull()
                }
                if (parsed == null) {
                    onShowMessage("ভিডিও ফাইল পুনরায় বাছাই করুন (ক্যাপশন কপি করা হয়েছে)।")
                } else {
                    val launched = YouTubeLaunchHelper.shareVideoToPlatform(context, parsed, activePlatform)
                    if (launched) {
                        onShowMessage("লেখা কপি করে ${activePlatform.labelBn}-এ ভিডিও পাঠানো হয়েছে!")
                    } else {
                        onShowMessage("${activePlatform.labelBn} খোলা যায়নি; ভিডিও পুনরায় বাছাই করে চেষ্টা করুন।")
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("shorts_send_to_platform_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("ভিডিওসহ ${activePlatform.shortTabBn}-এ পাঠান")
        }

        if (isYouTube) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    YouTubeLaunchHelper.copyToClipboard(context, "Shorts Description", activePack.description)
                    val opened = YouTubeLaunchHelper.openYouTubeStudio(context)
                    if (opened) {
                        onShowMessage("ডেসক্রিপশন কপি করে YouTube Studio খোলা হয়েছে!")
                    } else {
                        onShowMessage("ডেসক্রিপশন কপি হয়েছে! ফোনে YouTube Studio অ্যাপ ইনস্টল করা নেই।")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("shorts_open_yt_studio_button")
            ) {
                Icon(AyeshaIcons.PlayCircle, contentDescription = null)
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
