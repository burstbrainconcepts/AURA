package com.burstbrainconcepts.aura

object NativeLlama {
    init {
        System.loadLibrary("aura_llama")
    }

    external fun nativeLoadModel(modelPath: String): Boolean
    external fun nativeGenerate(prompt: String, maxTokens: Int): String
    external fun nativeUnloadModel()
}
