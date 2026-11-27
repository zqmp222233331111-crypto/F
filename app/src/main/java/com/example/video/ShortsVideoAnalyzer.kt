package com.example.video

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class PickedVideoInfo(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long
)

sealed class ShortsAnalyzeOutcome {
    data class Success(val result: ShortsAnalysisResult) : ShortsAnalyzeOutcome()
    data class Failure(val messageBn: String) : ShortsAnalyzeOutcome()
}

/**
 * Analyzes both visual frames and audio tracks of a gallery video using the
 * user's Gemini API Key via Gemini Resumable Files API + Gemini 2.5 Flash.
 * Streams video bytes directly from ContentResolver to prevent OutOfMemoryError.
 */
class ShortsVideoAnalyzer(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
) {

    fun inspectVideo(context: Context, uri: Uri): PickedVideoInfo {
        val resolver = context.contentResolver
        var displayName = "video.mp4"
        var sizeBytes = 0L

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                }
            }
        }

        if (sizeBytes <= 0L) {
            try {
                resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0) sizeBytes = afd.length
                }
            } catch (_: Exception) {
            }
        }

        var durationMs = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("video/") } ?: "video/mp4"
        return PickedVideoInfo(
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            durationMs = durationMs
        )
    }

    suspend fun analyzeVideo(
        context: Context,
        apiKey: String,
        videoUri: Uri,
        language: ShortsOutputLanguage,
        creatorNote: String,
        onProgress: (String) -> Unit
    ): ShortsAnalyzeOutcome = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext ShortsAnalyzeOutcome.Failure(
                "Gemini API কী পাওয়া যায়নি। সেটিংস থেকে আপনার API কী যাচাই করুন।"
            )
        }

        val info = inspectVideo(context, videoUri)
        if (info.sizeBytes <= 0L) {
            return@withContext ShortsAnalyzeOutcome.Failure(
                "ভিডিও ফাইলটি পড়া যাচ্ছে না বা আকার শূন্য। অনুগ্রহ করে অন্য একটি ভিডিও বাছাই করুন।"
            )
        }
        if (info.sizeBytes > MAX_VIDEO_BYTES) {
            return@withContext ShortsAnalyzeOutcome.Failure(
                "ভিডিওটির আকার ৫০০ MB-এর বেশি। অনুগ্রহ করে ছোট কোনো শর্টস ভিডিও বাছাই করুন।"
            )
        }

        var uploadedFileName: String? = null
        try {
            onProgress("ভিডিও আপলোড শুরু হচ্ছে...")
            val uploadUrl = startResumableUpload(trimmedKey, info)

            onProgress("ভিডিও ও অডিও পাঠানো হচ্ছে...")
            val uploaded = uploadVideoStream(context, videoUri, uploadUrl, info.sizeBytes, info.mimeType)
            uploadedFileName = uploaded.name

            onProgress("ভিডিও প্রক্রিয়াকরণ হচ্ছে...")
            val activeUri = waitUntilFileActive(trimmedKey, uploaded.name, uploaded.uri, uploaded.state)

            onProgress("আয়শা ভিডিও ও অডিও বিশ্লেষণ করছে...")
            val generateBody = buildGenerateContentRequestJson(
                fileUri = activeUri,
                mimeType = info.mimeType,
                language = language,
                creatorNote = creatorNote
            )

            val genRequest = Request.Builder()
                .url(GENERATE_CONTENT_ENDPOINT)
                .post(generateBody.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("x-goog-api-key", trimmedKey)
                .build()

            httpClient.newCall(genRequest).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext ShortsAnalyzeOutcome.Failure(
                        mapHttpErrorToBn(resp.code, rawBody)
                    )
                }

                val parsed = parseGenerateContentResponse(
                    rawResponse = rawBody,
                    language = language,
                    videoDisplayName = info.displayName,
                    videoUriString = videoUri.toString()
                )
                if (parsed != null) {
                    ShortsAnalyzeOutcome.Success(parsed)
                } else {
                    ShortsAnalyzeOutcome.Failure(
                        "ভিডিও বিশ্লেষণের উত্তরটি সঠিকভাবে পড়া যায়নি। অনুগ্রহ করে আবার চেষ্টা করুন।"
                    )
                }
            }
        } catch (_: UnknownHostException) {
            ShortsAnalyzeOutcome.Failure("ইন্টারনেট সংযোগ নেই। Wi-Fi বা মোবাইল ডেটা চালু করে আবার চেষ্টা করুন।")
        } catch (_: SocketTimeoutException) {
            ShortsAnalyzeOutcome.Failure("নেটওয়ার্ক সংযোগ ধীর হওয়ায় সময় শেষ হয়েছে। আবার চেষ্টা করুন।")
        } catch (e: AnalyzerHttpException) {
            ShortsAnalyzeOutcome.Failure(e.messageBn)
        } catch (e: IOException) {
            ShortsAnalyzeOutcome.Failure(
                e.message?.takeIf { it.isNotBlank() } ?: "ভিডিও পাঠাতে নেটওয়ার্ক সমস্যা হয়েছে।"
            )
        } catch (e: Exception) {
            ShortsAnalyzeOutcome.Failure(
                e.message?.takeIf { it.isNotBlank() } ?: "ভিডিও বিশ্লেষণে অপ্রত্যাশিত সমস্যা হয়েছে।"
            )
        } finally {
            uploadedFileName?.let { deleteUploadedFileQuietly(trimmedKey, it) }
        }
    }

    private fun startResumableUpload(apiKey: String, info: PickedVideoInfo): String {
        val metadataJson = mapAdapter.toJson(
            mapOf("file" to mapOf("display_name" to info.displayName.take(120)))
        )

        val request = Request.Builder()
            .url(UPLOAD_ENDPOINT)
            .post(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("X-Goog-Upload-Protocol", "resumable")
            .addHeader("X-Goog-Upload-Command", "start")
            .addHeader("X-Goog-Upload-Header-Content-Length", info.sizeBytes.toString())
            .addHeader("X-Goog-Upload-Header-Content-Type", info.mimeType)
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw AnalyzerHttpException(mapHttpErrorToBn(resp.code, raw))
            }
            val uploadUrl = resp.headers.names()
                .firstOrNull { it.equals("X-Goog-Upload-URL", ignoreCase = true) }
                ?.let { resp.header(it) }
            if (uploadUrl.isNullOrBlank()) {
                throw AnalyzerHttpException("আপলোড সেশন শুরু করা যায়নি। কিছুক্ষণ পর আবার চেষ্টা করুন।")
            }
            return uploadUrl
        }
    }

    private data class UploadedFileMeta(
        val name: String,
        val uri: String,
        val state: String
    )

    private fun uploadVideoStream(
        context: Context,
        videoUri: Uri,
        uploadUrl: String,
        contentLength: Long,
        mimeType: String
    ): UploadedFileMeta {
        val streamingBody = object : RequestBody() {
            override fun contentType() = mimeType.toMediaType()
            override fun contentLength() = contentLength
            override fun writeTo(sink: BufferedSink) {
                val input = context.contentResolver.openInputStream(videoUri)
                    ?: throw IOException("ভিডিও ফাইলটি খোলা যায়নি।")
                input.use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }

        val request = Request.Builder()
            .url(uploadUrl)
            .post(streamingBody)
            .addHeader("X-Goog-Upload-Offset", "0")
            .addHeader("X-Goog-Upload-Command", "upload, finalize")
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw AnalyzerHttpException(mapHttpErrorToBn(resp.code, raw))
            }
            val root = mapAdapter.fromJson(raw).orEmpty()
            @Suppress("UNCHECKED_CAST")
            val fileObj = (root["file"] as? Map<String, Any?>) ?: root
            val name = (fileObj["name"] as? String).orEmpty()
            val uri = (fileObj["uri"] as? String).orEmpty()
            val state = (fileObj["state"] as? String).orEmpty().ifBlank { "PROCESSING" }
            if (name.isBlank() || uri.isBlank()) {
                throw AnalyzerHttpException("সার্ভার থেকে ভিডিও ফাইলের তথ্য পাওয়া যায়নি।")
            }
            return UploadedFileMeta(name = name, uri = uri, state = state)
        }
    }

    private suspend fun waitUntilFileActive(
        apiKey: String,
        fileName: String,
        initialUri: String,
        initialState: String
    ): String {
        if (initialState.equals("ACTIVE", ignoreCase = true)) {
            return initialUri
        }
        if (initialState.equals("FAILED", ignoreCase = true)) {
            throw AnalyzerHttpException("ভিডিও ফাইলটি প্রসেস করা যায়নি। অনুগ্রহ করে অন্য ফরম্যাটের ভিডিও দিন।")
        }

        val cleanName = fileName.removePrefix("files/")
        val pollUrl = "$FILES_STATUS_BASE/$cleanName"

        repeat(45) {
            delay(2000L)
            val req = Request.Builder()
                .url(pollUrl)
                .get()
                .addHeader("x-goog-api-key", apiKey)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw AnalyzerHttpException(mapHttpErrorToBn(resp.code, raw))
                }
                val root = mapAdapter.fromJson(raw).orEmpty()
                @Suppress("UNCHECKED_CAST")
                val fileObj = (root["file"] as? Map<String, Any?>) ?: root
                val state = (fileObj["state"] as? String).orEmpty()
                val uri = (fileObj["uri"] as? String).orEmpty().ifBlank { initialUri }
                if (state.equals("ACTIVE", ignoreCase = true)) {
                    return uri
                }
                if (state.equals("FAILED", ignoreCase = true)) {
                    throw AnalyzerHttpException("সার্ভারে ভিডিও প্রক্রিয়াকরণ ব্যর্থ হয়েছে।")
                }
            }
        }
        throw AnalyzerHttpException("ভিডিও প্রসেস হতে বেশি সময় লাগছে। অনুগ্রহ করে ছোট ভিডিও দিয়ে আবার চেষ্টা করুন।")
    }

    private fun deleteUploadedFileQuietly(apiKey: String, fileName: String) {
        try {
            val cleanName = fileName.removePrefix("files/")
            val req = Request.Builder()
                .url("$FILES_STATUS_BASE/$cleanName")
                .delete()
                .addHeader("x-goog-api-key", apiKey)
                .build()
            httpClient.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    private class AnalyzerHttpException(val messageBn: String) : Exception(messageBn)

    companion object {
        const val UPLOAD_ENDPOINT =
            "https://generativelanguage.googleapis.com/upload/v1beta/files"
        const val FILES_STATUS_BASE =
            "https://generativelanguage.googleapis.com/v1beta/files"
        const val GENERATE_CONTENT_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

        private const val MAX_VIDEO_BYTES = 500L * 1024L * 1024L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val moshi: Moshi = Moshi.Builder().build()
        private val mapAdapter = moshi.adapter<Map<String, Any?>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )

        fun buildGenerateContentRequestJson(
            fileUri: String,
            mimeType: String,
            language: ShortsOutputLanguage,
            creatorNote: String
        ): String {
            val noteSection = if (creatorNote.isNotBlank()) {
                "\nCreator's extra context/instruction: ${creatorNote.trim()}"
            } else {
                ""
            }

            val promptText = """
                Carefully analyze BOTH the video's visual frames (scenes, actions, expressions, on-screen text) AND the complete audio track (spoken dialogue, voiceover, tone, background sound/music).
                You are generating a complete 3-platform short-video creator pack from this single video: YouTube Shorts, Facebook Reels, and TikTok.
                Language preference: ${language.promptInstruction}
                Requirements:
                1. visualSummaryBn: 1-2 short sentences in simple Bengali describing what happens visually in the video.
                2. audioSummaryBn: 1-2 short sentences in simple Bengali describing what is spoken or heard in the audio track (state clearly if there is only music or silence).
                3. youtube: Object for YouTube Shorts:
                   - titles: 3 catchy, high-CTR SEO YouTube Shorts titles (each under 85 characters)
                   - recommendedTitle: Best single YouTube Shorts title (under 85 characters)
                   - description: Engaging Shorts description with hook, CTA, and 6-10 hashtags ALWAYS including #shorts #youtubeshorts
                   - hashtags: List of those 6-10 hashtags (starting with #)
                4. facebook: Object for Facebook Reels:
                   - titles: 3 conversational, share-worthy Facebook Reels hook titles
                   - recommendedTitle: Best Facebook Reels hook title
                   - description: Warm storytelling Facebook Reels caption/description with engagement question and hashtags ALWAYS including #reels #facebookreels
                   - hashtags: List of 6-10 Facebook Reels hashtags (starting with #)
                5. tiktok: Object for TikTok:
                   - titles: 3 short, punchy, curiosity-driven TikTok hook lines
                   - recommendedTitle: Best TikTok hook title
                   - description: Trendy, snappy TikTok caption with hashtags ALWAYS including #fyp #tiktok
                   - hashtags: List of 6-10 TikTok hashtags (starting with #)$noteSection
            """.trimIndent()

            fun platformSchema(): Map<String, Any> = mapOf(
                "type" to "OBJECT",
                "properties" to mapOf(
                    "titles" to mapOf(
                        "type" to "ARRAY",
                        "items" to mapOf("type" to "STRING")
                    ),
                    "recommendedTitle" to mapOf("type" to "STRING"),
                    "description" to mapOf("type" to "STRING"),
                    "hashtags" to mapOf(
                        "type" to "ARRAY",
                        "items" to mapOf("type" to "STRING")
                    )
                ),
                "required" to listOf("titles", "recommendedTitle", "description", "hashtags")
            )

            val schema = mapOf(
                "type" to "OBJECT",
                "properties" to mapOf(
                    "visualSummaryBn" to mapOf("type" to "STRING"),
                    "audioSummaryBn" to mapOf("type" to "STRING"),
                    "youtube" to platformSchema(),
                    "facebook" to platformSchema(),
                    "tiktok" to platformSchema()
                ),
                "required" to listOf(
                    "visualSummaryBn",
                    "audioSummaryBn",
                    "youtube",
                    "facebook",
                    "tiktok"
                )
            )

            val parts = listOf(
                mapOf(
                    "fileData" to mapOf(
                        "mimeType" to mimeType,
                        "fileUri" to fileUri
                    )
                ),
                mapOf("text" to promptText)
            )

            val root = mapOf(
                "contents" to listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to parts
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.7,
                    "responseMimeType" to "application/json",
                    "responseSchema" to schema
                )
            )
            return mapAdapter.toJson(root)
        }

        private fun parsePlatformBlock(
            block: Map<*, *>?,
            platform: ShortsPlatform,
            defaultTag: String
        ): PlatformPackItem? {
            if (block == null) return null
            val titles = (block["titles"] as? List<*>)
                .orEmpty()
                .mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .distinct()

            val recommended = (block["recommendedTitle"] as? String)?.trim().orEmpty()
                .ifEmpty { titles.firstOrNull().orEmpty() }
            val finalTitles = if (recommended.isNotEmpty() && recommended !in titles) {
                listOf(recommended) + titles
            } else {
                titles.ifEmpty { listOf(recommended).filter { it.isNotEmpty() } }
            }

            val hashtags = (block["hashtags"] as? List<*>)
                .orEmpty()
                .mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .map { tag -> if (tag.startsWith("#")) tag else "#$tag" }
                .distinct()

            var description = (block["description"] as? String)?.trim().orEmpty()
            if (hashtags.isNotEmpty() && !description.contains(defaultTag, ignoreCase = true)) {
                description = if (description.isNotEmpty()) {
                    "$description\n\n${hashtags.joinToString(" ")}"
                } else {
                    hashtags.joinToString(" ")
                }
            }
            if (recommended.isBlank() && description.isBlank()) return null

            return PlatformPackItem(
                platform = platform,
                selectedTitle = recommended,
                titles = finalTitles,
                description = description,
                hashtags = hashtags
            )
        }

        @Suppress("UNCHECKED_CAST")
        fun parseGenerateContentResponse(
            rawResponse: String,
            language: ShortsOutputLanguage,
            videoDisplayName: String,
            videoUriString: String
        ): ShortsAnalysisResult? {
            return try {
                val root = mapAdapter.fromJson(rawResponse) ?: return null
                val candidates = root["candidates"] as? List<*> ?: return null
                val firstCandidate = candidates.firstOrNull() as? Map<String, Any?> ?: return null
                val content = firstCandidate["content"] as? Map<String, Any?> ?: return null
                val parts = content["parts"] as? List<*> ?: return null

                val rawText = parts
                    .mapNotNull { (it as? Map<*, *>)?.get("text") as? String }
                    .joinToString("")
                    .trim()

                val cleanJson = stripMarkdownFences(rawText)
                val payload = mapAdapter.fromJson(cleanJson) ?: return null

                val packs = mutableMapOf<ShortsPlatform, PlatformPackItem>()
                parsePlatformBlock(payload["youtube"] as? Map<*, *>, ShortsPlatform.YOUTUBE_SHORTS, "#shorts")
                    ?.let { packs[ShortsPlatform.YOUTUBE_SHORTS] = it }
                parsePlatformBlock(payload["facebook"] as? Map<*, *>, ShortsPlatform.FACEBOOK_REELS, "#reels")
                    ?.let { packs[ShortsPlatform.FACEBOOK_REELS] = it }
                parsePlatformBlock(payload["tiktok"] as? Map<*, *>, ShortsPlatform.TIKTOK, "#fyp")
                    ?.let { packs[ShortsPlatform.TIKTOK] = it }

                // Fallback if flat single-platform shape returned
                if (packs.isEmpty()) {
                    parsePlatformBlock(payload, ShortsPlatform.YOUTUBE_SHORTS, "#shorts")
                        ?.let { packs[ShortsPlatform.YOUTUBE_SHORTS] = it }
                }

                if (packs.isEmpty()) return null

                ShortsAnalysisResult(
                    platformPacks = packs,
                    activePlatform = ShortsPlatform.YOUTUBE_SHORTS,
                    visualSummaryBn = (payload["visualSummaryBn"] as? String)?.trim().orEmpty(),
                    audioSummaryBn = (payload["audioSummaryBn"] as? String)?.trim().orEmpty(),
                    language = language,
                    videoDisplayName = videoDisplayName,
                    videoUriString = videoUriString,
                    analyzedAtMillis = System.currentTimeMillis()
                )
            } catch (_: Exception) {
                null
            }
        }

        fun mapHttpErrorToBn(code: Int, body: String): String = when {
            code == 429 || body.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                body.contains("quota", ignoreCase = true) ->
                "ফ্রি এপিআই সীমা পূর্ণ হয়েছে। কিছুক্ষণ অপেক্ষা করে আবার চেষ্টা করুন।"
            code == 401 || code == 403 || body.contains("API_KEY_INVALID", ignoreCase = true) ->
                "API কী-টি সঠিক নয় বা অনুমোদিত নয়। সেটিংস থেকে কী-টি যাচাই করুন।"
            code == 400 ->
                "ভিডিও ফরম্যাট বা অনুরোধটি গ্রহণ করা যায়নি। অনুগ্রহ করে MP4/নতুন ভিডিও দিয়ে চেষ্টা করুন।"
            else ->
                "সার্ভার ত্রুটি হয়েছে (কোড: $code)। কিছুক্ষণ পরে আবার চেষ্টা করুন।"
        }

        private fun stripMarkdownFences(text: String): String {
            var trimmed = text.trim()
            if (trimmed.startsWith("```")) {
                trimmed = trimmed.removePrefix("```json").removePrefix("```").trim()
                if (trimmed.endsWith("```")) {
                    trimmed = trimmed.removeSuffix("```").trim()
                }
            }
            return trimmed
        }
    }
}

object YouTubeLaunchHelper {
    private const val YOUTUBE_PKG = "com.google.android.youtube"
    private const val YOUTUBE_STUDIO_PKG = "com.google.android.apps.youtube.creator"
    private const val FACEBOOK_PKG = "com.facebook.katana"
    private val TIKTOK_PKGS = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    }

    fun shareVideoToPlatform(context: Context, videoUri: Uri, platform: ShortsPlatform): Boolean {
        return when (platform) {
            ShortsPlatform.YOUTUBE_SHORTS -> shareVideoToPackages(context, videoUri, listOf(YOUTUBE_PKG), "YouTube-এ ভিডিও পাঠান")
            ShortsPlatform.FACEBOOK_REELS -> shareVideoToPackages(context, videoUri, listOf(FACEBOOK_PKG), "Facebook-এ ভিডিও পাঠান")
            ShortsPlatform.TIKTOK -> shareVideoToPackages(context, videoUri, TIKTOK_PKGS, "TikTok-এ ভিডিও পাঠান")
        }
    }

    fun shareVideoToYouTube(context: Context, videoUri: Uri): Boolean =
        shareVideoToPlatform(context, videoUri, ShortsPlatform.YOUTUBE_SHORTS)

    private fun shareVideoToPackages(
        context: Context,
        videoUri: Uri,
        candidatePackages: List<String>,
        chooserTitle: String
    ): Boolean {
        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, videoUri)
            clipData = android.content.ClipData.newRawUri("video", videoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pm = context.packageManager
        val matchedPkg = candidatePackages.firstOrNull { pkg ->
            val probe = Intent(baseIntent).setPackage(pkg)
            pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        }

        return try {
            if (matchedPkg != null) {
                context.startActivity(Intent(baseIntent).setPackage(matchedPkg))
            } else {
                val chooser = Intent.createChooser(baseIntent, chooserTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openYouTubeStudio(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(YOUTUBE_STUDIO_PKG)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false
        return try {
            context.startActivity(launch)
            true
        } catch (_: Exception) {
            false
        }
    }
}
