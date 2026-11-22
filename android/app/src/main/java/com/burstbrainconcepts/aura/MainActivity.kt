package com.burstbrainconcepts.aura

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Minimal native launcher. Assistant UI and local model runtime are added as
 * separately bundled modules; this shell deliberately exposes no privileged
 * JavaScript bridge or background tracking service.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xFF101522.toInt())
        }
        val title = TextView(this).apply {
            text = "AURA"
            textSize = 34f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        val status = TextView(this).apply {
            text = "Your private assistant is being prepared.\nLocal intelligence will be available when an engine and model are installed."
            textSize = 15f
            setTextColor(0xFFB8C3D9.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }
}
