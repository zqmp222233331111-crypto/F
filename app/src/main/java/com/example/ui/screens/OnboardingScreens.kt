package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.ApiKeyVerifyUiState
import com.example.ui.PermissionSnapshot
import com.example.ui.components.AyeshaBackground
import com.example.ui.components.AyeshaCard
import com.example.ui.components.AyeshaOrb
import com.example.ui.components.OrbVisualState
import com.example.ui.components.StatusBadge
import com.example.ui.components.SystemIntentUtils
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TealSuccess

@Composable
fun WelcomeScreen(
    onStart: (Boolean) -> Unit
) {
    var ageConfirmed by remember { mutableStateOf(false) }

    AyeshaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                AyeshaOrb(state = OrbVisualState.IDLE, size = 190.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "আয়শা",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "আপনার বাংলা ভয়েস ও ভিডিও এআই সহকারী এবং সঙ্গী",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))

                AyeshaCard {
                    Text(
                        text = "কথা বলুন, ভাবুন, একসাথে কাজ করুন",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "আয়শার সাথে কোনো টেক্সট চ্যাট নেই—শুধুমাত্র সরাসরি লাইভ অডিও ও ভিডিও কলের মাধ্যমে স্বাভাবিক বাংলায় কথা বলুন। চাইলে আপনার অনুমতিতে ফোন পরিচালনাতেও সাহায্য করবে।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    StatusBadge(
                        label = "আয়শা একটি কৃত্রিম বুদ্ধিমত্তা (AI) সহকারী",
                        color = AmberWarning
                    )
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ageConfirmed = !ageConfirmed }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = ageConfirmed,
                        onCheckedChange = { ageConfirmed = it },
                        modifier = Modifier.testTag("age_18_checkbox")
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "আমি নিশ্চিত করছি যে আমার বয়স ১৮ বছর বা তার বেশি এবং আমি বুঝি আয়শা একটি AI।",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = { onStart(ageConfirmed) },
                    enabled = ageConfirmed,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("welcome_start_button")
                ) {
                    Text("শুরু করুন", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun ApiKeyScreen(
    isKeyVerified: Boolean,
    initialMaskedPreview: String,
    verifyState: ApiKeyVerifyUiState,
    onVerifyAndSave: (String) -> Unit,
    onContinueIfReady: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var apiKeyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    AyeshaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 580.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Gemini API কী",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    StatusBadge(
                        label = if (isKeyVerified) "প্রস্তুত" else "কী যোগ করুন",
                        color = if (isKeyVerified) TealSuccess else AmberWarning
                    )
                }

                if (isKeyVerified && initialMaskedPreview.isNotBlank()) {
                    AyeshaCard(highlightBorder = TealSuccess.copy(alpha = 0.6f)) {
                        Text(
                            text = "বর্তমানে সংরক্ষিত কী: $initialMaskedPreview",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TealSuccess
                        )
                    }
                }

                AyeshaCard {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("আপনার Gemini API কী লিখুন") },
                        placeholder = { Text("AIza...") },
                        singleLine = true,
                        visualTransformation = if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            Row {
                                IconButton(
                                    onClick = { showKey = !showKey },
                                    modifier = Modifier.testTag("toggle_key_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showKey) "লুকান" else "দেখুন"
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboard.getText()?.text?.let { pasted ->
                                            apiKeyInput = pasted.trim()
                                        }
                                    },
                                    modifier = Modifier.testTag("paste_api_key_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "পেস্ট করুন"
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input")
                    )

                    Spacer(Modifier.height(10.dp))

                    when (verifyState) {
                        is ApiKeyVerifyUiState.Error -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = RoseDanger,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = verifyState.messageBn,
                                    color = RoseDanger,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        is ApiKeyVerifyUiState.Verified -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = TealSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "API কী যাচাই সম্পন্ন ও নিরাপদে সংরক্ষণ করা হয়েছে।",
                                    color = TealSuccess,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        else -> {}
                    }

                    Button(
                        onClick = { onVerifyAndSave(apiKeyInput) },
                        enabled = verifyState !is ApiKeyVerifyUiState.Verifying,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("verify_save_key_button")
                    ) {
                        if (verifyState is ApiKeyVerifyUiState.Verifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("যাচাই করা হচ্ছে...")
                        } else {
                            Text("যাচাই করে সেভ করুন")
                        }
                    }

                    if (isKeyVerified && onContinueIfReady != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onContinueIfReady,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("পরবর্তী ধাপে যান")
                        }
                    }
                }

                AyeshaCard {
                    Text(
                        text = "ফ্রি API কী পাওয়ার সহজ ধাপ:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("১. Google অ্যাকাউন্ট দিয়ে Google AI Studio-তে সাইন ইন করুন।", style = MaterialTheme.typography.bodyMedium)
                    Text("২. 'Create API key' বোতামে ট্যাপ করে নতুন কী তৈরি করুন।", style = MaterialTheme.typography.bodyMedium)
                    Text("৩. তৈরি হওয়া কী-টি কপি করুন।", style = MaterialTheme.typography.bodyMedium)
                    Text("৪. এখানে পেস্ট (Paste) করে 'যাচাই করে সেভ করুন' চাপুন।", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { SystemIntentUtils.openUrl(context, "https://aistudio.google.com/apikey") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_aistudio_link")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Google AI Studio থেকে ফ্রি কী নিন")
                    }
                }

                AyeshaCard(highlightBorder = AmberWarning.copy(alpha = 0.55f)) {
                    Text(
                        text = "গোপনীয়তা ও নিরাপত্তা তথ্য",
                        style = MaterialTheme.typography.titleMedium,
                        color = AmberWarning
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "কী এই ফোনে সেভ থাকে। কথা, ছবি ও স্ক্রিন Google-এ যায়। ফ্রি টিয়ারে Google এই তথ্য পণ্য উন্নয়নে ব্যবহার করতে পারে, তাই গোপন বা সংবেদনশীল তথ্য শেয়ার করবেন না।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionsSetupScreen(
    permissions: PermissionSnapshot,
    onRefreshPermissions: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefreshPermissions() }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefreshPermissions() }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefreshPermissions() }

    AyeshaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 580.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "প্রয়োজনীয় অনুমতিসমূহ",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "অডিও-ভিডিও কল ও পর্দার ওপরে ভাসমান বাবল চালাতে নিচের অনুমতিগুলো সাহায্য করে। চাইলে এড়িয়ে যেতে পারেন, তবে কলের জন্য মাইক্রোফোন অবশ্যই লাগবে।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                PermissionSetupCard(
                    icon = Icons.Default.Mic,
                    title = "মাইক্রোফোন (কলের জন্য আবশ্যক)",
                    description = "আয়শার সাথে সরাসরি কথা বলার জন্য মাইক্রোফোনের অনুমতি প্রয়োজন।",
                    granted = permissions.hasMic,
                    requiredLabel = "প্রয়োজনীয়",
                    onGrant = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                )

                PermissionSetupCard(
                    icon = Icons.Default.Videocam,
                    title = "ক্যামেরা (ভিডিও কল)",
                    description = "ভিডিও কলে আয়শাকে আপনার চারপাশ বা কোনো বস্তু দেখাতে ক্যামেরা ব্যবহৃত হয়।",
                    granted = permissions.hasCamera,
                    onGrant = { cameraLauncher.launch(Manifest.permission.CAMERA) }
                )

                PermissionSetupCard(
                    icon = Icons.Default.Notifications,
                    title = "নোটিফিকেশন ও ভাসমান অর্‌ব",
                    description = "অন্য অ্যাপ চালানোর সময় কল চালু রাখতে ও ভাসমান অর্‌ব দেখাতে সাহায্য করে।",
                    granted = permissions.hasNotifications && permissions.hasOverlay,
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissions.hasNotifications) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            SystemIntentUtils.openOverlaySettings(context)
                        }
                    },
                    secondaryLabel = if (!permissions.hasOverlay) "ভাসমান বাবল সেটিংস" else null,
                    onSecondary = { SystemIntentUtils.openOverlaySettings(context) }
                )

                PermissionSetupCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "ব্যাটারি অপ্টিমাইজেশন ছাড়",
                    description = "ব্যাকগ্রাউন্ডে দীর্ঘ কল চলাকালে স্যামসাং ও অ্যান্ড্রয়েড সিস্টেম যেন অ্যাপটি বন্ধ না করে।",
                    granted = permissions.ignoringBatteryOptimizations,
                    onGrant = { SystemIntentUtils.requestIgnoreBatteryOptimizations(context) }
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onFinish,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("permissions_continue_button")
                ) {
                    Text(if (permissions.hasMic) "এগিয়ে যান" else "এখন এড়িয়ে যান")
                }
            }
        }
    }
}

@Composable
private fun PermissionSetupCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    requiredLabel: String? = null,
    onGrant: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    AyeshaCard(
        highlightBorder = if (granted) TealSuccess.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) TealSuccess else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (requiredLabel != null && !granted) {
                    Text(requiredLabel, style = MaterialTheme.typography.labelMedium, color = AmberWarning)
                }
            }
            StatusBadge(
                label = if (granted) "চালু" else "বন্ধ",
                color = if (granted) TealSuccess else AmberWarning
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!granted || secondaryLabel != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!granted) {
                    Button(
                        onClick = onGrant,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("অনুমতি দিন")
                    }
                }
                if (secondaryLabel != null && onSecondary != null) {
                    OutlinedButton(onClick = onSecondary) {
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(secondaryLabel)
                    }
                }
            }
        }
    }
}
