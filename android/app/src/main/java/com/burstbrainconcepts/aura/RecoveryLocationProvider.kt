package com.burstbrainconcepts.aura

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Obtains a fresh fix only after explicit in-app consent and Android location permissions. */
class RecoveryLocationProvider(private val context: Context) {
    fun getFreshLocation(): JSONObject? {
        if (!hasForegroundPermission() || !hasBackgroundPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { provider ->
                try { manager.isProviderEnabled(provider) } catch (_: Exception) { false }
            }

        val location = if (Build.VERSION.SDK_INT >= 30 && providers.isNotEmpty()) {
            requestCurrentFix(manager, providers)
        } else {
            mostRecentKnown(manager, providers)
        } ?: return null

        val now = System.currentTimeMillis()
        if (location.time > now + 30_000 || now - location.time > MAX_AGE_MS) return null
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return null

        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(location.time))
        return JSONObject()
            .put("latitude", location.latitude)
            .put("longitude", location.longitude)
            .put("accuracyM", location.accuracy.toDouble())
            .put("capturedAt", timestamp)
    }

    private fun requestCurrentFix(manager: LocationManager, providers: List<String>): Location? {
        val lock = Any()
        var best: Location? = null
        val completed = CountDownLatch(1)
        val remaining = AtomicInteger(providers.size)
        val executor = Executors.newFixedThreadPool(providers.size.coerceAtLeast(1))
        val cancellation = CancellationSignal()
        try {
            providers.forEach { provider ->
                try {
                    manager.getCurrentLocation(provider, cancellation, executor) { candidate ->
                        if (candidate != null) {
                            synchronized(lock) {
                                if (best == null || candidate.accuracy < best!!.accuracy) best = candidate
                            }
                        }
                        if (remaining.decrementAndGet() == 0) completed.countDown()
                    }
                } catch (_: SecurityException) {
                    if (remaining.decrementAndGet() == 0) completed.countDown()
                } catch (_: IllegalArgumentException) {
                    if (remaining.decrementAndGet() == 0) completed.countDown()
                }
            }
            completed.await(15, TimeUnit.SECONDS)
            return synchronized(lock) { best }
        } finally {
            cancellation.cancel()
            executor.shutdownNow()
        }
    }

    private fun mostRecentKnown(manager: LocationManager, providers: List<String>): Location? {
        return providers.mapNotNull { provider ->
            try { manager.getLastKnownLocation(provider) } catch (_: Exception) { null }
        }.maxByOrNull { it.time }
    }

    private fun hasForegroundPermission(): Boolean {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasBackgroundPermission(): Boolean {
        return Build.VERSION.SDK_INT < 29 ||
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val MAX_AGE_MS = 5 * 60 * 1000L
    }
}
