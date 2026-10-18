package com.resibo.app.viewmodel

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.resibo.app.model.Payment
import com.resibo.app.simulator.PaymentSimulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class PaymentsUiState {
    object Loading : PaymentsUiState()
    data class Success(val payments: List<Payment>) : PaymentsUiState()
    data class Error(val message: String) : PaymentsUiState()
}

class PaymentsViewModel : ViewModel() {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var snapshotListener: ListenerRegistration? = null

    private val localPayments = mutableListOf<Payment>()

    private val _uiState = MutableStateFlow<PaymentsUiState>(PaymentsUiState.Loading)
    val uiState: StateFlow<PaymentsUiState> = _uiState.asStateFlow()

    private val _isNotificationAccessGranted = MutableStateFlow(false)
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    fun checkNotificationAccess(context: Context) {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        val isGranted = enabledPackages.contains(context.packageName)
        _isNotificationAccessGranted.value = isGranted
    }

    private var currentLoadedBusinessId: String? = null

    fun listenToPayments(businessId: String) {
        if (currentLoadedBusinessId != businessId) {
            currentLoadedBusinessId = businessId
            localPayments.clear()
            _uiState.value = PaymentsUiState.Loading
        }
        snapshotListener?.remove()
        if (localPayments.isNotEmpty()) {
            _uiState.value = PaymentsUiState.Success(localPayments.toList())
        } else {
            _uiState.value = PaymentsUiState.Loading
        }

        try {
            snapshotListener = firestore.collection("businesses")
                .document(businessId)
                .collection("payments")
                .orderBy("receivedAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (localPayments.isNotEmpty()) {
                            _uiState.value = PaymentsUiState.Success(localPayments.toList())
                        } else {
                            _uiState.value = PaymentsUiState.Success(emptyList())
                        }
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { Payment.fromSnapshot(doc.id, it) }
                        }
                        // Remove any local optimistic items that are already in the remote list
                        localPayments.removeAll { local -> list.any { it.id == local.id } }
                        // Deduplicate ONLY by document ID so ALL received transactions stay in POS & Sales
                        val combined = (list + localPayments).distinctBy { it.id }
                        _uiState.value = PaymentsUiState.Success(combined)
                    }
                }
        } catch (_: Exception) {
            _uiState.value = PaymentsUiState.Success(localPayments.toList())
        }
    }

    fun recordPosSale(
        context: Context,
        businessId: String,
        provider: String,
        amount: Double,
        customerName: String,
        referenceNumber: String? = null
    ) {
        val cleanRef = if (!referenceNumber.isNullOrBlank()) {
            referenceNumber.trim()
        } else {
            "POS-${System.currentTimeMillis() % 1000000}"
        }
        val cleanSender = if (customerName.isNotBlank()) customerName.trim() else "Walk-in Customer"
        val posPayment = Payment(
            id = "pos_${System.currentTimeMillis()}",
            provider = provider.lowercase(),
            amount = amount,
            senderNameMasked = cleanSender,
            referenceNumber = cleanRef,
            receivedAt = Timestamp.now(),
            source = "pos"
        )
        val currentList = when (val s = _uiState.value) {
            is PaymentsUiState.Success -> s.payments
            else -> emptyList()
        }
        localPayments.add(0, posPayment)
        val combined = (listOf(posPayment) + currentList).distinctBy { it.id }
        _uiState.value = PaymentsUiState.Success(combined)

        // Announce sale via voice soundbox
        com.resibo.app.audio.PaymentAudioAnnouncer.announcePayment(
            context = context,
            provider = provider,
            amount = amount,
            senderName = cleanSender,
            referenceNumber = cleanRef
        )

        // Save to Firestore
        try {
            firestore.collection("businesses")
                .document(businessId)
                .collection("payments")
                .document(posPayment.id)
                .set(posPayment.toMap())
        } catch (_: Exception) {}
    }

    fun simulateIncomingPayment(
        context: Context,
        businessId: String,
        provider: String,
        amount: Double,
        senderName: String,
        referenceNumber: String
    ) {
        val simPayment = Payment(
            id = "sim_${System.currentTimeMillis()}",
            provider = provider.lowercase(),
            amount = amount,
            senderNameMasked = senderName,
            referenceNumber = referenceNumber,
            receivedAt = Timestamp.now(),
            source = "simulated"
        )
        val currentList = when (val s = _uiState.value) {
            is PaymentsUiState.Success -> s.payments
            else -> emptyList()
        }
        localPayments.add(0, simPayment)
        val combined = (listOf(simPayment) + currentList).distinctBy { it.id }
        _uiState.value = PaymentsUiState.Success(combined)

        PaymentSimulator.simulatePayment(
            context = context,
            provider = provider,
            amount = amount,
            senderName = senderName,
            referenceNumber = referenceNumber,
            businessId = businessId
        )
    }

    fun clear() {
        snapshotListener?.remove()
        snapshotListener = null
        currentLoadedBusinessId = null
        localPayments.clear()
        _uiState.value = PaymentsUiState.Success(emptyList())
    }

    override fun onCleared() {
        super.onCleared()
        snapshotListener?.remove()
        snapshotListener = null
    }
}
