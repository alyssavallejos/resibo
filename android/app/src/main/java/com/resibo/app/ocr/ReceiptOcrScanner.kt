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

        // 1. Detect Provider
        var provider = "UNKNOWN"
        val lowerText = text.lowercase()
        when {
            lowerText.contains("maribank") || lowerText.contains("shopeepay") -> provider = "MARIBANK"
            lowerText.contains("seabank") -> provider = "SEABANK"
            lowerText.contains("gcash") -> provider = "GCASH"
            lowerText.contains("maya") || lowerText.contains("paymaya") -> provider = "MAYA"
            lowerText.contains("bpi") -> provider = "BPI"
            lowerText.contains("bdo") -> provider = "BDO"
            lowerText.contains("unionbank") -> provider = "UNIONBANK"
            lowerText.contains("gotyme") -> provider = "GOTYME"
            lowerText.contains("dbp") -> provider = "DBP"
            lowerText.contains("landbank") -> provider = "LANDBANK"
            lowerText.contains("metrobank") -> provider = "METROBANK"
            lowerText.contains("rcbc") -> provider = "RCBC"
        }

        // 2. Detect Reference Number
        var refNumber: String? = null

        // (a) Common regex: "Reference Number: BC550000034535407831", "Ref No. 1029 3847 5610"
        val refPattern = Pattern.compile(
            "(?:Reference[ \\t]*(?:Number|No\\.?|ID)|Ref(?:\\.|[ \\t]*No\\.?|[ \\t]*ID)?|Trans(?:action)?[ \\t]*ID)[ \\t:]*([A-Za-z0-9 \\t]{8,30})",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = refPattern.matcher(text)
        if (matcher.find()) {
            val candidate = matcher.group(1)?.substringBefore("\n")?.substringBefore("\r")?.replace(" ", "")?.trim()
            if (!candidate.isNullOrBlank() && candidate.length >= 8) {
                refNumber = candidate
            }
        }

        // (b) MariBank specific prefix: BC followed by 16-20 digits
        if (refNumber == null) {
            val mariRefPattern = Pattern.compile("\\b(BC\\d{14,22})\\b", Pattern.CASE_INSENSITIVE)
            val mariMatcher = mariRefPattern.matcher(text)
            if (mariMatcher.find()) {
                refNumber = mariMatcher.group(1)?.uppercase()
            }
        }

        // (c) Line-based lookup: If a line says "Reference Number", inspect the next line
        if (refNumber == null) {
            for (i in lines.indices) {
                val line = lines[i]
                if (line.contains("Reference", ignoreCase = true) || line.contains("Ref No", ignoreCase = true)) {
                    if (i + 1 < lines.size) {
                        val nextCandidate = lines[i + 1].replace(" ", "").trim()
                        if (nextCandidate.length >= 8 && nextCandidate.all { it.isLetterOrDigit() }) {
                            refNumber = nextCandidate
                            break
                        }
                    }
                }
            }
        }

        // (d) Generic numeric reference (GCash/Maya 10-16 digits)
        if (refNumber == null) {
            val numRefPattern = Pattern.compile("\\b(\\d{4}\\s*\\d{4}\\s*\\d{4,6})\\b")
            val numMatcher = numRefPattern.matcher(text)
            if (numMatcher.find()) {
                refNumber = numMatcher.group(1)?.replace(" ", "")
            }
        }

        // 3. Detect Amount
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

        // 4. Sender Name Extraction (Same-line and Next-line detection)
        var senderName = fallbackSender
        for (i in lines.indices) {
            val line = lines[i]
            // Inline check: "From: JUAN DELA CRUZ"
            val inlineMatcher = Pattern.compile(
                "^(?:From|Sender|Sent\\s+by|Mula\\s+kay|Source\\s+Account|Account\\s+Name)[\\s:]+(.+)$",
                Pattern.CASE_INSENSITIVE
            ).matcher(line)
            if (inlineMatcher.find()) {
                val candidate = inlineMatcher.group(1)
                    ?.replace(Regex("^(MariBank|GCash|Maya|Acct).*"), "")
                    ?.replace(Regex("[0-9*]{4,}"), "")
                    ?.trim() ?: ""
                if (candidate.isNotBlank() && candidate.length > 2) {
                    senderName = candidate
                    break
                }
            }
            // Next-line check: "From\nJUAN DELA CRUZ"
            if ((line.equals("From", ignoreCase = true) ||
                 line.equals("Sender", ignoreCase = true) ||
                 line.equals("Sent by", ignoreCase = true) ||
                 line.equals("Mula kay", ignoreCase = true)) && i + 1 < lines.size) {
                val candidate = lines[i + 1]
                    .replace(Regex("^(MariBank|GCash|Maya|Acct).*"), "")
                    .replace(Regex("[0-9*]{4,}"), "")
                    .trim()
                if (candidate.isNotBlank() && candidate.length > 2) {
                    senderName = candidate
                    break
                }
            }
        }

        // 5. Recipient Name & Account Last 4
        var recipientName = ""
        var recipientLast4 = ""
        for (i in lines.indices) {
            if (lines[i].equals("To", ignoreCase = true) && i + 1 < lines.size) {
                recipientName = lines[i + 1]
            }
            val acctMatcher = Pattern.compile("(?:(?:Acct\\.?\\s*No\\.?|Account\\s*No\\.?)[\\s:•*xX-]*|[•*xX]{2,}\\s*)([0-9]{4,})", Pattern.CASE_INSENSITIVE).matcher(lines[i])
            if (acctMatcher.find()) {
                val acct = acctMatcher.group(1) ?: ""
                if (acct.length >= 4) {
                    recipientLast4 = acct.takeLast(4)
                }
            }
        }

        // 6. Date & Time
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

    suspend fun scanAndVerify(
        receipt: Receipt,
        existingReceipts: List<Receipt> = emptyList(),
        incomingPayments: List<Payment> = emptyList()
    ): Receipt = withContext(Dispatchers.IO) {
        val checks = mutableListOf<CheckInfo>()
        val fraudReasons = mutableListOf<String>()

        // 1. Decode bitmap or reuse existing extracted data
        val extracted: ExtractedInfo
        val imageSource = receipt.imageUrl.ifBlank { receipt.imagePath }
        if (imageSource.isNotBlank()) {
            val bitmap = decodeBitmap(imageSource)
            if (bitmap != null) {
                val rawOcrText = scanText(bitmap)
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

        // 7. ONLINE TRANSACTION: Verify based on Name / Sender (From) and Amount (with Masked Logic Reasoning)
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

            // Look for matching payment in incomingPayments:
            var bestCandidate: Payment? = null
            var bestCandidateNameResult: NameMatchResult? = null

            // Tracking potential mismatches to provide clear and actionable fraud reasons
            var paymentWithSameAmountDifferentName: Payment? = null
            var paymentWithSameNameDifferentAmount: Payment? = null

            for (payment in incomingPayments) {
                val amtDiff = Math.abs(payment.amount - receiptAmount)
                val isAmtMatch = amtDiff <= 1.0 || receiptAmount <= 0.0

                // Test if any candidate name matches payment.senderNameMasked using masked logical reasoning
                var nameResult = NameMatchResult(false, "NONE", "")
                for (cName in candidateReceiptNames) {
                    val r = isSenderNameSimilar(cName, payment.senderNameMasked)
                    if (r.matches) {
                        nameResult = r
                        break
                    }
                }

                // Reference number match check for bonus confidence
                val normPayRef = normalizeRef(payment.referenceNumber)
                val isRefMatch = normReceiptRef.isNotBlank() && normPayRef.isNotBlank() && (
                    normPayRef == normReceiptRef ||
                    normPayRef.contains(normReceiptRef) ||
                    normReceiptRef.contains(normPayRef) ||
                    (normPayRef.length >= 8 && normReceiptRef.length >= 8 && normPayRef.takeLast(8) == normReceiptRef.takeLast(8))
                )

                if (isAmtMatch && nameResult.matches) {
                    // Both Name and Amount match!
                    if (bestCandidate == null || isRefMatch) {
                        bestCandidate = payment
                        bestCandidateNameResult = nameResult
                        if (isRefMatch) break // Highest confidence match
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
                nameMatchDetail = bestCandidateNameResult?.explanation ?: "Tugma ang nagpadala sa natanggap na bayad."

                checks.add(
                    CheckInfo(
                        name = "Sender (From) Name Verified",
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
                            detail = if (isRefTied) "Tugma ang reference number ($rawRef)." else "Natanggap ang bayad batay sa Sender Name at Amount."
                        )
                    )
                }
            } else {
                // No payment matched both Name and Amount -> FRAUD_RECEIPT
                checks.add(
                    CheckInfo(
                        name = "Sender (From) Name Verified",
                        passed = false,
                        detail = if (paymentWithSameAmountDifferentName != null) {
                            "Hindi tumutugma ang nagpadala! Ayon sa notification, si '${paymentWithSameAmountDifferentName.senderNameMasked}' ang nagbayad, hindi si '$primaryBuyerName'."
                        } else {
                            "Walang natanggap na notification mula kay '$primaryBuyerName'."
                        }
                    )
                )
                checks.add(
                    CheckInfo(
                        name = "Amount Match",
                        passed = false,
                        detail = if (paymentWithSameNameDifferentAmount != null) {
                            "Maling halaga! ₱${String.format(Locale.US, "%.2f", receiptAmount)} sa resibo vs ₱${String.format(Locale.US, "%.2f", paymentWithSameNameDifferentAmount.amount)} sa notification."
                        } else {
                            "Walang natanggap na pumasok na bayad para sa halagang ₱${String.format(Locale.US, "%.2f", receiptAmount)}."
                        }
                    )
                )
                checks.add(
                    CheckInfo(
                        name = "Bank/Wallet Transfer Received",
                        passed = false,
                        detail = "Walang kaukulang pumasok na pera sa telepono ng may-ari para sa nagpadala at halagang ito."
                    )
                )

                if (paymentWithSameAmountDifferentName != null) {
                    fraudReasons.add("Pekeng Resibo (Fraud): Maling nagpadala! Ang pumasok na bayad sa notification ay mula kay '${paymentWithSameAmountDifferentName.senderNameMasked}' ngunit ang resibo ay para kay '$primaryBuyerName'.")
                } else if (paymentWithSameNameDifferentAmount != null) {
                    fraudReasons.add("Pekeng Resibo (Fraud): Maling halaga! Ang sinabi sa resibo ay ₱${String.format(Locale.US, "%.2f", receiptAmount)} ngunit ₱${String.format(Locale.US, "%.2f", paymentWithSameNameDifferentAmount.amount)} lang ang pumasok mula kay '${paymentWithSameNameDifferentAmount.senderNameMasked}'.")
                } else {
                    fraudReasons.add("Pekeng Resibo (Fraud): Walang natanggap na notification ang Resibo para kay '$primaryBuyerName' na may halagang ₱${String.format(Locale.US, "%.2f", receiptAmount)}. Hindi pumasok ang pera sa bank o e-wallet.")
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
                listOf(
                    "✅ Kumpirmadong Natanggap: Tugma ang nagpadala (${receipt.buyerName.ifBlank { extracted.senderName }}) at halagang ₱${String.format(Locale.US, "%.2f", matchedPayment.amount)} sa natanggap na ${matchedPayment.provider.uppercase()} notification.",
                    nameMatchDetail
                )
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
            matchMethod = if (finalVerdict == ReceiptVerdict.RECEIPT_RECEIVED) (if (isOnline) "sender_and_amount" else "manual") else "fraud_detection"
        )
    }
}
