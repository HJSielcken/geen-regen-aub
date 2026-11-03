package org.theharmen.geenregenaub.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** A position the phone already knew, and how old it is. */
internal data class KnownLocation(val lat: Double, val lon: Double, val ageMs: Long)

internal interface LocationProvider {
    /** The newest position the phone already has, of any age. Never waits; null without permission. */
    fun lastKnownLocation(): KnownLocation?

    /** A fresh coarse (about 1 km) fix as (lat, lon), waiting up to ~10 s, or null without one. */
    suspend fun freshLocation(): Pair<Double, Double>?
}

/**
 * Coarse location straight from the platform LocationManager: no Google Play services needed,
 * and a 1 km radar cell does not need GPS precision.
 */
internal class CoarseLocationProvider(private val context: Context) : LocationProvider {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun enabledProviders(): List<String> {
        if (manager == null) return emptyList()
        return buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    }

    @SuppressLint("MissingPermission") // checked by hasPermission()
    override fun lastKnownLocation(): KnownLocation? {
        if (manager == null || !hasPermission()) return null
        val newest = enabledProviders()
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?: return null
        val age = (System.currentTimeMillis() - newest.time).coerceAtLeast(0)
        return KnownLocation(newest.latitude, newest.longitude, age)
    }

    override suspend fun freshLocation(): Pair<Double, Double>? {
        if (manager == null || !hasPermission()) return null
        val provider = enabledProviders().firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return null
        val fix = withTimeoutOrNull(FIX_TIMEOUT_MS) { requestFix(provider) }
        return fix?.let { it.latitude to it.longitude }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFix(provider: String): Location? = suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        try {
            LocationManagerCompat.getCurrentLocation(manager!!, provider, signal, executor) { location ->
                if (cont.isActive) cont.resume(location)
            }
        } catch (e: SecurityException) {
            // Background without ACCESS_BACKGROUND_LOCATION, or permission revoked meanwhile.
            if (cont.isActive) cont.resume(null)
        }
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 10_000L
    }
}
