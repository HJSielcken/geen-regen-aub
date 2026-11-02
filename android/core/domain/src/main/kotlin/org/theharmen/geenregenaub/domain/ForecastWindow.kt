package org.theharmen.geenregenaub.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.ln

/** True when the forecast window has ended or is too old to trust. */
fun RainForecast.isStale(now: Instant, maxAge: Duration = Duration.ofMinutes(45)): Boolean =
    Duration.between(issuedAt, now) > maxAge || remainingAt(now) == null

/**
 * Vertical scale for rain graphs. Logarithmic, like most radar apps, so drizzle is visible
 * and a cloudburst does not flatten everything else. 0 maps to 0, [TOP_MM_PER_HOUR] and up to 1.
 */
object RainScale {
    const val TOP_MM_PER_HOUR = 20.0
    private val top = ln(1.0 + TOP_MM_PER_HOUR)

    fun fraction(mmPerHour: Double): Float {
        if (mmPerHour <= 0.0) return 0f
        return (ln(1.0 + mmPerHour) / top).coerceIn(0.0, 1.0).toFloat()
    }
}
