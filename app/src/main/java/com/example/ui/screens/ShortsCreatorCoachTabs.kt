package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.CreatorAuditEntity
import com.example.ui.components.AyeshaCard
import com.example.ui.components.StatusBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavySurfaceHigh
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TealSuccess
import com.example.video.TrendPlatformFilter
import com.example.video.TrendRadarUiState
import com.example.video.TrendRegion
import com.example.video.VideoDoctorDiagnostics
import com.example.video.YouTubeLaunchHelper

@Composable
fun VideoDoctorDiagnosticsCard(
    diagnostics: VideoDoctorDiagnostics,
    onCheckTrendsForNiche: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scoreColor = when {
        diagnostics.viralScore >= 75 -> TealSuccess
        diagnostics.viralScore >= 50 -> AmberWarning
        else -> RoseDanger
    }

    AyeshaCard(highlightBorder = scoreColor.copy(alpha = 0.6f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ভিডিও ডক্টর রিপোর্ট (vidIQ স্টাইল)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "ধরন: ${diagnostics.detectedCategoryBn}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (diagnostics.targetAudienceScopeBn.isNotBlank()) {
                    Text(
                        text = "দর্শক: ${diagnostics.targetAudienceScopeBn}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = scoreColor.copy(alpha = 0.16f),
                border = BorderStroke(1.5.dp, scoreColor)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${diagnostics.viralScore}/100",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = scoreColor
                    )
                    Text(
                        text = "ভাইরাল স্কোর",
                        style = MaterialTheme.typography.labelSmall,
                        color = scoreColor
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        ScoreMeterRow(label = "প্রথম ৩ সেকেন্ডের হুক", score = diagnostics.hookScore)
        Spacer(Modifier.height(8.dp))
        ScoreMeterRow(label = "গতি ও রিটেনশন", score = diagnostics.pacingScore)

        if (diagnostics.strengthsBn.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            DiagnosticSectionBox(
                title = "শক্তিশালী দিক",
                iconColor = TealSuccess,
                items = diagnostics.strengthsBn,
                isWarning = false
            )
        }

        if (diagnostics.weaknessesBn.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            DiagnosticSectionBox(
                title = "কোথায় সমস্যা / ভিউ ড্রপের ঝুঁকি",
                iconColor = AmberWarning,
                items = diagnostics.weaknessesBn,
                isWarning = true
            )
        }

        if (diagnostics.rescueFixesBn.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "ভিউ বাড়ানোর সমাধান (Rescue Plan)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    diagnostics.rescueFixesBn.forEachIndexed { idx, fix ->
                        Text(
                            text = "${idx + 1}. $fix",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (diagnostics.thumbnailTextBn.isNotBlank() || diagnostics.pinnedCommentBn.isNotBlank() || diagnostics.bestUploadWindowBn.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
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
                    if (diagnostics.thumbnailTextBn.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("কভার/থাম্বনেইল লেখা:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(diagnostics.thumbnailTextBn, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(
                                onClick = {
                                    YouTubeLaunchHelper.copyToClipboard(context, "Cover Text", diagnostics.thumbnailTextBn)
                                    onShowMessage("থাম্বনেইল লেখা কপি হয়েছে!")
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "কভার লেখা কপি", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (diagnostics.pinnedCommentBn.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("পিন করার কমেন্ট:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(diagnostics.pinnedCommentBn, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(
                                onClick = {
                                    YouTubeLaunchHelper.copyToClipboard(context, "Pinned Comment", diagnostics.pinnedCommentBn)
                                    onShowMessage("পিন কমেন্ট কপি হয়েছে!")
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "কমেন্ট কপি", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (diagnostics.bestUploadWindowBn.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = TealSuccess, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "সেরা আপলোড সময়: ${diagnostics.bestUploadWindowBn}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TealSuccess
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { onCheckTrendsForNiche(diagnostics.detectedCategoryBn) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("doctor_check_trends_button")
        ) {
            Icon(Icons.Default.TrendingUp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("এই ধরনের লাইভ ভাইরাল ট্রেন্ড দেখুন")
        }
    }
}

@Composable
private fun ScoreMeterRow(label: String, score: Int) {
    val barColor = when {
        score >= 75 -> TealSuccess
        score >= 50 -> AmberWarning
        else -> RoseDanger
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("$score%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = barColor)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            color = barColor,
            trackColor = NavySurfaceHigh,
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(6.dp))
        )
    }
}

@Composable
private fun DiagnosticSectionBox(
    title: String,
    iconColor: androidx.compose.ui.graphics.Color,
    items: List<String>,
    isWarning: Boolean
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NavySurfaceHigh,
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isWarning) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = iconColor)
            }
            items.forEach { line ->
                Text("• $line", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun TrendRadarTabContent(
    uiState: TrendRadarUiState,
    detectedNicheHint: String,
    isKeyVerified: Boolean,
    onNicheChange: (String) -> Unit,
    onSelectRegion: (TrendRegion) -> Unit,
    onSelectPlatformFilter: (TrendPlatformFilter) -> Unit,
    onFetchTrends: () -> Unit,
    onStartCoachCall: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val quickNiches = listOf(
        "২ডি কার্টুন কমেডি",
        "ইসলামিক রিমাইন্ডার",
        "টেক ও মোবাইল",
        "গেমিং শর্টস",
        "ফানি ভ্লগ",
        "রান্না ও খাবার"
    )

    AyeshaCard(highlightBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "লাইভ ভাইরাল ট্রেন্ড রাডার",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "YouTube Shorts, FB Reels ও TikTok-এ এই মুহূর্তে কী চলছে—লাইভ সার্চ দিয়ে দেখুন।",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = uiState.nicheQuery,
            onValueChange = onNicheChange,
            enabled = !uiState.isLoading,
            label = { Text("আপনার ভিডিওর ধরন বা বিষয় লিখুন") },
            placeholder = { Text("যেমন: ২ডি কার্টুন কমেডি, ইসলামিক বা টেক") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("trend_niche_input")
        )

        if (detectedNicheHint.isNotBlank() && detectedNicheHint != uiState.nicheQuery) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { onNicheChange(detectedNicheHint) }) {
                Text("শেষ ভিডিওর ধরন ব্যবহার করুন: $detectedNicheHint")
            }
        }

        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(quickNiches) { tag ->
                FilterChip(
                    selected = uiState.nicheQuery.equals(tag, ignoreCase = true),
                    onClick = { if (!uiState.isLoading) onNicheChange(tag) },
                    label = { Text(tag) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("দর্শকের অঞ্চল:", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendRegion.entries.forEach { region ->
                FilterChip(
                    selected = uiState.region == region,
                    onClick = { if (!uiState.isLoading) onSelectRegion(region) },
                    label = { Text(region.labelBn) },
                    modifier = Modifier.testTag("trend_region_${region.id}")
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("প্ল্যাটফর্ম:", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TrendPlatformFilter.entries) { filter ->
                FilterChip(
                    selected = uiState.platformFilter == filter,
                    onClick = { if (!uiState.isLoading) onSelectPlatformFilter(filter) },
                    label = { Text(filter.labelBn) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TealSuccess.copy(alpha = 0.22f)
                    ),
                    modifier = Modifier.testTag("trend_platform_${filter.id}")
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (!isKeyVerified) onOpenConnectionSettings() else onFetchTrends()
            },
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("trend_fetch_button")
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(10.dp))
                Text("লাইভ ট্রেন্ড খোঁজা হচ্ছে...")
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("আজকের ভাইরাল ট্রেন্ড খুঁজুন")
            }
        }

        AnimatedVisibility(visible = !uiState.errorMessageBn.isNullOrBlank()) {
            Column {
                Spacer(Modifier.height(10.dp))
                StatusBadge(label = uiState.errorMessageBn.orEmpty(), color = RoseDanger)
            }
        }
    }

    val result = uiState.result
    if (result != null) {
        AyeshaCard(highlightBorder = TealSuccess.copy(alpha = 0.55f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ট্রেন্ড রিপোর্ট: ${result.nicheQuery}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(label = result.region.labelBn, color = TealSuccess)
            }
            Spacer(Modifier.height(8.dp))
            Text(result.trendSummaryBn, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "সেরা সময়: ${result.bestTimingBn}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberWarning
                )
            }

            if (result.ideas.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    result.ideas.forEach { idea ->
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = idea.topicTitleBn,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatusBadge(label = idea.platformFit, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(
                                    text = "প্রথম ৩ সেকেন্ড হুক: ${idea.hookIdeaBn}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TealSuccess
                                )
                                if (idea.whyTrendingBn.isNotBlank()) {
                                    Text(idea.whyTrendingBn, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "প্রতিযোগিতা: ${idea.competitionLevelBn}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberWarning
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            val text = "${idea.topicTitleBn}\nহুক: ${idea.hookIdeaBn}\n${idea.viralTags.joinToString(" ")}"
                                            YouTubeLaunchHelper.copyToClipboard(context, "Trend Idea", text)
                                            onShowMessage("ট্রেন্ড আইডিয়া ও ট্যাগ কপি হয়েছে!")
                                        },
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("কপি", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStartCoachCall,
                colors = ButtonDefaults.buttonColors(containerColor = TealSuccess),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("trend_call_coach_button")
            ) {
                Icon(Icons.Default.Call, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("কল করে স্ক্রিনে ট্রেন্ড ও অ্যানালিটিক্স দেখান")
            }
        }
    }
}

@Composable
fun CreatorGrowthLogTabContent(
    audits: List<CreatorAuditEntity>,
    onDeleteAudit: (Long) -> Unit,
    onDeleteAllAudits: () -> Unit,
    onStartCoachCall: () -> Unit
) {
    AyeshaCard(highlightBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ক্রিয়েটর গ্রোথ লগ (${audits.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "আগের ভিডিও স্কোর ও YouTube Studio অডিট এখানে জমা থাকে যাতে আয়শা পরবর্তী কলে উন্নতি তুলনা করতে পারে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (audits.isNotEmpty()) {
                IconButton(onClick = onDeleteAllAudits) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "সব লগ মুছুন", tint = RoseDanger)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onStartCoachCall,
            colors = ButtonDefaults.buttonColors(containerColor = TealSuccess),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("growth_call_studio_coach_button")
        ) {
            Icon(Icons.Default.Call, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("কলে গিয়ে YouTube Studio অ্যানালিটিক্স দেখান")
        }
    }

    if (audits.isEmpty()) {
        AyeshaCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Text("এখনো কোনো অডিট বা স্কোর লগ নেই", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "ভিডিও ডক্টর দিয়ে ভিডিও বিশ্লেষণ করলে বা কলের সময় স্ক্রিন শেয়ারে YouTube Studio দেখালে এখানে তুলনামূলক লগ জমা হবে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        audits.forEach { audit ->
            val badgeLabel = when (audit.source) {
                "VIDEO_DOCTOR" -> "ভিডিও ডক্টর"
                "STUDIO_CALL" -> "স্টুডিও কল অডিট"
                "TREND_CHECK" -> "ট্রেন্ড সার্চ"
                else -> audit.platform
            }
            AyeshaCard(highlightBorder = NavyBorder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(label = badgeLabel, color = MaterialTheme.colorScheme.primary)
                        if (audit.viralScore >= 0) {
                            val scoreClr = if (audit.viralScore >= 70) TealSuccess else AmberWarning
                            StatusBadge(label = "স্কোর ${audit.viralScore}/100", color = scoreClr)
                        }
                    }
                    IconButton(onClick = { onDeleteAudit(audit.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "লগ মুছুন", tint = RoseDanger, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text = audit.videoOrTopic,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val formattedDate = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    .format(Date(audit.timestamp))
                Text(
                    text = "${audit.platform} • $formattedDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (audit.viewsCount >= 0 || audit.ctrPercent >= 0f || audit.retentionPercent >= 0f) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (audit.viewsCount >= 0) {
                            StatusBadge(label = "ভিউ: ${audit.viewsCount}", color = TealSuccess)
                        }
                        if (audit.ctrPercent >= 0f) {
                            StatusBadge(label = "CTR: ${audit.ctrPercent}%", color = AmberWarning)
                        }
                        if (audit.retentionPercent >= 0f) {
                            StatusBadge(label = "রিটেনশন: ${audit.retentionPercent}%", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (audit.problemSummaryBn.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "সমস্যা/পর্যবেক্ষণ: ${audit.problemSummaryBn}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (audit.actionTakenBn.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "সমাধান/অ্যাকশন: ${audit.actionTakenBn}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TealSuccess
                    )
                }
            }
        }
    }
}
