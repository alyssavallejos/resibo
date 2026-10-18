package com.resibo.app.service

import android.app.Notification
import android.content.Context
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.resibo.app.audio.PaymentAudioAnnouncer
import com.resibo.app.parser.ParsedPayment
import com.resibo.app.parser.PaymentParsers
import java.util.concurrent.ConcurrentHashMap

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "ResiboNotification"
        const val SIMULATED_CHANNEL_ID = "resibo_simulated_channel"

        // Active business ID set by MainActivity / ViewModel
        var currentBusinessId: String? = null

        // In-memory deduplication cache: Key -> Timestamp
        private val recentPayments = ConcurrentHashMap<String, Long>()
        private const val DEDUP_WINDOW_MS = 60_000L // 1 minute

        fun isRecentDuplicate(provider: String, amount: Double, refNumber: String?): Boolean {
            val now = System.currentTimeMillis()
            // Clean up old entries
            recentPayments.entries.removeIf { now - it.value > DEDUP_WINDOW_MS }

            val key = if (!refNumber.isNullOrBlank()) {
                "$provider:$refNumber"
            } else {
                "$provider:$amount:${now / DEDUP_WINDOW_MS}"
            }

            if (recentPayments.containsKey(key)) {
                return true
            }
            recentPayments[key] = now
            return false
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // STRICT REQUIREMENT: The FIRST line of onNotificationPosted must return immediately
        // for any package not on the allowlist — no parsing, no storage, no logging.
        if (sbn == null || !isPackageAllowed(sbn.packageName)) return

        try {
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

            val pkg = sbn.packageName
            val defaultSmsPkg = try {
                Telephony.Sms.getDefaultSmsPackage(applicationContext)
            } catch (e: Exception) {
                null
            }

            val isSms = pkg == defaultSmsPkg || pkg.contains("messaging", ignoreCase = true)
            val isEmail = pkg == PaymentParsers.PKG_GMAIL || pkg == PaymentParsers.PKG_EMAIL
            var senderId: String? = null

            // For SMS, only allow bank or e-wallet sender names
            if (isSms) {
                val candidateSender = title.trim().uppercase()
                val isSenderAllowed = PaymentParsers.ALLOWED_SMS_SENDERS.any { allowed ->
                    candidateSender.contains(allowed)
                }
                if (!isSenderAllowed) {
                    return
                }
                senderId = candidateSender
            }

            // For Email alerts (Gmail/Mail), ensure it is from a recognized bank or wallet
            if (isEmail) {
                val isBankingEmail = PaymentParsers.ALLOWED_SMS_SENDERS.any { allowed ->
                    title.contains(allowed, ignoreCase = true) || text.contains(allowed, ignoreCase = true)
                }
                if (!isBankingEmail) {
                    return
                }
                senderId = if (title.contains("MariBank", ignoreCase = true)) "MARIBANK" else null
            }

            // Parse payment
            val parsed: ParsedPayment = PaymentParsers.parse(
                packageName = pkg,
                title = title,
                text = text,
                senderId = senderId
            ) ?: return

            // Deduplication
            if (isRecentDuplicate(parsed.provider, parsed.amount, parsed.referenceNumber)) {
                Log.d(TAG, "Ignored duplicate payment: ${parsed.provider} ₱${parsed.amount}")
                return
            }

            // Announce incoming payment out loud (100% free built-in soundbox)
            PaymentAudioAnnouncer.announcePayment(
                context = applicationContext,
                provider = parsed.provider,
                amount = parsed.amount,
                senderName = parsed.senderNameMasked,
                referenceNumber = parsed.referenceNumber
            )

            // Save to Firestore if business is registered
            savePaymentToLedger(
                payment = parsed,
                source = when {
                    isSms -> "sms"
                    isEmail -> "email"
                    else -> "notification"
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification", e)
        }
    }

    private fun isPackageAllowed(packageName: String): Boolean {
        val lower = packageName.lowercase()
        // Check if allowlisted bank or e-wallet app
        if (PaymentParsers.ALLOWED_PACKAGES.contains(packageName)) return true

        // Allow any package that belongs to recognized banks or digital wallets
        if (lower.contains("seabank") ||
            lower.contains("maribank") ||
            lower.contains("gcash") ||
            lower.contains("paymaya") ||
            lower.contains("maya") ||
            lower.contains("gotyme") ||
            lower.contains("bdo") ||
            lower.contains("bpi") ||
            lower.contains("unionbank") ||
            lower.contains("metrobank") ||
            lower.contains("landbank") ||
            lower.contains("shopee")
        ) return true

        // Allow default SMS app
        try {
            val defaultSms = Telephony.Sms.getDefaultSmsPackage(applicationContext)
            if (defaultSms != null && packageName == defaultSms) return true
        } catch (_: Exception) {}

        // Allow Resibo itself for debug/simulated notifications
        if (packageName == applicationContext.packageName) return true

        return false
    }

    private fun savePaymentToLedger(payment: ParsedPayment, source: String) {
        val bizId = currentBusinessId ?: getSavedBusinessId(applicationContext) ?: return

        val firestore = FirebaseFirestore.getInstance()
        val paymentRecord = hashMapOf(
            "provider" to payment.provider,
            "amount" to payment.amount,
            "senderNameMasked" to payment.senderNameMasked,
            "referenceNumber" to payment.referenceNumber,
            "receivedAt" to Timestamp.now(),
            "source" to source
        )

        // Write to active business document
        firestore.collection("businesses")
            .document(bizId)
            .collection("payments")
            .add(paymentRecord)
            .addOnSuccessListener {
                Log.i(TAG, "Recorded incoming payment in $bizId: ${payment.provider} ₱${payment.amount} (ref: ${payment.referenceNumber})")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save payment to Firestore", e)
            }

        // Also write to alias document if prefixed or non-prefixed
        val altBizId = if (bizId.startsWith("biz_")) bizId.removePrefix("biz_") else "biz_$bizId"
        if (altBizId != bizId) {
            try {
                firestore.collection("businesses")
                    .document(altBizId)
                    .collection("payments")
                    .add(paymentRecord)
            } catch (_: Exception) {}
        }
    }

    private fun getSavedBusinessId(context: Context): String? {
        val prefs = context.getSharedPreferences("resibo_prefs", Context.MODE_PRIVATE)
        return prefs.getString("active_business_id", null)
            ?: prefs.getString("biz_id", null)
            ?: prefs.getString("biz_slug", null)?.let { if (it.isNotBlank()) "biz_$it" else null }
    }
}
