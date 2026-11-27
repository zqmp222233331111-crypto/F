package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.atomic.AtomicReference

/**
 * Resolves an active Gemini text/multimodal `generateContent` model for the user's API key.
 * Tries current Flash models first and, if HTTP 404 occurs, dynamically inspects
 * `GET /v1beta/models` so deprecated model IDs never break features.
 */
object GeminiModelResolver {
    private val cachedWorkingModel = AtomicReference<String?>("gemini-3.8-flash")
    private val fallbackOrder = listOf(
        "gemini-3.8-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash-lite",
        "gemini-flash-latest",
        "gemini-2.5-flash"
    )

    private val moshi: Moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    fun candidateModels(): List<String> {
        val current = cachedWorkingModel.get()
        return buildList {
            if (!current.isNullOrBlank()) add(current)
            fallbackOrder.forEach { if (it !in this) add(it) }
        }
    }

    fun markWorking(modelId: String) {
        cachedWorkingModel.set(modelId)
    }

    fun endpointFor(modelId: String): String {
        val clean = modelId.removePrefix("models/")
        return "https://generativelanguage.googleapis.com/v1beta/models/$clean:generateContent"
    }

    fun discoverActiveFlashModel(httpClient: OkHttpClient, apiKey: String): String? {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models")
            .get()
            .addHeader("x-goog-api-key", apiKey.trim())
            .build()

        return try {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val raw = resp.body?.string().orEmpty()
                parseBestFlashModelFromList(raw)?.also { discovered ->
                    cachedWorkingModel.set(discovered)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseBestFlashModelFromList(rawJson: String): String? {
        return try {
            val root = mapAdapter.fromJson(rawJson) ?: return null
            val models = root["models"] as? List<*> ?: return null
            val eligible = models.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                val fullName = (map["name"] as? String)?.trim().orEmpty()
                val shortName = fullName.removePrefix("models/")
                val methods = (map["supportedGenerationMethods"] as? List<*>)
                    .orEmpty()
                    .mapNotNull { it as? String }
                val lower = shortName.lowercase()
                val supportsGenerate = methods.any { it.equals("generateContent", ignoreCase = true) }
                val excluded = lower.contains("live") ||
                    lower.contains("tts") ||
                    lower.contains("image") ||
                    lower.contains("embedding") ||
                    lower.contains("aqa") ||
                    lower.contains("robotics")
                if (supportsGenerate && !excluded && shortName.isNotBlank()) shortName else null
            }
            if (eligible.isEmpty()) return null

            // Prefer newest flash model, then any flash, then pro/general gemini
            eligible
                .filter { it.contains("flash", ignoreCase = true) }
                .sortedByDescending { scoreModelVersion(it) }
                .firstOrNull()
                ?: eligible
                    .filter { it.contains("gemini", ignoreCase = true) }
                    .sortedByDescending { scoreModelVersion(it) }
                    .firstOrNull()
                ?: eligible.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun scoreModelVersion(name: String): Double {
        val match = Regex("""gemini-(\d+(?:\.\d+)?)""").find(name.lowercase())
        val base = match?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val penalty = when {
            name.contains("lite", ignoreCase = true) -> -0.05
            name.contains("exp", ignoreCase = true) || name.contains("preview", ignoreCase = true) -> -0.02
            else -> 0.0
        }
        return base + penalty
    }
}
