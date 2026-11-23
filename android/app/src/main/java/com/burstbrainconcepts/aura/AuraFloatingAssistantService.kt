package com.burstbrainconcepts.aura

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Optional, explicitly enabled launcher bubble. It never captures audio.
 * The user can dismiss it with the close control or by stopping the service.
 */
class AuraFloatingAssistantService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE_SESSION || intent?.action == ACTION_HIDE) {
            removeBubble(intent.action == ACTION_HIDE)
            if (intent.action == ACTION_HIDE) stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        showBubble()
        return START_NOT_STICKY
    }

    private fun showBubble() {
        if (bubble != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 10, 12, 10)
            background = GradientDrawable().apply {
                setColor(Color.rgb(17, 23, 53))
                cornerRadius = 48f
                setStroke(2, Color.rgb(135, 59, 255))
            }
            elevation = 12f
            contentDescription = "AURA floating assistant launcher"
        }

        val launch = TextView(this).apply {
            text = "✦  AURA"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            contentDescription = "Open AURA assistant"
            setPadding(0, 2, 14, 2)
            setOnClickListener {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent?.putExtra(MainActivity.EXTRA_START_LISTENING, true)
                if (intent != null) startActivity(intent)
            }
        }
        val dismiss = TextView(this).apply {
            text = "×"
            textSize = 22f
            setTextColor(Color.rgb(25, 216, 232))
            contentDescription = "Dismiss AURA floating launcher"
            setPadding(8, 0, 4, 0)
            setOnClickListener {
                removeBubble()
                stopSelf()
            }
        }
        container.addView(launch)
        container.addView(dismiss)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 18
            y = 180
        }
        try {
            windowManager?.addView(container, params)
            bubble = container
        } catch (_: Exception) {
            bubble = null
            stopSelf()
        }
    }

    private fun removeBubble(disableFeature: Boolean = false) {
        if (disableFeature) getSharedPreferences("aura_presence", MODE_PRIVATE).edit().putBoolean("floating_enabled", false).apply()
        val current = bubble ?: return
        try {
            windowManager?.removeView(current)
        } catch (_: Exception) {
            // The system may already have removed the overlay window.
        }
        bubble = null
    }

    override fun onDestroy() {
        removeBubble()
        windowManager = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.burstbrainconcepts.aura.action.SHOW_FLOATING"
        const val ACTION_HIDE = "com.burstbrainconcepts.aura.action.HIDE_FLOATING"
        const val ACTION_SHOW_SESSION = "com.burstbrainconcepts.aura.action.SHOW_SESSION"
        const val ACTION_HIDE_SESSION = "com.burstbrainconcepts.aura.action.HIDE_SESSION"
    }
}
