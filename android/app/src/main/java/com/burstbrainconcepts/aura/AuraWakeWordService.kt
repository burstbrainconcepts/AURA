package com.burstbrainconcepts.aura

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * User-enabled, foreground wake phrase listener.
 *
 * Uses Android's on-device SpeechRecognizer where supported. It listens only
 * after the user enables the feature from the visible AURA app, displays a
 * persistent foreground notification, and never uploads captured audio.
 */
class AuraWakeWordService : Service(), RecognitionListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var stoppedByUser = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stoppedByUser = true
            stopListening()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            showStatusNotification("On-device wake listening is unavailable on this phone")
            stopSelf()
            return START_NOT_STICKY
        }

        stoppedByUser = false
        startForeground(NOTIFICATION_ID, buildNotification("Listening for “Hey AURA” • on-device"))
        startRecognizer()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AURA wake listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Visible status for AURA's user-enabled wake phrase listener"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPending = open?.let {
            PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val stop = Intent(this, AuraWakeWordService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.aura_launcher)
            .setContentTitle("AURA is available")
            .setContentText(message)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "Pause listening", stopPending)
            .build()
    }

    private fun showStatusNotification(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun startRecognizer() {
        if (stoppedByUser || listening) return
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) return
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also {
                    it.setRecognitionListener(this)
                }
            }
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
            }
            listening = true
            recognizer?.startListening(request)
        } catch (_: Exception) {
            listening = false
            scheduleRestart(1800)
        }
    }

    override fun onResults(results: android.os.Bundle?) {
        listening = false
        val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val phrase = candidates.firstOrNull { normalise(it).contains("hey aura") || normalise(it).startsWith("aura ") || normalise(it) == "aura" }
        if (phrase != null) {
            val cleaned = phrase.replace(Regex("(?i)^.*?hey\\s+aura[, ]*"), "", 1)
                .replace(Regex("(?i)^aura[, ]*"), "")
                .trim()
            val launch = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_START_LISTENING, cleaned.isBlank())
                if (cleaned.isNotBlank()) putExtra(MainActivity.EXTRA_VOICE_PROMPT, cleaned)
            }
            try {
                startActivity(launch)
            } catch (_: Exception) {
                updateNotification("Wake phrase detected • open AURA to continue")
            }
            stoppedByUser = true
            stopListening()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            scheduleRestart(350)
        }
    }

    private fun normalise(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun updateNotification(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun scheduleRestart(delay: Long) {
        if (!stoppedByUser) handler.postDelayed({ startRecognizer() }, delay)
    }

    override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Int) {
        listening = false
        scheduleRestart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200 else 500)
    }
    override fun onPartialResults(partialResults: android.os.Bundle?) {}
    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

    private fun stopListening() {
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        listening = false
    }

    override fun onDestroy() {
        stoppedByUser = true
        stopListening()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.burstbrainconcepts.aura.action.START_WAKE"
        const val ACTION_STOP = "com.burstbrainconcepts.aura.action.STOP_WAKE"
        private const val CHANNEL_ID = "aura_wake_listener"
        private const val NOTIFICATION_ID = 7401
    }
}
