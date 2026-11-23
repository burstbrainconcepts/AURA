package com.burstbrainconcepts.aura

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Uses only OS-provided on-device speech recognition and installed offline TTS voices.
 * It never falls back to a cloud speech recognizer.
 */
class OfflineVoiceController(
    private val activity: Activity,
    private val onTranscript: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onSilenceTimeout: () -> Unit
) : TextToSpeech.OnInitListener, RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var offlineTtsReady = false

    fun initialize() {
        tts = TextToSpeech(activity.applicationContext, this)
    }

    fun isOnDeviceRecognitionAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)
    }

    fun startListening() {
        if (Build.VERSION.SDK_INT < 31 || !isOnDeviceRecognitionAvailable()) {
            onStatus("On-device speech recognition is unavailable on this Android device.")
            return
        }
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(activity).also {
                    it.setRecognitionListener(this)
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            }
            onStatus("Listening on device...")
            recognizer?.startListening(intent)
        } catch (error: Exception) {
            onStatus("Could not start offline speech recognition: ${error.message ?: "unavailable"}")
        }
    }

    fun speak(text: String) {
        if (!offlineTtsReady) {
            onStatus("No offline text-to-speech voice is installed.")
            return
        }
        tts?.speak(text.take(4000), TextToSpeech.QUEUE_FLUSH, null, "aura-reply")
    }

    fun stopSpeaking() { tts?.stop() }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            onStatus("Offline text-to-speech initialization failed.")
            return
        }
        val engine = tts ?: return
        val preferredLanguage = Locale.getDefault().language
        val localVoice: Voice? = engine.voices
            ?.filter { !it.isNetworkConnectionRequired }
            ?.sortedBy {
                when {
                    it.locale.language == preferredLanguage -> 0
                    it.locale.language == "en" -> 1
                    else -> 2
                }
            }
            ?.firstOrNull()
        if (localVoice == null) {
            onStatus("No installed offline voice found. You can still use text chat.")
            return
        }
        engine.voice = localVoice
        offlineTtsReady = true
        onStatus("Offline speech output ready.")
    }

    override fun onReadyForSpeech(params: Bundle?) { onStatus("Listening...") }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { onStatus("Processing speech on device...") }
    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognised. Try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> { onSilenceTimeout(); "AURA went quiet and is returning to wake-word standby." }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            else -> "Offline speech recognition error ($error)."
        }
        onStatus(message)
    }
    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (!text.isNullOrBlank()) {
            onTranscript(text)
            onStatus("Speech captured. Processing offline...")
        } else onStatus("No speech was recognised.")
    }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun shutdown() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        const val REQUEST_AUDIO = 4312
    }
}
