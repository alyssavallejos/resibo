package com.resibo.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

object PaymentAudioAnnouncer : TextToSpeech.OnInitListener {
    private const val TAG = "ResiboAudioAnnouncer"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    fun initialize(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this).apply {
                try {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    setAudioAttributes(attrs)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(attrs)
            } catch (_: Exception) {}

            val result = tts?.setLanguage(Locale("en", "PH"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            tts?.setSpeechRate(0.95f) // Clear cadence for noisy retail counters
            tts?.setPitch(1.0f)
            isInitialized = true
            Log.i(TAG, "Native Android TextToSpeech initialized successfully (Media Audio Channel)")

            pendingText?.let {
                speakRaw(it)
                pendingText = null
            }
        } else {
            Log.e(TAG, "Failed to initialize TextToSpeech: status=$status")
        }
    }

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("resibo_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("audio_announcer_enabled", true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("resibo_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("audio_announcer_enabled", enabled).apply()
    }

    fun formatReferenceForSpeech(ref: String): String {
        val clean = ref.replace(Regex("(?i)^(ref|reference|no|txn|id)[.:\\s#]*"), "").trim()
        val alphanumeric = clean.filter { it.isLetterOrDigit() }
        if (alphanumeric.isBlank()) return ""
        return alphanumeric.map { it.uppercaseChar() }.joinToString(" ")
    }

    fun buildAnnouncementText(
        provider: String,
        amount: Double,
        senderName: String? = null,
        referenceNumber: String? = null
    ): String {
        val providerName = when (provider.lowercase()) {
            "gcash" -> "GCash"
            "maya" -> "Maya"
            "maribank" -> "MariBank"
            "seabank" -> "SeaBank"
            "gotyme" -> "GoTyme"
            "cash" -> "Cash"
            "bdo" -> "BDO"
            "bpi" -> "BPI"
            "unionbank" -> "UnionBank"
            "landbank" -> "LandBank"
            "metrobank" -> "Metrobank"
            else -> provider.uppercase()
        }

        val amountStr = if (amount == 1.0) {
            "1 peso"
        } else if (amount % 1.0 == 0.0) {
            "${amount.toInt()} pesos"
        } else {
            String.format(Locale.US, "%.2f pesos", amount)
        }

        val hasSender = !senderName.isNullOrBlank() &&
            !senderName.equals("Customer", ignoreCase = true) &&
            !senderName.equals("Sender", ignoreCase = true) &&
            !senderName.equals("Unknown", ignoreCase = true) &&
            !senderName.contains("Masked", ignoreCase = true)

        val baseMessage = if (hasSender) {
            "$providerName payment received: $amountStr from $senderName."
        } else {
            "$providerName payment received: $amountStr."
        }

        return if (!referenceNumber.isNullOrBlank()) {
            val spokenRef = formatReferenceForSpeech(referenceNumber)
            if (spokenRef.isNotBlank()) {
                "$baseMessage Reference number: $spokenRef."
            } else {
                baseMessage
            }
        } else {
            baseMessage
        }
    }

    fun announcePayment(
        context: Context,
        provider: String,
        amount: Double,
        senderName: String? = null,
        referenceNumber: String? = null
    ) {
        if (!isEnabled(context)) return
        val text = buildAnnouncementText(provider, amount, senderName, referenceNumber)
        speak(context, text)
    }

    fun speak(context: Context, text: String) {
        if (!isEnabled(context)) return
        initialize(context)

        if (!isInitialized) {
            pendingText = text
            return
        }

        speakRaw(text)
    }

    private fun speakRaw(text: String) {
        // Immediate soundbox cash-register chime tone through media channel
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
        } catch (_: Exception) {}

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "resibo_payment_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
