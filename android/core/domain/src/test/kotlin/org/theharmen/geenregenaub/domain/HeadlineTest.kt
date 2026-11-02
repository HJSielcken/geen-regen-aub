package org.theharmen.geenregenaub.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class HeadlineTest {
    private val issued = Instant.parse("2026-11-02T13:00:00Z")
    private val fiveMin = Duration.ofMinutes(5)

    private fun forecast(vararg values: Double) =
        RainForecast(issued, fiveMin, values.toList(), ForecastSource.KNMI)

    @Test
    fun `remaining keeps the step that contains now`() {
        val f = forecast(1.0, 2.0, 3.0, 4.0)
        val rest = f.remainingAt(issued.plusSeconds(7 * 60))!!
        assertEquals(issued.plus(fiveMin), rest.issuedAt)
        assertEquals(listOf(2.0, 3.0, 4.0), rest.intensities)
    }

    @Test
    fun `remaining at the issue time is the whole forecast`() {
        val f = forecast(1.0, 2.0)
        assertEquals(f, f.remainingAt(issued))
        assertEquals(f, f.remainingAt(issued.minusSeconds(60)))
    }

    @Test
    fun `remaining is null once the forecast is over`() {
        assertNull(forecast(1.0, 2.0).remainingAt(issued.plusSeconds(10 * 60)))
    }

    @Test
    fun `headline counts minutes from now`() {
        val values = DoubleArray(7) { 0.0 } + doubleArrayOf(1.0, 1.0) + DoubleArray(5) { 0.0 }
        val verdict = forecast(*values).verdict()
        assertEquals("Regen over 35 min", Headline.dutch(verdict, issued))
        assertEquals("Regen over 32 min", Headline.dutch(verdict, issued.plusSeconds(3 * 60)))
        assertEquals("Regen begint nu", Headline.dutch(verdict, issued.plusSeconds(40 * 60)))
    }

    @Test
    fun `headline for dry and raining`() {
        assertEquals("Droog", Headline.dutch(forecast(0.0, 0.0).verdict(), issued))
        assertEquals("Het regent nu", Headline.dutch(forecast(3.0, 0.0).verdict(), issued))
        assertEquals("Het giet nu", Headline.dutch(forecast(12.0).verdict(), issued))
    }

    @Test
    fun `minutes round up`() {
        assertEquals(1, Headline.minutesUntil(issued, issued.plusSeconds(1)))
        assertEquals(0, Headline.minutesUntil(issued, issued.minusSeconds(30)))
    }
}
