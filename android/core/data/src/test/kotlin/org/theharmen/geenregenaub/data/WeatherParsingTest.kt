package org.theharmen.geenregenaub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.theharmen.geenregenaub.data.weather.OpenMeteoJson
import org.theharmen.geenregenaub.domain.WeatherCondition
import java.time.Instant
import java.time.LocalDate

class WeatherParsingTest {
    // Trimmed real answer for Utrecht (timezone=auto, timeformat=unixtime), with a null hour added.
    private val body = """
        {"latitude":52.1,"longitude":5.12,"utc_offset_seconds":3600,"timezone":"Europe/Amsterdam",
         "current_units":{"temperature_2m":"°C"},
         "current":{"time":1793653200,"interval":900,"temperature_2m":9.4,"apparent_temperature":6.8,
                    "weather_code":3,"is_day":0,"wind_speed_10m":18.7,"wind_gusts_10m":38.2,
                    "wind_direction_10m":226,"relative_humidity_2m":87},
         "hourly":{"time":[1793653200,1793656800,1793660400],
                   "temperature_2m":[9.4,9.1,null],
                   "weather_code":[3,61,80],
                   "is_day":[0,0,0],
                   "precipitation_probability":[10,55,null],
                   "precipitation":[0.0,0.4,1.2],
                   "wind_speed_10m":[18.7,20.1,22.0],
                   "wind_direction_10m":[226,230,235]},
         "daily":{"time":[1793574000,1793660400],
                  "weather_code":[61,2],
                  "temperature_2m_max":[12.3,10.8],
                  "temperature_2m_min":[7.1,5.4],
                  "precipitation_sum":[2.4,0.0],
                  "precipitation_probability_max":[80,5],
                  "wind_speed_10m_max":[25.0,15.2],
                  "wind_gusts_10m_max":[52.0,31.0],
                  "wind_direction_10m_dominant":[220,250],
                  "sunrise":[1793598600,1793685060],
                  "sunset":[1793633700,1793720040],
                  "uv_index_max":[1.2,1.5]}}
    """.trimIndent()

    @Test
    fun `parses current, hourly and daily`() {
        val forecast = OpenMeteoJson.parse(body)

        val current = forecast.current!!
        assertEquals(9.4, current.temperature, 0.0)
        assertEquals(WeatherCondition.OVERCAST, current.condition)
        assertFalse(current.isDay)
        assertEquals(87, current.humidityPercent)

        // The hour without a temperature is skipped.
        assertEquals(2, forecast.hours.size)
        assertEquals(Instant.ofEpochSecond(1793656800), forecast.hours[1].time)
        assertEquals(WeatherCondition.LIGHT_RAIN, forecast.hours[1].condition)
        assertEquals(55, forecast.hours[1].precipitationProbability)

        // Local midnight (UTC+1) maps back to the calendar date.
        assertEquals(LocalDate.of(2026, 11, 2), forecast.days[0].date)
        assertEquals(LocalDate.of(2026, 11, 3), forecast.days[1].date)
        assertEquals(12.3, forecast.days[0].temperatureMax, 0.0)
        assertEquals(80, forecast.days[0].precipitationProbability)
        assertTrue(forecast.days[0].sunrise!!.isBefore(forecast.days[0].sunset))
    }

    @Test
    fun `missing sections give an empty forecast`() {
        val forecast = OpenMeteoJson.parse("""{"utc_offset_seconds":0}""")
        assertNull(forecast.current)
        assertTrue(forecast.hours.isEmpty())
        assertTrue(forecast.days.isEmpty())
    }
}
