package org.theharmen.geenregenaub.data.api

import kotlinx.coroutines.delay
import org.theharmen.geenregenaub.domain.ForecastSource
import org.theharmen.geenregenaub.domain.RainForecast
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Made-up forecast for builds without a relay key: a shower that drifts through every half hour,
 * so the screen and widget have something realistic to draw. Always labelled as sample data.
 */
internal class DemoRainApi(private val clock: Clock = Clock.systemUTC()) : RainApi {
    override suspend fun rain(lat: Double, lon: Double): RainApiResponse {
        delay(400)
        val now = clock.instant()
        val issued = now.truncatedTo(ChronoUnit.MINUTES).minus(Duration.ofMinutes(now.atZone(clock.zone).minute % 5L))
        val phase = (issued.epochSecond / 300) % 24
        val values = (0 until 25).map { i ->
            val x = ((i + phase) % 24) / 24.0
            val shower = max(0.0, sin(2 * PI * x) * 6.0 - 1.5)
            (shower * 10).toLong() / 10.0
        }
        return RainApiResponse(
            forecast = RainForecast(issued, Duration.ofMinutes(5), values, ForecastSource.KNMI),
            stale = false,
            attribution = "Voorbeelddata, geen echte verwachting",
        )
    }
}
