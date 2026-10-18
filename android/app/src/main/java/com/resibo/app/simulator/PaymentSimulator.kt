package com.resibo.app.simulator

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.resibo.app.service.PaymentNotificationListener

object PaymentSimulator {

    private const val CHANNEL_ID = PaymentNotificationListener.SIMULATED_CHANNEL_ID
    private const val CHANNEL_NAME = "Simulated Payments (Demo)"

    /**
     * Create Notification Channel (required on Android 8.0+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Used in Resibo debug builds to simulate incoming payments"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Post a simulated notification to trigger PaymentNotificationListener
     */
    fun simulatePayment(
        context: Context,
        provider: String, // "gcash", "maya", "bpi"
        amount: Double,
        senderName: String,
        referenceNumber: String,
        businessId: String? = null
    ) {
        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val formattedAmount = String.format("%.2f", amount)
        val (title, text) = when (provider.lowercase()) {
            "maya" -> {
                "Maya" to "You received PHP $formattedAmount from $senderName. Ref: $referenceNumber"
            }
            "maribank" -> {
                "MariBank" to "You have received PHP $formattedAmount from $senderName. Ref: $referenceNumber"
            }
            "seabank" -> {
                "SeaBank" to "SeaBank: Received PHP $formattedAmount from $senderName via InstaPay. Ref: $referenceNumber"
            }
            "gotyme" -> {
                "GoTyme Bank" to "You received PHP $formattedAmount from $senderName. Ref: $referenceNumber"
            }
            "bank", "bpi" -> {
                "BPI Online" to "BPI: You received PHP $formattedAmount from $senderName via InstaPay. Ref: $referenceNumber"
            }
            else -> { // Default GCash
                "GCash" to "You have received PHP $formattedAmount of GCash from $senderName with Ref No. $referenceNumber."
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationId = (System.currentTimeMillis() % 10000).toInt()
        notificationManager.notify(notificationId, notification)

        // Announce payment via built-in free audio announcer
        com.resibo.app.audio.PaymentAudioAnnouncer.announcePayment(
            context = context,
            provider = provider,
            amount = amount,
            senderName = senderName,
            referenceNumber = referenceNumber
        )

        // Also directly guarantee Firestore entry if notification listener permissions are not yet enabled
        if (!businessId.isNullOrBlank()) {
            val firestore = FirebaseFirestore.getInstance()
            val paymentRecord = hashMapOf(
                "provider" to provider.lowercase(),
                "amount" to amount,
                "senderNameMasked" to senderName,
                "referenceNumber" to referenceNumber,
                "receivedAt" to Timestamp.now(),
                "source" to "simulated"
            )
            firestore.collection("businesses")
                .document(businessId)
                .collection("payments")
                .add(paymentRecord)
        }
    }
}
