package com.resibo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.resibo.app.model.*
import com.resibo.app.ocr.ReceiptOcrScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ReceiptUiState {
    object Loading : ReceiptUiState()
    data class Success(val receipts: List<Receipt>) : ReceiptUiState()
    data class Error(val message: String) : ReceiptUiState()
}

class ReceiptsViewModel : ViewModel() {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var receiptsListener: ListenerRegistration? = null
    private var paymentsListener: ListenerRegistration? = null

    // Cache of receipts and incoming bank notifications
    private val localReceipts = mutableListOf<Receipt>()
    private val currentPayments = mutableListOf<Payment>()

    private val _uiState = MutableStateFlow<ReceiptUiState>(ReceiptUiState.Loading)
    val uiState: StateFlow<ReceiptUiState> = _uiState.asStateFlow()

    fun getPayments(): List<Payment> {
        synchronized(currentPayments) {
            return currentPayments.toList()
        }
    }

    fun listenToReceipts(businessId: String) {
        receiptsListener?.remove()
        paymentsListener?.remove()

        if (localReceipts.isNotEmpty()) {
            _uiState.value = ReceiptUiState.Success(localReceipts.toList())
        } else {
            _uiState.value = ReceiptUiState.Loading
        }

        // 1. Listen to incoming bank transfer notifications collected by PaymentNotificationListener
        try {
            paymentsListener = firestore.collection("businesses")
                .document(businessId)
                .collection("payments")
                .orderBy("receivedAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val paymentsList = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { Payment.fromSnapshot(doc.id, it) }
                        }
                        synchronized(currentPayments) {
                            currentPayments.clear()
                            currentPayments.addAll(paymentsList)
                        }

                        // Re-verify online receipts against newly arrived notifications
                        reVerifyAllReceipts(businessId)
                    }
                }
        } catch (_: Exception) {}

        // 2. Listen to receipts submitted by buyers
        try {
            receiptsListener = firestore.collection("businesses")
                .document(businessId)
                .collection("receipts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        _uiState.value = ReceiptUiState.Success(localReceipts.toList())
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { Receipt.fromSnapshot(doc.id, it) }
                        }
                        synchronized(localReceipts) {
                            localReceipts.clear()
                            localReceipts.addAll(list)
                        }
                        _uiState.value = ReceiptUiState.Success(list)

                        // Re-verify online receipts against bank transfer notifications
                        reVerifyAllReceipts(businessId)
                    }
                }
        } catch (_: Exception) {
            _uiState.value = ReceiptUiState.Success(localReceipts.toList())
        }
    }

    private fun reVerifyAllReceipts(businessId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val receiptsCopy: List<Receipt>
            val paymentsCopy: List<Payment>
            synchronized(localReceipts) { receiptsCopy = localReceipts.toList() }
            synchronized(currentPayments) { paymentsCopy = currentPayments.toList() }

            for (receipt in receiptsCopy) {
                // Skip manual in-store override
                if (receipt.matchMethod == "manual") continue

                val verified = ReceiptOcrScanner.scanAndVerify(
                    receipt = receipt,
                    existingReceipts = receiptsCopy,
                    incomingPayments = paymentsCopy
                )

                if (verified.verdict != receipt.verdict ||
                    verified.reasons != receipt.reasons ||
                    verified.checks != receipt.checks ||
                    verified.matchedPaymentId != receipt.matchedPaymentId ||
                    (receipt.extracted == null && verified.extracted != null)
                ) {
                    saveVerifiedReceipt(businessId, verified)
                }
            }
        }
    }

    fun overrideReceipt(businessId: String, receiptId: String, action: String) {
        val newVerdict = if (action == "mark_received") ReceiptVerdict.RECEIPT_RECEIVED else ReceiptVerdict.FRAUD_RECEIPT
        val newReason = if (action == "mark_received") {
            "Transaksyon sa tindahan: Manually marked as received by merchant (In-Store / Manual override)."
        } else {
            "Manually marked as fraud by merchant (Manual override)."
        }

        synchronized(localReceipts) {
            val index = localReceipts.indexOfFirst { it.id == receiptId }
            if (index != -1) {
                val updated = localReceipts[index].copy(
                    verdict = newVerdict,
                    matchMethod = "manual",
                    channel = if (action == "mark_received") "in_store" else localReceipts[index].channel,
                    reasons = listOf(newReason)
                )
                localReceipts[index] = updated
                _uiState.value = ReceiptUiState.Success(localReceipts.toList())
            }
        }

        try {
            firestore.collection("businesses")
                .document(businessId)
                .collection("receipts")
                .document(receiptId)
                .update(
                    mapOf(
                        "verdict" to if (action == "mark_received") "verified" else "fake",
                        "matchMethod" to "manual",
                        "channel" to if (action == "mark_received") "in_store" else "online",
                        "reasons" to listOf(newReason)
                    )
                )
        } catch (_: Exception) {}
    }

    fun saveVerifiedReceipt(businessId: String, receipt: Receipt) {
        synchronized(localReceipts) {
            val index = localReceipts.indexOfFirst { it.id == receipt.id }
            if (index != -1) {
                localReceipts[index] = receipt
                _uiState.value = ReceiptUiState.Success(localReceipts.toList())
            }
        }

        try {
            val ext = receipt.extracted
            val extractedMap = if (ext != null) {
                mapOf(
                    "provider" to ext.provider,
                    "amount" to ext.amount,
                    "reference_number" to ext.referenceNumber,
                    "date_time" to ext.dateTime,
                    "recipient_name" to ext.recipientName,
                    "recipient_number_last4" to ext.recipientNumberLast4,
                    "sender_name" to ext.senderName,
                    "transaction_status" to ext.transactionStatus
                )
            } else null

            val checksMap = receipt.checks.map {
                mapOf(
                    "name" to it.name,
                    "passed" to it.passed,
                    "detail" to it.detail
                )
            }

            val updates = mutableMapOf<String, Any?>(
                "verdict" to if (receipt.verdict == ReceiptVerdict.FRAUD_RECEIPT) "fake" else "verified",
                "reasons" to receipt.reasons,
                "checks" to checksMap,
                "channel" to receipt.channel,
                "matchMethod" to (receipt.matchMethod ?: "reference"),
                "matchedPaymentId" to receipt.matchedPaymentId
            )
            if (extractedMap != null) {
                updates["extracted"] = extractedMap
            }
            if (receipt.expectedAmount != null) {
                updates["expectedAmount"] = receipt.expectedAmount
            }

            firestore.collection("businesses")
                .document(businessId)
                .collection("receipts")
                .document(receipt.id)
                .update(updates)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        receiptsListener?.remove()
        receiptsListener = null
        paymentsListener?.remove()
        paymentsListener = null
    }
}
