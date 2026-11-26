package com.example.data

/**
 * Enforces strict privacy & safety guards in Kotlin code:
 * 1) Never save passwords, OTPs, PINs, or card numbers into local memory.
 * 2) Never read or type into password, OTP, PIN, or card input fields.
 * 3) Identify banking/wallet apps to block by default.
 */
object SensitiveDataFilter {

    private val sensitiveKeywords = listOf(
        "password", "passwd", "passcode", "pin", "otp", "cvv", "cvc",
        "card number", "credit card", "debit card", "secret", "mpin",
        "পাসওয়ার্ড", "পিন", "ওটিপি", "কার্ড নম্বর", "গোপন কোড"
    )

    private val cardNumberRegex = Regex("""\b(?:\d[ -]*?){13,19}\b""")
    private val otpOrPinCodeRegex = Regex("""\b\d{4,8}\b""")

    fun isSensitiveMemoryAttempt(key: String, value: String): Boolean {
        val combined = "$key $value".lowercase()
        if (sensitiveKeywords.any { combined.contains(it) }) return true
        if (cardNumberRegex.containsMatchIn(value)) return true
        if ((combined.contains("code") || combined.contains("কোড")) &&
            otpOrPinCodeRegex.containsMatchIn(value)
        ) {
            return true
        }
        return false
    }

    fun isSensitiveUiField(
        isPasswordNode: Boolean,
        viewId: String?,
        hintText: String?,
        contentDesc: String?,
        labelText: String?
    ): Boolean {
        if (isPasswordNode) return true
        val combined = listOfNotNull(viewId, hintText, contentDesc, labelText)
            .joinToString(" ")
            .lowercase()
        return sensitiveKeywords.any { combined.contains(it) }
    }

    val defaultBlockedApps: List<Pair<String, String>> = listOf(
        "com.bKash.customerapp" to "bKash (বিকাশ)",
        "com.konasl.nagad" to "Nagad (নগদ)",
        "com.dbbl.nexus.pay" to "NexusPay / Rocket",
        "com.rocket.app" to "Rocket (রকেট)",
        "bd.com.upay.customer" to "Upay (উপায়)",
        "com.ibbl.cellfin" to "CellFin (সেলফিন)",
        "com.citybank.citytouch" to "Citytouch",
        "com.bracbank.astha" to "BRAC Bank Astha",
        "com.ebl.skybanking" to "EBL Skybanking",
        "com.sslwireless.surecash" to "SureCash",
        "com.google.android.apps.walletnfcrel" to "Google Wallet"
    )

    private val bankingPackageHints = listOf(
        "bkash", "nagad", "rocket", "upay", "cellfin", "nexus", "bank",
        "wallet", "pay", "banking", "surecash", "tap", "okwallet", "mcash"
    )

    fun looksLikeBankingOrWalletApp(
        packageName: String,
        appLabel: String? = null,
        explicitlyBlockedPackages: Set<String>
    ): Boolean {
        if (explicitlyBlockedPackages.contains(packageName)) return true
        val pkgLower = packageName.lowercase()
        val labelLower = appLabel?.lowercase().orEmpty()
        return bankingPackageHints.any { hint ->
            pkgLower.contains(hint) || labelLower.contains(hint)
        }
    }
}
