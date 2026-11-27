package com.example.video

enum class ShortsOutputLanguage(
    val id: String,
    val labelBn: String,
    val promptInstruction: String
) {
    BANGLA(
        id = "bn",
        labelBn = "বাংলা",
        promptInstruction = "Write titles, captions, descriptions, and summaries in natural, engaging, viral Bangladeshi Bengali (using natural Bengali hooks + high-search keywords)."
    ),
    ENGLISH(
        id = "en",
        labelBn = "English",
        promptInstruction = "Write titles, captions, and descriptions in crisp, high-retention natural English (while keeping summaries and Video Doctor diagnostics in simple Bengali)."
    ),
    BANGLISH(
        id = "bn_en",
        labelBn = "মিশ্র (বাংলা + Eng)",
        promptInstruction = "Write titles, captions, and descriptions in a natural modern Bangladeshi creator mix of Bengali and punchy English keywords (while keeping summaries and Video Doctor diagnostics in simple Bengali)."
    );

    companion object {
        fun fromId(id: String?): ShortsOutputLanguage =
            entries.firstOrNull { it.id == id } ?: BANGLA
    }
}

enum class ShortsPlatform(
    val id: String,
    val labelBn: String,
    val shortTabBn: String
) {
    YOUTUBE_SHORTS("youtube", "YouTube Shorts", "YouTube"),
    FACEBOOK_REELS("facebook", "Facebook Reels", "FB Reels"),
    TIKTOK("tiktok", "TikTok", "TikTok");

    companion object {
        fun fromId(id: String?): ShortsPlatform =
            entries.firstOrNull { it.id == id } ?: YOUTUBE_SHORTS
    }
}

enum class ShortsSubTab(val id: String, val titleBn: String) {
    DOCTOR("doctor", "ভিডিও ডক্টর"),
    TRENDS("trends", "ট্রেন্ড রাডার"),
    GROWTH_LOG("growth", "গ্রোথ লগ")
}

enum class TrendRegion(val id: String, val labelBn: String, val promptHint: String) {
    BANGLADESH("bd", "বাংলাদেশ", "Bangladesh (Bangladeshi audience, Bengali & local viral trends)"),
    GLOBAL("global", "আন্তর্জাতিক (Global)", "Global international audience across YouTube Shorts, Facebook Reels, and TikTok")
}

enum class TrendPlatformFilter(val id: String, val labelBn: String) {
    ALL("all", "সব প্ল্যাটফর্ম"),
    YOUTUBE_SHORTS("youtube", "YouTube Shorts"),
    FACEBOOK_REELS("facebook", "FB Reels"),
    TIKTOK("tiktok", "TikTok")
}

data class VideoDoctorDiagnostics(
    val detectedCategoryBn: String,
    val targetAudienceScopeBn: String,
    val viralScore: Int,
    val hookScore: Int,
    val pacingScore: Int,
    val strengthsBn: List<String>,
    val weaknessesBn: List<String>,
    val rescueFixesBn: List<String>,
    val thumbnailTextBn: String,
    val pinnedCommentBn: String,
    val bestUploadWindowBn: String
)

data class PlatformPackItem(
    val platform: ShortsPlatform,
    val selectedTitle: String,
    val titles: List<String>,
    val description: String,
    val hashtags: List<String>
)

data class ShortsAnalysisResult(
    val platformPacks: Map<ShortsPlatform, PlatformPackItem>,
    val activePlatform: ShortsPlatform = ShortsPlatform.YOUTUBE_SHORTS,
    val visualSummaryBn: String,
    val audioSummaryBn: String,
    val language: ShortsOutputLanguage,
    val videoDisplayName: String,
    val videoUriString: String,
    val analyzedAtMillis: Long,
    val diagnostics: VideoDoctorDiagnostics? = null
) {
    val activePack: PlatformPackItem
        get() = platformPacks[activePlatform]
            ?: platformPacks.values.firstOrNull()
            ?: PlatformPackItem(activePlatform, "", emptyList(), "", emptyList())

    val selectedTitle: String get() = activePack.selectedTitle
    val titles: List<String> get() = activePack.titles
    val description: String get() = activePack.description
    val hashtags: List<String> get() = activePack.hashtags
}

data class ShortsAnalyzerUiState(
    val activeSubTab: ShortsSubTab = ShortsSubTab.DOCTOR,
    val selectedVideoUri: String? = null,
    val selectedVideoName: String = "",
    val selectedVideoSizeBytes: Long = 0L,
    val selectedVideoDurationMs: Long = 0L,
    val outputLanguage: ShortsOutputLanguage = ShortsOutputLanguage.BANGLA,
    val creatorNote: String = "",
    val isAnalyzing: Boolean = false,
    val progressMessageBn: String = "",
    val errorMessageBn: String? = null,
    val result: ShortsAnalysisResult? = null
)

data class TrendIdeaItem(
    val topicTitleBn: String,
    val platformFit: String,
    val hookIdeaBn: String,
    val whyTrendingBn: String,
    val competitionLevelBn: String,
    val viralTags: List<String>
)

data class TrendRadarResult(
    val nicheQuery: String,
    val region: TrendRegion,
    val platformFilter: TrendPlatformFilter,
    val trendSummaryBn: String,
    val bestTimingBn: String,
    val ideas: List<TrendIdeaItem>,
    val sources: List<String>,
    val fetchedAtMillis: Long
)

data class TrendRadarUiState(
    val nicheQuery: String = "",
    val region: TrendRegion = TrendRegion.BANGLADESH,
    val platformFilter: TrendPlatformFilter = TrendPlatformFilter.ALL,
    val isLoading: Boolean = false,
    val errorMessageBn: String? = null,
    val result: TrendRadarResult? = null
)
