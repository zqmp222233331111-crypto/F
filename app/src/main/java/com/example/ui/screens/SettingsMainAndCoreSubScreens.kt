package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.UserSettings
import com.example.data.VoiceMood
import com.example.data.db.CustomCharacterEntity
import com.example.data.db.MemoryEntity
import com.example.persona.AyeshaMode
import com.example.persona.VoiceCatalog
import com.example.persona.VoiceOption
import com.example.ui.ApiKeyVerifyUiState
import com.example.ui.components.AyeshaBackground
import com.example.ui.components.AyeshaCard
import com.example.ui.components.AyeshaIcons
import com.example.ui.components.CustomCharacterEditorDialog
import com.example.ui.components.StatusBadge
import com.example.ui.components.SubScreenTopBar
import com.example.ui.components.SystemIntentUtils
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceHigh
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TealSuccess

enum class SettingsSubPage(val titleBn: String) {
    CONNECTION("সংযোগ ও API কী"),
    VOICE("কণ্ঠ ও নমুনা"),
    PERSONA("ব্যক্তিত্ব ও চরিত্র"),
    MEMORY("স্মৃতি ব্যবস্থাপনা"),
    PHONE_CONTROL("ফোন নিয়ন্ত্রণ"),
    SAFETY("নিরাপত্তা ও ব্লকলিস্ট"),
    PERMISSIONS_BATTERY("পারমিশন ও ব্যাটারি"),
    THEME_ABOUT("থিম ও তথ্য")
}

@Composable
fun SettingsHomeScreen(
    settings: UserSettings,
    isKeyVerified: Boolean,
    onOpenSubPage: (SettingsSubPage) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMode = AyeshaMode.fromId(settings.selectedModeId)
    val activeVoice = VoiceCatalog.findByName(settings.selectedVoice)

    AyeshaBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "সেটিংস",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                SettingsGroupItem(
                    icon = AyeshaIcons.Key,
                    title = "১. সংযোগ",
                    subtitle = if (isKeyVerified) "API কী প্রস্তুত" else "API কী যোগ করুন",
                    accent = if (isKeyVerified) TealSuccess else AmberWarning,
                    testTag = "settings_card_connection",
                    onClick = { onOpenSubPage(SettingsSubPage.CONNECTION) }
                )
                SettingsGroupItem(
                    icon = AyeshaIcons.GraphicEq,
                    title = "২. কণ্ঠ",
                    subtitle = activeVoice.labelBn,
                    accent = MaterialTheme.colorScheme.primary,
                    testTag = "settings_card_voice",
                    onClick = { onOpenSubPage(SettingsSubPage.VOICE) }
                )
                SettingsGroupItem(
                    icon = Icons.Default.Face,
                    title = "৩. ব্যক্তিত্ব",
                    subtitle = "মোড: ${activeMode.titleBn} • ${settings.preferredLanguage}",
                    accent = MaterialTheme.colorScheme.secondary,
                    testTag = "settings_card_persona",
                    onClick = { onOpenSubPage(SettingsSubPage.PERSONA) }
                )
                SettingsGroupItem(
                    icon = AyeshaIcons.Psychology,
                    title = "৪. স্মৃতি",
                    subtitle = if (settings.memoryEnabled) "স্মৃতি সংরক্ষণ চালু" else "স্মৃতি সংরক্ষণ বন্ধ",
                    accent = if (settings.memoryEnabled) TealSuccess else AmberWarning,
                    testTag = "settings_card_memory",
                    onClick = { onOpenSubPage(SettingsSubPage.MEMORY) }
                )
                SettingsGroupItem(
                    icon = AyeshaIcons.PhoneAndroid,
                    title = "৫. ফোন নিয়ন্ত্রণ",
                    subtitle = if (settings.phoneControlMasterEnabled) "প্রধান সুইচ চালু" else "বন্ধ আছে",
                    accent = if (settings.phoneControlMasterEnabled) TealSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                    testTag = "settings_card_phone_control",
                    onClick = { onOpenSubPage(SettingsSubPage.PHONE_CONTROL) }
                )
                SettingsGroupItem(
                    icon = AyeshaIcons.Security,
                    title = "৬. নিরাপত্তা",
                    subtitle = "অনুমতি নিশ্চিতকরণ ও ব্লক করা অ্যাপ",
                    accent = AmberWarning,
                    testTag = "settings_card_safety",
                    onClick = { onOpenSubPage(SettingsSubPage.SAFETY) }
                )
                SettingsGroupItem(
                    icon = AyeshaIcons.BatterySaver,
                    title = "৭. পারমিশন ও ব্যাটারি",
                    subtitle = "অনুমতির অবস্থা ও Samsung নির্দেশিকা",
                    accent = TealSuccess,
                    testTag = "settings_card_permissions",
                    onClick = { onOpenSubPage(SettingsSubPage.PERMISSIONS_BATTERY) }
                )
                SettingsGroupItem(
                    icon = AyeshaIcons.Palette,
                    title = "৮. থিম ও তথ্য",
                    subtitle = "রঙ: ${settings.accentTheme.labelBn}, সাহায্য ও ডেটা মুছুন",
                    accent = MaterialTheme.colorScheme.primary,
                    testTag = "settings_card_theme",
                    onClick = { onOpenSubPage(SettingsSubPage.THEME_ABOUT) }
                )

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SettingsGroupItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsConnectionSubScreen(
    isKeyVerified: Boolean,
    maskedPreview: String,
    verifyState: ApiKeyVerifyUiState,
    onVerifyAndSave: (String) -> Unit,
    onDeleteKey: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var newKey by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    AyeshaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SubScreenTopBar(title = "সংযোগ ও API কী", onBack = onBack)
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AyeshaCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("বর্তমান অবস্থা", style = MaterialTheme.typography.titleMedium)
                        StatusBadge(
                            label = if (isKeyVerified) "প্রস্তুত" else "কী প্রয়োজন",
                            color = if (isKeyVerified) TealSuccess else AmberWarning
                        )
                    }
                    if (maskedPreview.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("সংরক্ষিত কী: $maskedPreview", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                AyeshaCard {
                    Text("কী পরিবর্তন বা পুনরায় যাচাই করুন", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("নতুন Gemini API কী") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (verifyState is ApiKeyVerifyUiState.Error) {
                        Spacer(Modifier.height(6.dp))
                        Text(verifyState.messageBn, color = RoseDanger, style = MaterialTheme.typography.bodyMedium)
                    } else if (verifyState is ApiKeyVerifyUiState.Verified) {
                        Spacer(Modifier.height(6.dp))
                        Text("সফলভাবে যাচাই ও সেভ হয়েছে।", color = TealSuccess, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onVerifyAndSave(newKey) },
                            enabled = verifyState !is ApiKeyVerifyUiState.Verifying,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (verifyState is ApiKeyVerifyUiState.Verifying) "যাচাই হচ্ছে..." else "যাচাই ও সেভ")
                        }
                        if (isKeyVerified) {
                            OutlinedButton(
                                onClick = { confirmDelete = true },
                                border = BorderStroke(1.dp, RoseDanger)
                            ) {
                                Text("কী মুছুন", color = RoseDanger)
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { SystemIntentUtils.openUrl(context, "https://aistudio.google.com/apikey") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(AyeshaIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Google AI Studio থেকে ফ্রি কী নিন")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = NavySurfaceHigh,
            title = { Text("API কী মুছে ফেলবেন?") },
            text = { Text("কী মুছে ফেললে কল সুবিধা বন্ধ থাকবে যতক্ষণ না নতুন কী যাচাই করেন।") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDeleteKey()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseDanger)
                ) {
                    Text("মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("বাতিল") }
            }
        )
    }
}

@Composable
fun SettingsVoiceSubScreen(
    selectedVoice: String,
    playingVoiceName: String?,
    sampleError: String?,
    onSelectVoice: (String) -> Unit,
    onPlaySample: (VoiceOption) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    AyeshaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SubScreenTopBar(title = "কণ্ঠ নির্বাচন (${VoiceCatalog.femaleVoices.size}টি)", onBack = onBack)
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!sampleError.isNullOrBlank()) {
                    AyeshaCard(highlightBorder = RoseDanger) {
                        Text(sampleError, color = RoseDanger, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Text(
                    text = "নারী কণ্ঠসমূহ (${VoiceCatalog.femaleVoices.size}টি)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                VoiceCatalog.femaleVoices.forEach { voice ->
                    VoiceRowCard(
                        voice = voice,
                        selected = selectedVoice.equals(voice.name, ignoreCase = true),
                        isPlaying = playingVoiceName == voice.name,
                        onSelect = { onSelectVoice(voice.name) },
                        onPlay = { onPlaySample(voice) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceRowCard(
    voice: VoiceOption,
    selected: Boolean,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit
) {
    AyeshaCard(
        highlightBorder = if (selected) MaterialTheme.colorScheme.primary else NavyBorder,
        modifier = Modifier.clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${voice.labelBn} (${voice.name})", style = MaterialTheme.typography.titleMedium)
                Text(voice.descriptionBn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                StatusBadge(label = "নির্বাচিত", color = TealSuccess)
                Spacer(Modifier.width(6.dp))
            }
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("play_voice_${voice.name}")
            ) {
                Icon(
                    imageVector = if (isPlaying) AyeshaIcons.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "থামান" else "নমুনা শুনুন",
                    tint = if (isPlaying) RoseDanger else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SettingsPersonaSubScreen(
    settings: UserSettings,
    customCharacters: List<CustomCharacterEntity>,
    onSelectMode: (AyeshaMode, String?) -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetEmotionLevel: (Float) -> Unit,
    onSetVoiceMood: (VoiceMood) -> Unit,
    onSetTurnPauseMs: (Int) -> Unit,
    onSaveCustomCharacter: (
        existingId: String?,
        name: String,
        role: String,
        callUserAs: String,
        lang: String,
        voice: String,
        interests: String,
        desc: String,
        funLevel: Float,
        emotion: Float,
        seriousness: Float,
        talkativeness: Float
    ) -> Unit,
    onDeleteCustomCharacter: (String) -> Unit,
    onTestCustomCharacter: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var editingCharacter by remember { mutableStateOf<CustomCharacterEntity?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }

    AyeshaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SubScreenTopBar(title = "ব্যক্তিত্ব ও কাস্টম চরিত্র", onBack = onBack)
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AyeshaCard {
                    Text("পছন্দের ভাষা", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("বাংলাদেশি বাংলা", "English", "বাংলা + English").forEach { lang ->
                            FilterChip(
                                selected = settings.preferredLanguage == lang,
                                onClick = { onSetLanguage(lang) },
                                label = { Text(lang) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("আবেগের মাত্রা: ${(settings.emotionLevel * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.emotionLevel,
                        onValueChange = onSetEmotionLevel
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("কণ্ঠের মেজাজ ও ভঙ্গি", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VoiceMood.entries, key = { it.id }) { mood ->
                            FilterChip(
                                selected = settings.voiceMood == mood,
                                onClick = { onSetVoiceMood(mood) },
                                label = { Text(mood.labelBn) }
                            )
                        }
                    }
                    Text(
                        text = settings.voiceMood.descriptionBn,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "কথা শোনার বিরতি: ${settings.turnPauseMs} ms",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = settings.turnPauseMs.toFloat(),
                        onValueChange = { onSetTurnPauseMs(it.toInt()) },
                        valueRange = 500f..1800f
                    )
                    Text(
                        text = "বেশি হলে আপনি থেমে শ্বাস নিলেও আয়শা কথা কাটবে না। পরিবর্তন পরবর্তী কল থেকে কার্যকর হবে।",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AyeshaCard {
                    Text("সকল মোড", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AyeshaMode.entries.forEach { mode ->
                            val selected = settings.selectedModeId == mode.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectMode(mode, settings.selectedCustomCharacterId) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mode.titleBn, style = MaterialTheme.typography.titleMedium)
                                    Text(mode.subtitleBn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (selected) {
                                    StatusBadge(label = "সক্রিয়", color = TealSuccess)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("কাস্টম চরিত্র (${customCharacters.size})", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            editingCharacter = null
                            showEditorDialog = true
                        },
                        modifier = Modifier.testTag("create_custom_character_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("নতুন চরিত্র")
                    }
                }

                customCharacters.forEach { ch ->
                    AyeshaCard(
                        highlightBorder = if (settings.selectedModeId == AyeshaMode.CUSTOM.id && settings.selectedCustomCharacterId == ch.id) {
                            MaterialTheme.colorScheme.primary
                        } else NavyBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${ch.name} • ${ch.relationshipRole}", style = MaterialTheme.typography.titleMedium)
                                Text("কণ্ঠ: ${ch.voiceName} • ভাষা: ${ch.language}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onTestCustomCharacter(ch.id) }) {
                                Icon(Icons.Default.Call, contentDescription = "টেস্ট কল", tint = TealSuccess)
                            }
                            IconButton(onClick = {
                                editingCharacter = ch
                                showEditorDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "সম্পাদনা")
                            }
                            IconButton(onClick = { onDeleteCustomCharacter(ch.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "মুছুন", tint = RoseDanger)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        CustomCharacterEditorDialog(
            initial = editingCharacter,
            onDismiss = { showEditorDialog = false },
            onSave = { name, role, callUserAs, lang, voice, interests, desc, funL, emoL, serL, talkL ->
                onSaveCustomCharacter(
                    editingCharacter?.id,
                    name, role, callUserAs, lang, voice, interests, desc, funL, emoL, serL, talkL
                )
                showEditorDialog = false
            }
        )
    }
}

@Composable
fun SettingsMemorySubScreen(
    memoryEnabled: Boolean,
    memories: List<MemoryEntity>,
    customCharacters: List<CustomCharacterEntity>,
    onToggleMemory: (Boolean) -> Unit,
    onUpdateMemory: (MemoryEntity, String) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onDeleteAllMemories: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var editingMem by remember { mutableStateOf<MemoryEntity?>(null) }
    var editText by remember { mutableStateOf("") }

    fun characterLabel(characterId: String): String {
        if (characterId.startsWith("mode_")) {
            val modeId = characterId.removePrefix("mode_")
            return AyeshaMode.fromId(modeId).titleBn
        }
        return customCharacters.firstOrNull { it.id == characterId }?.name ?: characterId
    }

    AyeshaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SubScreenTopBar(title = "স্মৃতি ব্যবস্থাপনা", onBack = onBack)
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AyeshaCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("স্থানীয় স্মৃতি সংরক্ষণ", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "শুধু এই ফোনে সংরক্ষিত থাকে। পাসওয়ার্ড, পিন বা ওটিপি কখনোই সংরক্ষণ হয় না।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = memoryEnabled, onCheckedChange = onToggleMemory)
                    }
                }

                if (memories.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("সংরক্ষিত তথ্য (${memories.size})", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onDeleteAllMemories) {
                            Text("সব মুছুন", color = RoseDanger)
                        }
                    }
                    memories.forEach { mem ->
                        AyeshaCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mem.factKey, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(mem.factValue, style = MaterialTheme.typography.bodyLarge)
                                    Text("চরিত্র: ${characterLabel(mem.characterId)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    editingMem = mem
                                    editText = mem.factValue
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "সম্পাদনা")
                                }
                                IconButton(onClick = { onDeleteMemory(mem.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "মুছুন", tint = RoseDanger)
                                }
                            }
                        }
                    }
                } else {
                    AyeshaCard {
                        Text(
                            text = "এখনো কোনো স্মৃতি জমা হয়নি। কল চলাকালে আপনার নাম, পছন্দ বা গুরুত্বপূর্ণ ঘটনার কথা বললে আয়শা মনে রাখবে।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    editingMem?.let { mem ->
        AlertDialog(
            onDismissRequest = { editingMem = null },
            containerColor = NavySurfaceHigh,
            title = { Text("স্মৃতি সম্পাদনা (${mem.factKey})") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("তথ্য") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateMemory(mem, editText)
                    editingMem = null
                }) { Text("আপডেট") }
            },
            dismissButton = {
                TextButton(onClick = { editingMem = null }) { Text("বাতিল") }
            }
        )
    }
}
