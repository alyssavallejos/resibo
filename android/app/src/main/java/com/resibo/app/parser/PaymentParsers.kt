package com.resibo.app.parser

import java.util.regex.Pattern

data class ParsedPayment(
    val provider: String, // "gcash", "maya", "bank"
    val amount: Double,
    val senderNameMasked: String,
    val referenceNumber: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val rawSnippet: String = ""
)

object PaymentParsers {

    // Package allowlist constants
    const val PKG_GCASH = "com.globe.gcash.android"
    const val PKG_MAYA = "com.paymaya"
    const val PKG_MARIBANK_PH = "com.seabank.ph" // Official MariBank PH Google Play package ID
    const val PKG_MARIBANK_LEGACY = "com.maribank.ph"
    const val PKG_MARIBANK_MOBILE = "com.maribank.mobile"
    const val PKG_MARIBANK_SG = "sg.com.maribankmobile.digitalbank"
    const val PKG_SEABANK_MOBILE = "ph.com.seabank.mobile"
    const val PKG_SEABANK_IBANK = "ph.com.seabank.ibank"
    const val PKG_SHOPEE = "com.shopee.ph"
    const val PKG_GMAIL = "com.google.android.gm"
    const val PKG_EMAIL = "com.android.email"
    const val PKG_GOTYME = "ph.com.gotyme.mobile"
    const val PKG_BDO_1 = "com.bdo.mobile"
    const val PKG_BDO_2 = "com.bdo.digital"
    const val PKG_BPI = "com.bpi.ng.app"
    const val PKG_UNIONBANK = "com.unionbankph.online"
    const val PKG_METROBANK_1 = "com.metrobank.mbc"
    const val PKG_METROBANK_2 = "com.mbtc.mbc"
    const val PKG_LANDBANK = "ph.gov.landbank.mobilerelease"

    // Recognized SMS / Notification Sender IDs
    val ALLOWED_SMS_SENDERS = setOf(
        "GCASH", "MAYA", "MARIBANK", "SEABANK", "GOTYME", "BDO", "BPI", "UNIONBANK", "METROBANK", "LANDBANK", "SHOPEE"
    )

    // Allowed packages set
    val ALLOWED_PACKAGES = setOf(
        PKG_GCASH,
        PKG_MAYA,
        PKG_MARIBANK_PH,
        PKG_MARIBANK_LEGACY,
        PKG_MARIBANK_MOBILE,
        PKG_MARIBANK_SG,
        PKG_SEABANK_MOBILE,
        PKG_SEABANK_IBANK,
        PKG_SHOPEE,
        PKG_GMAIL,
        PKG_EMAIL,
        PKG_GOTYME,
        PKG_BDO_1,
        PKG_BDO_2,
        PKG_BPI,
        PKG_UNIONBANK,
        PKG_METROBANK_1,
        PKG_METROBANK_2,
        PKG_LANDBANK
    )

    // Negative keywords: ignore outgoing, OTP, login, promo, or balance statements
    private val IGNORE_PATTERNS = listOf(
        Pattern.compile("you\\s+(?:have\\s+)?(paid|sent|transferred|withdrew|cashed in)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(nagbayad|nagpadala|nag-transfer|nag-cash in) ka", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(otp|one-time pin|authentication code|verification code)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(promo|discount|voucher|cashback offer|loan|congratulations)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(failed|unsuccessful|declined|cancelled|schedule)\\b", Pattern.CASE_INSENSITIVE)
    )

    /**
     * Check if text is strictly an outgoing transaction, promo, or security code
     */
    fun shouldIgnore(text: String): Boolean {
        for (pattern in IGNORE_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true
            }
        }
        return false
    }

    /**
     * Main parser router based on package name or provider hint
     */
    fun parse(packageName: String, title: String, text: String, senderId: String? = null): ParsedPayment? {
        val fullContent = "$title $text".trim()
        if (fullContent.isBlank() || shouldIgnore(fullContent)) {
            return null
        }

        return when {
            packageName == PKG_GCASH || senderId?.equals("GCASH", ignoreCase = true) == true -> {
                parseGCash(fullContent)
            }
            packageName == PKG_MAYA || senderId?.equals("MAYA", ignoreCase = true) == true -> {
                parseMaya(fullContent)
            }
            packageName in setOf(
                PKG_MARIBANK_PH,
                PKG_MARIBANK_LEGACY,
                PKG_MARIBANK_MOBILE,
                PKG_MARIBANK_SG,
                PKG_SEABANK_MOBILE,
                PKG_SEABANK_IBANK,
                PKG_SHOPEE
            ) ||
            packageName.contains("maribank", ignoreCase = true) ||
            packageName.contains("seabank", ignoreCase = true) ||
            senderId?.equals("MARIBANK", ignoreCase = true) == true ||
            senderId?.equals("SEABANK", ignoreCase = true) == true ||
            fullContent.contains("MariBank", ignoreCase = true) ||
            fullContent.contains("SeaBank", ignoreCase = true) -> {
                parseBank("maribank", fullContent)
            }
            packageName == PKG_GOTYME || senderId?.equals("GOTYME", ignoreCase = true) == true || fullContent.contains("GoTyme", ignoreCase = true) -> {
                parseBank("gotyme", fullContent)
            }
            packageName in setOf(PKG_BDO_1, PKG_BDO_2) || senderId?.equals("BDO", ignoreCase = true) == true -> {
                parseBank("bdo", fullContent)
            }
            packageName == PKG_BPI || senderId?.equals("BPI", ignoreCase = true) == true -> {
                parseBank("bpi", fullContent)
            }
            packageName == PKG_UNIONBANK || senderId?.equals("UNIONBANK", ignoreCase = true) == true -> {
                parseBank("unionbank", fullContent)
            }
            packageName in setOf(PKG_METROBANK_1, PKG_METROBANK_2) || senderId?.equals("METROBANK", ignoreCase = true) == true -> {
                parseBank("metrobank", fullContent)
            }
            packageName == PKG_LANDBANK || senderId?.equals("LANDBANK", ignoreCase = true) == true -> {
                parseBank("landbank", fullContent)
            }
            // Fallback for simulation / debug test notifications
            fullContent.contains("GCash", ignoreCase = true) -> parseGCash(fullContent)
            fullContent.contains("Maya", ignoreCase = true) -> parseMaya(fullContent)
            fullContent.contains("InstaPay", ignoreCase = true) || fullContent.contains("PESONet", ignoreCase = true) -> parseBank("bank", fullContent)
            else -> null
        }
    }

    /**
     * GCash Notification & SMS Formats
     * Examples:
     * - "You have received PHP 150.00 of GCash from JUAN DELA CRUZ 0917****123 with Ref No. 123456789012."
     * - "Natanggap mo ang PHP 500.00 mula kay MARIA CLARA 0918****456 Ref No. 987654321098."
     * - "You have received ₱200.00 via Express Send from ALYSSA V. Ref. No. 500123456789"
     * - "You received PHP 50.00 from JUAN D. Ref: 102938475610"
     */
    fun parseGCash(text: String): ParsedPayment? {
        val amount = extractAmount(text) ?: return null
        val refNo = extractReferenceNumber(text)
        val sender = extractSenderName(text, "gcash")

        return ParsedPayment(
            provider = "gcash",
            amount = amount,
            senderNameMasked = sender,
            referenceNumber = refNo,
            rawSnippet = text.take(120)
        )
    }

    /**
     * Maya Notification & SMS Formats
     * Examples:
     * - "You received PHP 250.00 from JOSE RIZAL on 10/18/2026. Ref: 20261018123456"
     * - "You have received ₱1,000.00 from JUAN D. via Maya Send Money. Reference ID: MAYA99887766"
     */
    fun parseMaya(text: String): ParsedPayment? {
        val amount = extractAmount(text) ?: return null
        val refNo = extractReferenceNumber(text)
        val sender = extractSenderName(text, "maya")

        return ParsedPayment(
            provider = "maya",
            amount = amount,
            senderNameMasked = sender,
            referenceNumber = refNo,
            rawSnippet = text.take(120)
        )
    }

    /**
     * Bank / InstaPay / PESONet incoming payment alerts
     */
    fun parseBank(bankKey: String, text: String): ParsedPayment? {
        val amount = extractAmount(text) ?: return null
        val refNo = extractReferenceNumber(text)
        val sender = extractSenderName(text, bankKey)

        return ParsedPayment(
            provider = bankKey,
            amount = amount,
            senderNameMasked = sender,
            referenceNumber = refNo,
            rawSnippet = text.take(120)
        )
    }

    /**
     * Extract currency amount (handles PHP, ₱, comma separators e.g. PHP 1,500.50, PHP 1.00, or ₱150.00)
     */
    fun extractAmount(text: String): Double? {
        val patterns = listOf(
            Pattern.compile("(?:Transfer amount|Amount)\\s*[:.]?\\s*(?:PHP|Php|₱)?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:PHP|Php|₱)\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("received\\s+(?:PHP|Php|₱)?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("amount\\s*(?:of)?\\s*(?:PHP|₱)?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)", Pattern.CASE_INSENSITIVE)
        )

        for (p in patterns) {
            val matcher = p.matcher(text)
            if (matcher.find()) {
                val clean = matcher.group(1)?.replace(",", "") ?: continue
                clean.toDoubleOrNull()?.let { return it }
            }
        }
        return null
    }

    /**
     * Extract Reference Number / Transaction ID
     * Patterns: "Ref No. 123456789012", "Ref: 123456", "Reference ID: ABC12345678", "account ending 3380"
     */
    fun extractReferenceNumber(text: String): String? {
        val patterns = listOf(
            Pattern.compile("\\b(?:Reference|Ref\\.?)\\s*(?:No\\.?|ID|Number|#)?\\s*[:.]?\\s*([A-Za-z0-9]{6,32})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(BC[0-9]{12,28})\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:Trace\\s*(?:No\\.?|Number)?)\\s*[:.]?\\s*([A-Za-z0-9]{6,32})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(Ref\\.?\\s*[0-9]{8,24})\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:account\\s+ending|ending)\\s*[:.]?\\s*([0-9]{4})", Pattern.CASE_INSENSITIVE)
        )

        for (p in patterns) {
            val matcher = p.matcher(text)
            if (matcher.find()) {
                val rawRef = matcher.group(1)?.replace(Regex("[^A-Za-z0-9]"), "")?.uppercase()
                if (!rawRef.isNullOrBlank()) {
                    return rawRef
                }
            }
        }
        return null
    }

    /**
     * Extract sender name or masked number (e.g. "JUAN D.", "MARIA CLARA 0917****123", "MariBank - 3380")
     */
    fun extractSenderName(text: String, provider: String): String {
        val patterns = listOf(
            Pattern.compile("Transfer from\\s*[:.]?\\s*([A-Za-z0-9.*\\s-]{3,35}?)(?:\\s+(?:Transfer to|to|dated|with|Ref)|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:from|mula kay)\\s+([A-Za-z0-9.*\\s-]{3,35}?)(?:\\s+(?:with|on|via|Ref|Ref\\.|Ref No\\.|Reference|to|dated|account)|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("transferred by\\s+([A-Za-z0-9.*\\s]{3,30}?)(?:\\s+(?:via|Ref|to)|$)", Pattern.CASE_INSENSITIVE)
        )

        for (p in patterns) {
            val matcher = p.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim()?.replace(Regex("\\s+"), " ")
                if (!candidate.isNullOrBlank() && candidate.length > 2 && !candidate.contains("GCash", ignoreCase = true)) {
                    return candidate
                }
            }
        }
        return "Sender (Masked)"
    }
}
