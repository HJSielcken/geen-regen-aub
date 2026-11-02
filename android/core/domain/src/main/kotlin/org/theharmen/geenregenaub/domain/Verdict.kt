package org.theharmen.geenregenaub.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The one-line answer to "will it rain here soon?". */
sealed interface Verdict {
    /** No rain above the threshold in the whole forecast window. */
    data class Dry(val window: Duration) : Verdict

    /** It is raining now; [until] is null when it lasts beyond the window. */
    data class RainingNow(val intensity: Intensity, val until: Instant?) : Verdict

    /** Rain starts later inside the window. */
    data class RainLater(val startsAt: Instant, val intensity: Intensity, val duration: Duration?) : Verdict
}

/** Default threshold: drizzle counts as rain. */
const val DEFAULT_THRESHOLD_MM_PER_HOUR = 0.1

fun RainForecast.verdict(thresholdMmPerHour: Double = DEFAULT_THRESHOLD_MM_PER_HOUR): Verdict {
    val window = step.multipliedBy(intensities.size.toLong())
    val first = intensities.indexOfFirst { it >= thresholdMmPerHour }
    if (first < 0) return Verdict.Dry(window)

    val endExclusive = (first until intensities.size).firstOrNull { intensities[it] < thresholdMmPerHour }
    val spell = intensities.subList(first, endExclusive ?: intensities.size)
    val intensity = Intensity.of(spell.max()) ?: Intensity.LIGHT
    val end = endExclusive?.let { timeOf(it) }

    return if (first == 0) {
        Verdict.RainingNow(intensity, end)
    } else {
        Verdict.RainLater(timeOf(first), intensity, end?.let { Duration.between(timeOf(first), it) })
    }
}

/** Dutch wording for a verdict, e.g. "Droog de komende 2 uur" or "Lichte regen vanaf 14:35". */
object VerdictText {
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    fun dutch(verdict: Verdict, zone: ZoneId): String = when (verdict) {
        is Verdict.Dry -> "Droog de komende ${window(verdict.window)}"
        is Verdict.RainingNow ->
            if (verdict.until == null) {
                "${noun(verdict.intensity)} houdt voorlopig aan"
            } else {
                "${noun(verdict.intensity)} tot ${clock.format(verdict.until.atZone(zone))}"
            }
        is Verdict.RainLater -> buildString {
            append("${noun(verdict.intensity)} vanaf ${clock.format(verdict.startsAt.atZone(zone))}")
            verdict.duration?.let { append(", ongeveer ${it.toMinutes()} min") }
        }
    }

    private fun noun(intensity: Intensity) = when (intensity) {
        Intensity.LIGHT -> "Lichte regen"
        Intensity.MODERATE -> "Regen"
        Intensity.HEAVY -> "Zware regen"
    }

    private fun window(duration: Duration): String {
        val minutes = duration.toMinutes()
        return if (minutes % 60 == 0L) "${minutes / 60} uur" else "$minutes minuten"
    }
}
