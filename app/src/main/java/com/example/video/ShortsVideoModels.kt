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
        promptInstruction = "Write titles, captions, and descriptions in crisp, high-retention natural English (while keeping visualSummaryBn and audioSummaryBn in simple Bengali)."
    ),
    BANGLISH(
        id = "bn_en",
        labelBn = "মিশ্র (বাংলা + Eng)",
        promptInstruction = "Write titles, captions, and descriptions in a natural modern Bangladeshi creator mix of Bengali and punchy English keywords (while keeping visualSummaryBn and audioSummaryBn in simple Bengali)."
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
    val analyzedAtMillis: Long
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
