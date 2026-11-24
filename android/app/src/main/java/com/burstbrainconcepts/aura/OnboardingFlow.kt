package com.burstbrainconcepts.aura

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * First-launch flow: Welcome -> Setup (permissions) -> Download AI Model -> Download Complete.
 * Everything is drawn full-screen over the main interface and removed when finished.
 * AURA runs its model on the device; there is intentionally no silent cloud fallback.
 */
class OnboardingFlow(
    private val activity: Activity,
    private val host: FrameLayout,
    private val onRequestPermissions: (microphone: Boolean, notifications: Boolean) -> Unit,
    private val onStartDownload: () -> Unit,
    private val onPauseOrResume: () -> Unit,
    private val onFinish: (openPage: String) -> Unit
) {
    private val ink = 0xFF10071F.toInt()
    private val panel = 0xFF1A1034.toInt()
    private val panel2 = 0xFF261746.toInt()
    private val purple = 0xFF702BFF.toInt()
    private val lavender = 0xFFC9A8FF.toInt()
    private val white = 0xFFFFFCFF.toInt()
    private val muted = 0xFFBDB1CE.toInt()
    private val green = 0xFF159B62.toInt()
    private val darkText = 0xFF20172E.toInt()
    private val greyText = 0xFF71687D.toInt()

    private val root = FrameLayout(activity).apply { setBackgroundColor(ink); isClickable = true }
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null
    private var actionButton: Button? = null
    private var downloadStarted = false
    private var downloadPaused = false

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(3), 0, dp(3))
        }

    private fun button(value: String, primary: Boolean, light: Boolean = false, action: () -> Unit): Button =
        Button(activity).apply {
            text = value
            isAllCaps = false
            textSize = 15f
            stateListAnimator = null
            setTextColor(if (primary) white else if (light) purple else white)
            background = rounded(if (primary) purple else if (light) 0xFFEFE9FB.toInt() else panel2, 16)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { action() }
        }

    private fun icon(res: Int, tint: Int, sizeDp: Int = 22): ImageView = ImageView(activity).apply {
        setImageDrawable(activity.resources.getDrawable(res, activity.theme).mutate().apply { setTint(tint) })
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    /** Full-screen vertical page with safe-area padding so nothing sits under the status bar. */
    private fun page(content: LinearLayout.() -> Unit): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            content()
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(column, ViewGroup.LayoutParams(-1, -2))
        }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val top = if (Build.VERSION.SDK_INT >= 30) insets.getInsets(android.view.WindowInsets.Type.statusBars()).top else insets.systemWindowInsetTop
            val bottom = if (Build.VERSION.SDK_INT >= 30) insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom else insets.systemWindowInsetBottom
            view.setPadding(0, top, 0, bottom)
            insets
        }
        return scroll
    }

    private fun show(view: View) {
        root.removeAllViews()
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        view.requestApplyInsets()
    }

    fun start() {
        host.addView(root, FrameLayout.LayoutParams(-1, -1))
        showWelcome()
    }

    private fun finish(page: String) {
        host.removeView(root)
        onFinish(page)
    }

    private fun showWelcome() {
        show(page {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(ImageView(activity).apply { setImageResource(R.drawable.aura_launcher) },
                LinearLayout.LayoutParams(dp(84), dp(84)).apply { topMargin = dp(28) })
            addView(label("Welcome to AURA 👋", 26f, white, true).apply { gravity = Gravity.CENTER })
            addView(label("Your intelligent, private and powerful AI assistant.", 14f, muted).apply { gravity = Gravity.CENTER })
            addView(View(activity), LinearLayout.LayoutParams(1, dp(20)))
            listOf(
                Triple(R.drawable.ic_chat, "Chat & Voice", "Talk, ask, and get things done"),
                Triple(R.drawable.ic_download, "Works Offline", "Use powerful AI models on your device"),
                Triple(R.drawable.ic_settings, "Your Memory", "Remembers what matters to you, only if you allow it")
            ).forEach { (res, title, detail) ->
                val row = LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = rounded(panel, 18)
                }
                row.addView(icon(res, lavender, 26))
                val text = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
                text.addView(label(title, 15f, white, true))
                text.addView(label(detail, 12f, muted))
                row.addView(text)
                addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
            addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(button("Get Started  →", true) { showSetup() }, LinearLayout.LayoutParams(-1, -2))
        })
    }

    private fun showSetup() {
        var microphone = true
        var notifications = true
        var overlay = false
        show(page {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(ImageView(activity).apply { setImageResource(R.drawable.aura_launcher) },
                LinearLayout.LayoutParams(dp(64), dp(64)).apply { topMargin = dp(20) })
            addView(label("Let's set up AURA", 24f, white, true).apply { gravity = Gravity.CENTER })
            addView(label("Allow a few permissions for the best experience.", 14f, muted).apply { gravity = Gravity.CENTER })
            addView(View(activity), LinearLayout.LayoutParams(1, dp(16)))
            fun option(res: Int, title: String, detail: String, initial: Boolean, change: (Boolean) -> Unit) {
                val row = LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(12), dp(10), dp(12))
                    background = rounded(panel, 16)
                }
                row.addView(icon(res, lavender, 24))
                val text = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(8), 0) }
                text.addView(label(title, 15f, white, true))
                text.addView(label(detail, 12f, muted))
                row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(Switch(activity).apply {
                    isChecked = initial
                    setOnCheckedChangeListener { _, checked -> change(checked) }
                })
                addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
            option(R.drawable.ic_mic, "Microphone", "For voice chat and commands", true) { microphone = it }
            option(R.drawable.ic_bell, "Notifications", "Get updates and task alerts", true) { notifications = it }
            option(R.drawable.ic_settings, "Display over other apps", "Enable the floating AURA bubble", false) { overlay = it }
            addView(label("Storage is only used for the AI model you choose to download.", 12f, muted).apply { setPadding(dp(4), dp(4), dp(4), 0) })
            addView(View(activity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(button("Continue", true) {
                onRequestPermissions(microphone, notifications)
                if (overlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.packageName)))
                }
                showDownload()
            }, LinearLayout.LayoutParams(-1, -2))
            addView(button("Skip for now", false) { showDownload() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        })
    }

    private fun showDownload() {
        downloadStarted = false
        downloadPaused = false
        show(page {
            gravity = Gravity.CENTER
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(20), dp(24), dp(20), dp(20))
                background = rounded(white, 26)
            }
            card.addView(icon(R.drawable.ic_download, purple, 40))
            card.addView(label("Download AI Model", 22f, darkText, true).apply { gravity = Gravity.CENTER })
            card.addView(label("Download the recommended model to chat offline, faster and privately.", 13f, greyText).apply { gravity = Gravity.CENTER })
            val model = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(0xFFF4F0FB.toInt(), 14)
            }
            model.addView(label("AURA Lite (397 MB)  •  Recommended", 14f, darkText, true))
            model.addView(label("Fast, efficient and works offline.", 12f, greyText))
            card.addView(model, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); bottomMargin = dp(14) })
            progressText = label("Ready to download • about 397 MB", 12f, greyText)
            card.addView(progressText)
            progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000; progress = 0 }
            card.addView(progressBar, LinearLayout.LayoutParams(-1, dp(14)))
            actionButton = button("Download", true) {
                if (!downloadStarted) {
                    downloadStarted = true
                    actionButton?.text = "Pause"
                    onStartDownload()
                } else {
                    if (downloadPaused) {
                        downloadPaused = false
                        actionButton?.text = "Pause"
                    }
                    onPauseOrResume()
                }
            }
            card.addView(actionButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            card.addView(button("Not now — explore first", false, light = true) { finish("Home") }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(card, LinearLayout.LayoutParams(-1, -2))
        })
    }

    /** Called from the download code; permille is 0..1000. */
    fun updateProgress(permille: Int, text: String) {
        progressBar?.progress = permille
        progressText?.text = text
    }

    fun setPaused(paused: Boolean) {
        downloadPaused = paused
        actionButton?.text = if (paused) "Resume" else "Pause"
    }

    fun showComplete() {
        show(page {
            gravity = Gravity.CENTER_HORIZONTAL
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(20), dp(28), dp(20), dp(20))
                background = rounded(white, 26)
            }
            card.addView(icon(R.drawable.ic_check, green, 48))
            card.addView(label("Download Complete!", 22f, darkText, true).apply { gravity = Gravity.CENTER })
            card.addView(label("AURA Lite is ready for offline use.", 13f, greyText).apply { gravity = Gravity.CENTER })
            listOf("Model installed", "Offline chat enabled", "Ready to use").forEach { item ->
                val line = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
                line.addView(icon(R.drawable.ic_check, green, 18))
                line.addView(label("  $item", 14f, darkText))
                card.addView(line, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
            }
            card.addView(button("Start Chatting", true) { finish("Chat") }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
            card.addView(button("Manage Models", false, light = true) { finish("Downloads") }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(View(activity), LinearLayout.LayoutParams(1, dp(60)))
            addView(card, LinearLayout.LayoutParams(-1, -2))
        })
    }
}
