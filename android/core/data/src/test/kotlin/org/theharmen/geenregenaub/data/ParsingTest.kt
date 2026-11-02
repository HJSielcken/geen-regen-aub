package org.theharmen.geenregenaub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.theharmen.geenregenaub.data.api.RainApiException
import org.theharmen.geenregenaub.data.api.RainResponseJson
import org.theharmen.geenregenaub.data.places.PdokPlaceSearch
import org.theharmen.geenregenaub.data.store.StoredJson
import org.theharmen.geenregenaub.domain.ForecastSource
import java.time.Duration
import java.time.Instant

class ParsingTest {
    private fun relayBody(source: String = "knmi", extra: String = ""): String {
        val start = Instant.parse("2026-11-02T21:00:00Z")
        val series = (0 until 25).joinToString(",") { i ->
            """{"time":"${start.plusSeconds(i * 300L)}","mm_per_hour":${if (i in 3..5) 1.8 else 0.0}}"""
        }
        return """
            {"point":{"lat":52.09,"lon":5.12},"source":"$source","issued_at":"2026-11-02T21:00:00Z",
             "generated_at":"2026-11-02T21:03:12Z","interval_minutes":5,"unit":"mm/h",
             "series":[$series],
             "summary":{"raining_now":false,"rain_starts_at":"2026-11-02T21:15:00Z","rain_ends_at":"2026-11-02T21:30:00Z",
                        "max_mm_per_hour":1.8,"total_mm":0.5},
             "stale":false,"attribution":"Radardata: KNMI, CC BY 4.0"$extra}
        """.trimIndent()
    }

    @Test
    fun `parses the relay contract`() {
        val response = RainResponseJson.parse(relayBody(extra = ""","new_field":{"x":1}"""))
        val forecast = response.forecast
        assertEquals(Instant.parse("2026-11-02T21:00:00Z"), forecast.issuedAt)
        assertEquals(Duration.ofMinutes(5), forecast.step)
        assertEquals(25, forecast.intensities.size)
        assertEquals(1.8, forecast.intensities[3], 0.0)
        assertEquals(ForecastSource.KNMI, forecast.source)
        assertEquals("Radardata: KNMI, CC BY 4.0", response.attribution)
    }

    @Test
    fun `unknown sources count as the fallback`() {
        assertEquals(ForecastSource.OPEN_METEO, RainResponseJson.parse(relayBody(source = "open-meteo")).forecast.source)
        assertEquals(ForecastSource.OPEN_METEO, RainResponseJson.parse(relayBody(source = "something")).forecast.source)
    }

    @Test
    fun `status codes map to reasons`() {
        assertEquals(FailureReason.UNAUTHORIZED, RainApiException.forStatus(401).reason)
        assertEquals(FailureReason.OUTSIDE_NL, RainApiException.forStatus(422).reason)
        assertEquals(FailureReason.RELAY_UNAVAILABLE, RainApiException.forStatus(503).reason)
        assertEquals(FailureReason.RELAY_UNAVAILABLE, RainApiException.forStatus(501).reason)
    }

    @Test
    fun `parses PDOK places`() {
        val body = """
            {"response":{"numFound":2,"start":0,"docs":[
              {"type":"woonplaats","weergavenaam":"Utrecht, Utrecht, Utrecht","centroide_ll":"POINT(5.10547 52.09074)"},
              {"type":"postcode","weergavenaam":"3511AA Utrecht"}
            ]}}
        """.trimIndent()
        val places = PdokPlaceSearch.parse(body)
        assertEquals(1, places.size)
        assertEquals("Utrecht, Utrecht, Utrecht", places[0].name)
        assertEquals(52.09074, places[0].lat, 1e-9)
        assertEquals(5.10547, places[0].lon, 1e-9)
    }

    @Test
    fun `cache round trips`() {
        val forecast = CachedForecast(
            forecast = RainResponseJson.parse(relayBody()).forecast,
            place = Place("Thuis", 52.1, 5.1, isCurrentLocation = false),
            fetchedAt = Instant.parse("2026-11-02T21:04:00Z"),
            stale = true,
            attribution = "Radardata: KNMI, CC BY 4.0",
            isDemo = true,
        )
        assertEquals(forecast, StoredJson.decodeForecast(StoredJson.encodeForecast(forecast)))
        assertNull(StoredJson.decodeForecast("{not json"))
        assertTrue(StoredJson.decodePlace(StoredJson.encodePlace(forecast.place)) == forecast.place)
    }
}
