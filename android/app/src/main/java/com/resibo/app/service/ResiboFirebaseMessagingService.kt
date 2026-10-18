package com.resibo.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ResiboFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "resibo_verdict_alerts"
        const val CHANNEL_NAME = "Resibo Verdict Alerts"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Resibo Update"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "New receipt verdict received"

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you immediately when a receipt is verified or flagged as fake"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }

    private fun saveTokenToFirestore(token: String) {
        val prefs = getSharedPreferences("resibo_prefs", Context.MODE_PRIVATE)
        val bizId = prefs.getString("active_business_id", null) ?: return

        try {
            FirebaseFirestore.getInstance()
                .collection("businesses")
                .document(bizId)
                .collection("fcmTokens")
                .document(token)
                .set(mapOf("token" to token, "updatedAt" to com.google.firebase.Timestamp.now()))
        } catch (_: Exception) {}
    }
}
