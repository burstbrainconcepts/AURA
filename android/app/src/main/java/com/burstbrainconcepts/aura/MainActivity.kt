package com.burstbrainconcepts.aura

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val conversation = mutableListOf<Pair<String, String>>()
    private lateinit var transcript: TextView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var download: Button
    private lateinit var mic: Button
    private lateinit var speakToggle: Button
    private lateinit var voice: OfflineVoiceController
    private var speakReplies = false
    private var activeDownloadId = -1L
    private var modelReady = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == activeDownloadId) verifyAndLoadModel(id)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadReceiver, filter)
        }
        buildInterface()
        voice = OfflineVoiceController(
            this,
            { spoken -> runOnUiThread { input.setText(spoken) } },
            { message -> runOnUiThread { status.text = message } }
        )
        voice.initialize()

        val modelFile = modelFile()
        if (modelFile.exists()) {
            status.text = "Checking downloaded offline model..."
            verifyAndLoadModel(-1L)
        } else {
            status.text = "Offline model not installed • download required once"
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 20)
            setBackgroundColor(0xFF101522.toInt())
        }
        val heading = TextView(this).apply {
            text = "AURA"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
        }
        status = TextView(this).apply {
            text = "PRIVATE AI • ON-DEVICE"
            textSize = 12f
            setTextColor(0xFF9EADC7.toInt())
            setPadding(0, 6, 0, 12)
        }
        download = Button(this).apply {
            text = "Download starter AI model (about 398 MB)"
            setOnClickListener { confirmModelDownload() }
        }
        transcript = TextView(this).apply {
            text = "Welcome to AURA. Download the starter model once; after that, chat can run offline on this device.\n"
            textSize = 16f
            setTextColor(0xFFE8EDF7.toInt())
            setPadding(12, 12, 12, 12)
        }
        val scroll = ScrollView(this).apply { addView(transcript) }
        input = EditText(this).apply {
            hint = "Message AURA..."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8794AA.toInt())
            setSingleLine(false)
            maxLines = 4
            isEnabled = false
        }
        val voiceControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        mic = Button(this).apply {
            text = "🎙 Speak"
            isEnabled = false
            setOnClickListener { voice.startListening() }
        }
        speakToggle = Button(this).apply {
            text = "Voice replies: off"
            isEnabled = false
            setOnClickListener {
                speakReplies = !speakReplies
                text = if (speakReplies) "Voice replies: on" else "Voice replies: off"
                if (!speakReplies) voice.stopSpeaking()
            }
        }
        voiceControls.addView(mic, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        voiceControls.addView(speakToggle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        send = Button(this).apply {
            text = "Send"
            isEnabled = false
            setOnClickListener { sendMessage() }
        }
        root.addView(heading)
        root.addView(status)
        root.addView(download)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(voiceControls)
        root.addView(send)
        setContentView(root)
    }

    private fun confirmModelDownload() {
        AlertDialog.Builder(this)
            .setTitle("Download AURA's starter model?")
            .setMessage("This downloads approximately 398 MB from Hugging Face. The model is stored in this app's private storage and can be used offline afterwards. Continue using your current internet connection?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Download") { _, _ -> startModelDownload() }
            .show()
    }

    private fun startModelDownload() {
        val request = DownloadManager.Request(android.net.Uri.parse(ModelCatalog.DOWNLOAD_URL))
            .setTitle("AURA offline AI model")
            .setDescription("Downloading Qwen 0.5B GGUF model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, ModelCatalog.FILE_NAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        activeDownloadId = manager.enqueue(request)
        download.isEnabled = false
        status.text = "Downloading model • Android will show download progress"
        append("AURA", "The model download has started. You can leave the app; Android will notify AURA when it finishes.")
    }

    private fun modelFile(): File {
        val directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("App storage is unavailable.")
        return File(directory, ModelCatalog.FILE_NAME)
    }

    private fun verifyAndLoadModel(downloadId: Long) {
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        if (downloadId >= 0) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            manager.query(query).use { cursor ->
                if (!cursor.moveToFirst()) {
                    runOnUiThread { status.text = "Download record not found"; download.isEnabled = true }
                    return
                }
                val state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (state != DownloadManager.STATUS_SUCCESSFUL) {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    runOnUiThread {
                        status.text = "Download failed (code $reason). Try again."
                        download.isEnabled = true
                    }
                    return
                }
            }
        }
        download.isEnabled = false
        status.text = "Verifying model integrity..."
        executor.execute {
            try {
                val file = modelFile()
                if (!file.exists() || file.length() < 300_000_000L) {
                    throw IllegalStateException("The downloaded model is incomplete.")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                FileInputStream(file).use { stream ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(ModelCatalog.SHA256, ignoreCase = true)) {
                    file.delete()
                    throw IllegalStateException("Model checksum did not match. The incomplete file was removed; download again.")
                }
                val loaded = NativeLlama.nativeLoadModel(file.absolutePath)
                if (!loaded) throw IllegalStateException("The native AI engine could not load the model.")
                modelReady = true
                runOnUiThread {
                    status.text = "OFFLINE AI READY • Qwen 0.5B • CPU mode"
                    download.text = "Model installed ✓"
                    download.isEnabled = false
                    input.isEnabled = true
                    send.isEnabled = true
                    mic.isEnabled = voice.isOnDeviceRecognitionAvailable()
                    speakToggle.isEnabled = true
                    append("AURA", "I'm ready. Your messages are processed locally on this device. Tap the microphone for on-device speech input or enable voice replies if an offline voice is installed.")
                }
            } catch (error: Exception) {
                runOnUiThread {
                    status.text = "Model setup failed: ${error.message ?: "unknown error"}"
                    download.isEnabled = true
                    input.isEnabled = false
                    send.isEnabled = false
                }
            }
        }
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (!modelReady || text.isEmpty() || text.length > 8000 || !send.isEnabled) return
        input.setText("")
        append("You", text)
        conversation.add("User" to text)
        while (conversation.size > 12) conversation.removeAt(0)
        send.isEnabled = false
        input.isEnabled = false
        mic.isEnabled = false
        status.text = "OFFLINE AI • thinking..."
        val prompt = conversation.joinToString("\n") { (role, message) -> "$role: $message" } + "\nAURA:"
        executor.execute {
            try {
                val answer = NativeLlama.nativeGenerate(prompt, 256).trim()
                if (answer.isEmpty()) throw IllegalStateException("The model returned an empty response.")
                conversation.add("AURA" to answer)
                while (conversation.size > 12) conversation.removeAt(0)
                runOnUiThread {
                    append("AURA", answer)
                    status.text = "OFFLINE AI READY • no network required"
                    send.isEnabled = true
                    input.isEnabled = true
                    mic.isEnabled = voice.isOnDeviceRecognitionAvailable()
                    if (speakReplies) voice.speak(answer)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    append("AURA", "I couldn't complete that response: ${error.message ?: "model error"}")
                    status.text = "OFFLINE AI • ready to retry"
                    send.isEnabled = true
                    input.isEnabled = true
                    mic.isEnabled = voice.isOnDeviceRecognitionAvailable()
                }
            }
        }
    }

    private fun append(speaker: String, text: String) {
        transcript.append("\n$speaker\n$text\n")
    }

    @Deprecated("Use permission result API")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == OfflineVoiceController.REQUEST_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                voice.startListening()
            } else {
                status.text = "Microphone permission was not granted."
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(downloadReceiver) } catch (_: Exception) { }
        voice.shutdown()
        executor.shutdownNow()
        try { NativeLlama.nativeUnloadModel() } catch (_: UnsatisfiedLinkError) { }
        super.onDestroy()
    }
}
