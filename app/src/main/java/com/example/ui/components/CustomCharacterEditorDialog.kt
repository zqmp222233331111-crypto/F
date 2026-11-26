package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.db.CustomCharacterEntity
import com.example.persona.VoiceCatalog
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.NavySurfaceHigh

const val MAX_CUSTOM_DESCRIPTION_CHARS = 10_000

@Composable
fun CustomCharacterEditorDialog(
    initial: CustomCharacterEntity?,
    onDismiss: () -> Unit,
    onSave: (
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
        talkativenessLevel: Float
    ) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "আয়শা") }
    var role by remember(initial?.id) { mutableStateOf(initial?.relationshipRole ?: "বন্ধু") }
    var callUserAs by remember(initial?.id) { mutableStateOf(initial?.callUserAs ?: "") }
    var language by remember(initial?.id) { mutableStateOf(initial?.language ?: "বাংলাদেশি বাংলা") }
    var voice by remember(initial?.id) { mutableStateOf(initial?.voiceName ?: "Sulafat") }
    var interests by remember(initial?.id) { mutableStateOf(initial?.interests ?: "") }
    var description by remember(initial?.id) {
        mutableStateOf((initial?.description ?: "").take(MAX_CUSTOM_DESCRIPTION_CHARS))
    }
    var funLevel by remember(initial?.id) { mutableFloatStateOf(initial?.funLevel ?: 0.6f) }
    var emotionLevel by remember(initial?.id) { mutableFloatStateOf(initial?.emotionLevel ?: 0.7f) }
    var seriousnessLevel by remember(initial?.id) { mutableFloatStateOf(initial?.seriousnessLevel ?: 0.4f) }
    var talkativenessLevel by remember(initial?.id) { mutableFloatStateOf(initial?.talkativenessLevel ?: 0.6f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavySurfaceHigh,
        title = { Text(if (initial == null) "নতুন কাস্টম চরিত্র" else "চরিত্র সম্পাদনা") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("সহকারীর নাম") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_dialog_name_input")
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("সম্পর্ক বা ভূমিকা") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_dialog_role_input")
                )
                OutlinedTextField(
                    value = callUserAs,
                    onValueChange = { callUserAs = it },
                    label = { Text("আপনাকে কী নামে ডাকবে") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("ভাষা") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("কণ্ঠ: ${VoiceCatalog.findByName(voice).labelBn} ($voice)", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(VoiceCatalog.allVoices, key = { it.name }) { v ->
                        FilterChip(
                            selected = voice.equals(v.name, ignoreCase = true),
                            onClick = { voice = v.name },
                            label = { Text("${v.labelBn} (${v.name})") }
                        )
                    }
                }

                OutlinedTextField(
                    value = interests,
                    onValueChange = { interests = it },
                    label = { Text("পছন্দের বিষয়") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(MAX_CUSTOM_DESCRIPTION_CHARS) },
                    label = { Text("নিজের মতো কাস্টম নির্দেশনা / বিবরণ") },
                    placeholder = {
                        Text("চরিত্রটি কেমন হবে, কীভাবে কথা বলবে, কী কী নিয়ম মানবে—সব এখানে নিজের মতো লিখুন (সর্বোচ্চ ১০,০০০ অক্ষর)...")
                    },
                    minLines = 6,
                    maxLines = 14,
                    shape = RoundedCornerShape(16.dp),
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("সর্বোচ্চ ১০,০০০ অক্ষর সাপোর্ট করে")
                            Text(
                                text = "${description.length} / 10,000",
                                color = if (description.length >= MAX_CUSTOM_DESCRIPTION_CHARS) {
                                    AmberWarning
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_dialog_description_input")
                )

                Spacer(Modifier.height(2.dp))
                Text("মজা: ${(funLevel * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = funLevel, onValueChange = { funLevel = it })
                Text("আবেগ: ${(emotionLevel * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = emotionLevel, onValueChange = { emotionLevel = it })
                Text("গম্ভীরতা: ${(seriousnessLevel * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = seriousnessLevel, onValueChange = { seriousnessLevel = it })
                Text("কথাবার্তার পরিমাণ: ${(talkativenessLevel * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = talkativenessLevel, onValueChange = { talkativenessLevel = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        role,
                        callUserAs,
                        language,
                        voice,
                        interests,
                        description.take(MAX_CUSTOM_DESCRIPTION_CHARS),
                        funLevel,
                        emotionLevel,
                        seriousnessLevel,
                        talkativenessLevel
                    )
                },
                modifier = Modifier.testTag("custom_dialog_save_button")
            ) {
                Text("সেভ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}
