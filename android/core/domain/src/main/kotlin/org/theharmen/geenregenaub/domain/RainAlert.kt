package org.theharmen.geenregenaub.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** What the user chose for "rain coming" notifications. */
data class AlertSettings(
    val enabled: Boolean = false,
    /** Warn when rain starts within this many minutes. */
    val leadTime: Duration = Duration.ofMinutes(30),
    /** Ignore rain lighter than this. */
    val minIntensity: Intensity = Intensity.LIGHT,
    /** No notifications between [quietStart] and [quietEnd] (local time) when true. */
    val quietHours: Boolean = true,
    val quietStart: LocalTime = LocalTime.of(23, 0),
    val quietEnd: LocalTime = LocalTime.of(7, 0),
) {
    companion object {
        val LEAD_TIME_OPTIONS: List<Duration> = listOf(15L, 30L, 45L, 60L).map { Duration.ofMinutes(it) }
    }
}

/** What the background check should do after looking at a fresh forecast. */
sealed interface AlertDecision {
    /** Do nothing. [rememberedStart] is what to store for next time (null clears it). */
    data class Stay(val rememberedStart: Instant?) : AlertDecision

    /** Notify the user. Store [rain]'s start so the same shower does not notify twice. */
    data class Notify(val rain: Verdict.RainLater, val minutesUntil: Long) : AlertDecision {
        val rememberedStart: Instant get() = rain.startsAt
    }
}

/**
 * Decides whether a forecast deserves a "rain coming" notification.
 *
 * Rules: rain at or above the chosen intensity must start within the lead time; the same
 * shower is announced once (a start within [SAME_SHOWER] of the last announced one counts as
 * the same shower, because nowcasts shift a bit every run); the memory is cleared once a
 * forecast is fully dry; nothing is sent during quiet hours or from a stale forecast.
 */
object RainAlertPolicy {
    val SAME_SHOWER: Duration = Duration.ofMinutes(45)
    val MAX_FORECAST_AGE: Duration = Duration.ofMinutes(30)

    fun decide(
        forecast: RainForecast,
        settings: AlertSettings,
        lastAnnouncedStart: Instant?,
        now: Instant,
        zone: ZoneId,
    ): AlertDecision {
        if (!settings.enabled) return AlertDecision.Stay(lastAnnouncedStart)
        if (Duration.between(forecast.issuedAt, now) > MAX_FORECAST_AGE) {
            return AlertDecision.Stay(lastAnnouncedStart)
        }
        val upcoming = forecast.remainingAt(now) ?: return AlertDecision.Stay(lastAnnouncedStart)

        return when (val verdict = upcoming.verdict(settings.minIntensity.minMmPerHour)) {
            is Verdict.Dry -> AlertDecision.Stay(null)
            // Already raining: too late to warn. Keep the memory so the shower is not announced
            // again if it briefly drops below the threshold.
            is Verdict.RainingNow -> AlertDecision.Stay(lastAnnouncedStart ?: now)
            is Verdict.RainLater -> {
                val until = Duration.between(now, verdict.startsAt)
                val alreadyAnnounced = lastAnnouncedStart != null &&
                    Duration.between(lastAnnouncedStart, verdict.startsAt).abs() < SAME_SHOWER
                when {
                    until > settings.leadTime -> AlertDecision.Stay(lastAnnouncedStart)
                    alreadyAnnounced -> AlertDecision.Stay(lastAnnouncedStart)
                    settings.quietHours && isQuiet(now, zone, settings) -> AlertDecision.Stay(lastAnnouncedStart)
                    else -> AlertDecision.Notify(verdict, until.toMinutes().coerceAtLeast(0))
                }
            }
        }
    }

    internal fun isQuiet(now: Instant, zone: ZoneId, settings: AlertSettings): Boolean {
        val time = now.atZone(zone).toLocalTime()
        val start = settings.quietStart
        val end = settings.quietEnd
        return if (start <= end) {
            !time.isBefore(start) && time.isBefore(end)
        } else {
            !time.isBefore(start) || time.isBefore(end)
        }
    }
}

/** Dutch notification wording. */
object AlertText {
    fun title(minutesUntil: Long): String = when {
        minutesUntil <= 2 -> "Het gaat zo regenen"
        else -> "Regen over $minutesUntil minuten"
    }

    fun body(rain: Verdict.RainLater, zone: ZoneId): String = VerdictText.dutch(rain, zone)
}
