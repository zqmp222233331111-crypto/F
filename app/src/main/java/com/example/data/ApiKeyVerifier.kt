package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed class KeyVerificationResult {
    data object Success : KeyVerificationResult()
    data class InvalidKey(val messageBn: String) : KeyVerificationResult()
    data class NoInternet(val messageBn: String) : KeyVerificationResult()
    data class QuotaReached(val messageBn: String) : KeyVerificationResult()
    data class ServerError(val messageBn: String) : KeyVerificationResult()
}

/**
 * Verifies the user-supplied Gemini API key against GET models/gemini-3.8-live
 * using header x-goog-api-key. Never logs the API key.
 */
class ApiKeyVerifier(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    suspend fun verify(apiKey: String): KeyVerificationResult = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            return@withContext KeyVerificationResult.InvalidKey(
                "দয়া করে আপনার Gemini API কী লিখুন বা পেস্ট করুন।"
            )
        }

        val request = Request.Builder()
            .url(VERIFY_ENDPOINT)
            .get()
            .addHeader("x-goog-api-key", trimmed)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string().orEmpty()
                when {
                    code in 200..299 -> KeyVerificationResult.Success
                    code == 429 || body.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                        body.contains("quota", ignoreCase = true) -> {
                        KeyVerificationResult.QuotaReached(
                            "ফ্রি সীমা শেষ, কিছুক্ষণ পরে চেষ্টা করুন।"
                        )
                    }
                    code == 400 || code == 401 || code == 403 ||
                        body.contains("API_KEY_INVALID", ignoreCase = true) ||
                        body.contains("INVALID_ARGUMENT", ignoreCase = true) -> {
                        KeyVerificationResult.InvalidKey(
                            "API কী-টি সঠিক নয় বা অনুমোদিত নয়। দয়া করে যাচাই করে আবার দিন।"
                        )
                    }
                    else -> {
                        KeyVerificationResult.ServerError(
                            "সার্ভার থেকে সাড়া পাওয়া যায়নি (কোড: $code)। কিছুক্ষণ পরে চেষ্টা করুন।"
                        )
                    }
                }
            }
        } catch (_: UnknownHostException) {
            KeyVerificationResult.NoInternet(
                "ইন্টারনেট সংযোগ নেই। Wi-Fi বা মোবাইল ডেটা চালু করে আবার চেষ্টা করুন।"
            )
        } catch (_: SocketTimeoutException) {
            KeyVerificationResult.NoInternet(
                "ইন্টারনেট সংযোগ খুব ধীর বা বিচ্ছিন্ন। অনুগ্রহ করে আবার চেষ্টা করুন।"
            )
        } catch (_: IOException) {
            KeyVerificationResult.NoInternet(
                "নেটওয়ার্ক সংযোগে সমস্যা হয়েছে। ইন্টারনেট চালু আছে কি না দেখুন।"
            )
        }
    }

    companion object {
        const val VERIFY_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-live"
    }
}
