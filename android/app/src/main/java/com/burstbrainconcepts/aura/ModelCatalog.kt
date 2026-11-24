package com.burstbrainconcepts.aura

/**
 * Downloadable on-device models. Every entry is pinned to an exact Hugging Face
 * commit and verified against a SHA-256 checksum before it is loaded.
 * All three are Apache-2.0 licensed.
 */
data class ModelSpec(
    val id: String,
    val name: String,
    val tagline: String,
    val fileName: String,
    val downloadUrl: String,
    val bytes: Long,
    val sha256: String,
    /** Minimum total device RAM (GB) AURA recommends for this model. */
    val minRamGb: Int
) {
    val sizeLabel: String
        get() = if (bytes >= 1_000_000_000L) String.format(java.util.Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        else "${bytes / 1_000_000} MB"
}

object ModelCatalog {
    val ALL: List<ModelSpec> = listOf(
        ModelSpec(
            id = "lite",
            name = "AURA Lite",
            tagline = "Fastest. Quick answers, light reasoning.",
            fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/d71b21e09fe053c278d73092a855b8a99684693a/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            bytes = 397_808_192L,
            sha256 = "6eb923e7d26e9cea28811e1a8e852009b21242fb157b26149d3b188f3a8c8653",
            minRamGb = 3
        ),
        ModelSpec(
            id = "base",
            name = "AURA Base",
            tagline = "Balanced. Noticeably smarter, still quick.",
            fileName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/9eadc66189c7641e1ddd226b8267a9119b2ce2d4/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            bytes = 986_048_768L,
            sha256 = "1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370",
            minRamGb = 4
        ),
        ModelSpec(
            id = "pro",
            name = "AURA Pro",
            tagline = "Smartest. Best writing and coding; slower on a phone.",
            fileName = "Qwen2.5-7B-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-GGUF/resolve/8911e8a47f92bac19d6f5c64a2e2095bd2f7d031/Qwen2.5-7B-Instruct-Q4_K_M.gguf",
            bytes = 4_683_074_240L,
            sha256 = "65b8fcd92af6b4fefa935c625d1ac27ea29dcb6ee14589c55a8f115ceaaa1423",
            minRamGb = 10
        )
    )

    @Volatile var current: ModelSpec = ALL.first()
        private set

    fun select(id: String?) {
        current = ALL.firstOrNull { it.id == id } ?: ALL.first()
    }

    // Compatibility accessors used by the download and load code.
    val FILE_NAME: String get() = current.fileName
    val DOWNLOAD_URL: String get() = current.downloadUrl
    val EXPECTED_BYTES: Long get() = current.bytes
    val SHA256: String get() = current.sha256
}
