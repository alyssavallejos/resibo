package com.resibo.app.model

data class PaymentAccount(
    val provider: String = "gcash", // "gcash", "maya", "bank"
    val accountName: String = "",
    val last4: String = ""
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "provider" to provider,
            "accountName" to accountName,
            "last4" to last4
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): PaymentAccount {
            return PaymentAccount(
                provider = map["provider"] as? String ?: "gcash",
                accountName = map["accountName"] as? String ?: "",
                last4 = map["last4"] as? String ?: ""
            )
        }
    }
}
