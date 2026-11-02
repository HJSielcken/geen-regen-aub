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

internal interface LocationProvider {
    /** A coarse (about 1 km) fix as (lat, lon), or null without permission or a fix. */
    suspend fun currentLocation(): Pair<Double, Double>?
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

    @SuppressLint("MissingPermission") // checked by hasPermission()
    override suspend fun currentLocation(): Pair<Double, Double>? {
        if (manager == null || !hasPermission()) return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        val recent = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time < MAX_AGE_MS }
        if (recent != null) return recent.latitude to recent.longitude

        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return null
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
        const val MAX_AGE_MS = 10 * 60 * 1000L
        const val FIX_TIMEOUT_MS = 10_000L
    }
}
