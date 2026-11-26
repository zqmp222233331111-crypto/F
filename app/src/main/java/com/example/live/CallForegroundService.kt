package com.example.live

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.AyeshaApplication
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service keeping live audio/video/screen-share calls alive in background
 * with persistent Mute and End Call notification actions and FloatingOrbOverlay.
 */
class CallForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var overlay: FloatingOrbOverlay? = null

    private var activeCamera = false
    private var activeProjection = false
    private var isForegroundAppVisible = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val app = application as AyeshaApplication
        overlay = FloatingOrbOverlay(this) {
            app.container.phoneControlCoordinator.stopTaskNow()
        }

        observerJob = serviceScope.launch {
            launch {
                app.container.liveSessionManager.callState.collectLatest { state ->
                    if (state.isInCall) {
                        updateNotification(
                            isMuted = state.isMuted,
                            modeTitle = state.modeTitleBn,
                            statusLabel = state.status.labelBn
                        )
                    }
                }
            }
            launch {
                app.container.phoneControlCoordinator.uiState.collectLatest { ctrl ->
                    if (!isForegroundAppVisible && app.container.liveSessionManager.callState.value.isInCall) {
                        overlay?.show(ctrl.isTaskRunning)
                    } else {
                        overlay?.updateTaskState(ctrl.isTaskRunning)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as AyeshaApplication
        when (intent?.action) {
            ACTION_START_CALL -> {
                activeCamera = intent.getBooleanExtra(EXTRA_USE_CAMERA, false)
                activeProjection = false
                startForegroundWithTypes(
                    buildNotification(
                        isMuted = app.container.liveSessionManager.callState.value.isMuted,
                        modeTitle = app.container.liveSessionManager.callState.value.modeTitleBn,
                        statusLabel = app.container.liveSessionManager.callState.value.status.labelBn
                    )
                )
            }
            ACTION_TOGGLE_MUTE -> {
                app.container.liveSessionManager.toggleMute()
            }
            ACTION_END_CALL -> {
                app.container.liveSessionManager.endCall()
                stopSelfCleanly()
            }
            ACTION_APP_BACKGROUNDED -> {
                isForegroundAppVisible = false
                if (app.container.liveSessionManager.callState.value.isInCall) {
                    val taskRunning = app.container.phoneControlCoordinator.uiState.value.isTaskRunning
                    overlay?.show(taskRunning)
                }
            }
            ACTION_APP_FOREGROUNDED -> {
                isForegroundAppVisible = true
                overlay?.hide()
            }
            ACTION_START_SCREEN_SHARE -> {
                val resultCode = intent.getIntExtra(EXTRA_PROJECTION_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val data: Intent? = intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
                if (resultCode == Activity.RESULT_OK && data != null) {
                    activeProjection = true
                    startForegroundWithTypes(
                        buildNotification(
                            isMuted = app.container.liveSessionManager.callState.value.isMuted,
                            modeTitle = app.container.liveSessionManager.callState.value.modeTitleBn,
                            statusLabel = "স্ক্রিন শেয়ার চলছে"
                        )
                    )
                    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val projection = mpm.getMediaProjection(resultCode, data)
                    if (projection != null) {
                        app.container.liveSessionManager.attachMediaProjection(projection)
                    }
                }
            }
            ACTION_STOP_SCREEN_SHARE -> {
                activeProjection = false
                app.container.liveSessionManager.stopScreenShare()
                startForegroundWithTypes(
                    buildNotification(
                        isMuted = app.container.liveSessionManager.callState.value.isMuted,
                        modeTitle = app.container.liveSessionManager.callState.value.modeTitleBn,
                        statusLabel = app.container.liveSessionManager.callState.value.status.labelBn
                    )
                )
            }
            ACTION_UPDATE_CAMERA -> {
                activeCamera = intent.getBooleanExtra(EXTRA_USE_CAMERA, false)
                startForegroundWithTypes(
                    buildNotification(
                        isMuted = app.container.liveSessionManager.callState.value.isMuted,
                        modeTitle = app.container.liveSessionManager.callState.value.modeTitleBn,
                        statusLabel = app.container.liveSessionManager.callState.value.status.labelBn
                    )
                )
            }
            ACTION_STOP_SERVICE -> {
                stopSelfCleanly()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithTypes(notification: Notification) {
        var types = 0
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (activeCamera && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        if (activeProjection) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && types != 0) {
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(isMuted: Boolean, modeTitle: String, statusLabel: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(isMuted, modeTitle, statusLabel))
    }

    private fun buildNotification(
        isMuted: Boolean,
        modeTitle: String,
        statusLabel: String
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CallForegroundService::class.java).setAction(ACTION_TOGGLE_MUTE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, CallForegroundService::class.java).setAction(ACTION_END_CALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("আয়শা • $modeTitle")
            .setContentText(statusLabel)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(
                0,
                if (isMuted) "আনমিউট" else "মিউট",
                muteIntent
            )
            .addAction(0, "কল কাটুন", endIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.call_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "আয়শা লাইভ অডিও ও ভিডিও কলের চলমান বিজ্ঞপ্তি"
        }
        nm.createNotificationChannel(channel)
    }

    private fun stopSelfCleanly() {
        overlay?.hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        overlay?.hide()
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "ayesha_live_call_channel"
        const val NOTIFICATION_ID = 4001

        const val ACTION_START_CALL = "com.example.live.START_CALL"
        const val ACTION_TOGGLE_MUTE = "com.example.live.TOGGLE_MUTE"
        const val ACTION_END_CALL = "com.example.live.END_CALL"
        const val ACTION_APP_BACKGROUNDED = "com.example.live.APP_BG"
        const val ACTION_APP_FOREGROUNDED = "com.example.live.APP_FG"
        const val ACTION_START_SCREEN_SHARE = "com.example.live.START_SCREEN_SHARE"
        const val ACTION_STOP_SCREEN_SHARE = "com.example.live.STOP_SCREEN_SHARE"
        const val ACTION_UPDATE_CAMERA = "com.example.live.UPDATE_CAMERA"
        const val ACTION_STOP_SERVICE = "com.example.live.STOP_SERVICE"

        const val EXTRA_USE_CAMERA = "use_camera"
        const val EXTRA_PROJECTION_CODE = "projection_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"
    }
}
