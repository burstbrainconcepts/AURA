package com.burstbrainconcepts.aura

import android.content.Context
import java.io.File

/**
 * Process-wide owner of the on-device model. The main app and the floating
 * voice bubble share one loaded copy, and every call into the native engine
 * is serialised because the native context is not thread-safe.
 */
object AssistantEngine {
    private val lock = Any()
    private var loadedPath: String? = null

    fun isLoaded(): Boolean = synchronized(lock) { loadedPath != null }

    /** Loads a model file and records which one is active. */
    fun loadModel(path: String): Boolean = synchronized(lock) {
        val ok = NativeLlama.nativeLoadModel(path)
        loadedPath = if (ok) path else null
        ok
    }

    /**
     * Makes sure the user's selected model is loaded, reading it from app storage if it
     * is installed. Returns a plain-language problem, or null when ready.
     */
    fun ensureLoaded(context: Context): String? {
        val prefs = context.getSharedPreferences("aura_models", Context.MODE_PRIVATE)
        ModelCatalog.select(prefs.getString("selected", null))
        val file = File(context.filesDir, ModelCatalog.FILE_NAME)
        return synchronized(lock) {
            if (loadedPath == file.absolutePath) {
                null
            } else if (!file.exists()) {
                "No AI model is installed yet. Open AURA to download one."
            } else {
                try {
                    if (loadModel(file.absolutePath)) null else "The AI model could not be loaded."
                } catch (error: Throwable) {
                    loadedPath = null
                    error.message ?: "The AI engine is unavailable."
                }
            }
        }
    }

    fun generate(systemPrompt: String, prompt: String, maxTokens: Int): String = synchronized(lock) {
        NativeLlama.nativeGenerate(systemPrompt, prompt, maxTokens)
    }

    fun unload() = synchronized(lock) {
        try { NativeLlama.nativeUnloadModel() } catch (_: UnsatisfiedLinkError) { }
        loadedPath = null
    }
}
