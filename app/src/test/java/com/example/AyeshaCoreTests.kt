package com.example

import com.example.data.ApiKeyVerifier
import com.example.data.KeyVerificationResult
import com.example.data.SensitiveDataFilter
import com.example.data.UserSettings
import com.example.data.db.MemoryEntity
import com.example.live.LiveToolDeclarations
import com.example.persona.AyeshaMode
import com.example.persona.SystemInstructionBuilder
import com.example.persona.VoiceCatalog
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `system instruction builder includes safety, islamic grounding, and voice gender`() {
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
        assertTrue(instruction.contains("রাফি"))
        assertTrue(instruction.contains("UNTRUSTED DATA"))
    }

    @Test
    fun `live tool declarations include 15 phone tools and 2 memory tools`() {
        val toolNames = LiveToolDeclarations.expectedFunctionToolNames(
            includeMemoryTools = true,
            includePhoneControlTools = true
        )
        assertEquals(17, toolNames.size)
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
}
