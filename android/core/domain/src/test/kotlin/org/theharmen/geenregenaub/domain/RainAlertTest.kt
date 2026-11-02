package org.theharmen.geenregenaub.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class RainAlertTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val issued = Instant.parse("2026-11-02T13:00:00Z") // 14:00 in Amsterdam
    private val fiveMin = Duration.ofMinutes(5)
    private val on = AlertSettings(enabled = true, leadTime = Duration.ofMinutes(30))

    private fun forecast(values: List<Double>, at: Instant = issued) =
        RainForecast(at, fiveMin, values, ForecastSource.KNMI)

    /** Dry for [dryMinutes], then rain of [mm] for the rest of a 2-hour window. */
    private fun rainAfter(dryMinutes: Int, mm: Double = 1.0, at: Instant = issued): RainForecast {
        val drySteps = dryMinutes / 5
        return forecast(List(24) { if (it < drySteps) 0.0 else mm }, at)
    }

    private fun decide(
        f: RainForecast,
        settings: AlertSettings = on,
        last: Instant? = null,
        now: Instant = issued,
    ) = RainAlertPolicy.decide(f, settings, last, now, zone)

    @Test
    fun `rain inside the lead time notifies`() {
        val decision = decide(rainAfter(20))
        assertTrue(decision is AlertDecision.Notify)
        decision as AlertDecision.Notify
        assertEquals(20L, decision.minutesUntil)
        assertEquals(issued.plus(Duration.ofMinutes(20)), decision.rememberedStart)
        assertEquals("Regen over 20 minuten", AlertText.title(decision.minutesUntil))
        assertEquals("Lichte regen vanaf 14:20", AlertText.body(decision.rain, zone))
    }

    @Test
    fun `rain beyond the lead time waits`() {
        assertEquals(AlertDecision.Stay(null), decide(rainAfter(45)))
    }

    @Test
    fun `disabled alerts never notify`() {
        assertEquals(AlertDecision.Stay(null), decide(rainAfter(10), settings = on.copy(enabled = false)))
    }

    @Test
    fun `the same shower is announced once even if it shifts`() {
        val first = decide(rainAfter(20)) as AlertDecision.Notify
        val later = decide(rainAfter(15, at = issued.plus(Duration.ofMinutes(15))), last = first.rememberedStart,
            now = issued.plus(Duration.ofMinutes(15)))
        assertEquals(AlertDecision.Stay(first.rememberedStart), later)
    }

    @Test
    fun `a new shower after a dry forecast notifies again`() {
        val first = decide(rainAfter(20)) as AlertDecision.Notify
        val dry = decide(forecast(List(24) { 0.0 }), last = first.rememberedStart)
        assertEquals(AlertDecision.Stay(null), dry)
        assertTrue(decide(rainAfter(25), last = null) is AlertDecision.Notify)
    }

    @Test
    fun `rain lighter than the chosen intensity is ignored`() {
        val settings = on.copy(minIntensity = Intensity.MODERATE)
        assertEquals(AlertDecision.Stay(null), decide(rainAfter(10, mm = 1.0), settings))
        assertTrue(decide(rainAfter(10, mm = 3.0), settings) is AlertDecision.Notify)
    }

    @Test
    fun `already raining does not notify and is remembered`() {
        assertEquals(AlertDecision.Stay(issued), decide(rainAfter(0)))
    }

    @Test
    fun `quiet hours suppress notifications`() {
        val night = Instant.parse("2026-11-02T23:30:00Z") // 00:30 in Amsterdam
        assertEquals(AlertDecision.Stay(null), decide(rainAfter(10, at = night), now = night))
        val noQuiet = on.copy(quietHours = false)
        assertTrue(decide(rainAfter(10, at = night), settings = noQuiet, now = night) is AlertDecision.Notify)
    }

    @Test
    fun `a stale forecast does not notify`() {
        val now = issued.plus(Duration.ofMinutes(40))
        assertEquals(AlertDecision.Stay(null), decide(rainAfter(50), now = now))
    }

    @Test
    fun `time until rain counts from now, not from issue time`() {
        val now = issued.plus(Duration.ofMinutes(10))
        val decision = decide(rainAfter(40), now = now) as AlertDecision.Notify
        assertEquals(30L, decision.minutesUntil)
    }

    @Test
    fun `quiet hours wrap around midnight`() {
        fun at(hhmm: String) = Instant.parse("2026-11-02T${hhmm}:00Z").atZone(ZoneId.of("UTC")).toInstant()
        val utc = ZoneId.of("UTC")
        assertTrue(RainAlertPolicy.isQuiet(at("23:00"), utc, on))
        assertTrue(RainAlertPolicy.isQuiet(at("06:59"), utc, on))
        assertTrue(!RainAlertPolicy.isQuiet(at("07:00"), utc, on))
        assertTrue(!RainAlertPolicy.isQuiet(at("22:59"), utc, on))
    }

    @Test
    fun `from drops steps that have passed`() {
        val f = forecast(List(24) { it.toDouble() })
        val trimmed = f.from(issued.plus(Duration.ofMinutes(12)))!!
        assertEquals(issued.plus(Duration.ofMinutes(10)), trimmed.issuedAt)
        assertEquals(22, trimmed.intensities.size)
        assertEquals(2.0, trimmed.intensities.first(), 0.0)
        assertNull(f.from(issued.plus(Duration.ofHours(2))))
        assertEquals(f, f.from(issued.minusSeconds(60)))
    }

    @Test
    fun `rain scale is zero for dry, one at the top and monotonic`() {
        assertEquals(0f, RainScale.fraction(0.0), 0f)
        assertEquals(1f, RainScale.fraction(50.0), 0f)
        assertTrue(RainScale.fraction(0.1) > 0f)
        assertTrue(RainScale.fraction(1.0) < RainScale.fraction(5.0))
    }
}
