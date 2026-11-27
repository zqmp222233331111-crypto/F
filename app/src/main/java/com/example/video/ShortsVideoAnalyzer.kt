package com.example.video

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.GeminiModelResolver
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedInputStream
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
 * user's Gemini API Key via chunked Gemini Resumable Files API + fast Gemini Flash models.
 * Streams video in fixed 4 MiB chunks to eliminate monolithic-upload SocketTimeoutExceptions and OOMs.
 */
class ShortsVideoAnalyzer(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
) {

    private val uploadClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(75, TimeUnit.SECONDS)
            .readTimeout(75, TimeUnit.SECONDS)
            .build()
    }

    private val analyzeClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)
            .callTimeout(260, TimeUnit.SECONDS)
            .build()
    }

    private enum class AnalyzeStage {
        UPLOAD,
        PROCESSING,
        AI_ANALYSIS
    }

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

        if (sizeBytes <= 0L) {
            try {
                resolver.openInputStream(uri)?.use { stream ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = stream.read(buf)
                        if (read == -1) break
                        total += read
                    }
                    sizeBytes = total
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
        var currentStage = AnalyzeStage.UPLOAD
        try {
            onProgress("ভিডিও আপলোড শুরু হচ্ছে...")
            val uploadUrl = startResumableUpload(trimmedKey, info)

            val uploaded = uploadVideoInChunks(
                context = context,
                videoUri = videoUri,
                uploadUrl = uploadUrl,
                totalSizeBytes = info.sizeBytes,
                mimeType = info.mimeType,
                onProgress = onProgress
            )
            uploadedFileName = uploaded.name

            currentStage = AnalyzeStage.PROCESSING
            onProgress("ভিডিও প্রক্রিয়াকরণ হচ্ছে...")
            val activeUri = waitUntilFileActive(trimmedKey, uploaded.name, uploaded.uri, uploaded.state)

            currentStage = AnalyzeStage.AI_ANALYSIS
            onProgress("আয়শা দ্রুত ভিডিও বিশ্লেষণ করছে...")
            val responsePair = executeGenerateWithFallback(
                apiKey = trimmedKey,
                fileUri = activeUri,
                mimeType = info.mimeType,
                language = language,
                creatorNote = creatorNote
            )
            if (responsePair.errorMessageBn != null) {
                return@withContext ShortsAnalyzeOutcome.Failure(responsePair.errorMessageBn)
            }

            val parsed = parseGenerateContentResponse(
                rawResponse = responsePair.rawBody.orEmpty(),
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
        } catch (_: UnknownHostException) {
            ShortsAnalyzeOutcome.Failure("ইন্টারনেট সংযোগ নেই। Wi-Fi বা মোবাইল ডেটা চালু করে আবার চেষ্টা করুন।")
        } catch (_: SocketTimeoutException) {
            val stageMsg = when (currentStage) {
                AnalyzeStage.UPLOAD -> "ভিডিও আপলোডের সময় সংযোগ সাড়া দেয়নি। অনুগ্রহ করে আবার চেষ্টা করুন।"
                AnalyzeStage.PROCESSING -> "সার্ভারে ভিডিও প্রসেস হতে বেশি সময় নিচ্ছে। একটু পর আবার চেষ্টা করুন।"
                AnalyzeStage.AI_ANALYSIS -> "AI বিশ্লেষণের উত্তর আসতে বেশি সময় লেগেছে। অনুগ্রহ করে আবার চেষ্টা করুন।"
            }
            ShortsAnalyzeOutcome.Failure(stageMsg)
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

    private data class GenerateResponseHolder(
        val rawBody: String? = null,
        val errorMessageBn: String? = null
    )

    private fun executeGenerateWithFallback(
        apiKey: String,
        fileUri: String,
        mimeType: String,
        language: ShortsOutputLanguage,
        creatorNote: String
    ): GenerateResponseHolder {
        val tried = linkedSetOf<String>()
        var lastErrorBn: String? = null
        var lastTimedOut = false

        fun callModel(modelId: String, fastConfig: Boolean): Pair<Int, String> {
            val thinkingLevel = if (fastConfig) GeminiModelResolver.fastThinkingLevelFor(modelId) else null
            val bodyJson = buildGenerateContentRequestJson(
                fileUri = fileUri,
                mimeType = mimeType,
                language = language,
                creatorNote = creatorNote,
                thinkingLevel = thinkingLevel,
                includeFastMediaResolution = fastConfig
            )
            val req = Request.Builder()
                .url(GeminiModelResolver.endpointFor(modelId))
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("x-goog-api-key", apiKey)
                .build()
            return analyzeClient.newCall(req).execute().use { resp ->
                resp.code to resp.body?.string().orEmpty()
            }
        }

        fun tryModelWithOptionalConfigStrip(modelId: String): GenerateResponseHolder? {
            var (code, raw) = callModel(modelId, fastConfig = true)
            if (code == 400 && isUnsupportedFastConfigError(raw)) {
                val retried = callModel(modelId, fastConfig = false)
                code = retried.first
                raw = retried.second
            }
            if (code in 200..299) {
                GeminiModelResolver.markWorking(modelId)
                return GenerateResponseHolder(rawBody = raw)
            }
            lastErrorBn = mapHttpErrorToBn(code, raw)
            // Keep trying next model on 404 or transient 429/5xx overload; abort immediately on auth/bad request.
            return if (code == 404 || code == 429 || code in 500..599) {
                null
            } else {
                GenerateResponseHolder(errorMessageBn = lastErrorBn)
            }
        }

        for (modelId in GeminiModelResolver.fastVideoCandidateModels()) {
            tried.add(modelId)
            try {
                val result = tryModelWithOptionalConfigStrip(modelId)
                if (result != null) return result
            } catch (_: SocketTimeoutException) {
                lastTimedOut = true
                lastErrorBn = "AI বিশ্লেষণের উত্তর আসতে বেশি সময় লেগেছে। অনুগ্রহ করে আবার চেষ্টা করুন।"
                // Fail over to the next faster model (e.g. gemini-3.5-flash-lite)
            }
        }

        val discovered = GeminiModelResolver.discoverActiveFlashModel(analyzeClient, apiKey)
        if (!discovered.isNullOrBlank() && discovered !in tried) {
            try {
                val result = tryModelWithOptionalConfigStrip(discovered)
                if (result != null) return result
            } catch (_: SocketTimeoutException) {
                lastTimedOut = true
                lastErrorBn = "AI বিশ্লেষণের উত্তর আসতে বেশি সময় লেগেছে। অনুগ্রহ করে আবার চেষ্টা করুন।"
            }
        }

        if (lastTimedOut && lastErrorBn == null) {
            throw SocketTimeoutException("All candidate models timed out")
        }

        return GenerateResponseHolder(
            errorMessageBn = lastErrorBn ?: "সার্ভার ত্রুটি হয়েছে। কিছুক্ষণ পরে আবার চেষ্টা করুন।"
        )
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

        uploadClient.newCall(request).execute().use { resp ->
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

    private suspend fun uploadVideoInChunks(
        context: Context,
        videoUri: Uri,
        uploadUrl: String,
        totalSizeBytes: Long,
        mimeType: String,
        onProgress: (String) -> Unit
    ): UploadedFileMeta {
        val rawStream = context.contentResolver.openInputStream(videoUri)
            ?: throw IOException("ভিডিও ফাইলটি খোলা যায়নি।")

        val buffer = ByteArray(UPLOAD_CHUNK_BYTES)
        var offset = 0L

        BufferedInputStream(rawStream, 64 * 1024).use { stream ->
            while (true) {
                val bytesRead = readUpToChunkSize(stream, buffer)
                if (bytesRead <= 0) break

                stream.mark(1)
                val nextByte = stream.read()
                val isFinalChunk = (nextByte == -1)
                if (!isFinalChunk) {
                    stream.reset()
                }

                val responseBody = sendChunkWithRetry(
                    uploadUrl = uploadUrl,
                    chunkBuffer = buffer,
                    chunkLength = bytesRead,
                    startOffset = offset,
                    isFinalChunk = isFinalChunk,
                    mimeType = mimeType
                )

                offset += bytesRead
                val pct = ((offset * 100.0) / totalSizeBytes.coerceAtLeast(offset)).toInt().coerceIn(1, 100)
                onProgress("ভিডিও আপলোড হচ্ছে... $pct%")

                if (isFinalChunk) {
                    return parseUploadedFileMeta(responseBody)
                }
            }
        }

        throw AnalyzerHttpException("ভিডিও আপলোড সম্পূর্ণ হয়নি। অনুগ্রহ করে আবার চেষ্টা করুন।")
    }

    private suspend fun sendChunkWithRetry(
        uploadUrl: String,
        chunkBuffer: ByteArray,
        chunkLength: Int,
        startOffset: Long,
        isFinalChunk: Boolean,
        mimeType: String
    ): String {
        var currentOffset = startOffset
        var localOffset = 0
        val chunkEndOffset = startOffset + chunkLength
        var lastException: Exception? = null

        for (attempt in 0 until 3) {
            val remaining = chunkLength - localOffset
            if (remaining <= 0 && !isFinalChunk) {
                return ""
            }
            val command = if (isFinalChunk) "upload, finalize" else "upload"
            val body = chunkBuffer.toRequestBody(
                contentType = mimeType.toMediaType(),
                offset = localOffset,
                byteCount = remaining.coerceAtLeast(0)
            )
            val request = Request.Builder()
                .url(uploadUrl)
                .post(body)
                .addHeader("X-Goog-Upload-Offset", currentOffset.toString())
                .addHeader("X-Goog-Upload-Command", command)
                .build()

            try {
                uploadClient.newCall(request).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        return raw
                    }
                    if (resp.code == 408 || resp.code == 429 || resp.code in 500..599) {
                        lastException = AnalyzerHttpException(mapHttpErrorToBn(resp.code, raw))
                    } else {
                        throw AnalyzerHttpException(mapHttpErrorToBn(resp.code, raw))
                    }
                }
            } catch (e: UnknownHostException) {
                throw e
            } catch (e: AnalyzerHttpException) {
                lastException = e
                if (attempt == 2) throw e
            } catch (e: IOException) {
                lastException = e
            }

            if (attempt < 2) {
                delay(700L * (attempt + 1))
                val serverOffset = queryUploadedOffset(uploadUrl)
                if (serverOffset != null && serverOffset > currentOffset && serverOffset <= chunkEndOffset) {
                    if (serverOffset == chunkEndOffset && !isFinalChunk) {
                        return ""
                    }
                    localOffset = (serverOffset - startOffset).toInt().coerceIn(0, chunkLength)
                    currentOffset = serverOffset
                }
            }
        }

        throw (lastException ?: SocketTimeoutException("Chunk upload timed out at offset $currentOffset"))
    }

    private fun queryUploadedOffset(uploadUrl: String): Long? {
        return try {
            val req = Request.Builder()
                .url(uploadUrl)
                .post(ByteArray(0).toRequestBody(null))
                .addHeader("X-Goog-Upload-Command", "query")
                .build()
            uploadClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val status = resp.headers.names()
                    .firstOrNull { it.equals("X-Goog-Upload-Status", ignoreCase = true) }
                    ?.let { resp.header(it) }
                if (status.equals("final", ignoreCase = true)) {
                    return null
                }
                val sizeStr = resp.headers.names()
                    .firstOrNull { it.equals("X-Goog-Upload-Size-Received", ignoreCase = true) }
                    ?.let { resp.header(it) }
                sizeStr?.toLongOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseUploadedFileMeta(raw: String): UploadedFileMeta {
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

    private fun readUpToChunkSize(stream: BufferedInputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = stream.read(buffer, total, buffer.size - total)
            if (read == -1) break
            total += read
        }
        return total
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

        repeat(60) { attempt ->
            delay(if (attempt == 0) 900L else if (attempt < 10) 1500L else 2000L)
            val req = Request.Builder()
                .url(pollUrl)
                .get()
                .addHeader("x-goog-api-key", apiKey)
                .build()
            uploadClient.newCall(req).execute().use { resp ->
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
            uploadClient.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    private class AnalyzerHttpException(val messageBn: String) : Exception(messageBn)

    companion object {
        const val UPLOAD_ENDPOINT =
            "https://generativelanguage.googleapis.com/upload/v1beta/files"
        const val FILES_STATUS_BASE =
            "https://generativelanguage.googleapis.com/v1beta/files"

        // Must be a multiple of 256 KiB for Google Resumable Upload intermediate chunks.
        const val UPLOAD_CHUNK_BYTES = 4 * 1024 * 1024

        private const val MAX_VIDEO_BYTES = 500L * 1024L * 1024L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val moshi: Moshi = Moshi.Builder().build()
        private val mapAdapter = moshi.adapter<Map<String, Any?>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )

        fun isUnsupportedFastConfigError(rawBody: String): Boolean {
            val lower = rawBody.lowercase()
            return lower.contains("thinking") ||
                lower.contains("mediaresolution") ||
                lower.contains("media_resolution") ||
                lower.contains("unknown name")
        }

        fun buildGenerateContentRequestJson(
            fileUri: String,
            mimeType: String,
            language: ShortsOutputLanguage,
            creatorNote: String,
            thinkingLevel: String? = "low",
            includeFastMediaResolution: Boolean = true
        ): String {
            val noteSection = if (creatorNote.isNotBlank()) {
                "\nCreator's extra context/instruction: ${creatorNote.trim()}"
            } else {
                ""
            }

            val promptText = """
                Analyze BOTH the video's visual frames (scenes, actions, expressions, on-screen text, pacing, cuts) AND the complete audio track (spoken dialogue, voiceover, sound effects, tone, music). Keep every text field concise and high-impact.
                IMPORTANT: Do NOT assume any fixed video category. Automatically detect what kind of video this actually is (for example: 2D/3D cartoon animation, dialogue-free slapstick comedy, vlog, tech tutorial, Islamic reminder, gaming, cooking, etc.) and tailor everything to that exact niche and audience.
                You are acting like an elite vidIQ-grade Shorts/Reels/TikTok Video Doctor & Growth Coach generating:
                (A) Universal Video Doctor Diagnostics in simple Bengali, AND
                (B) A 3-platform creator pack (YouTube Shorts, Facebook Reels, TikTok).
                Language preference for titles/descriptions: ${language.promptInstruction}

                Requirements:
                1. visualSummaryBn: 1 short sentence in simple Bengali describing what happens visually.
                2. audioSummaryBn: 1 short sentence in simple Bengali describing what is spoken or heard (note clearly if dialogue-free cartoon SFX/music only).
                3. diagnostics: Video Doctor audit object:
                   - detectedCategoryBn: Short Bengali label of the video's detected niche (e.g. "২ডি কার্টুন কমেডি", "টেক রিভিউ", "ইসলামিক আলোচনা").
                   - targetAudienceScopeBn: Who will watch this most & whether it works locally or globally (e.g. "সারা বিশ্বের কিডস ও ফ্যামিলি দর্শক" or "বাংলাদেশি তরুণ দর্শক").
                   - viralScore: Honest integer 0..100 overall viral potential score.
                   - hookScore: Integer 0..100 rating the first 3 seconds' scroll-stopping power.
                   - pacingScore: Integer 0..100 rating visual rhythm, sound energy, and watch-through retention.
                   - strengthsBn: 2 concise strengths of this video in Bengali.
                   - weaknessesBn: 2 honest drop-off risks or weak spots (with timestamps like "০:০০-০:০২ সেকেন্ডে..." where helpful) in Bengali.
                   - rescueFixesBn: 3 concrete, practical fixes to maximize views EVEN if the raw video is ordinary or weak (e.g. exact trim/cut suggestion, on-screen text overlay, sound/loop trick, curiosity title angle) in Bengali.
                   - thumbnailTextBn: Ultra-punchy 2-4 word text to place on the video cover/thumbnail.
                   - pinnedCommentBn: 1 engaging question or comment to pin right after uploading to boost comments/algorithm reach.
                   - bestUploadWindowBn: Best target time window to publish this niche (e.g. "বিকেল ৫টা – রাত ৯টা").
                4. youtube: Object for YouTube Shorts:
                   - titles: 3 catchy, high-CTR SEO YouTube Shorts titles (under 85 chars each)
                   - recommendedTitle: Best single YouTube Shorts title (under 85 chars)
                   - description: Engaging Shorts description with hook, CTA, and 6-8 hashtags ALWAYS including #shorts #youtubeshorts
                   - hashtags: List of those 6-8 hashtags (starting with #)
                5. facebook: Object for Facebook Reels:
                   - titles: 3 conversational, share-worthy Facebook Reels hook titles
                   - recommendedTitle: Best Facebook Reels hook title
                   - description: Warm storytelling Facebook Reels caption with engagement question and 6-8 hashtags ALWAYS including #reels #facebookreels
                   - hashtags: List of 6-8 Facebook Reels hashtags (starting with #)
                6. tiktok: Object for TikTok:
                   - titles: 3 short, punchy, curiosity-driven TikTok hook lines
                   - recommendedTitle: Best TikTok hook title
                   - description: Trendy, snappy TikTok caption with 6-8 hashtags ALWAYS including #fyp #tiktok
                   - hashtags: List of 6-8 TikTok hashtags (starting with #)$noteSection
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

            val diagnosticsSchema = mapOf(
                "type" to "OBJECT",
                "properties" to mapOf(
                    "detectedCategoryBn" to mapOf("type" to "STRING"),
                    "targetAudienceScopeBn" to mapOf("type" to "STRING"),
                    "viralScore" to mapOf("type" to "INTEGER"),
                    "hookScore" to mapOf("type" to "INTEGER"),
                    "pacingScore" to mapOf("type" to "INTEGER"),
                    "strengthsBn" to mapOf("type" to "ARRAY", "items" to mapOf("type" to "STRING")),
                    "weaknessesBn" to mapOf("type" to "ARRAY", "items" to mapOf("type" to "STRING")),
                    "rescueFixesBn" to mapOf("type" to "ARRAY", "items" to mapOf("type" to "STRING")),
                    "thumbnailTextBn" to mapOf("type" to "STRING"),
                    "pinnedCommentBn" to mapOf("type" to "STRING"),
                    "bestUploadWindowBn" to mapOf("type" to "STRING")
                ),
                "required" to listOf(
                    "detectedCategoryBn",
                    "targetAudienceScopeBn",
                    "viralScore",
                    "hookScore",
                    "pacingScore",
                    "strengthsBn",
                    "weaknessesBn",
                    "rescueFixesBn",
                    "thumbnailTextBn",
                    "pinnedCommentBn",
                    "bestUploadWindowBn"
                )
            )

            val schema = mapOf(
                "type" to "OBJECT",
                "properties" to mapOf(
                    "visualSummaryBn" to mapOf("type" to "STRING"),
                    "audioSummaryBn" to mapOf("type" to "STRING"),
                    "diagnostics" to diagnosticsSchema,
                    "youtube" to platformSchema(),
                    "facebook" to platformSchema(),
                    "tiktok" to platformSchema()
                ),
                "required" to listOf(
                    "visualSummaryBn",
                    "audioSummaryBn",
                    "diagnostics",
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

            val generationConfig = mutableMapOf<String, Any>(
                "temperature" to 0.5,
                "maxOutputTokens" to 2500,
                "responseMimeType" to "application/json",
                "responseSchema" to schema
            )
            if (includeFastMediaResolution) {
                generationConfig["mediaResolution"] = "MEDIA_RESOLUTION_LOW"
            }
            if (!thinkingLevel.isNullOrBlank()) {
                generationConfig["thinkingConfig"] = mapOf("thinkingLevel" to thinkingLevel)
            }

            val root = mapOf(
                "contents" to listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to parts
                    )
                ),
                "generationConfig" to generationConfig
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

                val diagnostics = parseDiagnostics(payload["diagnostics"] as? Map<*, *>)

                ShortsAnalysisResult(
                    platformPacks = packs,
                    activePlatform = ShortsPlatform.YOUTUBE_SHORTS,
                    visualSummaryBn = (payload["visualSummaryBn"] as? String)?.trim().orEmpty(),
                    audioSummaryBn = (payload["audioSummaryBn"] as? String)?.trim().orEmpty(),
                    language = language,
                    videoDisplayName = videoDisplayName,
                    videoUriString = videoUriString,
                    analyzedAtMillis = System.currentTimeMillis(),
                    diagnostics = diagnostics
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun parseDiagnostics(block: Map<*, *>?): VideoDoctorDiagnostics? {
            if (block == null) return null
            fun stringList(key: String): List<String> = (block[key] as? List<*>)
                .orEmpty()
                .mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }

            fun boundedScore(key: String): Int {
                val raw = (block[key] as? Number)?.toInt() ?: 0
                return raw.coerceIn(0, 100)
            }

            val category = (block["detectedCategoryBn"] as? String)?.trim().orEmpty()
            val viral = boundedScore("viralScore")
            val hook = boundedScore("hookScore")
            val pacing = boundedScore("pacingScore")
            val strengths = stringList("strengthsBn")
            val weaknesses = stringList("weaknessesBn")
            val fixes = stringList("rescueFixesBn")
            if (category.isBlank() && viral == 0 && fixes.isEmpty()) return null

            return VideoDoctorDiagnostics(
                detectedCategoryBn = category.ifBlank { "শর্ট ভিডিও" },
                targetAudienceScopeBn = (block["targetAudienceScopeBn"] as? String)?.trim().orEmpty(),
                viralScore = viral,
                hookScore = hook,
                pacingScore = pacing,
                strengthsBn = strengths,
                weaknessesBn = weaknesses,
                rescueFixesBn = fixes,
                thumbnailTextBn = (block["thumbnailTextBn"] as? String)?.trim().orEmpty(),
                pinnedCommentBn = (block["pinnedCommentBn"] as? String)?.trim().orEmpty(),
                bestUploadWindowBn = (block["bestUploadWindowBn"] as? String)?.trim().orEmpty()
            )
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
