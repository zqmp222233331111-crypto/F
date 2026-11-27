package com.example.video

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the latest 3-platform analyzed video pack (YouTube Shorts, Facebook Reels, TikTok)
 * locally so both the Shorts tab and live voice calls can read or edit it anytime.
 */
class LatestShortsDraftStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _latestDraft = MutableStateFlow(loadFromPrefs())
    val latestDraft: StateFlow<ShortsAnalysisResult?> = _latestDraft.asStateFlow()

    fun save(result: ShortsAnalysisResult) {
        _latestDraft.value = result
        persist(result)
    }

    fun selectPlatform(platform: ShortsPlatform) {
        val current = _latestDraft.value ?: return
        val updated = current.copy(activePlatform = platform)
        _latestDraft.value = updated
        persist(updated)
    }

    fun updateSelectedTitle(newTitle: String) {
        val current = _latestDraft.value ?: return
        val active = current.activePlatform
        val existingPack = current.platformPacks[active] ?: return
        val updatedPacks = current.platformPacks.toMutableMap().apply {
            put(active, existingPack.copy(selectedTitle = newTitle))
        }
        val updated = current.copy(platformPacks = updatedPacks)
        _latestDraft.value = updated
        persist(updated)
    }

    fun updateDescription(newDescription: String) {
        val current = _latestDraft.value ?: return
        val active = current.activePlatform
        val existingPack = current.platformPacks[active] ?: return
        val updatedPacks = current.platformPacks.toMutableMap().apply {
            put(active, existingPack.copy(description = newDescription))
        }
        val updated = current.copy(platformPacks = updatedPacks)
        _latestDraft.value = updated
        persist(updated)
    }

    fun clear() {
        _latestDraft.value = null
        prefs.edit().remove(KEY_DRAFT_JSON).apply()
    }

    private fun persist(result: ShortsAnalysisResult) {
        val packsObj = JSONObject()
        result.platformPacks.forEach { (platform, pack) ->
            packsObj.put(
                platform.id,
                JSONObject().apply {
                    put("selectedTitle", pack.selectedTitle)
                    put("titles", JSONArray(pack.titles))
                    put("description", pack.description)
                    put("hashtags", JSONArray(pack.hashtags))
                }
            )
        }
        val obj = JSONObject().apply {
            put("activePlatform", result.activePlatform.id)
            put("platformPacks", packsObj)
            put("visualSummaryBn", result.visualSummaryBn)
            put("audioSummaryBn", result.audioSummaryBn)
            put("languageId", result.language.id)
            put("videoDisplayName", result.videoDisplayName)
            put("videoUriString", result.videoUriString)
            put("analyzedAtMillis", result.analyzedAtMillis)
        }
        prefs.edit().putString(KEY_DRAFT_JSON, obj.toString()).apply()
    }

    private fun loadFromPrefs(): ShortsAnalysisResult? {
        val raw = prefs.getString(KEY_DRAFT_JSON, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val packsObj = obj.optJSONObject("platformPacks")
            val packs = mutableMapOf<ShortsPlatform, PlatformPackItem>()

            if (packsObj != null) {
                ShortsPlatform.entries.forEach { platform ->
                    val pObj = packsObj.optJSONObject(platform.id) ?: return@forEach
                    val titles = jsonArrayToList(pObj.optJSONArray("titles"))
                    val selectedTitle = pObj.optString("selectedTitle").orEmpty()
                    val description = pObj.optString("description").orEmpty()
                    val hashtags = jsonArrayToList(pObj.optJSONArray("hashtags"))
                    if (selectedTitle.isNotBlank() || description.isNotBlank()) {
                        packs[platform] = PlatformPackItem(
                            platform = platform,
                            selectedTitle = selectedTitle,
                            titles = titles.ifEmpty { listOf(selectedTitle) },
                            description = description,
                            hashtags = hashtags
                        )
                    }
                }
            } else {
                // Backward compatibility with single-platform saved drafts
                val selectedTitle = obj.optString("selectedTitle").orEmpty()
                val description = obj.optString("description").orEmpty()
                if (selectedTitle.isNotBlank() || description.isNotBlank()) {
                    packs[ShortsPlatform.YOUTUBE_SHORTS] = PlatformPackItem(
                        platform = ShortsPlatform.YOUTUBE_SHORTS,
                        selectedTitle = selectedTitle,
                        titles = jsonArrayToList(obj.optJSONArray("titles")).ifEmpty { listOf(selectedTitle) },
                        description = description,
                        hashtags = jsonArrayToList(obj.optJSONArray("hashtags"))
                    )
                }
            }

            if (packs.isEmpty()) return null

            ShortsAnalysisResult(
                platformPacks = packs,
                activePlatform = ShortsPlatform.fromId(obj.optString("activePlatform")),
                visualSummaryBn = obj.optString("visualSummaryBn").orEmpty(),
                audioSummaryBn = obj.optString("audioSummaryBn").orEmpty(),
                language = ShortsOutputLanguage.fromId(obj.optString("languageId")),
                videoDisplayName = obj.optString("videoDisplayName").orEmpty(),
                videoUriString = obj.optString("videoUriString").orEmpty(),
                analyzedAtMillis = obj.optLong("analyzedAtMillis", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length())
            .mapNotNull { idx -> arr.optString(idx)?.takeIf { it.isNotBlank() } }
    }

    companion object {
        private const val PREFS_NAME = "ayesha_latest_shorts_draft"
        private const val KEY_DRAFT_JSON = "draft_json"
    }
}
