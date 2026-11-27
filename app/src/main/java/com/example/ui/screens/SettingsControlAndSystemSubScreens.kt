package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AccentTheme
import com.example.data.UserSettings
import com.example.data.db.BlockedAppEntity
import com.example.ui.PermissionSnapshot
import com.example.ui.components.AyeshaBackground
import com.example.ui.components.AyeshaCard
import com.example.ui.components.AyeshaIcons
import com.example.ui.components.StatusBadge
import com.example.ui.components.SubScreenTopBar
import com.example.ui.components.SystemIntentUtils
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.NavySurfaceHigh
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TealSuccess

@Composable
fun SettingsPhoneControlSubScreen(
    settings: UserSettings,
    permissions: PermissionSnapshot,
    onRefreshPermissions: () -> Unit,
    onSetMaster: (Boolean) -> Unit,
    onRequestAccessibilitySetup: () -> Unit,
    onSetAllowAppOpening: (Boolean) -> Unit,
    onSetAllowTapping: (Boolean) -> Unit,
    onSetAllowTyping: (Boolean) -> Unit,
    onSetAllowKeyboardSwitch: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AyeshaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SubScreenTopBar(title = "ফোন নিয়ন্ত্রণ", onBack = onBack)
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AyeshaCard(highlightBorder = if (settings.phoneControlMasterEnabled) TealSuccess else MaterialTheme.colorScheme.outline) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ফোন নিয়ন্ত্রণ প্রধান সুইচ", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "চালু করলে কলের সময় আপনার মৌখিক নির্দেশে আয়শা ফোন পরিচালনা করতে পারবে।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.phoneControlMasterEnabled,
                            onCheckedChange = onSetMaster,
                            modifier = Modifier.testTag("phone_control_master_switch")
                        )
                    }
                }

                AyeshaCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("অ্যাকসেসিবিলিটি সেবা", style = MaterialTheme.typography.titleMedium)
                        StatusBadge(
                            label = if (permissions.isAccessibilityEnabled) "চালু" else "বন্ধ",
                            color = if (permissions.isAccessibilityEnabled) TealSuccess else AmberWarning
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "পর্দার উপাদান পড়া এবং ট্যাপ/স্ক্রল করার জন্য আয়শা অ্যাকসেসিবিলিটি সেবা চালু করা প্রয়োজন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onRequestAccessibilitySetup,
                        modifier = Modifier.testTag("setup_accessibility_button")
                    ) {
                        Text(if (permissions.isAccessibilityEnabled) "অ্যাকসেসিবিলিটি সেটিংস দেখুন" else "ব্যাখ্যা পড়ে চালু করুন")
                    }
                }

                AyeshaCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AyeshaIcons.Keyboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("আয়শা কিবোর্ড (এজেন্ট IME)", style = MaterialTheme.typography.titleMedium)
                        }
                        StatusBadge(
                            label = if (permissions.isAyeshaKeyboardEnabled) "সক্রিয়" else "নিষ্ক্রিয়",
                            color = if (permissions.isAyeshaKeyboardEnabled) TealSuccess else AmberWarning
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "বাংলা ও ইংরেজি ইউনিকোড টাইপ করার জন্য সিস্টেম কিবোর্ড তালিকায় 'আয়শা কিবোর্ড' একবার চালু করুন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { SystemIntentUtils.openInputMethodSettings(context) },
                        modifier = Modifier.testTag("setup_ime_button")
                    ) {
                        Text("কিবোর্ড সেটিংস খুলুন")
                    }
                }

                AyeshaCard {
                    Text("আলাদা কাজের অনুমতি (Per-Tool Switches)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("অ্যাপ খোলা (open_app)", settings.allowAppOpening, onSetAllowAppOpening)
                    ToggleRow("ট্যাপ, লং-প্রেস ও স্ক্রল", settings.allowTapping, onSetAllowTapping)
                    ToggleRow("লেখা টাইপ ও মোছা", settings.allowTyping, onSetAllowTyping)
                    ToggleRow("স্বয়ংক্রিয় কিবোর্ড পরিবর্তন", settings.allowKeyboardSwitch, onSetAllowKeyboardSwitch)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsSafetySubScreen(
    requireConfirmation: Boolean,
    blockedApps: List<BlockedAppEntity>,
    onToggleConfirmation: (Boolean) -> Unit,
    onAddBlockedApp: (packageName: String, displayName: String) -> Unit,
    onRemoveBlockedApp: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var newPkg by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    AyeshaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SubScreenTopBar(title = "নিরাপত্তা ও ব্লকলিস্ট", onBack = onBack)
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AyeshaCard(highlightBorder = TealSuccess.copy(alpha = 0.55f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("গুরুত্বপূর্ণ কাজের আগে নিশ্চিতকরণ", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "মেসেজ/পোস্ট পাঠানো, কল করা, ডিলিট, কেনাকাটা বা টাকা লেনদেনের আগে পর্দায় ও কণ্ঠে অনুমতি চাইবে।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = requireConfirmation, onCheckedChange = onToggleConfirmation)
                    }
                }

                AyeshaCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AyeshaIcons.Shield, contentDescription = null, tint = AmberWarning)
                        Spacer(Modifier.width(8.dp))
                        Text("পাসওয়ার্ড, ওটিপি ও লক স্ক্রিন সুরক্ষা", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "• পাসওয়ার্ড, পিন, ওটিপি বা কার্ডের ফিল্ড আয়শা কখনোই পড়ে না বা সেখানে টাইপ করে না।\n• লক স্ক্রিন কখনোই বাইপাস করা হয় না।\n• বিকাশ, নগদ, রকেটসহ ব্যাংকিং অ্যাপ ডিফল্টভাবে ব্লক থাকে।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AyeshaCard {
                    Text("নতুন অ্যাপ ব্লকলিস্টে যোগ করুন", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("অ্যাপের নাম (যেমন: আমার ব্যাংক)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newPkg,
                        onValueChange = { newPkg = it },
                        label = { Text("প্যাকেজ নাম (যেমন: com.example.bank)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (newPkg.isNotBlank()) {
                                onAddBlockedApp(newPkg, newName)
                                newPkg = ""
                                newName = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ব্লক তালিকায় যোগ করুন")
                    }
                }

                Text("ব্লক করা অ্যাপসমূহ (${blockedApps.size})", style = MaterialTheme.typography.titleMedium)
                blockedApps.forEach { app ->
                    AyeshaCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(app.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onRemoveBlockedApp(app.packageName) }) {
                                Icon(Icons.Default.Delete, contentDescription = "সরান", tint = RoseDanger)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPermissionsBatterySubScreen(
    permissions: PermissionSnapshot,
    onRefreshPermissions: () -> Unit,
    onRequestAccessibilitySetup: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefreshPermissions() }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefreshPermissions() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefreshPermissions() }

    AyeshaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SubScreenTopBar(title = "পারমিশন ও ব্যাটারি", onBack = onBack)
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LivePermissionRow("মাইক্রোফোন", permissions.hasMic) {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                LivePermissionRow("ক্যামেরা", permissions.hasCamera) {
                    camLauncher.launch(Manifest.permission.CAMERA)
                }
                LivePermissionRow("নোটিফিকেশন", permissions.hasNotifications) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        SystemIntentUtils.openNotificationSettings(context)
                    }
                }
                LivePermissionRow("ভাসমান অর্‌ব (Overlay)", permissions.hasOverlay) {
                    SystemIntentUtils.openOverlaySettings(context)
                }
                LivePermissionRow("ব্যাটারি অপ্টিমাইজেশন ছাড়", permissions.ignoringBatteryOptimizations) {
                    SystemIntentUtils.requestIgnoreBatteryOptimizations(context)
                }
                LivePermissionRow("অ্যাকসেসিবিলিটি সেবা", permissions.isAccessibilityEnabled) {
                    onRequestAccessibilitySetup()
                }
                LivePermissionRow("আয়শা কিবোর্ড", permissions.isAyeshaKeyboardEnabled) {
                    SystemIntentUtils.openInputMethodSettings(context)
                }

                Spacer(Modifier.height(6.dp))
                AyeshaCard(highlightBorder = AmberWarning.copy(alpha = 0.6f)) {
                    Text(
                        text = "Samsung One UI ব্যাটারি নির্দেশিকা (Sleeping Apps)",
                        style = MaterialTheme.typography.titleMedium,
                        color = AmberWarning
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "স্যামসাং ফোনে ব্যাকগ্রাউন্ড কল বা অ্যাকসেসিবিলিটি হঠাৎ বন্ধ হওয়া ঠেকাতে:\n" +
                            "১. ফোনের Settings > Battery and device care > Battery > Background usage limits খুলুন।\n" +
                            "২. Sleeping apps এবং Deep sleeping apps তালিকা থেকে 'আয়শা' অ্যাপটি থাকলে সরিয়ে দিন।\n" +
                            "৩. 'Never auto sleeping apps'-এ 'আয়শা' যোগ করুন।\n" +
                            "৪. App info > Battery-তে গিয়ে 'Unrestricted' বেছে নিন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { SystemIntentUtils.openAppDetailsSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("অ্যাপের ব্যাটারি তথ্য খুলুন")
                    }
                }
            }
        }
    }
}

@Composable
private fun LivePermissionRow(
    title: String,
    granted: Boolean,
    onFix: () -> Unit
) {
    AyeshaCard(highlightBorder = if (granted) TealSuccess.copy(alpha = 0.5f) else AmberWarning.copy(alpha = 0.5f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (granted) "অনুমতি দেওয়া আছে" else "চালু করা প্রয়োজন",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (granted) TealSuccess else AmberWarning
                )
            }
            if (!granted) {
                Button(onClick = onFix) {
                    Text("ঠিক করুন")
                }
            } else {
                StatusBadge(label = "ঠিক আছে", color = TealSuccess)
            }
        }
    }
}

@Composable
fun SettingsThemeAboutSubScreen(
    accentTheme: AccentTheme,
    dailyGreetingEnabled: Boolean,
    onSelectTheme: (AccentTheme) -> Unit,
    onToggleDailyGreeting: (Boolean) -> Unit,
    onDeleteAllAppData: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var confirmWipe by remember { mutableStateOf(false) }

    AyeshaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SubScreenTopBar(title = "থিম ও তথ্য", onBack = onBack)
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AyeshaCard {
                    Text("অ্যাকসেন্ট রঙ বেছে নিন", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccentTheme.entries.take(2).forEach { theme ->
                            FilterChip(
                                selected = accentTheme == theme,
                                onClick = { onSelectTheme(theme) },
                                label = { Text(theme.labelBn) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccentTheme.entries.drop(2).forEach { theme ->
                            FilterChip(
                                selected = accentTheme == theme,
                                onClick = { onSelectTheme(theme) },
                                label = { Text(theme.labelBn) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                AyeshaCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("দৈনিক শুভেচ্ছা নোটিফিকেশন", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "প্রতিদিন সকালে একটি সংক্ষিপ্ত শুভেচ্ছা বার্তা পাঠাবে (ডিফল্টভাবে বন্ধ)।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = dailyGreetingEnabled, onCheckedChange = onToggleDailyGreeting)
                    }
                }

                AyeshaCard {
                    Text("গোপনীয়তা নীতি", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "আপনার Gemini API কী অ্যান্ড্রয়েড Keystore-এ এনক্রিপ্টেড অবস্থায় শুধু এই ফোনে থাকে এবং ক্লাউড ব্যাকআপে যায় না। কোনো অডিও বা ভিডিও কল রেকর্ড করা হয় না। লাইভ কথোপকথন ও দৃশ্যমান ফ্রেম কেবল রিয়েল-টাইম জবাবের জন্য Google Gemini Live API-তে যায়।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AyeshaCard {
                    Text("ব্যবহার নির্দেশিকা", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "• হোম থেকে অডিও বা ভিডিও কল শুরু করুন।\n• আয়শা কথা বলার সময় আপনি কথা বললে সে সাথে সাথে থেমে আপনার কথা শুনবে।\n• ফোন নিয়ন্ত্রণের কাজ চলাকালে যেকোনো সময় 'থামো' বলুন বা লাল বোতাম চাপুন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = { confirmWipe = true },
                    border = BorderStroke(1.dp, RoseDanger),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("delete_all_app_data_button")
                ) {
                    Icon(AyeshaIcons.DeleteForever, contentDescription = null, tint = RoseDanger)
                    Spacer(Modifier.width(8.dp))
                    Text("অ্যাপের সব ডেটা মুছে ফেলুন", color = RoseDanger)
                }
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = NavySurfaceHigh,
            title = { Text("সব ডেটা স্থায়ীভাবে মুছবেন?") },
            text = { Text("আপনার API কী, সব স্মৃতি, কাস্টম চরিত্র, ইতিহাস এবং সেটিংস মুছে অ্যাপটি প্রথমবারের অবস্থায় ফিরে যাবে।") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmWipe = false
                        onDeleteAllAppData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseDanger)
                ) { Text("হ্যাঁ, সব মুছুন") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("বাতিল") }
            }
        )
    }
}
