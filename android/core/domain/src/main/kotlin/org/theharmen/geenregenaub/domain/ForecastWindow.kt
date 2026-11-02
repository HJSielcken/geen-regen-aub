package org.theharmen.geenregenaub.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.ln

/**
 * The part of the forecast that is still ahead of [now]: steps that ended before [now] are
 * dropped, so a cached forecast keeps giving the right answer as it ages.
 * Returns null when nothing is left.
 */
fun RainForecast.from(now: Instant): RainForecast? {
    if (!now.isAfter(issuedAt)) return this
    val elapsedSteps = (Duration.between(issuedAt, now).toMillis() / step.toMillis()).toInt()
    if (elapsedSteps >= intensities.size) return null
    if (elapsedSteps == 0) return this
    return copy(issuedAt = timeOf(elapsedSteps), intensities = intensities.drop(elapsedSteps))
}

/** True when the forecast window has ended or is too old to trust. */
fun RainForecast.isStale(now: Instant, maxAge: Duration = Duration.ofMinutes(45)): Boolean =
    Duration.between(issuedAt, now) > maxAge || from(now) == null

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
