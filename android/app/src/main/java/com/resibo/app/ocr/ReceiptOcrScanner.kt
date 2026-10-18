package com.resibo.app.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.resibo.app.model.CheckInfo
import com.resibo.app.model.ExtractedInfo
import com.resibo.app.model.Payment
import com.resibo.app.model.Receipt
import com.resibo.app.model.ReceiptVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.resume

data class NameMatchResult(
    val matches: Boolean,
    val confidence: String, // "EXACT", "MASKED_LOGIC", "INITIALS", "FUZZY", "GENERIC", "NONE"
    val explanation: String
)

object ReceiptOcrScanner {
    private const val TAG = "ReceiptOcrScanner"
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    fun decodeBitmap(imageUrlOrBase64: String): Bitmap? {
        if (imageUrlOrBase64.isBlank()) return null
        return try {
            if (imageUrlOrBase64.startsWith("data:") || imageUrlOrBase64.contains(";base64,")) {
                val cleanBase64 = imageUrlOrBase64.substringAfter(";base64,").trim()
                val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } else if (imageUrlOrBase64.startsWith("http://") || imageUrlOrBase64.startsWith("https://")) {
                val url = URL(imageUrlOrBase64)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input: InputStream = connection.inputStream
                BitmapFactory.decodeStream(input)
            } else {
                // Try decoding directly as base64
                val decodedBytes = Base64.decode(imageUrlOrBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap from image: ${e.message}")
            null
        }
    }

    suspend fun scanText(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                cont.resume(visionText.text)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR recognition failed: ${e.message}")
                cont.resume("")
            }
    }

    fun parseReceiptText(rawText: String, expectedAmount: Double? = null, fallbackSender: String = ""): ExtractedInfo {
        if (rawText.isBlank()) {
            return ExtractedInfo(
                provider = "UNKNOWN",
                amount = expectedAmount ?: 0.0,
                referenceNumber = null,
                senderName = fallbackSender,
                transactionStatus = "SUCCESSFUL"
            )
        }

        val text = rawText.replace("\r", "")
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val lowerText = text.lowercase()

        // 1. Detect Destination Bank (e.g. "To: Maribank" or "Destination Bank: Maya")
        var destinationBank = ""
        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase()
            if (lower == "to" || lower.startsWith("to:") || lower.startsWith("recipient bank") ||
                lower.startsWith("destination bank") || lower.startsWith("transfer to") || lower.startsWith("sent to")) {
                val destCandidate = if (lower == "to" || lower == "transfer to" || lower == "sent to") {
                    if (i + 1 < lines.size) lines[i + 1] else ""
                } else {
                    line.substringAfter(":").trim()
                }
                val destLower = destCandidate.lowercase()
                when {
                    destLower.contains("maribank") || destLower.contains("shopee") -> destinationBank = "MARIBANK"
                    destLower.contains("seabank") -> destinationBank = "SEABANK"
                    destLower.contains("gcash") -> destinationBank = "GCASH"
                    destLower.contains("maya") -> destinationBank = "MAYA"
                    destLower.contains("unionbank") || destLower.contains("union bank") -> destinationBank = "UNIONBANK"
                    destLower.contains("bdo") -> destinationBank = "BDO"
                    destLower.contains("bpi") -> destinationBank = "BPI"
                    destLower.contains("gotyme") -> destinationBank = "GOTYME"
                    destLower.contains("landbank") -> destinationBank = "LANDBANK"
                    destLower.contains("metrobank") -> destinationBank = "METROBANK"
                    destLower.contains("rcbc") -> destinationBank = "RCBC"
                    destLower.contains("chinabank") || destLower.contains("china bank") -> destinationBank = "CHINABANK"
                }
                if (destinationBank.isNotBlank()) break
            }
        }

        // Helper to detect bank from snippet
        fun detectBankFromSnippet(s: String): String? {
            val l = s.lowercase()
            return when {
                l.contains("unionbank") || l.contains("union bank") || l.contains("ub online") || l.contains("ubp") -> "UNIONBANK"
                l.contains("gotyme") || l.contains("go tyme") -> "GOTYME"
                l.contains("gcash") -> "GCASH"
                l.contains("maya") || l.contains("paymaya") -> "MAYA"
                l.contains("seabank") -> "SEABANK"
                l.contains("maribank") || l.contains("shopeepay") -> "MARIBANK"
                l.contains("bdo") || l.contains("bdo unibank") -> "BDO"
                l.contains("bpi") || l.contains("bank of the philippine islands") -> "BPI"
                l.contains("landbank") || l.contains("land bank") -> "LANDBANK"
                l.contains("metrobank") || l.contains("metropolitan bank") -> "METROBANK"
                l.contains("rcbc") -> "RCBC"
                l.contains("chinabank") || l.contains("china bank") -> "CHINABANK"
                l.contains("security bank") || l.contains("secb") -> "SECURITYBANK"
                l.contains("pnb") -> "PNB"
                l.contains("eastwest") || l.contains("east west") -> "EASTWEST"
                l.contains("dbp") -> "DBP"
                else -> null
            }
        }

        // 2. Detect Issuing Provider (Source Bank / Wallet)
        var provider = "UNKNOWN"
        val headerSnippet = lines.take(5).joinToString(" ")
        val headerBank = detectBankFromSnippet(headerSnippet)
        if (headerBank != null && headerBank != destinationBank) {
            provider = headerBank
        } else {
            // Check lines before "To"
            val toIndex = lines.indexOfFirst { it.equals("To", ignoreCase = true) || it.startsWith("To:", ignoreCase = true) }
            val beforeToSnippet = if (toIndex > 0) lines.take(toIndex).joinToString(" ") else ""
            val beforeToBank = detectBankFromSnippet(beforeToSnippet)
            if (beforeToBank != null && beforeToBank != destinationBank) {
                provider = beforeToBank
            } else {
                val fullTextBank = detectBankFromSnippet(lowerText)
                if (fullTextBank != null) {
                    provider = fullTextBank
                } else if (destinationBank.isNotBlank()) {
                    provider = destinationBank
                }
            }
        }

        // 3. Detect Reference Number / Transaction ID
        var refNumber: String? = null

        // (a) Dedicated UnionBank pattern (UB followed by 6-12 digits e.g. UB660079)
        val ubRefPattern = Pattern.compile("\\b(UB[0-9]{6,12})\\b", Pattern.CASE_INSENSITIVE)
        val ubMatcher = ubRefPattern.matcher(text)
        if (ubMatcher.find()) {
            refNumber = ubMatcher.group(1)?.uppercase()
        }

        // (b) Dedicated InstaPay Trace / Reference pattern
        if (refNumber == null) {
            val instaRefPattern = Pattern.compile("(?:InstaPay\\s*(?:Reference\\s*(?:Number|No\\.?)|Ref\\s*(?:No\\.?|ID)|Trace\\s*No\\.?)?)\\s*[:.]?\\s*([0-9]{6,18})", Pattern.CASE_INSENSITIVE)
            val instaMatcher = instaRefPattern.matcher(text)
            if (instaMatcher.find()) {
                refNumber = instaMatcher.group(1)?.trim()
            }
        }

        // (c) MariBank BC pattern (BC followed by 14-22 digits)
        if (refNumber == null) {
            val mariRefPattern = Pattern.compile("\\b(BC\\d{14,22})\\b", Pattern.CASE_INSENSITIVE)
            val mariMatcher = mariRefPattern.matcher(text)
            if (mariMatcher.find()) {
                refNumber = mariMatcher.group(1)?.uppercase()
            }
        }

        // (d) Maya prefix pattern (MAYA followed by 6-16 alphanumerics)
        if (refNumber == null) {
            val mayaRefPattern = Pattern.compile("\\b(MAYA[A-Za-z0-9]{6,16})\\b", Pattern.CASE_INSENSITIVE)
            val mayaMatcher = mayaRefPattern.matcher(text)
            if (mayaMatcher.find()) {
                refNumber = mayaMatcher.group(1)?.uppercase()
            }
        }

        // (e) Standard Reference Number pattern with word boundaries and negative check
        if (refNumber == null) {
            val refPattern = Pattern.compile(
                "\\b(?:Reference\\s*(?:Number|No\\.?|ID)?|Ref\\s*(?:No\\.?|ID)|Ref\\.|Trans(?:action)?\\s*ID|Trace\\s*(?:No\\.?|Number)?)\\s*[:.]?\\s*([A-Za-z0-9 \\t]{6,32})",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = refPattern.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.substringBefore("\n")?.substringBefore("\r")?.replace(" ", "")?.trim()
                if (!candidate.isNullOrBlank() && candidate.length >= 6 &&
                    !candidate.contains("erence", ignoreCase = true) &&
                    !candidate.contains("number", ignoreCase = true) &&
                    !candidate.contains("reference", ignoreCase = true)) {
                    refNumber = candidate
                }
            }
        }

        // (f) Line-based lookup: If a line matches Reference/Ref, inspect the next line
        if (refNumber == null) {
            for (i in lines.indices) {
                val line = lines[i]
                if (line.matches(Regex(".*\\b(?:Reference(\\s*Number|\\s*No\\.?)?|Ref(\\s*No\\.?|\\.)|InstaPay\\s*Ref(erence)?)\\b.*", RegexOption.IGNORE_CASE))) {
                    if (i + 1 < lines.size) {
                        val nextCandidate = lines[i + 1].replace(" ", "").trim()
                        if (nextCandidate.length >= 6 && nextCandidate.all { it.isLetterOrDigit() } &&
                            !nextCandidate.contains("erence", ignoreCase = true) &&
                            !nextCandidate.contains("number", ignoreCase = true) &&
                            !nextCandidate.contains("reference", ignoreCase = true)) {
                            refNumber = nextCandidate
                            break
                        }
                    }
                }
            }
        }

        // (g) Generic numeric reference (GCash/Maya 10-16 digits)
        if (refNumber == null) {
            val numRefPattern = Pattern.compile("\\b(\\d{4}\\s*\\d{4}\\s*\\d{4,6})\\b")
            val numMatcher = numRefPattern.matcher(text)
            if (numMatcher.find()) {
                refNumber = numMatcher.group(1)?.replace(" ", "")
            }
        }

        // 4. Detect Amount
        var detectedAmount: Double = expectedAmount ?: 0.0
        val amtPattern = Pattern.compile(
            "(?:Total\\s*Amount|Transfer\\s*Amount|Amount|PHP|₱)[\\s:]*(?:PHP|₱)?\\s*([0-9,]+\\.[0-9]{2})",
            Pattern.CASE_INSENSITIVE
        )
        val amtMatcher = amtPattern.matcher(text)
        if (amtMatcher.find()) {
            val amtStr = amtMatcher.group(1)?.replace(",", "")?.trim()
            amtStr?.toDoubleOrNull()?.let {
                detectedAmount = it
            }
        } else {
            // Standalone PHP 1.00
            val standaloneAmt = Pattern.compile("(?:PHP|₱)\\s*([0-9,]+(?:\\.[0-9]{2})?)", Pattern.CASE_INSENSITIVE)
            val standaloneMatcher = standaloneAmt.matcher(text)
            if (standaloneMatcher.find()) {
                val amtStr = standaloneMatcher.group(1)?.replace(",", "")?.trim()
                amtStr?.toDoubleOrNull()?.let {
                    detectedAmount = it
                }
            }
        }

        // 5. Sender Name & Sender Account Last 4 Extraction
        var senderName = fallbackSender
        var senderAccountLast4 = ""
        val genericAccountKeywords = setOf(
            "CLASSIC SAVINGS", "SAVINGS", "SAVINGS ACCOUNT", "CURRENT ACCOUNT",
            "EPAYCARD", "PAYCARD", "CHECKING", "CHECKING ACCOUNT", "WALLET",
            "ACCOUNT", "SOURCE ACCOUNT", "PESO ACCOUNT", "DEPOSIT ACCOUNT"
        )

        for (i in lines.indices) {
            val line = lines[i]

            // Look for card / account ending (e.g. **** 2894 or 1234 5678 2894)
            val cardMatcher = Pattern.compile("(?:[•*xX]{2,}\\s*|[0-9]{4}\\s+)([0-9]{4})\\b").matcher(line)
            if (cardMatcher.find() && senderAccountLast4.isBlank()) {
                senderAccountLast4 = cardMatcher.group(1) ?: ""
            }

            // Inline check: "From: JHON LLOYD GALVE CABAHUG"
            val inlineMatcher = Pattern.compile(
                "^(?:From|Sender|Sent\\s+by|Mula\\s+kay|Source\\s+Account|Account\\s+Name)[\\s:]+(.+)$",
                Pattern.CASE_INSENSITIVE
            ).matcher(line)
            if (inlineMatcher.find()) {
                val candidate = inlineMatcher.group(1)
                    ?.replace(Regex("(?i)^(MariBank|GCash|Maya|Acct|Classic\\s+Savings).*"), "")
                    ?.replace(Regex("[0-9*]{4,}"), "")
                    ?.trim() ?: ""
                if (candidate.isNotBlank() && candidate.length > 2 && candidate.any { it.isLetter() } &&
                    !genericAccountKeywords.contains(candidate.uppercase())) {
                    senderName = candidate
                    break
                }
            }

            // Next-lines check: "From" followed by account type, card number, then human name
            if (line.equals("From", ignoreCase = true) ||
                line.equals("Sender", ignoreCase = true) ||
                line.equals("Sent by", ignoreCase = true) ||
                line.equals("Mula kay", ignoreCase = true)) {

                for (offset in 1..4) {
                    if (i + offset >= lines.size) break
                    val nextLine = lines[i + offset].trim()

                    val nextCardMatcher = Pattern.compile("(?:[•*xX]{2,}\\s*|[0-9]{4}\\s+)([0-9]{4})\\b").matcher(nextLine)
                    if (nextCardMatcher.find() && senderAccountLast4.isBlank()) {
                        senderAccountLast4 = nextCardMatcher.group(1) ?: ""
                    }

                    val candidate = nextLine
                        .replace(Regex("(?i)^(MariBank|GCash|Maya|Acct|UnionBank).*"), "")
                        .replace(Regex("[0-9*]{4,}"), "")
                        .trim()

                    val upperCandidate = candidate.uppercase()
                    if (candidate.isNotBlank() && candidate.length > 2 && candidate.any { it.isLetter() } &&
                        !genericAccountKeywords.contains(upperCandidate) &&
                        !upperCandidate.startsWith("PHP") &&
                        !upperCandidate.startsWith("TO") &&
                        !upperCandidate.contains("ACCOUNT")) {
                        senderName = candidate
                        break
                    }
                }
                if (senderName.isNotBlank() && senderName != fallbackSender) break
            }
        }

        // 6. Recipient Name & Account Last 4
        var recipientName = ""
        var recipientLast4 = ""
        for (i in lines.indices) {
            val line = lines[i]
            if ((line.equals("To", ignoreCase = true) || line.startsWith("To:", ignoreCase = true)) && i + 1 < lines.size) {
                for (offset in 1..3) {
                    if (i + offset >= lines.size) break
                    val targetLine = lines[i + offset].trim()
                    if (recipientLast4.isBlank()) {
                        val acctMatcher = Pattern.compile("(?:(?:Acct\\.?\\s*No\\.?|Account\\s*No\\.?)[\\s:•*xX-]*|[•*xX]{2,}\\s*|\\b\\d{4}\\s+\\d{4}\\s+)([0-9]{3,4})\\b", Pattern.CASE_INSENSITIVE).matcher(targetLine)
                        if (acctMatcher.find()) {
                            recipientLast4 = acctMatcher.group(1) ?: ""
                        }
                    }
                    if (recipientName.isBlank() && targetLine.any { it.isLetter() } &&
                        !targetLine.equals("Maribank", ignoreCase = true) &&
                        !targetLine.equals("ShopeePay", ignoreCase = true) &&
                        !targetLine.equals("GCash", ignoreCase = true) &&
                        !targetLine.equals("Maya", ignoreCase = true)) {
                        recipientName = targetLine
                    }
                }
            }
        }

        // 7. Date & Time
        var dateTime = ""
        val datePattern = Pattern.compile("(\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4}[,\\s]+\\d{1,2}:\\d{2})")
        val dateMatcher = datePattern.matcher(text)
        if (dateMatcher.find()) {
            dateTime = dateMatcher.group(1) ?: ""
        }

        return ExtractedInfo(
            provider = provider,
            amount = detectedAmount,
            referenceNumber = refNumber,
            dateTime = dateTime,
            recipientName = recipientName,
            recipientNumberLast4 = recipientLast4,
            senderName = senderName.ifBlank { fallbackSender },
            senderAccountLast4 = senderAccountLast4,
            destinationBank = destinationBank,
            transactionStatus = "SUCCESSFUL"
        )
    }

    fun normalizeRef(r: String?): String {
        if (r.isNullOrBlank()) return ""
        return r.replace(Regex("[^A-Za-z0-9]"), "").uppercase()
    }

    fun cleanName(raw: String): String {
        return raw
            .replace(Regex("(?i)^(From|Mula kay|Sender|Sent by|Transfer from|Acct|Account|via)[:\\s]+"), "")
            .replace(Regex("\\b09[0-9*]{9,11}\\b"), "") // Remove phone numbers
            .replace(Regex("\\b\\+?63[0-9*]{10}\\b"), "")
            .replace(Regex("(?i)\\b(MariBank|GCash|Maya|ShopeePay|Bank|Express Send)\\b"), "")
            .replace(Regex("(?i)\\bAccount\\s+[0-9*]+\\b"), "")
            .replace(Regex("[^A-Za-z0-9*\\s.,-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val a = s1.lowercase()
        val b = s2.lowercase()
        val costs = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            costs[0] = i
            var nw = i - 1
            for (j in 1..b.length) {
                val cj = Math.min(
                    1 + Math.min(costs[j], costs[j - 1]),
                    if (a[i - 1] == b[j - 1]) nw else nw + 1
                )
                nw = costs[j]
                costs[j] = cj
            }
        }
        return costs[b.length]
    }

    private fun tokenMatches(sToken: String, rToken: String): Boolean {
        if (sToken.isBlank() || rToken.isBlank()) return false
        val s = sToken.uppercase()
        val r = rToken.uppercase()
        if (s == r) return true

        // If single character initial (e.g. "D" or "D.")
        val sClean = s.replace(".", "").trim()
        val rClean = r.replace(".", "").trim()
        if (sClean.length == 1) {
            return rClean.startsWith(sClean)
        }

        // Masked token containing asterisks (e.g. "JU**", "JH**", "J***N", "LL***")
        if (sClean.contains("*")) {
            val prefix = sClean.takeWhile { it != '*' }
            val suffix = sClean.takeLastWhile { it != '*' }

            if (prefix.isNotEmpty() && !rClean.startsWith(prefix)) {
                return false
            }
            if (suffix.isNotEmpty() && !rClean.endsWith(suffix)) {
                return false
            }
            return rClean.length >= (prefix.length + suffix.length)
        }

        // Unmasked prefix match (e.g. "Alyssa" vs "Aly")
        if (rClean.startsWith(sClean) || sClean.startsWith(rClean)) {
            return true
        }

        // Minor typo tolerance
        if (sClean.length >= 4 && rClean.length >= 4 && levenshteinDistance(sClean, rClean) <= 1) {
            return true
        }

        return false
    }

    fun isSenderNameSimilar(receiptName: String?, notificationSender: String?): NameMatchResult {
        if (receiptName.isNullOrBlank() || notificationSender.isNullOrBlank()) {
            return NameMatchResult(false, "NONE", "Walang sapat na pangalan para suriin.")
        }

        val cleanReceipt = cleanName(receiptName)
        val cleanSender = cleanName(notificationSender)

        if (cleanReceipt.isBlank() || cleanSender.isBlank()) {
            return NameMatchResult(false, "NONE", "Walang sapat na detalye ng pangalan.")
        }

        // 1. Generic sender check (e.g. "Sender (Masked)", "Bank Transfer")
        val genericNames = setOf("SENDER", "SENDER (MASKED)", "MASKED", "BANK TRANSFER", "INSTAPAY", "PESONET", "UNKNOWN")
        if (genericNames.contains(cleanSender.uppercase())) {
            return NameMatchResult(true, "GENERIC", "Generic sender ang nakasaad sa notification ('$cleanSender').")
        }

        // 2. Exact match (case-insensitive)
        if (cleanReceipt.equals(cleanSender, ignoreCase = true)) {
            return NameMatchResult(true, "EXACT", "Eksaktong tugma ang pangalan: '$receiptName'.")
        }

        // 3. Substring match
        if (cleanReceipt.contains(cleanSender, ignoreCase = true) || cleanSender.contains(cleanReceipt, ignoreCase = true)) {
            return NameMatchResult(true, "EXACT", "Tugma ang bahagi ng pangalan sa pagitan ng resibo at notification.")
        }

        val rTokens = cleanReceipt.uppercase().split(Regex("[\\s,.-]+")).filter { it.isNotBlank() }
        val sTokens = cleanSender.uppercase().split(Regex("[\\s,.-]+")).filter { it.isNotBlank() }

        if (rTokens.isEmpty() || sTokens.isEmpty()) {
            return NameMatchResult(false, "NONE", "Walang wastong titik sa pangalan.")
        }

        // 4. Token-by-token matching with masked logic (GCash / Bank masked pattern, e.g. "JU** D." matches "Juan Dela Cruz")
        var rIdx = 0
        var forwardMatched = 0
        for (sTok in sTokens) {
            while (rIdx < rTokens.size && !tokenMatches(sTok, rTokens[rIdx])) {
                rIdx++
            }
            if (rIdx < rTokens.size) {
                forwardMatched++
                rIdx++
            }
        }
        if (forwardMatched == sTokens.size && forwardMatched > 0) {
            return NameMatchResult(
                true,
                "MASKED_LOGIC",
                "Tugma ang masked name ('$cleanSender') sa pangalan sa resibo ('$cleanReceipt') batay sa lohika ng titik at inisyal."
            )
        }

        // Check reverse order (e.g. "Vallejos, Alyssa" vs "A*** V***")
        val rTokensRev = rTokens.asReversed()
        var rRevIdx = 0
        var reverseMatched = 0
        for (sTok in sTokens) {
            while (rRevIdx < rTokensRev.size && !tokenMatches(sTok, rTokensRev[rRevIdx])) {
                rRevIdx++
            }
            if (rRevIdx < rTokensRev.size) {
                reverseMatched++
                rRevIdx++
            }
        }
        if (reverseMatched == sTokens.size && reverseMatched > 0) {
            return NameMatchResult(
                true,
                "MASKED_LOGIC",
                "Tugma ang masked name ('$cleanSender') sa pangalan sa resibo ('$cleanReceipt') (apelyido una)."
            )
        }

        // 5. Initials matching (e.g. "Jhon Lloyd Cabahug" vs "J*** L*** C." or "JH** LL*** C.")
        val rInitials = rTokens.map { it.first() }
        val sInitials = sTokens.mapNotNull { it.firstOrNull { ch -> ch.isLetter() } }
        if (sInitials.isNotEmpty() && sInitials.size <= rInitials.size) {
            var iIdx = 0
            var initCount = 0
            for (sInit in sInitials) {
                while (iIdx < rInitials.size && rInitials[iIdx] != sInit) {
                    iIdx++
                }
                if (iIdx < rInitials.size) {
                    initCount++
                    iIdx++
                }
            }
            if (initCount == sInitials.size && initCount >= 2) {
                return NameMatchResult(
                    true,
                    "INITIALS",
                    "Tugma ang mga inisyal ('${sInitials.joinToString("")}') sa pangalan sa resibo ('$cleanReceipt')."
                )
            }
        }

        // 6. Fuzzy distance check on clean unmasked strings
        if (!cleanSender.contains("*") && !cleanReceipt.contains("*")) {
            val dist = levenshteinDistance(cleanReceipt, cleanSender)
            if (dist <= 2) {
                return NameMatchResult(
                    true,
                    "FUZZY",
                    "Halos eksaktong tugma ang pangalan ('$cleanReceipt' at '$cleanSender')."
                )
            }
        }

        return NameMatchResult(
            false,
            "NONE",
            "Hindi tumutugma ang '$cleanSender' sa '$cleanReceipt'."
        )
    }

    fun isInstitutionalSender(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val clean = name.trim().uppercase()
        val institutions = listOf(
            "UNION BANK", "UNIONBANK", "UBP",
            "BDO", "BANCO DE ORO",
            "BPI", "BANK OF THE PHILIPPINE ISLANDS",
            "METROBANK", "METROPOLITAN BANK",
            "LANDBANK", "LAND BANK",
            "RCBC", "RIZAL COMMERCIAL",
            "SECURITY BANK", "SECB",
            "CHINA BANK", "CHINABANK",
            "PNB", "PHILIPPINE NATIONAL BANK",
            "EASTWEST", "EAST WEST",
            "SEABANK", "MARIBANK", "SHOPEEPAY",
            "GOTYME", "GO TYME",
            "CIMB", "KOMO", "DISKARTECH",
            "MAYA BANK", "PAYMAYA",
            "DBP", "DEVELOPMENT BANK",
            "ASIA UNITED BANK", "AUB",
            "PSBANK", "PHILTRUST", "ROBINSONS BANK",
            "BANK TRANSFER", "INSTAPAY", "PESONET"
        )
        return institutions.any { inst ->
            clean == inst || clean.startsWith("$inst ") || clean.endsWith(" $inst") || clean.contains(inst)
        }
    }

    fun isInstitutionalPaymentMatch(
        payment: Payment,
        extracted: ExtractedInfo,
        receiptText: String = "",
        receiptAmount: Double
    ): NameMatchResult {
        val amtDiff = Math.abs(payment.amount - receiptAmount)
        if (amtDiff > 1.0 && receiptAmount > 0.0) {
            return NameMatchResult(
                false,
                "AMOUNT_MISMATCH",
                "Hindi tugma ang halaga sa resibo (₱${String.format(Locale.US, "%.2f", receiptAmount)}) vs natanggap (₱${String.format(Locale.US, "%.2f", payment.amount)})."
            )
        }

        val senderInst = payment.senderNameMasked.uppercase().trim()
        val provider = extracted.provider.uppercase().trim()
        val lowerText = receiptText.lowercase()

        // 1. Account / Card ending match (e.g. "2894" in payment.referenceNumber matches receipt senderAccountLast4)
        val normPayRef = normalizeRef(payment.referenceNumber)
        val senderLast4 = extracted.senderAccountLast4.trim()
        if (senderLast4.isNotBlank() && normPayRef.isNotBlank() && (normPayRef == senderLast4 || normPayRef.endsWith(senderLast4) || senderLast4.endsWith(normPayRef))) {
            return NameMatchResult(
                true,
                "ACCOUNT_LAST4",
                "Kumpirmadong InstaPay transfer mula sa $senderInst (account ending $senderLast4). Tugma ang card/account sa resibo."
            )
        }

        // 2. Direct Bank Provider Match
        val providerMatches = when {
            senderInst.contains("UNION") && (provider == "UNIONBANK" || lowerText.contains("unionbank") || lowerText.contains("union bank") || lowerText.contains("ub online")) -> true
            senderInst.contains("BDO") && (provider == "BDO" || lowerText.contains("bdo")) -> true
            senderInst.contains("BPI") && (provider == "BPI" || lowerText.contains("bpi")) -> true
            senderInst.contains("METROBANK") && (provider == "METROBANK" || lowerText.contains("metrobank")) -> true
            senderInst.contains("LANDBANK") && (provider == "LANDBANK" || lowerText.contains("landbank")) -> true
            senderInst.contains("GOTYME") && (provider == "GOTYME" || lowerText.contains("gotyme")) -> true
            senderInst.contains("SEABANK") && (provider == "SEABANK" || lowerText.contains("seabank")) -> true
            senderInst.contains("MARIBANK") && (provider == "MARIBANK" || lowerText.contains("maribank")) -> true
            senderInst.contains("MAYA") && (provider == "MAYA" || lowerText.contains("maya")) -> true
            senderInst.contains("GCASH") && (provider == "GCASH" || lowerText.contains("gcash")) -> true
            senderInst.contains("RCBC") && (provider == "RCBC" || lowerText.contains("rcbc")) -> true
            senderInst.contains("CHINA") && (provider == "CHINABANK" || lowerText.contains("chinabank") || lowerText.contains("china bank")) -> true
            senderInst.contains("SECURITY") && (provider == "SECURITYBANK" || lowerText.contains("security bank")) -> true
            provider != "UNKNOWN" && (senderInst.contains(provider) || provider.contains(senderInst)) -> true
            else -> false
        }

        if (providerMatches) {
            return NameMatchResult(
                true,
                "INSTAPAY_BANK",
                "Kumpirmadong Interbank InstaPay transfer mula sa $senderInst. Natanggap sa ${payment.provider.uppercase()} ang halagang ₱${String.format(Locale.US, "%.2f", payment.amount)}."
            )
        }

        // 3. Fallback: Does receipt text explicitly name the sending institution?
        if (lowerText.contains(senderInst.lowercase())) {
            return NameMatchResult(
                true,
                "INSTAPAY_TEXT",
                "Natanggap ang InstaPay transfer mula sa $senderInst."
            )
        }

        return NameMatchResult(
            false,
            "NONE",
            "Hindi tumutugma ang sending bank ('$senderInst') sa resibo."
        )
    }

    suspend fun scanAndVerify(
        receipt: Receipt,
        existingReceipts: List<Receipt> = emptyList(),
        incomingPayments: List<Payment> = emptyList()
    ): Receipt = withContext(Dispatchers.IO) {
        val checks = mutableListOf<CheckInfo>()
        val fraudReasons = mutableListOf<String>()

        // 1. Decode bitmap or reuse existing extracted data
        var rawOcrText = ""
        val extracted: ExtractedInfo
        val imageSource = receipt.imageUrl.ifBlank { receipt.imagePath }
        if (imageSource.isNotBlank()) {
            val bitmap = decodeBitmap(imageSource)
            if (bitmap != null) {
                rawOcrText = scanText(bitmap)
                extracted = if (rawOcrText.isNotBlank()) {
                    parseReceiptText(rawOcrText, receipt.expectedAmount, receipt.buyerName)
                } else {
                    receipt.extracted ?: ExtractedInfo(
                        amount = receipt.expectedAmount ?: 0.0,
                        senderName = receipt.buyerName
                    )
                }
            } else {
                if (receipt.extracted != null) {
                    extracted = receipt.extracted
                } else {
                    checks.add(CheckInfo("Receipt Image Format", false, "Hindi mabasa o blangko ang larawan."))
                    fraudReasons.add("Pekeng Resibo (Fraud): Hindi mabasa o sirang larawan ng resibo.")
                    return@withContext receipt.copy(
                        verdict = ReceiptVerdict.FRAUD_RECEIPT,
                        checks = checks,
                        reasons = fraudReasons,
                        matchMethod = "failed_image"
                    )
                }
            }
        } else {
            extracted = receipt.extracted ?: ExtractedInfo(
                amount = receipt.expectedAmount ?: 0.0,
                senderName = receipt.buyerName
            )
        }

        // 2. Provider Check
        if (extracted.provider != "UNKNOWN") {
            checks.add(CheckInfo("Provider Detected", true, "Kinikilalang resibo mula sa ${extracted.provider}."))
        } else {
            checks.add(CheckInfo("Provider Detected", false, "Hindi kilalang bangko o e-wallet provider."))
            fraudReasons.add("Pekeng Resibo (Fraud): Hindi kinikilalang Philippine bank o e-wallet provider.")
        }

        // 3. Reference Number Format Check
        val rawRef = extracted.referenceNumber?.ifBlank { null }
            ?: receipt.extracted?.referenceNumber?.ifBlank { null }
        val normReceiptRef = normalizeRef(rawRef)

        if (normReceiptRef.isNotBlank() && normReceiptRef.length >= 6) {
            checks.add(CheckInfo("Reference Number Format", true, "Wastong reference number: $rawRef."))
        } else {
            checks.add(CheckInfo("Reference Number Format", false, "Walang wastong reference number na natagpuan."))
        }

        // 4. Duplicate Reference Check (Anti-Replay Scam)
        if (normReceiptRef.isNotBlank()) {
            val duplicate = existingReceipts.any { other ->
                other.id != receipt.id &&
                (normalizeRef(other.extracted?.referenceNumber) == normReceiptRef ||
                 (other.matchMethod == "reference" && other.reasons.any { it.contains(normReceiptRef) }))
            }
            if (duplicate) {
                checks.add(CheckInfo("Duplicate Check (Replay)", false, "Nagamit na ang reference number na ito sa ibang transaksyon."))
                fraudReasons.add("Pekeng Resibo (Fraud): Replay Scam - nagamit na ang reference number ($rawRef) sa ibang order.")
            } else {
                checks.add(CheckInfo("Duplicate Check (Replay)", true, "Natatanging reference number (hindi pa nagagamit)."))
            }
        }

        // 5. Forensics & Tamper Check
        val forensics = receipt.forensics
        if (forensics != null && (forensics.tamperRisk.equals("high", ignoreCase = true) || forensics.editedSigns.isNotEmpty())) {
            checks.add(CheckInfo("Forensics & Anti-Tamper", false, "Nakitaan ng mga palatandaan ng pamemeke o image editing."))
            fraudReasons.add("Pekeng Resibo (Fraud): May mga palatandaan ng Photoshop, fake generator app, o pag-edit.")
        } else {
            checks.add(CheckInfo("Forensics & Anti-Tamper", true, "Walang nakitang palatandaan ng pag-edit o pamemeke."))
        }

        // 6. Receipt Amount Validity Check
        val receiptAmount = if (extracted.amount > 0) extracted.amount else (receipt.expectedAmount ?: 0.0)
        if (receiptAmount > 0) {
            checks.add(CheckInfo("Amount Stated", true, "Halaga sa resibo: ₱${String.format(Locale.US, "%.2f", receiptAmount)}."))
        } else {
            checks.add(CheckInfo("Amount Stated", false, "Hindi wastong halaga ng transaksyon."))
            fraudReasons.add("Pekeng Resibo (Fraud): Walang wastong halaga ng bayad.")
        }

        // 7. ONLINE TRANSACTION: Verify based on Name / Sender (From) and Amount (with Masked Logic Reasoning & Interbank Handling)
        val isOnline = receipt.isOnlineTransaction
        var matchedPayment: Payment? = null
        var isSenderNameVerified = false
        var isAmountVerified = false
        var nameMatchDetail = ""

        if (isOnline) {
            val candidateReceiptNames = listOfNotNull(
                receipt.buyerName.takeIf { it.isNotBlank() },
                extracted.senderName.takeIf { it.isNotBlank() }
            ).distinct()
            val primaryBuyerName = candidateReceiptNames.firstOrNull() ?: "Hindi Kilalang Mamimili"

            var bestCandidate: Payment? = null
            var bestCandidateNameResult: NameMatchResult? = null

            var paymentWithSameAmountDifferentName: Payment? = null
            var paymentWithSameNameDifferentAmount: Payment? = null

            for (payment in incomingPayments) {
                val amtDiff = Math.abs(payment.amount - receiptAmount)
                val isAmtMatch = amtDiff <= 1.0 || receiptAmount <= 0.0

                val isInst = isInstitutionalSender(payment.senderNameMasked)
                val nameResult: NameMatchResult = if (isInst) {
                    isInstitutionalPaymentMatch(payment, extracted, rawOcrText, receiptAmount)
                } else {
                    var bestRes = NameMatchResult(false, "NONE", "")
                    for (cName in candidateReceiptNames) {
                        val r = isSenderNameSimilar(cName, payment.senderNameMasked)
                        if (r.matches) {
                            bestRes = r
                            break
                        }
                    }
                    bestRes
                }

                // Reference number match check for bonus confidence
                val normPayRef = normalizeRef(payment.referenceNumber)
                val isRefMatch = normReceiptRef.isNotBlank() && normPayRef.isNotBlank() && (
                    normPayRef == normReceiptRef ||
                    normPayRef.contains(normReceiptRef) ||
                    normReceiptRef.contains(normPayRef) ||
                    (normPayRef.length >= 8 && normReceiptRef.length >= 8 && normPayRef.takeLast(8) == normReceiptRef.takeLast(8))
                )
                val isAccountMatch = extracted.senderAccountLast4.isNotBlank() && normPayRef.isNotBlank() && (
                    normPayRef == extracted.senderAccountLast4 ||
                    normPayRef.endsWith(extracted.senderAccountLast4)
                )

                if (isAmtMatch && nameResult.matches) {
                    if (bestCandidate == null || isRefMatch || isAccountMatch) {
                        bestCandidate = payment
                        bestCandidateNameResult = nameResult
                        if (isRefMatch || isAccountMatch) break
                    }
                } else if (isAmtMatch && !nameResult.matches) {
                    if (paymentWithSameAmountDifferentName == null) {
                        paymentWithSameAmountDifferentName = payment
                    }
                } else if (!isAmtMatch && nameResult.matches) {
                    if (paymentWithSameNameDifferentAmount == null) {
                        paymentWithSameNameDifferentAmount = payment
                    }
                }
            }

            if (bestCandidate != null) {
                matchedPayment = bestCandidate
                isSenderNameVerified = true
                isAmountVerified = true
                nameMatchDetail = bestCandidateNameResult?.explanation ?: "Tugma ang transaksyon sa natanggap na bayad."

                checks.add(
                    CheckInfo(
                        name = if (isInstitutionalSender(matchedPayment.senderNameMasked)) "Interbank / InstaPay Transfer Verified" else "Sender (From) Name Verified",
                        passed = true,
                        detail = nameMatchDetail
                    )
                )
                checks.add(
                    CheckInfo(
                        name = "Amount Match",
                        passed = true,
                        detail = "Tugma ang halaga (₱${String.format(Locale.US, "%.2f", matchedPayment.amount)}) sa natanggap na notification mula sa ${matchedPayment.provider.uppercase()}."
                    )
                )
                checks.add(
                    CheckInfo(
                        name = "Bank/Wallet Transfer Received",
                        passed = true,
                        detail = "Natanggap ng Resibo ang pumasok na bayad mula sa ${matchedPayment.provider.uppercase()}."
                    )
                )

                if (normReceiptRef.isNotBlank()) {
                    val normPayRef = normalizeRef(matchedPayment.referenceNumber)
                    val isRefTied = normPayRef.isNotBlank() && (normPayRef == normReceiptRef || normPayRef.contains(normReceiptRef))
                    checks.add(
                        CheckInfo(
                            name = "Reference Cross-Reference",
                            passed = isRefTied,
                            detail = if (isRefTied) "Tugma ang reference number ($rawRef)." else "Natanggap ang bayad batay sa Sender Name / Channel at Amount."
                        )
                    )
                }
            } else {
                // No payment matched both Name/Channel and Amount -> FRAUD_RECEIPT
                if (paymentWithSameAmountDifferentName != null) {
                    val differentSender = paymentWithSameAmountDifferentName.senderNameMasked
                    val isDiffInst = isInstitutionalSender(differentSender)
                    val msg = if (isDiffInst) {
                        "Hindi tumutugma ang bangko! Ayon sa notification, pumasok ang pera mula sa '$differentSender', ngunit ang resibo ay mula sa '${extracted.provider}' para kay '$primaryBuyerName'."
                    } else {
                        "Maling nagpadala! Ayon sa notification, si '$differentSender' ang nagbayad, hindi si '$primaryBuyerName'."
                    }
                    checks.add(CheckInfo("Sender (From) / Channel Verified", false, msg))
                    fraudReasons.add("Pekeng Resibo (Fraud): $msg")
                } else if (paymentWithSameNameDifferentAmount != null) {
                    val diffAmt = paymentWithSameNameDifferentAmount.amount
                    val msg = "Maling halaga! Ang sinabi sa resibo ay ₱${String.format(Locale.US, "%.2f", receiptAmount)} ngunit ₱${String.format(Locale.US, "%.2f", diffAmt)} lang ang pumasok mula kay '${paymentWithSameNameDifferentAmount.senderNameMasked}'."
                    checks.add(CheckInfo("Amount Match", false, msg))
                    fraudReasons.add("Pekeng Resibo (Fraud): $msg")
                } else {
                    val msg = "Walang natanggap na notification ang Resibo para kay '$primaryBuyerName' (${extracted.provider}) na may halagang ₱${String.format(Locale.US, "%.2f", receiptAmount)}. Hindi pumasok ang pera sa bank o e-wallet."
                    checks.add(CheckInfo("Bank/Wallet Transfer Received", false, msg))
                    fraudReasons.add("Pekeng Resibo (Fraud): $msg")
                }
            }
        } else {
            // In-Store Transaction / Manual merchant confirmation
            checks.add(
                CheckInfo(
                    name = "In-Store Transaction",
                    passed = true,
                    detail = "Transaksyon sa tindahan (In-Store / Manual cash confirmation)."
                )
            )
        }

        // 8. Binary Verdict: Strict binary (RECEIPT_RECEIVED vs FRAUD_RECEIPT)
        val finalVerdict = if (fraudReasons.isEmpty() && (!isOnline || (isSenderNameVerified && isAmountVerified))) {
            ReceiptVerdict.RECEIPT_RECEIVED
        } else {
            ReceiptVerdict.FRAUD_RECEIPT
        }

        val finalReasons = if (finalVerdict == ReceiptVerdict.RECEIPT_RECEIVED) {
            if (isOnline && matchedPayment != null) {
                val isInst = isInstitutionalSender(matchedPayment.senderNameMasked)
                val summaryLine = if (isInst) {
                    "✅ Kumpirmadong Interbank Transfer: Natanggap mula sa ${matchedPayment.senderNameMasked} ang halagang ₱${String.format(Locale.US, "%.2f", matchedPayment.amount)} sa pamamagitan ng ${matchedPayment.provider.uppercase()}."
                } else {
                    "✅ Kumpirmadong Natanggap: Tugma ang nagpadala (${receipt.buyerName.ifBlank { extracted.senderName }}) at halagang ₱${String.format(Locale.US, "%.2f", matchedPayment.amount)} sa natanggap na ${matchedPayment.provider.uppercase()} notification."
                }
                listOf(summaryLine, nameMatchDetail)
            } else {
                listOf("✅ In-Store Transaction: Kumpirmadong natanggap sa tindahan.")
            }
        } else {
            fraudReasons
        }

        receipt.copy(
            extracted = extracted.copy(referenceNumber = rawRef ?: extracted.referenceNumber),
            expectedAmount = if (extracted.amount > 0) extracted.amount else receipt.expectedAmount,
            verdict = finalVerdict,
            checks = checks,
            reasons = finalReasons,
            matchedPaymentId = matchedPayment?.id ?: receipt.matchedPaymentId,
            matchMethod = if (finalVerdict == ReceiptVerdict.RECEIPT_RECEIVED) {
                if (isOnline) {
                    if (matchedPayment != null && isInstitutionalSender(matchedPayment.senderNameMasked)) "interbank_instapay" else "sender_and_amount"
                } else "manual"
            } else "fraud_detection"
        )
    }
}
