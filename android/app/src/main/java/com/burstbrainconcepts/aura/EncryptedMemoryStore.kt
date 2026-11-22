package com.burstbrainconcepts.aura

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * User-controlled memory encrypted with an Android Keystore AES-256-GCM key.
 * Memory is disabled by default; disabling it deletes the ciphertext and key.
 */
class EncryptedMemoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("aura_memory_settings", Context.MODE_PRIVATE)
    private val file = File(appContext.filesDir, "aura-memory-v1.bin")

    val enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)

    @Synchronized
    fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        if (!value) {
            file.delete()
            val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
        }
    }

    @Synchronized
    fun add(text: String): Int {
        check(enabled) { "Enable AURA memory before saving anything." }
        require(text.isNotBlank() && text.length <= 4000) { "Memory must contain 1–4,000 characters." }
        val items = readArray()
        items.put(JSONObject()
            .put("id", java.util.UUID.randomUUID().toString())
            .put("text", text.trim())
            .put("createdAt", System.currentTimeMillis()))
        while (items.length() > MAX_ITEMS) items.remove(0)
        writeArray(items)
        return items.length()
    }

    @Synchronized
    fun list(): List<String> {
        if (!enabled) return emptyList()
        val items = readArray()
        return (0 until items.length()).mapNotNull { index ->
            items.optJSONObject(index)?.optString("text")?.takeIf { it.isNotBlank() }
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun readArray(): JSONArray {
        if (!file.exists()) return JSONArray()
        val bytes = file.readBytes()
        require(bytes.size > IV_LENGTH + TAG_LENGTH) { "Encrypted memory file is incomplete." }
        val buffer = ByteBuffer.wrap(bytes)
        val iv = ByteArray(IV_LENGTH)
        buffer.get(iv)
        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH * 8, iv))
        return JSONArray(String(cipher.doFinal(encrypted), Charsets.UTF_8))
    }

    private fun writeArray(items: JSONArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(items.toString().toByteArray(Charsets.UTF_8))
        val output = ByteBuffer.allocate(cipher.iv.size + encrypted.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeBytes(output)
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "aura.memory.aes.v1"
        private const val KEY_ENABLED = "enabled"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH = 16
        private const val MAX_ITEMS = 50
    }
}
