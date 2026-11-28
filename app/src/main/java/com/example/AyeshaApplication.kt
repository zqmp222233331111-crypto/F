package com.example

import android.app.Application
import android.content.Context
import com.example.control.PhoneControlCoordinator
import com.example.data.ApiKeyVerifier
import com.example.data.AyeshaRepository
import com.example.data.KeystoreManager
import com.example.data.SettingsRepository
import com.example.data.db.AyeshaDatabase
import com.example.live.CallAutoMemorySummarizer
import com.example.live.LiveSessionManager
import com.example.live.LiveWebSearchService
import com.example.live.VoiceSamplePlayer
import com.example.video.CreatorCoachChatService
import com.example.video.LatestShortsDraftStore
import com.example.video.ShortsVideoAnalyzer
import com.example.video.TrendRadarService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val keystoreManager by lazy(LazyThreadSafetyMode.NONE) { KeystoreManager(appContext) }
    val apiKeyVerifier by lazy(LazyThreadSafetyMode.NONE) { ApiKeyVerifier() }
    val database by lazy(LazyThreadSafetyMode.NONE) { AyeshaDatabase.getInstance(appContext) }
    val settingsRepository by lazy(LazyThreadSafetyMode.NONE) { SettingsRepository(appContext) }
    val repository by lazy(LazyThreadSafetyMode.NONE) { AyeshaRepository(database, settingsRepository, keystoreManager) }
    val phoneControlCoordinator by lazy { PhoneControlCoordinator(appContext, settingsRepository, repository) }
    val latestShortsDraftStore by lazy { LatestShortsDraftStore(appContext) }
    val shortsVideoAnalyzer by lazy(LazyThreadSafetyMode.NONE) { ShortsVideoAnalyzer() }
    val creatorCoachChatService by lazy(LazyThreadSafetyMode.NONE) { CreatorCoachChatService() }
    val trendRadarService by lazy(LazyThreadSafetyMode.NONE) { TrendRadarService() }
    val liveWebSearchService by lazy(LazyThreadSafetyMode.NONE) { LiveWebSearchService() }
    val callAutoMemorySummarizer by lazy(LazyThreadSafetyMode.NONE) { CallAutoMemorySummarizer() }
    val liveSessionManager by lazy {
        LiveSessionManager(
            context = appContext,
            keystoreManager = keystoreManager,
            settingsRepository = settingsRepository,
            repository = repository,
            phoneControlCoordinator = phoneControlCoordinator,
            latestShortsDraftStore = latestShortsDraftStore,
            liveWebSearchService = liveWebSearchService,
            callAutoMemorySummarizer = callAutoMemorySummarizer,
            trendRadarService = trendRadarService
        )
    }
    val voiceSamplePlayer by lazy(LazyThreadSafetyMode.NONE) { VoiceSamplePlayer() }

    init {
        ioScope.launch {
            latestShortsDraftStore.preload()
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
