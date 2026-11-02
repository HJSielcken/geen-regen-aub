package org.theharmen.geenregenaub.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class VerdictTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val issued = Instant.parse("2026-11-02T13:00:00Z") // 14:00 in Amsterdam
    private val fiveMin = Duration.ofMinutes(5)

    private fun forecast(vararg values: Double) =
        RainForecast(issued, fiveMin, values.toList(), ForecastSource.KNMI)

    private fun dry(steps: Int) = DoubleArray(steps) { 0.0 }

    @Test
    fun `all zeros is dry for the whole window`() {
        val verdict = forecast(*dry(24)).verdict()
        assertEquals(Verdict.Dry(Duration.ofHours(2)), verdict)
        assertEquals("Droog de komende 2 uur", VerdictText.dutch(verdict, zone))
    }

    @Test
    fun `values below the threshold count as dry`() {
        assertEquals(Verdict.Dry(Duration.ofMinutes(15)), forecast(0.05, 0.0, 0.09).verdict())
    }

    @Test
    fun `rain later reports start, intensity and duration`() {
        // dry 35 min, then 20 min of moderate rain, then dry
        val values = dry(7) + doubleArrayOf(1.0, 3.0, 2.0, 0.5) + dry(13)
        val verdict = forecast(*values).verdict()
        assertEquals(
            Verdict.RainLater(issued.plus(Duration.ofMinutes(35)), Intensity.MODERATE, Duration.ofMinutes(20)),
            verdict,
        )
        assertEquals("Regen vanaf 14:35, ongeveer 20 min", VerdictText.dutch(verdict, zone))
    }

    @Test
    fun `raining now until it stops`() {
        val verdict = forecast(0.4, 0.3, 0.0, 0.0).verdict()
        assertEquals(Verdict.RainingNow(Intensity.LIGHT, issued.plus(Duration.ofMinutes(10))), verdict)
        assertEquals("Lichte regen tot 14:10", VerdictText.dutch(verdict, zone))
    }

    @Test
    fun `heavy rain that outlasts the window`() {
        val verdict = forecast(12.0, 15.0, 11.0).verdict()
        assertEquals(Verdict.RainingNow(Intensity.HEAVY, null), verdict)
        assertEquals("Zware regen houdt voorlopig aan", VerdictText.dutch(verdict, zone))
    }

    @Test
    fun `intensity bands`() {
        assertEquals(null, Intensity.of(0.0))
        assertEquals(Intensity.LIGHT, Intensity.of(0.1))
        assertEquals(Intensity.MODERATE, Intensity.of(2.5))
        assertEquals(Intensity.HEAVY, Intensity.of(25.0))
    }
}
