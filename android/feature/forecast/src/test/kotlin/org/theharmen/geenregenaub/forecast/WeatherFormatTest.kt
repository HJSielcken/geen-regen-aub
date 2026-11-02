package org.theharmen.geenregenaub.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WeatherFormatTest {
    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun `days read as today, tomorrow, then weekday and date`() {
        assertEquals("Vandaag", WeatherFormat.day(today, today, "Vandaag", "Morgen"))
        assertEquals("Morgen", WeatherFormat.day(today.plusDays(1), today, "Vandaag", "Morgen"))
        assertEquals("wo 4 nov", WeatherFormat.day(today.plusDays(2), today, "Vandaag", "Morgen"))
        assertEquals("wo", WeatherFormat.weekday(today.plusDays(2)))
    }

    @Test
    fun `wind shows direction and beaufort`() {
        assertEquals("ZW 4", WeatherFormat.wind(22.0, 225.0))
        assertEquals("4", WeatherFormat.wind(22.0, null))
        assertNull(WeatherFormat.wind(null, 225.0))
        assertEquals("ZW 4 Bft · 22 km/u", WeatherFormat.windLong(22.0, 225.0))
    }

    @Test
    fun `times and amounts use Dutch notation`() {
        val zone = ZoneId.of("Europe/Amsterdam")
        assertEquals("07:32", WeatherFormat.time(Instant.parse("2026-11-02T06:32:00Z"), zone))
        assertEquals("7", WeatherFormat.hour(Instant.parse("2026-11-02T06:32:00Z"), zone))
        assertEquals("2,4 mm", WeatherFormat.mm(2.4))
    }
}
