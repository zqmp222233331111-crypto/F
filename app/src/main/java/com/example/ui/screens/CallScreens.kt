package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.control.PhoneControlUiState
import com.example.live.AudioRoute
import com.example.live.CallForegroundService
import com.example.live.LiveCallStatus
import com.example.live.LiveCallUiState
import com.example.ui.components.AyeshaBackground
import com.example.ui.components.AyeshaCard
import com.example.ui.components.AyeshaOrb
import com.example.ui.components.OrbVisualState
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatDurationBn
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurfaceHigh
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TealSuccess
import java.util.concurrent.Executors

@Composable
fun AudioCallScreen(
    callState: LiveCallUiState,
    phoneControlState: PhoneControlUiState,
    onToggleMute: () -> Unit,
    onCycleAudioRoute: () -> Unit,
    onStopTask: () -> Unit,
    onResolveConfirmation: (Boolean) -> Unit,
    onEndCall: () -> Unit
) {
    BackHandler { onEndCall() }
    val context = LocalContext.current
    val screenShareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_START_SCREEN_SHARE
                putExtra(CallForegroundService.EXTRA_PROJECTION_CODE, res.resultCode)
                putExtra(CallForegroundService.EXTRA_PROJECTION_DATA, res.data)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    AyeshaBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status & Timer
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = callState.characterName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(label = callState.modeTitleBn, color = MaterialTheme.colorScheme.primary)
                    StatusBadge(
                        label = formatDurationBn(callState.elapsedSeconds),
                        color = TealSuccess
                    )
                }
                Spacer(Modifier.height(10.dp))
                StatusBadge(
                    label = callState.status.labelBn,
                    color = when (callState.status) {
                        LiveCallStatus.LISTENING -> TealSuccess
                        LiveCallStatus.SPEAKING -> MaterialTheme.colorScheme.secondary
                        LiveCallStatus.ERROR -> RoseDanger
                        else -> AmberWarning
                    }
                )
                if (callState.isScreenSharing) {
                    Spacer(Modifier.height(6.dp))
                    StatusBadge(label = "স্ক্রিন শেয়ার চলছে", color = AmberWarning)
                }
            }

            // Center Reactive Orb + Active Task / Confirmation / Error
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AyeshaOrb(
                    state = OrbVisualState.fromCallStatus(callState.status),
                    amplitude = callState.orbAmplitude,
                    size = 240.dp,
                    modifier = Modifier.testTag("audio_call_orb")
                )

                if (callState.errorMessageBn != null) {
                    AyeshaCard(highlightBorder = RoseDanger) {
                        Text(
                            text = callState.errorMessageBn,
                            color = RoseDanger,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                PhoneControlCallOverlayCards(
                    phoneControlState = phoneControlState,
                    onStopTask = onStopTask,
                    onResolveConfirmation = onResolveConfirmation
                )
            }

            // Bottom Call Controls: Mute, Speaker/Bluetooth, End Call
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallCircleButton(
                    icon = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (callState.isMuted) "আনমিউট" else "মিউট",
                    active = callState.isMuted,
                    activeColor = AmberWarning,
                    testTag = "call_mute_button",
                    onClick = onToggleMute
                )

                CallCircleButton(
                    icon = when (callState.audioRoute) {
                        AudioRoute.SPEAKER -> Icons.Default.VolumeUp
                        AudioRoute.BLUETOOTH -> Icons.Default.BluetoothAudio
                        AudioRoute.EARPIECE -> Icons.Default.PhoneInTalk
                    },
                    label = when (callState.audioRoute) {
                        AudioRoute.SPEAKER -> "স্পিকার"
                        AudioRoute.BLUETOOTH -> "ব্লুটুথ"
                        AudioRoute.EARPIECE -> "ইয়ারপিস"
                    },
                    active = callState.audioRoute != AudioRoute.EARPIECE,
                    activeColor = TealSuccess,
                    testTag = "call_audio_route_button",
                    onClick = onCycleAudioRoute
                )

                CallCircleButton(
                    icon = if (callState.isScreenSharing) Icons.Default.StopScreenShare else Icons.Default.PresentToAll,
                    label = if (callState.isScreenSharing) "শেয়ার বন্ধ" else "স্ক্রিন",
                    active = callState.isScreenSharing,
                    activeColor = TealSuccess,
                    testTag = "audio_screen_share",
                    onClick = {
                        if (callState.isScreenSharing) {
                            context.startService(
                                Intent(context, CallForegroundService::class.java).setAction(
                                    CallForegroundService.ACTION_STOP_SCREEN_SHARE
                                )
                            )
                        } else {
                            val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            screenShareLauncher.launch(mpm.createScreenCaptureIntent())
                        }
                    }
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onEndCall,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = RoseDanger,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("end_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "কল কাটুন",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "শেষ করুন",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
fun VideoCallScreen(
    callState: LiveCallUiState,
    phoneControlState: PhoneControlUiState,
    onSubmitCameraFrame: (androidx.camera.core.ImageProxy) -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleMute: () -> Unit,
    onStopTask: () -> Unit,
    onResolveConfirmation: (Boolean) -> Unit,
    onEndCall: () -> Unit
) {
    BackHandler { onEndCall() }
    val context = LocalContext.current

    val screenShareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = CallForegroundService.ACTION_START_SCREEN_SHARE
                putExtra(CallForegroundService.EXTRA_PROJECTION_CODE, res.resultCode)
                putExtra(CallForegroundService.EXTRA_PROJECTION_DATA, res.data)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep)
    ) {
        if (callState.isCameraOn) {
            CameraXPreviewLayer(
                isFrontCamera = callState.isFrontCamera,
                onFrame = onSubmitCameraFrame,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AyeshaOrb(
                    state = OrbVisualState.fromCallStatus(callState.status),
                    amplitude = callState.orbAmplitude,
                    size = 180.dp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (callState.isScreenSharing) "স্ক্রিন শেয়ার চলছে" else "ক্যামেরা বন্ধ আছে",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Top HUD: Timer + Screen Share Indicator (left) & Mini Ayesha Orb (right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NavyDeep.copy(alpha = 0.72f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(
                            text = "${callState.characterName} • ${callState.modeTitleBn}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${formatDurationBn(callState.elapsedSeconds)} • ${callState.status.labelBn}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TealSuccess
                        )
                    }
                }
                if (callState.isScreenSharing) {
                    StatusBadge(label = "স্ক্রিন শেয়ার চলছে", color = AmberWarning)
                }
                if (callState.errorMessageBn != null) {
                    StatusBadge(label = callState.errorMessageBn, color = RoseDanger)
                }
            }

            Surface(
                shape = CircleShape,
                color = NavyDeep.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                AyeshaOrb(
                    state = OrbVisualState.fromCallStatus(callState.status),
                    amplitude = callState.orbAmplitude,
                    size = 86.dp,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        // Bottom Controls + Task Cards
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PhoneControlCallOverlayCards(
                phoneControlState = phoneControlState,
                onStopTask = onStopTask,
                onResolveConfirmation = onResolveConfirmation
            )

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = NavyDeep.copy(alpha = 0.86f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallCircleButton(
                        icon = Icons.Default.Cameraswitch,
                        label = "ঘোরান",
                        active = false,
                        activeColor = MaterialTheme.colorScheme.primary,
                        testTag = "video_switch_camera",
                        onClick = onSwitchCamera
                    )
                    CallCircleButton(
                        icon = if (callState.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        label = if (callState.isCameraOn) "ক্যামেরা" else "বন্ধ",
                        active = !callState.isCameraOn,
                        activeColor = AmberWarning,
                        testTag = "video_toggle_camera",
                        onClick = onToggleCamera
                    )
                    CallCircleButton(
                        icon = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (callState.isMuted) "আনমিউট" else "মিউট",
                        active = callState.isMuted,
                        activeColor = AmberWarning,
                        testTag = "video_toggle_mute",
                        onClick = onToggleMute
                    )
                    CallCircleButton(
                        icon = if (callState.isScreenSharing) Icons.Default.StopScreenShare else Icons.Default.PresentToAll,
                        label = if (callState.isScreenSharing) "শেয়ার বন্ধ" else "স্ক্রিন",
                        active = callState.isScreenSharing,
                        activeColor = TealSuccess,
                        testTag = "video_screen_share",
                        onClick = {
                            if (callState.isScreenSharing) {
                                context.startService(
                                    Intent(context, CallForegroundService::class.java).setAction(
                                        CallForegroundService.ACTION_STOP_SCREEN_SHARE
                                    )
                                )
                            } else {
                                val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                screenShareLauncher.launch(mpm.createScreenCaptureIntent())
                            }
                        }
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onEndCall,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = RoseDanger,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("video_end_call")
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "কল কাটুন")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("শেষ", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneControlCallOverlayCards(
    phoneControlState: PhoneControlUiState,
    onStopTask: () -> Unit,
    onResolveConfirmation: (Boolean) -> Unit
) {
    phoneControlState.pendingConfirmation?.let { pending ->
        AyeshaCard(highlightBorder = AmberWarning) {
            Text(
                text = pending.titleBn,
                style = MaterialTheme.typography.titleMedium,
                color = AmberWarning
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = pending.descriptionBn,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onResolveConfirmation(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = TealSuccess),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("confirm_action_approve")
                ) {
                    Text("অনুমতি দিন")
                }
                OutlinedButton(
                    onClick = { onResolveConfirmation(false) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("confirm_action_cancel")
                ) {
                    Text("বাতিল করুন")
                }
            }
        }
    }

    if (phoneControlState.isTaskRunning) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = NavySurfaceHigh.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, RoseDanger.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = phoneControlState.activeTaskDescription.ifBlank { "ফোন নিয়ন্ত্রণ কাজ চলছে..." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "ধাপ ${phoneControlState.stepCount}/${phoneControlState.maxSteps}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onStopTask,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseDanger,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("stop_task_red_button")
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("থামো", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CallCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (active) activeColor.copy(alpha = 0.28f) else NavySurfaceHigh,
                contentColor = if (active) activeColor else Color.White
            ),
            modifier = Modifier
                .size(54.dp)
                .testTag(testTag)
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun CameraXPreviewLayer(
    isFrontCamera: Boolean,
    onFrame: (androidx.camera.core.ImageProxy) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(isFrontCamera, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            try {
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(analysisExecutor) { imageProxy ->
                            onFrame(imageProxy)
                        }
                    }
                val selector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (_: Exception) {
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
