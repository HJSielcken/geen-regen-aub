package org.theharmen.geenregenaub.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.theharmen.geenregenaub.data.weather.AirQualityJson
import java.time.Instant

class AirQualityParsingTest {
    private val body = """
        {"latitude":52.1,"longitude":5.1,"utc_offset_seconds":3600,
         "current":{"time":1793653200,"interval":3600,"european_aqi":14.2},
         "hourly":{"time":[1793653200,1793656800,1793660400],"european_aqi":[14,null,27.6]}}
    """.trimIndent()

    @Test
    fun `parses the current index and skips empty hours`() {
        val air = AirQualityJson.parse(body)
        assertEquals(14, air.current)
        assertEquals(2, air.hours.size)
        assertEquals(28, air.hours[1].index)
        assertEquals(Instant.ofEpochSecond(1793660400), air.hours[1].time)
    }

    @Test
    fun `missing blocks give an empty air quality`() {
        val air = AirQualityJson.parse("{}")
        assertEquals(null, air.current)
        assertEquals(0, air.hours.size)
    }
}
