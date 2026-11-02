package org.theharmen.geenregenaub.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WeatherTest {
    @Test
    fun `wind direction uses Dutch compass letters`() {
        assertEquals("N", Wind.direction(0.0))
        assertEquals("N", Wind.direction(350.0))
        assertEquals("NO", Wind.direction(30.0))
        assertEquals("O", Wind.direction(90.0))
        assertEquals("ZO", Wind.direction(135.0))
        assertEquals("Z", Wind.direction(180.0))
        assertEquals("ZW", Wind.direction(225.0))
        assertEquals("W", Wind.direction(270.0))
        assertEquals("NW", Wind.direction(315.0))
        assertEquals("N", Wind.direction(360.0))
        assertEquals("NW", Wind.direction(-45.0))
    }

    @Test
    fun `beaufort scale from km per hour`() {
        assertEquals(0, Wind.beaufort(0.0))
        assertEquals(1, Wind.beaufort(3.0))
        assertEquals(3, Wind.beaufort(15.0))
        assertEquals(4, Wind.beaufort(20.0))
        assertEquals(6, Wind.beaufort(45.0))
        assertEquals(11, Wind.beaufort(110.0))
        assertEquals(12, Wind.beaufort(130.0))
    }

    @Test
    fun `wmo codes map to conditions`() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromWmo(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromWmo(2))
        assertEquals(WeatherCondition.FOG, WeatherCondition.fromWmo(48))
        assertEquals(WeatherCondition.RAIN_SHOWERS, WeatherCondition.fromWmo(81))
        assertEquals(WeatherCondition.THUNDERSTORM_HAIL, WeatherCondition.fromWmo(99))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWmo(42))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWmo(null))
        assertEquals("Helder", WeatherCondition.CLEAR.dutch(isDay = false))
        assertEquals("Zonnig", WeatherCondition.CLEAR.dutch(isDay = true))
    }

    @Test
    fun `temperature is rounded`() {
        assertEquals("12°", formatTemperature(11.6))
        assertEquals("-3°", formatTemperature(-2.7))
        assertEquals("0°", formatTemperature(-0.4))
    }

    private fun hour(time: String) = HourWeather(
        time = Instant.parse(time),
        temperature = 10.0,
        condition = WeatherCondition.CLEAR,
        isDay = true,
        precipitationProbability = null,
        precipitationMm = null,
        windSpeedKmh = null,
        windDirectionDegrees = null,
    )

    @Test
    fun `hours start at the current hour`() {
        val forecast = WeatherForecast(
            current = null,
            hours = listOf("2026-11-02T21:00:00Z", "2026-11-02T22:00:00Z", "2026-11-02T23:00:00Z", "2026-11-03T00:00:00Z").map(::hour),
            days = emptyList(),
        )
        val next = forecast.hoursFrom(Instant.parse("2026-11-02T22:40:00Z"), 2)
        assertEquals(listOf(Instant.parse("2026-11-02T22:00:00Z"), Instant.parse("2026-11-02T23:00:00Z")), next.map { it.time })

        // 23:00Z and 00:00Z are both on 3 November in Amsterdam (UTC+1).
        val nov3 = forecast.hoursOn(LocalDate.of(2026, 11, 3), ZoneId.of("Europe/Amsterdam"))
        assertEquals(2, nov3.size)
    }
}
