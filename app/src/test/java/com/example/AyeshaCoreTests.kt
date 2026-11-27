package com.example

import com.example.data.ApiKeyVerifier
import com.example.data.KeyVerificationResult
import com.example.data.SensitiveDataFilter
import com.example.data.UserSettings
import com.example.data.db.MemoryEntity
import com.example.live.CallAutoMemorySummarizer
import com.example.live.LiveToolDeclarations
import com.example.live.LiveWebSearchService
import com.example.persona.AyeshaMode
import com.example.persona.SystemInstructionBuilder
import com.example.persona.VoiceCatalog
import com.example.video.ShortsOutputLanguage
import com.example.video.ShortsPlatform
import com.example.video.ShortsVideoAnalyzer
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class AyeshaCoreTests {

    @Test
    fun `api key verifier distinguishes success, wrong key, quota, and no internet`() = runTest {
        fun clientReturning(code: Int, body: String): OkHttpClient =
            OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    assertEquals("test-key", chain.request().header("x-goog-api-key"))
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                })
                .build()

        assertTrue(ApiKeyVerifier(clientReturning(200, "{}")).verify("test-key") is KeyVerificationResult.Success)
        assertTrue(ApiKeyVerifier(clientReturning(400, "API_KEY_INVALID")).verify("test-key") is KeyVerificationResult.InvalidKey)
        assertTrue(ApiKeyVerifier(clientReturning(429, "RESOURCE_EXHAUSTED")).verify("test-key") is KeyVerificationResult.QuotaReached)

        val offlineClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { throw UnknownHostException("offline") })
            .build()
        assertTrue(ApiKeyVerifier(offlineClient).verify("test-key") is KeyVerificationResult.NoInternet)
    }

    @Test
    fun `sensitive data filter blocks secrets, password UI fields, and banking apps`() {
        assertTrue(SensitiveDataFilter.isSensitiveMemoryAttempt("otp_code", "482910"))
        assertTrue(SensitiveDataFilter.isSensitiveMemoryAttempt("bank_pin", "1234"))
        assertFalse(SensitiveDataFilter.isSensitiveMemoryAttempt("favorite_tea", "মসলা চা"))

        assertTrue(SensitiveDataFilter.isSensitiveUiField(true, null, null, null, null))
        assertTrue(SensitiveDataFilter.isSensitiveUiField(false, "et_otp", "Enter OTP", null, null))
        assertFalse(SensitiveDataFilter.isSensitiveUiField(false, "search_box", "খুঁজুন", null, null))

        val blocked = SensitiveDataFilter.defaultBlockedApps.map { it.first }.toSet()
        assertTrue(SensitiveDataFilter.looksLikeBankingOrWalletApp("com.bKash.customerapp", "bKash", blocked))
        assertTrue(SensitiveDataFilter.looksLikeBankingOrWalletApp("com.konasl.nagad", "Nagad", blocked))
        assertFalse(SensitiveDataFilter.looksLikeBankingOrWalletApp("com.whatsapp", "WhatsApp", blocked))
    }

    @Test
    fun `system instruction builder includes safety, islamic grounding, all-mode web search, and voice gender`() {
        val instruction = SystemInstructionBuilder.build(
            settings = UserSettings(memoryEnabled = true, phoneControlMasterEnabled = true),
            mode = AyeshaMode.ISLAMIC,
            customCharacter = null,
            memories = listOf(MemoryEntity(characterId = "mode_islamic", factKey = "name", factValue = "রাফি")),
            effectiveVoice = VoiceCatalog.findByName("Puck")
        )
        assertTrue(instruction.contains("999"))
        assertTrue(instruction.contains("আসসালামু আলাইকুম"))
        assertTrue(instruction.contains("Google Search"))
        assertTrue(instruction.contains("web_search"))
        assertTrue(instruction.contains("রাফি"))
        assertTrue(instruction.contains("UNTRUSTED DATA"))
        assertTrue(instruction.contains("get_latest_shorts_draft"))

        val friendInstruction = SystemInstructionBuilder.build(
            settings = UserSettings(),
            mode = AyeshaMode.FRIEND,
            customCharacter = null,
            memories = emptyList(),
            effectiveVoice = VoiceCatalog.findByName("Sulafat")
        )
        assertTrue(friendInstruction.contains("LIVE GOOGLE SEARCH (ALL MODES)"))
    }

    @Test
    fun `live tool declarations include 15 phone tools, 2 memory tools, web_search, and shorts draft tool`() {
        val toolNames = LiveToolDeclarations.expectedFunctionToolNames(
            includeMemoryTools = true,
            includePhoneControlTools = true
        )
        assertEquals(19, toolNames.size)
        assertTrue(toolNames.contains("web_search"))
        assertTrue(toolNames.contains("get_latest_shorts_draft"))
        assertTrue(toolNames.contains("save_memory"))
        assertTrue(toolNames.contains("delete_memory"))
        assertEquals(15, LiveToolDeclarations.phoneToolNames.size)
        assertTrue(toolNames.containsAll(LiveToolDeclarations.phoneToolNames))
    }

    @Test
    fun `custom character preserves 10000 character description in system instruction`() {
        val longDescription = "ক".repeat(10_000)
        val customChar = com.example.data.db.CustomCharacterEntity(
            id = "custom_1",
            name = "তানিয়া",
            relationshipRole = "সহকারী",
            callUserAs = "ভাইয়া",
            language = "বাংলাদেশি বাংলা",
            voiceName = "Sulafat",
            interests = "বই, প্রযুক্তি",
            description = longDescription,
            funLevel = 0.6f,
            emotionLevel = 0.7f,
            seriousnessLevel = 0.4f,
            talkativenessLevel = 0.6f
        )
        val instruction = SystemInstructionBuilder.build(
            settings = UserSettings(selectedModeId = AyeshaMode.CUSTOM.id, selectedCustomCharacterId = "custom_1"),
            mode = AyeshaMode.CUSTOM,
            customCharacter = customChar,
            memories = emptyList(),
            effectiveVoice = VoiceCatalog.findByName("Sulafat")
        )
        assertTrue(instruction.contains(longDescription))
        assertTrue(instruction.contains("তানিয়া"))
        assertTrue(instruction.contains("ভাইয়া"))
        assertTrue(instruction.contains("আমির ইসলাম"))
        assertTrue(instruction.contains("SILENT UNLESS ASKED"))
    }

    @Test
    fun `shorts analyzer builds 3-platform multimodal request and parses all 3 platform packs`() {
        val reqJson = ShortsVideoAnalyzer.buildGenerateContentRequestJson(
            fileUri = "https://generativelanguage.googleapis.com/v1beta/files/abc123",
            mimeType = "video/mp4",
            language = ShortsOutputLanguage.BANGLA,
            creatorNote = "ট্রাভেল ভ্লগ"
        )
        assertTrue(reqJson.contains("files/abc123"))
        assertTrue(reqJson.contains("youtube"))
        assertTrue(reqJson.contains("facebook"))
        assertTrue(reqJson.contains("tiktok"))

        val fakeResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\"visualSummaryBn\":\"পাহাড়ে সূর্যোদয়ের দৃশ্য।\",\"audioSummaryBn\":\"ভয়েসওভারে ভ্রমণের গল্প বলা হয়েছে।\",\"youtube\":{\"titles\":[\"সাজেকে সূর্যোদয় দেখে অবাক!\"],\"recommendedTitle\":\"সাজেকে সূর্যোদয় দেখে অবাক!\",\"description\":\"সকালে মেঘের রাজ্যে দারুণ মুহূর্ত।\",\"hashtags\":[\"#shorts\",\"#travel\"]},\"facebook\":{\"titles\":[\"মেঘের রাজ্যে এক সকাল\"],\"recommendedTitle\":\"মেঘের রাজ্যে এক সকাল\",\"description\":\"সাজেক ভ্যালির অভিজ্ঞতা কেমন লাগলো?\",\"hashtags\":[\"#reels\",\"#facebookreels\"]},\"tiktok\":{\"titles\":[\"সাজেকের সকাল দেখলে মন জুড়িয়ে যাবে\"],\"recommendedTitle\":\"সাজেকের সকাল দেখলে মন জুড়িয়ে যাবে\",\"description\":\"মেঘের দেশে সকাল\",\"hashtags\":[\"#fyp\",\"tiktok\"]}}"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = ShortsVideoAnalyzer.parseGenerateContentResponse(
            rawResponse = fakeResponse,
            language = ShortsOutputLanguage.BANGLA,
            videoDisplayName = "sajek.mp4",
            videoUriString = "content://media/video/1"
        )
        assertNotNull(parsed)
        assertEquals(3, parsed!!.platformPacks.size)
        assertEquals("সাজেকে সূর্যোদয় দেখে অবাক!", parsed.selectedTitle)
        assertEquals("মেঘের রাজ্যে এক সকাল", parsed.platformPacks[ShortsPlatform.FACEBOOK_REELS]?.selectedTitle)
        assertTrue(parsed.platformPacks[ShortsPlatform.TIKTOK]?.hashtags?.contains("#tiktok") == true)
    }

    @Test
    fun `live web search and post-call auto memory summarizer parse valid gemini responses`() {
        val searchRaw = """
            {
              "candidates": [
                {
                  "content": { "parts": [{ "text": "ঢাকায় আজ আবহাওয়া পরিষ্কার এবং তাপমাত্রা ২৮ ডিগ্রি।" }] },
                  "groundingMetadata": {
                    "groundingChunks": [
                      { "web": { "title": "Weather BD", "uri": "https://example.com/weather" } }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val searchParsed = LiveWebSearchService.parseSearchResponse(searchRaw)
        assertNotNull(searchParsed)
        assertEquals(true, searchParsed!!["success"])
        assertTrue((searchParsed["answer"] as String).contains("২৮ ডিগ্রি"))

        val memRaw = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "{\"lastCallSummary\":\"ইউটিউব শর্টস চ্যানেল বড় করার পরিকল্পনা নিয়ে কথা হয়েছে।\",\"facts\":[{\"key\":\"youtube_niche\",\"value\":\"ট্রাভেল শর্টস\"}]}" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val facts = CallAutoMemorySummarizer.parseSummarizerResponse(memRaw)
        assertEquals(2, facts.size)
        assertEquals("last_call_summary", facts[0].key)
        assertEquals("youtube_niche", facts[1].key)
    }
}
