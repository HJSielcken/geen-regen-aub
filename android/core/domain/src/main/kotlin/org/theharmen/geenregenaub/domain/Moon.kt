package org.theharmen.geenregenaub.domain

import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

/** The moon as seen on [at]: how far through its cycle it is and how much of it is lit. */
data class MoonPhase(
    val at: Instant,
    /** 0 at new moon, 0.5 at full moon, back to 1 at the next new moon. */
    val age: Double,
    val previousNewMoon: Instant,
    val nextNewMoon: Instant,
    val nextFullMoon: Instant,
) {
    /** Lit fraction of the disc, 0–1. */
    val illumination: Double get() = (1 - cos(2 * PI * age)) / 2

    val waxing: Boolean get() = age < 0.5

    val name: MoonPhaseName get() = MoonPhaseName.entries[Math.floorMod((age * 8).roundToLong(), 8L).toInt()]
}

/** The eight named phases, in order from new moon. */
enum class MoonPhaseName(val dutch: String) {
    NEW("Nieuwe maan"),
    WAXING_CRESCENT("Wassende sikkel"),
    FIRST_QUARTER("Eerste kwartier"),
    WAXING_GIBBOUS("Wassende maan"),
    FULL("Volle maan"),
    WANING_GIBBOUS("Afnemende maan"),
    LAST_QUARTER("Laatste kwartier"),
    WANING_CRESCENT("Afnemende sikkel"),
}

/**
 * New and full moons from Meeus, "Astronomical Algorithms" ch. 49, with the main periodic terms:
 * good to a few minutes, which is plenty for a date on a widget. No network needed.
 */
object Moon {
    private const val SYNODIC_DAYS = 29.530588861
    private const val UNIX_EPOCH_JD = 2440587.5

    fun phase(at: Instant): MoonPhase {
        val previousNew = previousNewMoon(at)
        val nextNew = nextPhase(at, full = false)
        val nextFull = nextPhase(at, full = true)
        val span = (nextNew.epochSecond - previousNew.epochSecond).toDouble()
        val age = ((at.epochSecond - previousNew.epochSecond) / span).coerceIn(0.0, 1.0)
        return MoonPhase(at, age, previousNew, nextNew, nextFull)
    }

    /** The first new (or full) moon strictly after [after]. */
    fun nextPhase(after: Instant, full: Boolean): Instant {
        var k = floor(approximateK(after)) + if (full) 0.5 else 0.0
        // Step back a little and walk forward, so the estimate's error never skips one.
        k -= 2
        while (true) {
            val time = instantOf(k)
            if (time.isAfter(after)) return time
            k += 1
        }
    }

    private fun previousNewMoon(at: Instant): Instant {
        var k = floor(approximateK(at)) + 2
        while (true) {
            val time = instantOf(k)
            if (!time.isAfter(at)) return time
            k -= 1
        }
    }

    /** Lunations since the new moon of 6 January 2000. */
    private fun approximateK(at: Instant): Double {
        val jd = at.epochSecond / 86400.0 + UNIX_EPOCH_JD
        return (jd - 2451550.09766) / SYNODIC_DAYS
    }

    private fun instantOf(k: Double): Instant {
        val jd = julianDay(k)
        return Instant.ofEpochSecond(((jd - UNIX_EPOCH_JD) * 86400.0).roundToLong())
    }

    /** JDE of new moon (whole k) or full moon (k + 0.5). */
    internal fun julianDay(k: Double): Double {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val mean = 2451550.09766 + SYNODIC_DAYS * k + 0.00015437 * t2 - 0.000000150 * t3 + 0.00000000073 * t2 * t2
        val e = 1 - 0.002516 * t - 0.0000074 * t2
        val m = rad(2.5534 + 29.10535670 * k - 0.0000014 * t2)
        val mp = rad(201.5643 + 385.81693528 * k + 0.0107582 * t2 + 0.00001238 * t3)
        val f = rad(160.7108 + 390.67050284 * k - 0.0016118 * t2 - 0.00000227 * t3)
        val om = rad(124.7746 - 1.56375588 * k + 0.0020672 * t2)
        val full = k - floor(k) > 0.25
        val a = if (full) FULL_TERMS else NEW_TERMS
        val correction = a[0] * sin(mp) +
            a[1] * e * sin(m) +
            a[2] * sin(2 * mp) +
            a[3] * sin(2 * f) +
            a[4] * e * sin(mp - m) +
            a[5] * e * sin(mp + m) +
            a[6] * e * e * sin(2 * m) -
            0.00111 * sin(mp - 2 * f) -
            0.00057 * sin(mp + 2 * f) +
            0.00056 * e * sin(2 * mp + m) -
            0.00042 * sin(3 * mp) +
            0.00042 * e * sin(m + 2 * f) +
            0.00038 * e * sin(m - 2 * f) -
            0.00024 * e * sin(2 * mp - m) -
            0.00017 * sin(om)
        return mean + correction
    }

    private val NEW_TERMS = doubleArrayOf(-0.40720, 0.17241, 0.01608, 0.01039, 0.00739, -0.00514, 0.00208)
    private val FULL_TERMS = doubleArrayOf(-0.40614, 0.17302, 0.01614, 0.01043, 0.00734, -0.00515, 0.00209)

    private fun rad(degrees: Double) = Math.toRadians(degrees)
}
