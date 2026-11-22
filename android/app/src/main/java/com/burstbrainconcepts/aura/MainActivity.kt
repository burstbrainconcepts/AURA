package com.burstbrainconcepts.aura

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Native chat shell for a local llama.cpp OpenAI-compatible server.
 * It connects only to the device loopback endpoint; no conversation is uploaded.
 * The native inference engine/model packaging is a separate build integration.
 */
class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val messages = JSONArray()
    private lateinit var transcript: TextView
    private lateinit var input: EditText
    private lateinit var send: Button
    private val endpoint = "http://localhost:8080/v1/chat/completions"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        messages.put(JSONObject().put("role", "system").put("content",
            "You are AURA, a helpful private assistant. Never claim to have performed actions without confirmation."))

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
        val status = TextView(this).apply {
            text = "LOCAL MODE • waiting for engine at localhost:8080"
            textSize = 12f
            setTextColor(0xFF9EADC7.toInt())
            setPadding(0, 6, 0, 12)
        }
        transcript = TextView(this).apply {
            text = "AURA is ready. Start the local inference engine to begin a private conversation.\n"
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
        }
        send = Button(this).apply {
            text = "Send"
            setOnClickListener { sendMessage(status) }
        }
        root.addView(heading)
        root.addView(status)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(send)
        setContentView(root)
    }

    private fun sendMessage(status: TextView) {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty() || prompt.length > 8000 || send.isEnabled.not()) return
        input.setText("")
        append("You", prompt)
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        send.isEnabled = false
        status.text = "LOCAL MODE • generating..."
        executor.execute {
            try {
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 120000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                try {
                    val body = JSONObject()
                        .put("model", "local-model")
                        .put("messages", messages)
                        .put("temperature", 0.7)
                        .put("max_tokens", 512)
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val response = stream.bufferedReader().use { it.readText() }
                    if (code !in 200..299) throw IllegalStateException("Local engine returned HTTP $code")
                    val answer = JSONObject(response).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content").trim()
                    messages.put(JSONObject().put("role", "assistant").put("content", answer))
                    runOnUiThread {
                        append("AURA", answer)
                        status.text = "LOCAL MODE • connected"
                        send.isEnabled = true
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                // Keep the failed request visible to the user, but do not retain a
                // half-finished assistant turn in the conversation context.
                if (messages.length() > 1) messages.remove(messages.length() - 1)
                runOnUiThread {
                    append("AURA", "I couldn't reach the local AI engine. Start the llama.cpp server on this device and retry.")
                    status.text = "LOCAL MODE • engine unavailable"
                    send.isEnabled = true
                }
            }
        }
    }

    private fun append(speaker: String, text: String) {
        transcript.append("\n$speaker\n$text\n")
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
