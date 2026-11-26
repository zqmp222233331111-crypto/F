package com.example

import android.app.Application
import android.content.Context
import com.example.control.PhoneControlCoordinator
import com.example.data.ApiKeyVerifier
import com.example.data.AyeshaRepository
import com.example.data.KeystoreManager
import com.example.data.SettingsRepository
import com.example.data.db.AyeshaDatabase
import com.example.live.LiveSessionManager
import com.example.live.VoiceSamplePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val keystoreManager = KeystoreManager(appContext)
    val apiKeyVerifier = ApiKeyVerifier()
    val database = AyeshaDatabase.getInstance(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val repository = AyeshaRepository(database, settingsRepository, keystoreManager)
    val phoneControlCoordinator = PhoneControlCoordinator(appContext, settingsRepository, repository)
    val liveSessionManager = LiveSessionManager(
        context = appContext,
        keystoreManager = keystoreManager,
        settingsRepository = settingsRepository,
        repository = repository,
        phoneControlCoordinator = phoneControlCoordinator
    )
    val voiceSamplePlayer = VoiceSamplePlayer()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.ensureDefaultBlockedAppsSeeded()
        }
    }
}

class AyeshaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
