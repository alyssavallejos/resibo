package com.resibo.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.resibo.app.model.Business
import com.resibo.app.model.PaymentAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

sealed class BusinessState {
    object Idle : BusinessState()
    object Loading : BusinessState()
    data class Loaded(val business: Business) : BusinessState()
    object NotFound : BusinessState()
    data class Error(val message: String) : BusinessState()
}

class BusinessViewModel : ViewModel() {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val _businessState = MutableStateFlow<BusinessState>(BusinessState.Idle)
    val businessState: StateFlow<BusinessState> = _businessState.asStateFlow()

    private var currentOwnerUid: String? = null

    fun loadBusinessForOwner(ownerUid: String, context: Context? = null) {
        currentOwnerUid = ownerUid
        _businessState.value = BusinessState.Loading

        // First check local preferences for this specific owner
        if (context != null) {
            val prefs = context.getSharedPreferences("resibo_prefs", Context.MODE_PRIVATE)
            val savedOwner = prefs.getString("biz_owner_uid", null)
            val savedName = prefs.getString("biz_name", null)
            val savedSlug = prefs.getString("biz_slug", null)
            val savedId = prefs.getString("biz_id", null)

            if (savedOwner == ownerUid && !savedName.isNullOrBlank() && !savedSlug.isNullOrBlank() && !savedId.isNullOrBlank()) {
                val biz = Business(
                    id = savedId,
                    name = savedName,
                    slug = savedSlug,
                    ownerUid = ownerUid,
                    accounts = listOf(
                        PaymentAccount(
                            prefs.getString("biz_acc_provider", "gcash") ?: "gcash",
                            prefs.getString("biz_acc_name", "") ?: "",
                            prefs.getString("biz_acc_last4", "") ?: ""
                        )
                    )
                )
                syncToCloud(biz)
                _businessState.value = BusinessState.Loaded(biz)
                return
            }
        }

        try {
            firestore.collection("businesses")
                .whereEqualTo("ownerUid", ownerUid)
                .limit(1)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        val doc = snapshot.documents[0]
                        val biz = Business.fromSnapshot(doc.id, doc.data ?: emptyMap())
                        if (context != null) {
                            val prefs = context.getSharedPreferences("resibo_prefs", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("biz_id", biz.id)
                                .putString("biz_owner_uid", ownerUid)
                                .putString("biz_name", biz.name)
                                .putString("biz_slug", biz.slug)
                                .apply()
                        }
                        _businessState.value = BusinessState.Loaded(biz)
                    } else {
                        _businessState.value = BusinessState.NotFound
                    }
                }
                .addOnFailureListener {
                    _businessState.value = BusinessState.NotFound
                }
        } catch (_: Exception) {
            _businessState.value = BusinessState.NotFound
        }
    }

    fun generateSlug(name: String): String {
        return name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")
    }

    fun syncToCloud(biz: Business) {
        try {
            val businessData = mapOf(
                "name" to biz.name.trim(),
                "slug" to biz.slug.trim().lowercase(),
                "ownerUid" to biz.ownerUid,
                "accounts" to biz.accounts.map { it.toMap() }
            )
            val docId = if (biz.id.isNotBlank()) biz.id else "biz_${biz.slug}"
            firestore.collection("businesses").document(docId).set(businessData, com.google.firebase.firestore.SetOptions.merge())
        } catch (_: Exception) {}
    }

    fun saveBusiness(
        name: String,
        slugInput: String,
        accounts: List<PaymentAccount>,
        context: Context? = null,
        onSuccess: (Business) -> Unit
    ) {
        val uid = currentOwnerUid ?: "owner_${System.currentTimeMillis()}"
        val finalSlug = if (slugInput.isBlank()) generateSlug(name) else slugInput.trim().lowercase()
        val docId = "biz_$finalSlug"

        _businessState.value = BusinessState.Loading

        val created = Business(
            id = docId,
            name = name.trim(),
            slug = finalSlug,
            ownerUid = uid,
            accounts = accounts
        )

        // Save locally first for instant offline responsiveness
        if (context != null) {
            val prefs = context.getSharedPreferences("resibo_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("active_business_id", docId)
                .putString("biz_id", docId)
                .putString("biz_owner_uid", uid)
                .putString("biz_name", name.trim())
                .putString("biz_slug", finalSlug)
                .putString("biz_acc_provider", accounts.firstOrNull()?.provider ?: "gcash")
                .putString("biz_acc_name", accounts.firstOrNull()?.accountName ?: "")
                .putString("biz_acc_last4", accounts.firstOrNull()?.last4 ?: "")
                .apply()
        }

        val businessData = mapOf(
            "name" to name.trim(),
            "slug" to finalSlug,
            "ownerUid" to uid,
            "accounts" to accounts.map { it.toMap() }
        )

        // Sync directly to Cloud Firestore
        firestore.collection("businesses").document(docId)
            .set(businessData, com.google.firebase.firestore.SetOptions.merge())
            .addOnCompleteListener {
                _businessState.value = BusinessState.Loaded(created)
                onSuccess(created)
            }
    }

    fun clearBusiness(context: Context? = null) {
        currentOwnerUid = null
        _businessState.value = BusinessState.Idle
        if (context != null) {
            val prefs = context.getSharedPreferences("resibo_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .remove("biz_id")
                .remove("biz_owner_uid")
                .remove("biz_name")
                .remove("biz_slug")
                .remove("biz_acc_provider")
                .remove("biz_acc_name")
                .remove("biz_acc_last4")
                .remove("active_business_id")
                .apply()
        }
    }

    fun getBuyerLink(slug: String): String {
        return "https://resibo-ph.web.app/pay/$slug"
    }
}
