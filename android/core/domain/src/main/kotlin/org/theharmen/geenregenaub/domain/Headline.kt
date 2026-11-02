package org.theharmen.geenregenaub.domain

import java.time.Duration
import java.time.Instant

/**
 * The part of the forecast that still lies ahead at [now]: drops the steps that have fully passed,
 * keeping the step that contains [now]. Returns null when the whole forecast is in the past.
 */
fun RainForecast.remainingAt(now: Instant): RainForecast? {
    val passed = (0 until intensities.size).count { !timeOf(it + 1).isAfter(now) }
    if (passed >= intensities.size) return null
    if (passed == 0) return this
    return copy(issuedAt = timeOf(passed), intensities = intensities.drop(passed))
}

/** The big, glanceable answer: "Droog", "Regen over 25 min", "Het regent nu". */
object Headline {
    fun dutch(verdict: Verdict, now: Instant): String = when (verdict) {
        is Verdict.Dry -> "Droog"
        is Verdict.RainingNow -> when (verdict.intensity) {
            Intensity.LIGHT -> "Het miezert nu"
            Intensity.MODERATE -> "Het regent nu"
            Intensity.HEAVY -> "Het giet nu"
        }
        is Verdict.RainLater -> {
            val minutes = minutesUntil(now, verdict.startsAt)
            if (minutes <= 0) "Regen begint nu" else "Regen over $minutes min"
        }
    }

    /** Whole minutes from [now] until [at], rounded up so "over 1 min" never means "already". */
    fun minutesUntil(now: Instant, at: Instant): Long {
        val seconds = Duration.between(now, at).seconds
        return if (seconds <= 0) 0 else (seconds + 59) / 60
    }
}
