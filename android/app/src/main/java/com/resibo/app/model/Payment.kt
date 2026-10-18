package com.resibo.app.model

import com.google.firebase.Timestamp

data class Payment(
    val id: String = "",
    val provider: String = "gcash",
    val amount: Double = 0.0,
    val senderNameMasked: String = "",
    val referenceNumber: String? = null,
    val receivedAt: Timestamp? = null,
    val source: String = "notification" // "notification" | "sms" | "manual"
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "provider" to provider,
            "amount" to amount,
            "senderNameMasked" to senderNameMasked,
            "referenceNumber" to referenceNumber,
            "receivedAt" to receivedAt,
            "source" to source
        )
    }

    companion object {
        fun fromSnapshot(id: String, data: Map<String, Any?>): Payment {
            val amountNum = data["amount"] as? Number
            val timestamp = (data["receivedAt"] as? Timestamp)
                ?: (data["timestamp"] as? Timestamp)
                ?: (data["createdAt"] as? Timestamp)
                ?: Timestamp.now()
            return Payment(
                id = id,
                provider = data["provider"] as? String ?: "gcash",
                amount = amountNum?.toDouble() ?: 0.0,
                senderNameMasked = data["senderNameMasked"] as? String ?: "",
                referenceNumber = data["referenceNumber"] as? String,
                receivedAt = timestamp,
                source = data["source"] as? String ?: "notification"
            )
        }
    }
}
