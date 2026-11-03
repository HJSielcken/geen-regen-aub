package org.theharmen.geenregenaub.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class MoonTest {
    private fun assertNear(expected: String, actual: Instant) {
        val off = Duration.between(Instant.parse(expected), actual).abs()
        assertTrue("$actual is not near $expected", off < Duration.ofMinutes(10))
    }

    @Test
    fun `new and full moons match the published times`() {
        // USNO: new moon 9 Feb 2024 22:59 UTC, full moon 24 Feb 2024 12:30 UTC.
        assertNear("2024-02-09T22:59:00Z", Moon.nextPhase(Instant.parse("2024-02-01T00:00:00Z"), full = false))
        assertNear("2024-02-24T12:30:00Z", Moon.nextPhase(Instant.parse("2024-02-10T00:00:00Z"), full = true))
    }

    @Test
    fun `phase between new and full moon is waxing`() {
        val phase = Moon.phase(Instant.parse("2024-02-17T00:00:00Z"))
        assertTrue(phase.waxing)
        assertEquals(MoonPhaseName.FIRST_QUARTER, phase.name)
        assertTrue(phase.illumination in 0.4..0.7)
    }

    @Test
    fun `phase on the night of a full moon`() {
        val phase = Moon.phase(Instant.parse("2024-02-24T12:00:00Z"))
        assertEquals(MoonPhaseName.FULL, phase.name)
        assertTrue(phase.illumination > 0.99)
        assertTrue(phase.nextNewMoon.isAfter(phase.at))
        assertTrue(phase.previousNewMoon.isBefore(phase.at))
    }

    @Test
    fun `air quality bands`() {
        assertEquals(AirQualityLevel.GOOD, AirQualityLevel.of(14))
        assertEquals(AirQualityLevel.FAIR, AirQualityLevel.of(21))
        assertEquals(AirQualityLevel.EXTREMELY_POOR, AirQualityLevel.of(140))
    }
}
