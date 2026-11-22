package com.burstbrainconcepts.aura

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted app-private storage for remote-recovery credentials and replay protection. */
class EncryptedRecoveryStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "aura-recovery-v1.bin")

    @Synchronized
    fun read(): JSONObject {
        if (!file.exists()) return JSONObject()
        val bytes = file.readBytes()
        require(bytes.size > IV_LENGTH + TAG_LENGTH) { "Recovery settings file is incomplete." }
        val buffer = ByteBuffer.wrap(bytes)
        val iv = ByteArray(IV_LENGTH)
        buffer.get(iv)
        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH * 8, iv))
        return JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
    }

    @Synchronized
    fun write(value: JSONObject) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        val bytes = ByteBuffer.allocate(cipher.iv.size + encrypted.size)
            .put(cipher.iv).put(encrypted).array()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    @Synchronized
    fun isLocationOptIn(): Boolean = read().optBoolean("locationOptIn", false)

    @Synchronized
    fun setLocationOptIn(enabled: Boolean) {
        val value = read()
        value.put("locationOptIn", enabled)
        write(value)
    }

    @Synchronized
    fun getProcessedCommand(commandId: String): JSONObject? {
        val commands = read().optJSONObject("processedCommands") ?: return null
        val value = commands.optJSONObject(commandId) ?: return null
        return JSONObject(value.toString())
    }

    @Synchronized
    fun saveProcessedCommand(commandId: String, nonce: String, result: String, detail: String) {
        val value = read()
        val commands = value.optJSONObject("processedCommands") ?: JSONObject()
        commands.put(commandId, JSONObject().put("nonce", nonce).put("result", result).put("detail", detail.take(1000)))
        while (commands.length() > MAX_NONCES) {
            val iterator = commands.keys()
            if (iterator.hasNext()) commands.remove(iterator.next()) else break
        }
        value.put("processedCommands", commands)
        write(value)
    }

    @Synchronized
    fun consumeNonce(nonce: String): Boolean {
        val value = read()
        val entries = value.optJSONArray("processedNonces") ?: JSONArray()
        for (index in 0 until entries.length()) {
            if (entries.optString(index) == nonce) return false
        }
        entries.put(nonce)
        while (entries.length() > MAX_NONCES) entries.remove(0)
        value.put("processedNonces", entries)
        write(value)
        return true
    }

    @Synchronized
    fun clear() {
        file.delete()
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
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
        private const val KEY_ALIAS = "aura.recovery.config.aes.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH = 16
        private const val MAX_NONCES = 1000
    }
}
