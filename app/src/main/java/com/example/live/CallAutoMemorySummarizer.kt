package com.example.live

import com.example.data.GeminiModelResolver
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ExtractedMemoryFact(
    val key: String,
    val value: String
)

/**
 * Summarizes meaningful voice/video call transcripts into concise, durable local
 * memory facts after a call finishes, so Ayesha naturally remembers context next time.
 */
class CallAutoMemorySummarizer(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    suspend fun extractMemories(
        apiKey: String,
        transcript: String,
        existingKeys: List<String>
    ): List<ExtractedMemoryFact> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedTranscript = transcript.trim().takeLast(6000)
        if (trimmedKey.isEmpty() || trimmedTranscript.length < 25) {
            return@withContext emptyList()
        }

        val requestJson = buildSummarizerRequestJson(trimmedTranscript, existingKeys)

        fun tryModel(modelId: String): Pair<Int, String> {
            val request = Request.Builder()
                .url(GeminiModelResolver.endpointFor(modelId))
                .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("x-goog-api-key", trimmedKey)
                .build()
            return httpClient.newCall(request).execute().use { resp ->
                resp.code to resp.body?.string().orEmpty()
            }
        }

        return@withContext try {
            val tried = linkedSetOf<String>()
            for (modelId in GeminiModelResolver.candidateModels()) {
                tried.add(modelId)
                val (code, raw) = tryModel(modelId)
                if (code in 200..299) {
                    GeminiModelResolver.markWorking(modelId)
                    return@withContext parseSummarizerResponse(raw)
                }
                if (code != 404) return@withContext emptyList()
            }
            val discovered = GeminiModelResolver.discoverActiveFlashModel(httpClient, trimmedKey)
            if (!discovered.isNullOrBlank() && discovered !in tried) {
                val (code, raw) = tryModel(discovered)
                if (code in 200..299) {
                    GeminiModelResolver.markWorking(discovered)
                    return@withContext parseSummarizerResponse(raw)
                }
            }
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val moshi: Moshi = Moshi.Builder().build()
        private val mapAdapter = moshi.adapter<Map<String, Any?>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )

        fun buildSummarizerRequestJson(
            transcript: String,
            existingKeys: List<String>
        ): String {
            val prompt = """
                You are extracting durable, helpful personal context from a finished voice call so the assistant remembers it on future calls.
                Transcript:
                $transcript

                Existing memory keys (reuse exact key if updating): ${existingKeys.joinToString(", ").ifBlank { "none" }}

                Rules:
                - NEVER store passwords, OTPs, PINs, bank details, or card numbers.
                - Return `lastCallSummary`: 1 short Bengali sentence summarizing the main topic, project, or mood of this call so the assistant can naturally follow up next time (empty string if trivial greeting only).
                - Return `facts`: 0 to 3 durable user facts/preferences/projects (e.g. key="current_project", value="ইউটিউব শর্টস চ্যানেল নিয়ে কাজ করছেন").
            """.trimIndent()

            val schema = mapOf(
                "type" to "OBJECT",
                "properties" to mapOf(
                    "lastCallSummary" to mapOf("type" to "STRING"),
                    "facts" to mapOf(
                        "type" to "ARRAY",
                        "items" to mapOf(
                            "type" to "OBJECT",
                            "properties" to mapOf(
                                "key" to mapOf("type" to "STRING"),
                                "value" to mapOf("type" to "STRING")
                            ),
                            "required" to listOf("key", "value")
                        )
                    )
                ),
                "required" to listOf("lastCallSummary", "facts")
            )

            val root = mapOf(
                "contents" to listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(mapOf("text" to prompt))
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.2,
                    "responseMimeType" to "application/json",
                    "responseSchema" to schema
                )
            )
            return mapAdapter.toJson(root)
        }

        fun parseSummarizerResponse(rawResponse: String): List<ExtractedMemoryFact> {
            return try {
                val root = mapAdapter.fromJson(rawResponse) ?: return emptyList()
                val candidates = root["candidates"] as? List<*> ?: return emptyList()
                val first = candidates.firstOrNull() as? Map<*, *> ?: return emptyList()
                val content = first["content"] as? Map<*, *> ?: return emptyList()
                val parts = content["parts"] as? List<*> ?: return emptyList()
                val rawText = parts
                    .mapNotNull { (it as? Map<*, *>)?.get("text") as? String }
                    .joinToString("")
                    .trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val payload = mapAdapter.fromJson(rawText) ?: return emptyList()
                val result = mutableListOf<ExtractedMemoryFact>()
                val summary = (payload["lastCallSummary"] as? String)?.trim().orEmpty()
                if (summary.isNotBlank()) {
                    result.add(ExtractedMemoryFact("last_call_summary", summary))
                }

                val factsList = payload["facts"] as? List<*>
                factsList.orEmpty().take(3).forEach { item ->
                    val obj = item as? Map<*, *> ?: return@forEach
                    val k = (obj["key"] as? String)?.trim().orEmpty()
                    val v = (obj["value"] as? String)?.trim().orEmpty()
                    if (k.isNotBlank() && v.isNotBlank()) {
                        result.add(ExtractedMemoryFact(k, v))
                    }
                }
                result
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
