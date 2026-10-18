package com.resibo.app.model

import com.google.firebase.Timestamp

enum class ReceiptVerdict(val serialized: String, val label: String) {
    RECEIPT_RECEIVED("verified", "Receipt Received"),
    FRAUD_RECEIPT("fake", "Fraud Receipt");

    val isReceived: Boolean get() = this == RECEIPT_RECEIVED
    val isFraud: Boolean get() = this == FRAUD_RECEIPT

    companion object {
        // Interoperability aliases
        val VERIFIED: ReceiptVerdict get() = RECEIPT_RECEIVED
        val FAKE: ReceiptVerdict get() = FRAUD_RECEIPT

        fun fromString(str: String?): ReceiptVerdict {
            return when (str?.lowercase()?.trim()) {
                "fake", "fraud", "fraud_receipt", "tampered", "invalid" -> FRAUD_RECEIPT
                else -> RECEIPT_RECEIVED
            }
        }
    }
}

data class CheckInfo(
    val name: String = "",
    val passed: Boolean = true,
    val detail: String = ""
)

data class ForensicsInfo(
    val editedSigns: List<String> = emptyList(),
    val generatorAppSigns: List<String> = emptyList(),
    val tamperRisk: String = "low", // "low" | "medium" | "high"
    val explanation: String = ""
)

data class ExtractedInfo(
    val provider: String = "gcash",
    val amount: Double = 0.0,
    val referenceNumber: String? = null,
    val dateTime: String = "",
    val recipientName: String = "",
    val recipientNumberLast4: String = "",
    val senderName: String = "",
    val senderAccountLast4: String = "",
    val destinationBank: String = "",
    val transactionStatus: String = "successful"
)

data class Receipt(
    val id: String = "",
    val imagePath: String = "",
    val imageUrl: String = "",
    val buyerName: String = "",
    val note: String = "",
    val expectedAmount: Double? = null,
    val verdict: ReceiptVerdict = ReceiptVerdict.RECEIPT_RECEIVED,
    val reasons: List<String> = emptyList(),
    val checks: List<CheckInfo> = emptyList(),
    val forensics: ForensicsInfo? = null,
    val extracted: ExtractedInfo? = null,
    val matchedPaymentId: String? = null,
    val matchMethod: String? = null, // "reference", "amount_time", "manual"
    val channel: String = "online", // "online" | "in_store" | "pos"
    val createdAt: Timestamp? = null
) {
    val isOnlineTransaction: Boolean
        get() = (channel.equals("online", ignoreCase = true) || channel.isBlank()) && matchMethod != "manual"
    companion object {
        fun fromSnapshot(id: String, data: Map<String, Any?>): Receipt {
            val amountNum = data["expectedAmount"] as? Number
            @Suppress("UNCHECKED_CAST")
            val reasonsList = (data["reasons"] as? List<String>) ?: emptyList()

            // Parse checks list
            val checksList = (data["checks"] as? List<*>)?.mapNotNull { item ->
                if (item is Map<*, *>) {
                    CheckInfo(
                        name = item["name"] as? String ?: "",
                        passed = item["passed"] as? Boolean ?: false,
                        detail = item["detail"] as? String ?: ""
                    )
                } else null
            } ?: emptyList()

            // Parse forensics
            val forensicsMap = data["forensics"] as? Map<*, *>
            val forensicsInfo = forensicsMap?.let {
                @Suppress("UNCHECKED_CAST")
                ForensicsInfo(
                    editedSigns = (it["edited_signs"] as? List<String>) ?: emptyList(),
                    generatorAppSigns = (it["generator_app_signs"] as? List<String>) ?: emptyList(),
                    tamperRisk = it["tamper_risk"] as? String ?: "low",
                    explanation = it["explanation"] as? String ?: ""
                )
            }

            // Parse extracted
            val extractedMap = data["extracted"] as? Map<*, *>
            val extractedInfo = extractedMap?.let {
                ExtractedInfo(
                    provider = it["provider"] as? String ?: "gcash",
                    amount = (it["amount"] as? Number)?.toDouble() ?: (amountNum?.toDouble() ?: 0.0),
                    referenceNumber = it["reference_number"] as? String,
                    dateTime = it["date_time"] as? String ?: "",
                    recipientName = it["recipient_name"] as? String ?: "",
                    recipientNumberLast4 = it["recipient_number_last4"] as? String ?: "",
                    senderName = it["sender_name"] as? String ?: "",
                    senderAccountLast4 = it["sender_account_last4"] as? String ?: "",
                    destinationBank = it["destination_bank"] as? String ?: "",
                    transactionStatus = it["transaction_status"] as? String ?: "successful"
                )
            }

            return Receipt(
                id = id,
                imagePath = data["imagePath"] as? String ?: "",
                imageUrl = data["imageUrl"] as? String ?: "",
                buyerName = data["buyerName"] as? String ?: "Unknown Buyer",
                note = data["note"] as? String ?: "",
                expectedAmount = extractedInfo?.amount ?: amountNum?.toDouble(),
                verdict = ReceiptVerdict.fromString(data["verdict"] as? String),
                reasons = reasonsList,
                checks = checksList,
                forensics = forensicsInfo,
                extracted = extractedInfo,
                matchedPaymentId = data["matchedPaymentId"] as? String,
                matchMethod = data["matchMethod"] as? String,
                channel = data["channel"] as? String ?: "online",
                createdAt = data["createdAt"] as? Timestamp
            )
        }
    }
}
