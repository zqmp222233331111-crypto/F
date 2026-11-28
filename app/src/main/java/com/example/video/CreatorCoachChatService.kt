package com.example.video

import com.example.data.GeminiModelResolver
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed class CreatorChatOutcome {
    data class Success(val replyBn: String) : CreatorChatOutcome()
    data class Failure(val messageBn: String) : CreatorChatOutcome()
}

/**
 * Interactive text chat coach for YouTube Shorts, Facebook Reels, and TikTok.
 * Keeps context of the latest analyzed video and formats any generated scripts,
 * descriptions, and hashtags cleanly with paragraph spacing.
 */
class CreatorCoachChatService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    suspend fun sendMessage(
        apiKey: String,
        userMessage: String,
        recentMessages: List<CreatorChatMessage>,
        latestDraft: ShortsAnalysisResult?,
        outputLanguage: ShortsOutputLanguage
    ): CreatorChatOutcome = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedMsg = userMessage.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext CreatorChatOutcome.Failure(
                "Gemini API কী পাওয়া যায়নি। সেটিংস থেকে আপনার API কী যাচাই করুন।"
            )
        }
        if (trimmedMsg.isEmpty()) {
            return@withContext CreatorChatOutcome.Failure("অনুগ্রহ করে কিছু লিখুন।")
        }

        val requestJson = buildChatRequestJson(
            userMessage = trimmedMsg,
            recentMessages = recentMessages,
            latestDraft = latestDraft,
            outputLanguage = outputLanguage
        )

        var lastFailure: CreatorChatOutcome.Failure? = null
        val candidates = GeminiModelResolver.fastVideoCandidateModels()
        for (model in candidates) {
            val req = Request.Builder()
                .url(GeminiModelResolver.endpointFor(model))
                .header("x-goog-api-key", trimmedKey)
                .post(requestJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            try {
                httpClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val text = parseChatReply(raw)
                        if (!text.isNullOrBlank()) {
                            return@withContext CreatorChatOutcome.Success(text)
                        }
                        lastFailure = CreatorChatOutcome.Failure(
                            "কোচের উত্তরটি পড়া যায়নি। আবার চেষ্টা করুন।"
                        )
                    } else {
                        val mapped = ShortsVideoAnalyzer.mapHttpErrorToBn(resp.code, raw)
                        lastFailure = CreatorChatOutcome.Failure(mapped)
                        if (resp.code == 401 || resp.code == 403) {
                            return@withContext lastFailure!!
                        }
                    }
                }
            } catch (_: UnknownHostException) {
                return@withContext CreatorChatOutcome.Failure(
                    "ইন্টারনেট সংযোগ নেই। Wi-Fi বা মোবাইল ডেটা চালু করে আবার চেষ্টা করুন।"
                )
            } catch (_: SocketTimeoutException) {
                lastFailure = CreatorChatOutcome.Failure(
                    "সার্ভার থেকে উত্তর আসতে দেরি হচ্ছে। আবার চেষ্টা করুন।"
                )
            } catch (e: IOException) {
                lastFailure = CreatorChatOutcome.Failure(
                    e.message?.takeIf { it.isNotBlank() } ?: "নেটওয়ার্ক সমস্যা হয়েছে।"
                )
            }
        }
        lastFailure ?: CreatorChatOutcome.Failure("উত্তর তৈরি করা যায়নি। একটু পরে আবার চেষ্টা করুন।")
    }

    companion object {
        private val moshi = Moshi.Builder().build()
        private val mapType = Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )
        private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

        fun buildChatRequestJson(
            userMessage: String,
            recentMessages: List<CreatorChatMessage>,
            latestDraft: ShortsAnalysisResult?,
            outputLanguage: ShortsOutputLanguage
        ): String {
            val draftContext = if (latestDraft != null) {
                val diag = latestDraft.diagnostics
                buildString {
                    appendLine("Active analyzed video context:")
                    appendLine("- File: ${latestDraft.videoDisplayName}")
                    appendLine("- Visual summary: ${latestDraft.visualSummaryBn}")
                    appendLine("- Audio summary: ${latestDraft.audioSummaryBn}")
                    if (diag != null) {
                        appendLine("- Category: ${diag.detectedCategoryBn}, Viral Score: ${diag.viralScore}/100, Hook: ${diag.hookScore}/100, Pacing: ${diag.pacingScore}/100")
                        appendLine("- Strengths: ${diag.strengthsBn.joinToString("; ")}")
                        appendLine("- Weaknesses: ${diag.weaknessesBn.joinToString("; ")}")
                        appendLine("- Fixes: ${diag.rescueFixesBn.joinToString("; ")}")
                    }
                    latestDraft.platformPacks[ShortsPlatform.YOUTUBE_SHORTS]?.let { yt ->
                        appendLine("- YouTube Title: ${yt.selectedTitle}")
                    }
                }
            } else {
                "No video analyzed yet in this session."
            }

            val historyBlock = recentMessages
                .takeLast(8)
                .filter { it.textBn.isNotBlank() || it.attachedVideoName != null }
                .joinToString("\n") { msg ->
                    val role = if (msg.isUser) "Creator" else "Ayesha Coach"
                    val videoPart = msg.attachedVideoName?.let { "[Sent video: $it] " }.orEmpty()
                    "$role: $videoPart${msg.textBn}".trim()
                }

            val systemText = """
                You are Ayesha (আয়শা), a sharp, friendly Bangladeshi Shorts/Reels/TikTok Creator Coach inside a chat interface.
                Speak primarily in natural, clear Bengali unless the user requests English/Banglish. Output language preference for titles/scripts/captions: ${outputLanguage.promptInstruction}
                Rules:
                - Give practical, high-retention advice on 3-second hooks, scripts, editing pace, YouTube Shorts vs Facebook Reels vs TikTok captions, thumbnails, and algorithm growth.
                - Note: YouTube has Title + Description + Hashtags, whereas TikTok and Facebook Reels only have a single Caption + Hashtags (never call TikTok/Facebook captions "Description").
                - Formatting: Whenever writing descriptions, captions, or scripts, ALWAYS use clean paragraph breaks (blank lines between sections) and place hashtags on a separate bottom line separated cleanly by spaces (e.g. #shorts #bangla #viral), NEVER glued to prose.
                - Keep replies focused, readable, and encouraging without markdown clutter (** or ###).
            """.trimIndent()

            val fullPrompt = buildString {
                appendLine(systemText)
                appendLine()
                appendLine(draftContext)
                if (historyBlock.isNotBlank()) {
                    appendLine()
                    appendLine("Recent conversation:")
                    appendLine(historyBlock)
                }
                appendLine()
                appendLine("Creator's latest message: $userMessage")
            }

            val root = mapOf(
                "contents" to listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(mapOf("text" to fullPrompt))
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.7,
                    "maxOutputTokens" to 1200
                )
            )
            return mapAdapter.toJson(root)
        }

        fun parseChatReply(rawResponse: String): String? {
            return try {
                val root = mapAdapter.fromJson(rawResponse) ?: return null
                val candidates = root["candidates"] as? List<*> ?: return null
                val first = candidates.firstOrNull() as? Map<*, *> ?: return null
                val content = first["content"] as? Map<*, *> ?: return null
                val parts = content["parts"] as? List<*> ?: return null
                val text = parts.filter { (it as? Map<*, *>)?.get("thought") != true }
                    .ifEmpty { parts }
                    .mapNotNull { (it as? Map<*, *>)?.get("text") as? String }
                    .joinToString("")
                    .trim()
                text.takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
        }
    }
}
