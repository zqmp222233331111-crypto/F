package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.UserSettings
import com.example.data.db.CustomCharacterEntity
import com.example.persona.AyeshaMode
import com.example.persona.VoiceCatalog
import com.example.ui.components.AyeshaBackground
import com.example.ui.components.AyeshaCard
import com.example.ui.components.AyeshaIcons
import com.example.ui.components.AyeshaOrb
import com.example.ui.components.CustomCharacterEditorDialog
import com.example.ui.components.MAX_CUSTOM_DESCRIPTION_CHARS
import com.example.ui.components.OrbVisualState
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.TealSuccess
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    settings: UserSettings,
    customCharacters: List<CustomCharacterEntity>,
    isKeyVerified: Boolean,
    onSelectMode: (AyeshaMode, String?) -> Unit,
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
        emotionLevel: Float,
        seriousnessLevel: Float,
        talkativenessLevel: Float,
        onSaved: () -> Unit
    ) -> Unit,
    onOpenPersonaSettings: () -> Unit,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeMode = AyeshaMode.fromId(settings.selectedModeId)
    val activeCustomChar = customCharacters.firstOrNull { it.id == settings.selectedCustomCharacterId }
        ?: customCharacters.firstOrNull()
    val activeVoice = VoiceCatalog.findByName(
        if (activeMode == AyeshaMode.CUSTOM && activeCustomChar != null) activeCustomChar.voiceName
        else settings.selectedVoice
    )

    // Inline custom character draft state on HomeScreen so clicking "কাস্টম" immediately shows an editable 10,000-char writing box
    var draftName by remember { mutableStateOf("আয়শা") }
    var draftRole by remember { mutableStateOf("বন্ধু") }
    var draftCallUserAs by remember { mutableStateOf("") }
    var draftDescription by remember { mutableStateOf("") }

    LaunchedEffect(activeCustomChar?.id, activeCustomChar?.name, activeCustomChar?.relationshipRole, activeCustomChar?.callUserAs, activeCustomChar?.description) {
        draftName = activeCustomChar?.name ?: "আয়শা"
        draftRole = activeCustomChar?.relationshipRole ?: "বন্ধু"
        draftCallUserAs = activeCustomChar?.callUserAs ?: ""
        draftDescription = (activeCustomChar?.description ?: "").take(MAX_CUSTOM_DESCRIPTION_CHARS)
    }

    val hasUnsavedCustomChanges = activeMode == AyeshaMode.CUSTOM && (
        draftName.trim() != (activeCustomChar?.name ?: "আয়শা") ||
            draftRole.trim() != (activeCustomChar?.relationshipRole ?: "বন্ধু") ||
            draftCallUserAs.trim() != (activeCustomChar?.callUserAs ?: "") ||
            draftDescription.trim() != (activeCustomChar?.description ?: "")
        )

    var showCustomDialog by remember { mutableStateOf(false) }
    var dialogInitialChar by remember { mutableStateOf<CustomCharacterEntity?>(null) }

    fun persistInlineDraftIfNeeded(thenRun: () -> Unit) {
        if (hasUnsavedCustomChanges || (activeMode == AyeshaMode.CUSTOM && activeCustomChar == null && draftDescription.isNotBlank())) {
            onSaveCustomCharacter(
                activeCustomChar?.id,
                draftName.ifBlank { "আয়শা" },
                draftRole.ifBlank { "বন্ধু" },
                draftCallUserAs,
                activeCustomChar?.language ?: settings.preferredLanguage,
                activeCustomChar?.voiceName ?: settings.selectedVoice,
                activeCustomChar?.interests ?: "",
                draftDescription.take(MAX_CUSTOM_DESCRIPTION_CHARS),
                activeCustomChar?.funLevel ?: 0.6f,
                activeCustomChar?.emotionLevel ?: settings.emotionLevel,
                activeCustomChar?.seriousnessLevel ?: 0.4f,
                activeCustomChar?.talkativenessLevel ?: 0.6f,
                thenRun
            )
        } else {
            thenRun()
        }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            persistInlineDraftIfNeeded(onStartAudioCall)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("কলের জন্য মাইক্রোফোন অনুমতি প্রয়োজন। সেটিংস থেকে চালু করুন।")
            }
        }
    }

    val videoPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micOk = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micOk) {
            persistInlineDraftIfNeeded(onStartVideoCall)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("ভিডিও কলের জন্য অন্তত মাইক্রোফোন অনুমতি প্রয়োজন।")
            }
        }
    }

    fun tryAudioCall() {
        if (!isKeyVerified) {
            onOpenConnectionSettings()
            return
        }
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            persistInlineDraftIfNeeded(onStartAudioCall)
        } else {
            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun tryVideoCall() {
        if (!isKeyVerified) {
            onOpenConnectionSettings()
            return
        }
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val camGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (micGranted && camGranted) {
            persistInlineDraftIfNeeded(onStartVideoCall)
        } else {
            videoPermLauncher.launch(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            )
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header: Name + Current Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (activeMode == AyeshaMode.CUSTOM) {
                                draftName.ifBlank { activeCustomChar?.name ?: "আয়শা" }
                            } else {
                                "আয়শা"
                            },
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "মোড: ${activeMode.titleBn} • কণ্ঠ: ${activeVoice.labelBn}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (settings.phoneControlMasterEnabled) {
                        StatusBadge(label = "ফোন নিয়ন্ত্রণ চালু", color = TealSuccess)
                    } else if (!isKeyVerified) {
                        StatusBadge(label = "কী প্রয়োজন", color = AmberWarning)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Center Large Animated Orb
                AyeshaOrb(
                    state = OrbVisualState.IDLE,
                    size = 210.dp,
                    modifier = Modifier.testTag("home_ayesha_orb")
                )

                Text(
                    text = activeMode.subtitleBn,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isKeyVerified) {
                        "কথা বলতে নিচের অডিও বা ভিডিও কল চাপুন"
                    } else {
                        "কল শুরু করতে প্রথমে যাচাইকৃত Gemini API কী যোগ করুন"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                // Two Big Primary Call Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = ::tryAudioCall,
                        enabled = isKeyVerified,
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .testTag("audio_call_button")
                    ) {
                        Icon(
                            imageVector = if (isKeyVerified) Icons.Default.Call else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "অডিও কল",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = ::tryVideoCall,
                        enabled = isKeyVerified,
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (isKeyVerified) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .testTag("video_call_button")
                    ) {
                        Icon(
                            imageVector = if (isKeyVerified) AyeshaIcons.Videocam else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isKeyVerified) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "ভিডিও কল",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isKeyVerified) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                // Horizontal Mode Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ব্যক্তিত্ব মোড",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = {
                            dialogInitialChar = null
                            showCustomDialog = true
                        },
                        modifier = Modifier.testTag("home_create_custom_btn")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("কাস্টম তৈরি")
                    }
                }

                val orderedModes = remember {
                    listOf(
                        AyeshaMode.ASSISTANT,
                        AyeshaMode.FRIEND,
                        AyeshaMode.ISLAMIC,
                        AyeshaMode.CUSTOM,
                        AyeshaMode.FUN_COMPANION,
                        AyeshaMode.TEACHER,
                        AyeshaMode.ENGLISH_PRACTICE,
                        AyeshaMode.MOTIVATOR,
                        AyeshaMode.STORYTELLER,
                        AyeshaMode.QUIZ_RIDDLE,
                        AyeshaMode.GIRLFRIEND,
                        AyeshaMode.WIFE,
                        AyeshaMode.BOYFRIEND
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(orderedModes, key = { it.id }) { mode ->
                        val selected = activeMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onSelectMode(
                                    mode,
                                    if (mode == AyeshaMode.CUSTOM) {
                                        activeCustomChar?.id ?: customCharacters.firstOrNull()?.id
                                    } else {
                                        settings.selectedCustomCharacterId
                                    }
                                )
                            },
                            label = {
                                Text(
                                    text = mode.titleBn,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("mode_chip_${mode.id}")
                        )
                    }
                }

                // Direct Custom Character Writing Box shown immediately when "কাস্টম" is selected
                if (activeMode == AyeshaMode.CUSTOM) {
                    Spacer(Modifier.height(12.dp))
                    AyeshaCard(highlightBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "কাস্টম চরিত্র নিজের মতো সাজান",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = {
                                    dialogInitialChar = null
                                    showCustomDialog = true
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("নতুন")
                            }
                        }

                        if (customCharacters.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                items(customCharacters, key = { it.id }) { ch ->
                                    FilterChip(
                                        selected = ch.id == activeCustomChar?.id,
                                        onClick = { onSelectMode(AyeshaMode.CUSTOM, ch.id) },
                                        label = { Text("${ch.name} (${ch.relationshipRole})") }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = draftName,
                                onValueChange = { draftName = it },
                                label = { Text("নাম") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_custom_name_input")
                            )
                            OutlinedTextField(
                                value = draftRole,
                                onValueChange = { draftRole = it },
                                label = { Text("সম্পর্ক / ভূমিকা") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_custom_role_input")
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = draftCallUserAs,
                            onValueChange = { draftCallUserAs = it },
                            label = { Text("আপনাকে কী নামে ডাকবে (ঐচ্ছিক)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = draftDescription,
                            onValueChange = { draftDescription = it.take(MAX_CUSTOM_DESCRIPTION_CHARS) },
                            label = { Text("নিজের মতো সাজিয়ে লিখুন (কাস্টম নির্দেশনা)") },
                            placeholder = {
                                Text("এখানে বিস্তারিত লিখুন আয়শা কেমন স্বভাবের হবে, কীভাবে কথা বলবে, কী বিষয়ে কথা বলবে বা কী কী নিয়ম মানবে...")
                            },
                            minLines = 5,
                            maxLines = 12,
                            shape = RoundedCornerShape(16.dp),
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("সর্বোচ্চ ১০,০০০ অক্ষর পর্যন্ত লিখতে পারবেন")
                                    Text(
                                        text = "${draftDescription.length} / 10,000",
                                        color = if (draftDescription.length >= MAX_CUSTOM_DESCRIPTION_CHARS) {
                                            AmberWarning
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_custom_description_input")
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    onSaveCustomCharacter(
                                        activeCustomChar?.id,
                                        draftName.ifBlank { "আয়শা" },
                                        draftRole.ifBlank { "বন্ধু" },
                                        draftCallUserAs,
                                        activeCustomChar?.language ?: settings.preferredLanguage,
                                        activeCustomChar?.voiceName ?: settings.selectedVoice,
                                        activeCustomChar?.interests ?: "",
                                        draftDescription.take(MAX_CUSTOM_DESCRIPTION_CHARS),
                                        activeCustomChar?.funLevel ?: 0.6f,
                                        activeCustomChar?.emotionLevel ?: settings.emotionLevel,
                                        activeCustomChar?.seriousnessLevel ?: 0.4f,
                                        activeCustomChar?.talkativenessLevel ?: 0.6f
                                    ) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("কাস্টম নির্দেশনা সেভ হয়েছে!")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("home_custom_save_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("সেভ করুন")
                            }

                            OutlinedButton(
                                onClick = {
                                    dialogInitialChar = (activeCustomChar ?: CustomCharacterEntity(
                                        id = "",
                                        name = draftName.ifBlank { "আয়শা" },
                                        relationshipRole = draftRole.ifBlank { "বন্ধু" },
                                        callUserAs = draftCallUserAs,
                                        language = settings.preferredLanguage,
                                        voiceName = settings.selectedVoice,
                                        interests = "",
                                        description = draftDescription,
                                        funLevel = 0.6f,
                                        emotionLevel = settings.emotionLevel,
                                        seriousnessLevel = 0.4f,
                                        talkativenessLevel = 0.6f
                                    ))
                                    showCustomDialog = true
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(AyeshaIcons.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("আরও")
                            }
                        }

                        TextButton(
                            onClick = onOpenPersonaSettings,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("সব কাস্টম চরিত্র ম্যানেজ করুন")
                        }
                    }
                }

                if (activeMode == AyeshaMode.ISLAMIC) {
                    Spacer(Modifier.height(10.dp))
                    AyeshaCard(highlightBorder = TealSuccess.copy(alpha = 0.55f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AyeshaIcons.AutoAwesome, contentDescription = null, tint = TealSuccess)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "ইসলামিক মোড সক্রিয় • Google Search গ্রাউন্ডিং চালু",
                                style = MaterialTheme.typography.labelLarge,
                                color = TealSuccess
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "সালাম দিয়ে শুরু হবে, নির্ভরযোগ্য রেফারেন্স যাচাই করে জানাবে এবং ব্যক্তিগত মাসায়েলের জন্য আলেমের পরামর্শ নেওয়ার উৎসাহ দেবে।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (settings.phoneControlMasterEnabled) {
                    Spacer(Modifier.height(10.dp))
                    AyeshaCard(highlightBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AyeshaIcons.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "ফোন নিয়ন্ত্রণ সক্রিয় আছে",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "কল চলাকালে বললেই অ্যাপ খোলা, ট্যাপ, স্ক্রল ও টাইপ করতে পারবে। কাজের সময় যেকোনো মুহূর্তে 'থামো' বলুন।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
        SnackbarHost(hostState = snackbarHostState)
    }

    if (showCustomDialog) {
        val editingId = dialogInitialChar?.id?.takeIf { it.isNotBlank() }
        CustomCharacterEditorDialog(
            initial = dialogInitialChar,
            onDismiss = { showCustomDialog = false },
            onSave = { name, role, callUserAs, lang, voice, interests, desc, funL, emoL, serL, talkL ->
                onSaveCustomCharacter(
                    editingId,
                    name,
                    role,
                    callUserAs,
                    lang,
                    voice,
                    interests,
                    desc,
                    funL,
                    emoL,
                    serL,
                    talkL
                ) {
                    showCustomDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar("কাস্টম চরিত্র সেভ ও সক্রিয় করা হয়েছে!")
                    }
                }
            }
        )
    }
}
