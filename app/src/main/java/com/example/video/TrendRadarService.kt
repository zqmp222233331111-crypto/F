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
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed class TrendRadarOutcome {
    data class Success(val result: TrendRadarResult) : TrendRadarOutcome()
    data class Failure(val messageBn: String) : TrendRadarOutcome()
}

/**
 * Queries real-time Google Search grounded creator trends across YouTube Shorts,
 * Facebook Reels, and TikTok for ANY niche using GeminiModelResolver.
 */
class TrendRadarService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()
) {

    suspend fun fetchTrends(
        apiKey: String,
        nicheQuery: String,
        region: TrendRegion,
        platformFilter: TrendPlatformFilter
    ): TrendRadarOutcome = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext TrendRadarOutcome.Failure(
                "API কী পাওয়া যায়নি। সেটিংস থেকে আপনার Gemini API কী যাচাই করুন।"
            )
        }

        val effectiveNiche = nicheQuery.trim().ifBlank { "শর্ট ভিডিও ভাইরাল কনটেন্ট" }
        val payload = buildTrendRequestJson(effectiveNiche, region, platformFilter)

        fun tryModel(modelId: String): Pair<Int, String> {
            val req = Request.Builder()
                .url(GeminiModelResolver.endpointFor(modelId))
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("x-goog-api-key", trimmedKey)
                .build()
            return httpClient.newCall(req).execute().use { resp ->
                resp.code to resp.body?.string().orEmpty()
            }
        }

        return@withContext try {
            val tried = linkedSetOf<String>()
            var lastCode = 0
            var lastRaw = ""

            for (modelId in GeminiModelResolver.candidateModels()) {
                tried.add(modelId)
                val (code, raw) = tryModel(modelId)
                lastCode = code
                lastRaw = raw
                if (code in 200..299) {
                    GeminiModelResolver.markWorking(modelId)
                    val parsed = parseTrendResponse(raw, effectiveNiche, region, platformFilter)
                    return@withContext if (parsed != null) {
                        TrendRadarOutcome.Success(parsed)
                    } else {
                        TrendRadarOutcome.Failure("ট্রেন্ডের তথ্য সঠিকভাবে পড়া যায়নি। অনুগ্রহ করে আবার চেষ্টা করুন।")
                    }
                }
                if (code != 404) {
                    return@withContext TrendRadarOutcome.Failure(ShortsVideoAnalyzer.mapHttpErrorToBn(code, raw))
                }
            }

            val discovered = GeminiModelResolver.discoverActiveFlashModel(httpClient, trimmedKey)
            if (!discovered.isNullOrBlank() && discovered !in tried) {
                val (code, raw) = tryModel(discovered)
                lastCode = code
                lastRaw = raw
                if (code in 200..299) {
                    GeminiModelResolver.markWorking(discovered)
                    val parsed = parseTrendResponse(raw, effectiveNiche, region, platformFilter)
                    return@withContext if (parsed != null) {
                        TrendRadarOutcome.Success(parsed)
                    } else {
                        TrendRadarOutcome.Failure("ট্রেন্ডের তথ্য সঠিকভাবে পড়া যায়নি। অনুগ্রহ করে আবার চেষ্টা করুন।")
                    }
                }
            }

            TrendRadarOutcome.Failure(ShortsVideoAnalyzer.mapHttpErrorToBn(lastCode, lastRaw))
        } catch (_: UnknownHostException) {
            TrendRadarOutcome.Failure("ইন্টারনেট সংযোগ নেই। ইন্টারনেট চালু করে আবার চেষ্টা করুন।")
        } catch (_: SocketTimeoutException) {
            TrendRadarOutcome.Failure("ট্রেন্ড খুঁজতে বেশি সময় লাগছে। আবার চেষ্টা করুন।")
        } catch (e: Exception) {
            TrendRadarOutcome.Failure(e.message?.takeIf { it.isNotBlank() } ?: "ট্রেন্ড আনতে সমস্যা হয়েছে।")
        }
    }

    suspend fun fetchLiveTrendSummaryForCall(
        apiKey: String,
        niche: String,
        platformLabel: String
    ): Map<String, Any?> {
        val filter = when {
            platformLabel.contains("tiktok", ignoreCase = true) -> TrendPlatformFilter.TIKTOK
            platformLabel.contains("facebook", ignoreCase = true) || platformLabel.contains("fb", ignoreCase = true) ->
                TrendPlatformFilter.FACEBOOK_REELS
            platformLabel.contains("youtube", ignoreCase = true) -> TrendPlatformFilter.YOUTUBE_SHORTS
            else -> TrendPlatformFilter.ALL
        }
        val region = if (niche.contains("global", ignoreCase = true) || niche.contains("english", ignoreCase = true)) {
            TrendRegion.GLOBAL
        } else {
            TrendRegion.BANGLADESH
        }
        return when (val outcome = fetchTrends(apiKey, niche, region, filter)) {
            is TrendRadarOutcome.Success -> mapOf(
                "success" to true,
                "niche" to outcome.result.nicheQuery,
                "trend_summary" to outcome.result.trendSummaryBn,
                "best_upload_timing" to outcome.result.bestTimingBn,
                "ideas" to outcome.result.ideas.map { idea ->
                    mapOf(
                        "topic" to idea.topicTitleBn,
                        "platform" to idea.platformFit,
                        "hook_first_3_seconds" to idea.hookIdeaBn,
                        "why_trending" to idea.whyTrendingBn,
                        "competition" to idea.competitionLevelBn,
                        "tags" to idea.viralTags.joinToString(" ")
                    )
                },
                "sources" to outcome.result.sources
            )
            is TrendRadarOutcome.Failure -> mapOf(
                "success" to false,
                "error" to outcome.messageBn
            )
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val moshi: Moshi = Moshi.Builder().build()
        private val mapAdapter = moshi.adapter<Map<String, Any?>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )

        fun buildTrendRequestJson(
            nicheQuery: String,
            region: TrendRegion,
            platformFilter: TrendPlatformFilter
        ): String {
            val platformText = when (platformFilter) {
                TrendPlatformFilter.ALL -> "YouTube Shorts, Facebook Reels, and TikTok"
                else -> platformFilter.labelBn
            }
            val prompt = """
                Use live Google Search right now to find the latest viral video trends, hooks, formats, and search topics for:
                - Creator Niche / Topic: "$nicheQuery"
                - Target Audience Region: ${region.promptHint}
                - Target Platform(s): $platformText

                Respond ONLY with a single raw JSON object (no markdown fences, no extra prose) using this exact structure:
                {
                  "trendSummaryBn": "2-3 concise Bengali sentences summarizing what format/topic/sound is trending right now in this niche",
                  "bestTimingBn": "Best upload time window & posting frequency advice in Bengali",
                  "ideas": [
                    {
                      "topicTitleBn": "Actionable video topic idea in Bengali",
                      "platformFit": "YouTube Shorts" or "Facebook Reels" or "TikTok" or "সব প্ল্যাটফর্ম",
                      "hookIdeaBn": "Exact first 3 seconds visual or spoken hook to stop viewers from scrolling",
                      "whyTrendingBn": "1 short Bengali sentence explaining why this angle gets high views right now",
                      "competitionLevelBn": "কম" or "মাঝারি" or "বেশি",
                      "viralTags": ["#tag1", "#tag2", "#tag3", "#tag4", "#tag5"]
                    }
                  ]
                }
                Include 4 diverse, practical, up-to-date ideas in the "ideas" array. All explanations must be natural Bengali.
            """.trimIndent()

            val root = mapOf(
                "contents" to listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(mapOf("text" to prompt))
                    )
                ),
                "tools" to listOf(mapOf("googleSearch" to emptyMap<String, Any>())),
                "generationConfig" to mapOf(
                    "temperature" to 0.4
                )
            )
            return mapAdapter.toJson(root)
        }

        fun parseTrendResponse(
            rawResponse: String,
            nicheQuery: String,
            region: TrendRegion,
            platformFilter: TrendPlatformFilter
        ): TrendRadarResult? {
            return try {
                val root = mapAdapter.fromJson(rawResponse) ?: return null
                val candidates = root["candidates"] as? List<*> ?: return null
                val first = candidates.firstOrNull() as? Map<*, *> ?: return null
                val content = first["content"] as? Map<*, *> ?: return null
                val parts = content["parts"] as? List<*> ?: return null

                val rawText = parts
                    .mapNotNull { (it as? Map<*, *>)?.get("text") as? String }
                    .joinToString("\n")
                    .trim()
                if (rawText.isBlank()) return null

                val groundingMeta = first["groundingMetadata"] as? Map<*, *>
                val chunks = groundingMeta?.get("groundingChunks") as? List<*>
                val sources = chunks.orEmpty()
                    .mapNotNull { chunk ->
                        val web = (chunk as? Map<*, *>)?.get("web") as? Map<*, *>
                        val title = (web?.get("title") as? String)?.trim()
                        val uri = (web?.get("uri") as? String)?.trim()
                        if (!title.isNullOrBlank()) title else uri
                    }
                    .distinct()
                    .take(4)

                val extractedJson = extractJsonObject(rawText)
                val payload = extractedJson?.let { runCatching { mapAdapter.fromJson(it) }.getOrNull() }

                if (payload == null) {
                    // Graceful fallback if grounded search returned plain text
                    return TrendRadarResult(
                        nicheQuery = nicheQuery,
                        region = region,
                        platformFilter = platformFilter,
                        trendSummaryBn = rawText.take(650),
                        bestTimingBn = "বিকেল ৫টা – রাত ৯টা (দর্শকের সক্রিয় সময়)",
                        ideas = emptyList(),
                        sources = sources,
                        fetchedAtMillis = System.currentTimeMillis()
                    )
                }

                val summary = (payload["trendSummaryBn"] as? String)?.trim().orEmpty()
                val timing = (payload["bestTimingBn"] as? String)?.trim().orEmpty()
                val rawIdeas = (payload["ideas"] as? List<*>).orEmpty()
                val ideas = rawIdeas.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    val topic = (map["topicTitleBn"] as? String)?.trim().orEmpty()
                    val hook = (map["hookIdeaBn"] as? String)?.trim().orEmpty()
                    if (topic.isBlank() && hook.isBlank()) return@mapNotNull null
                    val tags = (map["viralTags"] as? List<*>)
                        .orEmpty()
                        .mapNotNull { (it as? String)?.trim()?.takeIf { t -> t.isNotEmpty() } }
                        .map { if (it.startsWith("#")) it else "#$it" }
                        .distinct()
                    TrendIdeaItem(
                        topicTitleBn = topic,
                        platformFit = (map["platformFit"] as? String)?.trim().orEmpty().ifBlank { platformFilter.labelBn },
                        hookIdeaBn = hook,
                        whyTrendingBn = (map["whyTrendingBn"] as? String)?.trim().orEmpty(),
                        competitionLevelBn = (map["competitionLevelBn"] as? String)?.trim().orEmpty().ifBlank { "মাঝারি" },
                        viralTags = tags
                    )
                }

                if (summary.isBlank() && ideas.isEmpty()) return null

                TrendRadarResult(
                    nicheQuery = nicheQuery,
                    region = region,
                    platformFilter = platformFilter,
                    trendSummaryBn = summary.ifBlank { "সাম্প্রতিক ট্রেন্ডিং আইডিয়াগুলো নিচে দেওয়া হলো।" },
                    bestTimingBn = timing.ifBlank { "বিকেল ৫টা – রাত ৯টা" },
                    ideas = ideas,
                    sources = sources,
                    fetchedAtMillis = System.currentTimeMillis()
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun extractJsonObject(text: String): String? {
            var cleaned = text.trim()
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.removePrefix("```json").removePrefix("```").trim()
                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.removeSuffix("```").trim()
                }
            }
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else null
        }
    }
}
