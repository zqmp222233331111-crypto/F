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
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Executes live Google Search grounded lookups via Gemini Flash REST API
 * when the live voice agent calls the `web_search` function tool.
 * Keeps `BidiGenerateContent` WebSocket connections free from built-in setup quota failures.
 */
class LiveWebSearchService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    suspend fun search(apiKey: String, query: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val trimmedKey = apiKey.trim()
        if (cleanQuery.isEmpty() || trimmedKey.isEmpty()) {
            return@withContext mapOf(
                "success" to false,
                "error" to "অনুসন্ধানের বিষয় বা API কী খালি।"
            )
        }

        val groundedResult = executeGeminiSearch(trimmedKey, cleanQuery, withGoogleSearch = true)
        if (groundedResult != null) {
            return@withContext groundedResult
        }

        mapOf(
            "success" to false,
            "error" to "লাইভ সার্চ থেকে এই মুহূর্তে তথ্য আনা যায়নি। অনুমান না করে ব্যবহারকারীকে সত্য কথা জানান।"
        )
    }

    private fun executeGeminiSearch(
        apiKey: String,
        query: String,
        withGoogleSearch: Boolean
    ): Map<String, Any?>? {
        val bodyMap = mutableMapOf<String, Any?>(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf(
                            "text" to "Search the web and give a concise, accurate, up-to-date factual answer (3-5 sentences max) for: $query"
                        )
                    )
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.2
            )
        )
        if (withGoogleSearch) {
            bodyMap["tools"] = listOf(mapOf("googleSearch" to emptyMap<String, Any>()))
        }
        val payload = mapAdapter.toJson(bodyMap)

        fun tryModel(modelId: String): Pair<Int, Map<String, Any?>?> {
            val request = Request.Builder()
                .url(GeminiModelResolver.endpointFor(modelId))
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("x-goog-api-key", apiKey)
                .build()
            return httpClient.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful) resp.code to parseSearchResponse(raw)
                else resp.code to null
            }
        }

        return try {
            val tried = linkedSetOf<String>()
            for (modelId in GeminiModelResolver.candidateModels()) {
                tried.add(modelId)
                val (code, parsed) = tryModel(modelId)
                if (code in 200..299 && parsed != null) {
                    GeminiModelResolver.markWorking(modelId)
                    return parsed
                }
                if (code != 404) return null
            }
            val discovered = GeminiModelResolver.discoverActiveFlashModel(httpClient, apiKey)
            if (!discovered.isNullOrBlank() && discovered !in tried) {
                val (code, parsed) = tryModel(discovered)
                if (code in 200..299 && parsed != null) {
                    GeminiModelResolver.markWorking(discovered)
                    return parsed
                }
            }
            null
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val moshi: Moshi = Moshi.Builder().build()
        private val mapAdapter = moshi.adapter<Map<String, Any?>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )

        fun parseSearchResponse(rawResponse: String): Map<String, Any?>? {
            return try {
                val root = mapAdapter.fromJson(rawResponse) ?: return null
                val candidates = root["candidates"] as? List<*> ?: return null
                val first = candidates.firstOrNull() as? Map<*, *> ?: return null
                val content = first["content"] as? Map<*, *> ?: return null
                val parts = content["parts"] as? List<*> ?: return null
                val answer = parts
                    .mapNotNull { (it as? Map<*, *>)?.get("text") as? String }
                    .joinToString(" ")
                    .trim()
                if (answer.isBlank()) return null

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

                mapOf(
                    "success" to true,
                    "answer" to answer,
                    "sources" to sources
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
