package com.resibo.app.model

data class Business(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val ownerUid: String = "",
    val accounts: List<PaymentAccount> = emptyList()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "slug" to slug,
            "ownerUid" to ownerUid,
            "accounts" to accounts.map { it.toMap() }
        )
    }

    companion object {
        fun fromSnapshot(id: String, data: Map<String, Any?>): Business {
            val accountsList = (data["accounts"] as? List<*>)?.mapNotNull { item ->
                if (item is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    PaymentAccount.fromMap(item as Map<String, Any?>)
                } else null
            } ?: emptyList()

            return Business(
                id = id,
                name = data["name"] as? String ?: "",
                slug = data["slug"] as? String ?: "",
                ownerUid = data["ownerUid"] as? String ?: "",
                accounts = accountsList
            )
        }
    }
}
