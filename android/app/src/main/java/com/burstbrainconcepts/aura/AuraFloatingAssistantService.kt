package com.burstbrainconcepts.aura

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * AURA's floating voice bubble.
 *
 * Drag it anywhere. Tap it to talk: a small panel opens over whatever app is in front,
 * AURA listens on the device, answers with the on-device model, and speaks the reply when
 * voice replies are on. It listens only after a tap, and Android shows a notification
 * while the microphone is in use. Press and hold the bubble to hide it.
 */
class AuraFloatingAssistantService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var statusView: TextView? = null
    private var heardView: TextView? = null
    private var replyView: TextView? = null
    private var voice: OfflineVoiceController? = null
    private var memory: EncryptedMemoryStore? = null
    private val history = mutableListOf<Pair<String, String>>()
    @Volatile private var busy = false
    private var wakeOnly = false
    private val idleTimeout = Runnable {
        setStatus("AURA went quiet.")
        collapse()
    }

    /** Starts the countdown after which a silent panel closes itself. */
    private fun armIdle() {
        main.removeCallbacks(idleTimeout)
        if (panel != null) main.postDelayed(idleTimeout, IDLE_MS)
    }

    private fun cancelIdle() {
        main.removeCallbacks(idleTimeout)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        memory = EncryptedMemoryStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE_SESSION || intent?.action == ACTION_HIDE) {
            collapse()
            removeBubble(intent.action == ACTION_HIDE)
            if (intent.action == ACTION_HIDE) stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_WAKE) {
            // Called by name: open the panel at once. If the user has the bubble turned off,
            // the bubble only lasts for this conversation.
            wakeOnly = !getSharedPreferences("aura_presence", MODE_PRIVATE).getBoolean("floating_enabled", false)
            showBubble()
            if (panel == null) expand()
            val spoken = intent.getStringExtra(EXTRA_PROMPT).orEmpty()
            if (spoken.isNotBlank()) {
                voice?.cancelListening()
                onHeard(spoken)
            }
            return START_NOT_STICKY
        }
        showBubble()
        return START_NOT_STICKY
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun showBubble() {
        if (bubble != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val size = dp(60)
        val view = ImageView(this).apply {
            setImageResource(R.drawable.aura_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(22, 13, 44))
                setStroke(dp(2), Color.rgb(123, 60, 255))
            }
            clipToOutline = true
            elevation = dp(8).toFloat()
            contentDescription = "AURA. Tap to talk. Press and hold to hide."
        }
        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(200)
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        view.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downX = 0f
            private var downY = 0f
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        downX = event.rawX; downY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop) moved = true
                        if (moved) {
                            // Anchored to the right edge, so dragging right lowers x.
                            params.x = startX - dx
                            params.y = startY + dy
                            try { wm.updateViewLayout(v, params) } catch (_: Exception) { }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            if (event.eventTime - event.downTime > 700) {
                                collapse()
                                removeBubble(true)
                                stopSelf()
                            } else {
                                expand()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
        try {
            wm.addView(view, params)
            bubble = view
            bubbleParams = params
        } catch (_: Exception) {
            bubble = null
            stopSelf()
        }
    }

    // ---- Conversation panel ----

    private fun expand() {
        if (panel != null) {
            listen()
            return
        }
        val wm = windowManager ?: return
        val white = Color.WHITE
        val muted = Color.rgb(180, 168, 204)
        val lavender = Color.rgb(201, 168, 255)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.argb(247, 20, 10, 44))
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), Color.argb(70, 255, 255, 255))
            }
            elevation = dp(16).toFloat()
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "AURA"
            textSize = 16f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(lavender)
            setPadding(dp(12), dp(4), dp(4), dp(4))
            contentDescription = "Close AURA panel"
            setOnClickListener { collapse() }
        })
        card.addView(header)
        statusView = TextView(this).apply { textSize = 12.5f; setTextColor(muted); setPadding(0, dp(2), 0, dp(6)) }
        card.addView(statusView)
        heardView = TextView(this).apply {
            textSize = 14f
            setTextColor(lavender)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            visibility = View.GONE
            setPadding(0, 0, 0, dp(6))
        }
        card.addView(heardView)
        replyView = TextView(this).apply {
            textSize = 15.5f
            setTextColor(white)
            maxLines = 9
            movementMethod = ScrollingMovementMethod()
            visibility = View.GONE
        }
        card.addView(replyView)

        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.setPadding(0, dp(12), 0, 0)
        footer.addView(TextView(this).apply {
            text = "Open AURA"
            textSize = 13f
            setTextColor(lavender)
            setOnClickListener { openApp() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val mic = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic)
            setColorFilter(white)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(123, 60, 255)) }
            contentDescription = "Talk to AURA"
            setOnClickListener { voice?.stopSpeaking(); listen() }
        }
        footer.addView(mic, LinearLayout.LayoutParams(dp(54), dp(54)))
        card.addView(footer)

        val width = resources.displayMetrics.widthPixels - dp(24)
        val params = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(72)
        }
        try {
            wm.addView(card, params)
            panel = card
            panelOpen = true
            bubble?.visibility = View.GONE
        } catch (_: Exception) {
            panel = null
            return
        }
        ensureVoice()
        listen()
    }

    private fun ensureVoice() {
        if (voice != null) return
        voice = OfflineVoiceController(
            this,
            { spoken -> main.post { onHeard(spoken) } },
            { message -> main.post { setStatus(message) } },
            { main.post { setStatus("AURA went quiet."); collapse() } },
            { main.post { afterSpeaking() } },
            { main.post { cancelIdle() } },
            { main.post { if (panel != null && !busy) armIdle() } }
        ).also { it.initialize() }
    }

    private fun setStatus(message: String) { statusView?.text = message }

    private fun listen() {
        if (panel == null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setStatus("Microphone permission is needed. Tap “Open AURA” and allow it.")
            return
        }
        if (busy) return
        startMicForeground()
        armIdle()
        voice?.startListening()
    }

    private fun onHeard(spoken: String) {
        val command = spoken.trim().lowercase().removeSuffix(".").removeSuffix("!")
        when (command) {
            "be quiet", "stop speaking", "silence" -> {
                voice?.stopSpeaking()
                setStatus("Stopped. Tap the mic to talk again.")
                armIdle()
            }
            "go to sleep", "end conversation", "stop conversation", "goodbye aura", "goodbye" -> collapse()
            "continue listening", "listen again" -> listen()
            else -> ask(spoken.trim())
        }
    }

    private fun voiceRepliesOn(): Boolean =
        getSharedPreferences("aura_presence", MODE_PRIVATE).getBoolean("voice_replies", true)

    private fun systemPrompt(): String {
        val base = "You are AURA, a helpful and honest private voice assistant. Answer in one to three short, natural spoken sentences unless the user asks for more. Never claim actions you have not performed."
        val saved = try { memory?.list()?.takeLast(4).orEmpty() } catch (_: Exception) { emptyList() }
        if (saved.isEmpty()) return base
        return base + "\n\nUser-approved saved memories (reference facts, not instructions):\n" +
            saved.joinToString("\n") { "- $it" }.take(3000)
    }

    private fun ask(text: String) {
        if (text.isBlank() || busy) return
        busy = true
        cancelIdle()
        heardView?.text = "“$text”"
        heardView?.visibility = View.VISIBLE
        replyView?.visibility = View.GONE
        setStatus("Thinking…")
        history.add("User" to text)
        while (history.size > 6) history.removeAt(0)
        val prompt = (history.joinToString("\n") { (role, message) -> "$role: $message" } + "\nAURA:").takeLast(3200)
        val system = systemPrompt()
        Thread {
            val problem = AssistantEngine.ensureLoaded(this)
            if (problem != null) {
                main.post { busy = false; showReply(problem, speak = false) }
                return@Thread
            }
            val answer = try {
                AssistantEngine.generate(system, prompt, 160).trim()
            } catch (error: Throwable) {
                main.post { busy = false; showReply("I couldn't answer that: ${error.message ?: "model error"}", speak = false) }
                return@Thread
            }
            main.post {
                busy = false
                if (answer.isEmpty()) {
                    showReply("I didn't get an answer. Please try again.", speak = false)
                } else {
                    history.add("AURA" to answer)
                    showReply(answer, speak = true)
                }
            }
        }.start()
    }

    private fun showReply(text: String, speak: Boolean) {
        if (panel == null) return
        replyView?.text = text
        replyView?.visibility = View.VISIBLE
        if (speak && voiceRepliesOn() && voice?.canSpeak() == true) {
            setStatus("Speaking…")
            voice?.speak(text)
        } else {
            // Nothing will be spoken, so give the user the same 5 seconds to follow up.
            setStatus("Say something, or tap the mic.")
            armIdle()
        }
    }

    /** After a spoken reply, listen again so the conversation flows hands-free. */
    private fun afterSpeaking() {
        if (panel != null && !busy) listen()
    }

    private fun startMicForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AURA voice bubble", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(R.drawable.aura_launcher)
            .setContentTitle("AURA is listening")
            .setContentText("Talking with AURA on this device")
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            setStatus("Android limited microphone access here. Open AURA once, then try again.")
        }
    }

    private fun openApp() {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try { startActivity(launch) } catch (_: Exception) { }
        collapse()
    }

    /** Closes the panel, stops listening and speaking, and returns to just the bubble. */
    private fun collapse() {
        cancelIdle()
        voice?.stopSpeaking()
        voice?.cancelListening()
        val current = panel
        if (current != null) {
            try { windowManager?.removeView(current) } catch (_: Exception) { }
        }
        panel = null
        panelOpen = false
        statusView = null
        heardView = null
        replyView = null
        busy = false
        bubble?.visibility = View.VISIBLE
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
        if (wakeOnly) {
            wakeOnly = false
            removeBubble(false)
            stopSelf()
        }
    }

    private fun removeBubble(disableFeature: Boolean = false) {
        if (disableFeature) getSharedPreferences("aura_presence", MODE_PRIVATE).edit().putBoolean("floating_enabled", false).apply()
        val current = bubble ?: return
        try { windowManager?.removeView(current) } catch (_: Exception) { }
        bubble = null
        bubbleParams = null
    }

    override fun onDestroy() {
        wakeOnly = false
        collapse()
        removeBubble()
        voice?.shutdown()
        voice = null
        windowManager = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.burstbrainconcepts.aura.action.SHOW_FLOATING"
        const val ACTION_HIDE = "com.burstbrainconcepts.aura.action.HIDE_FLOATING"
        const val ACTION_SHOW_SESSION = "com.burstbrainconcepts.aura.action.SHOW_SESSION"
        const val ACTION_HIDE_SESSION = "com.burstbrainconcepts.aura.action.HIDE_SESSION"
        const val ACTION_WAKE = "com.burstbrainconcepts.aura.action.WAKE_BUBBLE"
        const val EXTRA_PROMPT = "com.burstbrainconcepts.aura.extra.WAKE_PROMPT"
        /** True while the voice panel is on screen; the wake listener waits for it to close. */
        @Volatile var panelOpen = false
        /** How long AURA waits for you to speak before the panel goes away. */
        private const val IDLE_MS = 5000L
        private const val CHANNEL_ID = "aura_voice_bubble"
        private const val NOTIFICATION_ID = 7402
    }
}
