package com.burstbrainconcepts.aura

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * HTTPS enrollment and signed-command polling.
 * The device private key, bearer token, server verification key and replay list are encrypted at rest.
 */
class RemoteRecoveryClient(context: Context) {
    private val app = context.applicationContext
    private val store = EncryptedRecoveryStore(app)
    private val deviceAdmin = ComponentName(app, AuraDeviceAdminReceiver::class.java)

    private fun ensureEd25519Available() {
        try {
            KeyPairGenerator.getInstance("Ed25519")
            KeyFactory.getInstance("Ed25519")
            Signature.getInstance("Ed25519")
        } catch (error: Exception) {
            throw IllegalStateException(
                "Ed25519 is not available from this device's current security providers. Recovery enrollment and command verification remain unavailable until a supported provider or platform configuration is available.",
                error
            )
        }
    }

    fun isEnrolled(): Boolean {
        val config = store.read()
        return config.optString("deviceToken").isNotBlank() && config.optString("serverUrl").isNotBlank() && config.optString("commandServerPublicKeyPem").isNotBlank()
    }

    fun savedServerUrl(): String = store.read().optString("serverUrl")

    fun isLocationOptIn(): Boolean = store.isLocationOptIn()

    fun setLocationOptIn(enabled: Boolean) = store.setLocationOptIn(enabled)

    fun refreshEnrollmentCapabilities(): String {
        val config = store.read()
        val server = config.optString("serverUrl")
        val account = config.optString("accountToken")
        require(server.isNotBlank() && account.length >= 32) { "Connect AURA Recovery before changing recovery capabilities." }
        return enroll(server, account)
    }

    fun clearLastLocation() {
        val config = store.read()
        val server = normalizeServerUrl(config.optString("serverUrl"))
        val account = config.optString("accountToken")
        val deviceId = config.optString("deviceId")
        require(account.length >= 32 && deviceId.isNotBlank()) { "Recovery account is not configured." }
        request(server, "POST", "/v1/devices/$deviceId/location/clear", account, JSONObject())
    }

    fun enroll(serverUrl: String, accountToken: String, label: String = "Android device"): String {
        ensureEd25519Available()
        val base = normalizeServerUrl(serverUrl)
        require(accountToken.length >= 32) { "Account token must contain at least 32 characters." }

        val config = store.read()
        var deviceId = config.optString("deviceId")
        var privateKeyB64 = config.optString("devicePrivateKey")
        var publicKeyPem = config.optString("devicePublicKeyPem")
        if (deviceId.isBlank()) deviceId = UUID.randomUUID().toString()
        if (privateKeyB64.isBlank() || publicKeyPem.isBlank()) {
            val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            privateKeyB64 = Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP)
            publicKeyPem = pem("PUBLIC KEY", pair.public.encoded)
            config.put("devicePrivateKey", privateKeyB64)
            config.put("devicePublicKeyPem", publicKeyPem)
        }
        config.put("deviceId", deviceId)
        store.write(config)

        val capabilities = JSONArray().put("device.recovery.queue")
        val manager = app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (manager.isAdminActive(deviceAdmin)) capabilities.put("device.lock.supported")
        if (store.isLocationOptIn() && hasBackgroundLocationPermission()) capabilities.put("device.location.user_consented")

        val challengeRequest = JSONObject()
            .put("deviceId", deviceId)
            .put("publicKeyPem", publicKeyPem)
            .put("platform", "android")
            .put("label", label.take(80))
            .put("capabilities", capabilities)
        val challenge = request(base, "POST", "/v1/enrollment/challenges", accountToken, challengeRequest)
        val challengeId = challenge.getString("challengeId")
        val message = challenge.getString("message")

        val privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(
            PKCS8EncodedKeySpec(Base64.decode(privateKeyB64, Base64.NO_WRAP))
        )
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(privateKey)
        signer.update(message.toByteArray(StandardCharsets.UTF_8))
        val proof = Base64.encodeToString(signer.sign(), Base64.NO_WRAP)

        val completed = request(
            base, "POST", "/v1/enrollment/complete", accountToken,
            JSONObject()
                .put("challengeId", challengeId)
                .put("publicKeyPem", publicKeyPem)
                .put("signature", proof)
        )
        config.put("serverUrl", base)
        config.put("accountToken", accountToken)
        config.put("deviceToken", completed.getString("deviceToken"))
        config.put("commandServerPublicKeyPem", completed.getString("commandServerPublicKeyPem"))
        store.write(config)
        return deviceId
    }

    fun pollAndExecute(): Int {
        ensureEd25519Available()
        val config = store.read()
        val base = normalizeServerUrl(config.optString("serverUrl"))
        val token = config.optString("deviceToken")
        val deviceId = config.optString("deviceId")
        val serverKeyPem = config.optString("commandServerPublicKeyPem")
        require(token.isNotBlank() && deviceId.isNotBlank() && serverKeyPem.isNotBlank()) {
            "Enroll this device with AURA Recovery first."
        }

        val response = request(base, "GET", "/v1/device/commands", token, null)
        val commands = response.optJSONArray("commands") ?: JSONArray()
        var handled = 0
        var locationReported = false
        for (index in 0 until commands.length()) {
            val item = commands.optJSONObject(index) ?: continue
            val signed = item.optJSONObject("signedCommand") ?: continue
            val envelope = signed.optJSONObject("envelope") ?: continue
            val nonce = envelope.optString("nonce")
            val commandId = envelope.optString("commandId")
            if (!commandId.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                handled++
                continue
            }
            var result = "rejected"
            var detail = "Invalid command signature or envelope."
            try {
                if (!verifyCommand(signed, envelope, serverKeyPem, deviceId)) {
                    acknowledge(base, token, commandId, result, detail)
                    handled++
                    continue
                }
                if (!store.consumeNonce(nonce)) {
                    val previous = store.getProcessedCommand(commandId)
                    if (previous != null && previous.optString("nonce") == nonce) {
                        acknowledge(base, token, commandId, previous.optString("result", "rejected"), previous.optString("detail"))
                    } else {
                        acknowledge(base, token, commandId, "rejected", "Command nonce was already consumed.")
                    }
                    handled++
                    continue
                }
                when (envelope.getString("command")) {
                    "recovery.checkin" -> {
                        result = "executed"
                        detail = "Device checked in successfully."
                    }
                    "device.lock" -> {
                        val manager = app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                        if (!manager.isAdminActive(deviceAdmin)) {
                            result = "rejected"
                            detail = "AURA screen-lock permission is not enabled on this device."
                        } else {
                            manager.lockNow()
                            result = "executed"
                            detail = "Android screen lock requested by the account owner."
                        }
                    }
                    "location.refresh" -> {
                        if (!store.isLocationOptIn() || !hasBackgroundLocationPermission()) {
                            result = "rejected"
                            detail = "Location sharing is not enabled with background permission."
                        } else {
                            val location = RecoveryLocationProvider(app).getFreshLocation()
                            if (location == null) {
                                result = "failed"
                                detail = "No fresh location fix was available."
                            } else {
                                request(base, "POST", "/v1/device/checkin", token, JSONObject().put("location", location))
                                locationReported = true
                                result = "executed"
                                detail = "Fresh consented location check-in sent."
                            }
                        }
                    }
                    else -> {
                        result = "rejected"
                        detail = "This command is not implemented on this Android client."
                    }
                }
            } catch (error: Exception) {
                result = "failed"
                detail = (error.message ?: "Command processing failed").take(1000)
            }
            store.saveProcessedCommand(commandId, nonce, result, detail)
            acknowledge(base, token, commandId, result, detail)
            handled++
        }
        if (!locationReported) {
            val checkin = JSONObject()
            if (store.isLocationOptIn() && hasBackgroundLocationPermission()) {
                RecoveryLocationProvider(app).getFreshLocation()?.let { checkin.put("location", it) }
            }
            request(base, "POST", "/v1/device/checkin", token, checkin)
        }
        return handled
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT < 29 ||
            app.checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun verifyCommand(
        signed: JSONObject,
        envelope: JSONObject,
        serverKeyPem: String,
        expectedDeviceId: String
    ): Boolean {
        if (signed.optString("algorithm") != "Ed25519") return false
        if (envelope.optInt("version") != 1 || envelope.optString("deviceId") != expectedDeviceId) return false
        val command = envelope.optString("command")
        if (command !in setOf("session.revoke", "device.lock", "location.refresh", "recovery.checkin")) return false
        val issued = parseUtc(envelope.optString("issuedAt")) ?: return false
        val expires = parseUtc(envelope.optString("expiresAt")) ?: return false
        val now = System.currentTimeMillis()
        if (issued.time > now + 30_000 || expires.time <= now || expires.time <= issued.time) return false
        if (expires.time - issued.time > 15 * 60 * 1000) return false
        if (envelope.optString("nonce").length !in 20..128) return false
        if (envelope.optString("commandId").isBlank() || envelope.optString("requestedBy").isBlank()) return false

        val canonical = String.format(
            Locale.US,
            "{\"version\":%d,\"commandId\":%s,\"deviceId\":%s,\"command\":%s,\"issuedAt\":%s,\"expiresAt\":%s,\"nonce\":%s,\"requestedBy\":%s}",
            envelope.getInt("version"),
            JSONObject.quote(envelope.getString("commandId")),
            JSONObject.quote(envelope.getString("deviceId")),
            JSONObject.quote(envelope.getString("command")),
            JSONObject.quote(envelope.getString("issuedAt")),
            JSONObject.quote(envelope.getString("expiresAt")),
            JSONObject.quote(envelope.getString("nonce")),
            JSONObject.quote(envelope.getString("requestedBy"))
        )
        val keyBytes = Base64.decode(
            serverKeyPem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replace("\\s".toRegex(), ""),
            Base64.DEFAULT
        )
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(keyBytes))
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(publicKey)
        verifier.update(canonical.toByteArray(StandardCharsets.UTF_8))
        return verifier.verify(Base64.decode(signed.getString("signature"), Base64.DEFAULT))
    }

    private fun acknowledge(base: String, token: String, commandId: String, result: String, detail: String) {
        require(commandId.matches(Regex("^[0-9a-fA-F-]{36}$"))) { "Invalid command ID." }
        request(
            base, "POST", "/v1/device/commands/$commandId/ack", token,
            JSONObject().put("result", result).put("detail", detail.take(1000))
        )
    }

    private fun request(
        base: String,
        method: String,
        path: String,
        bearerToken: String,
        body: JSONObject?
    ): JSONObject {
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $bearerToken")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val json = JSONObject(payload)
            if (status !in 200..299) {
                throw IOException("AURA Recovery request failed ($status): ${json.optString("error", "request_failed")}")
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeServerUrl(value: String): String {
        val url = URL(value.trim().trimEnd('/'))
        require(url.protocol == "https" && !url.host.isNullOrBlank()) {
            "AURA Recovery must use a valid HTTPS server URL."
        }
        return url.toString().trimEnd('/')
    }

    private fun parseUtc(value: String): Date? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(value)
    } catch (_: Exception) { null }

    private fun pem(type: String, bytes: ByteArray): String {
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "-----BEGIN $type-----\n" + encoded.chunked(64).joinToString("\n") +
            "\n-----END $type-----"
    }
}
