package org.theharmen.geenregenaub.data.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToLong

internal interface WeatherApi {
    /** The raw JSON body; parse it with [OpenMeteoJson]. @throws IOException on any failure. */
    suspend fun forecastJson(lat: Double, lon: Double): String

    /** The air quality JSON; parse it with [AirQualityJson]. @throws IOException on any failure. */
    suspend fun airQualityJson(lat: Double, lon: Double): String
}

/**
 * Open-Meteo's free forecast API (no key, CC BY 4.0, non-commercial use), called straight from
 * the phone. Coordinates are rounded to ~1 km before they leave the phone, as for the relay.
 * https://open-meteo.com/en/docs
 */
internal class OpenMeteoWeatherApi : WeatherApi {
    override suspend fun forecastJson(lat: Double, lon: Double): String = get(BASE, lat, lon, QUERY)

    /** Copernicus CAMS air quality, from Open-Meteo's separate air quality API (same terms). */
    override suspend fun airQualityJson(lat: Double, lon: Double): String = get(AIR_BASE, lat, lon, AIR_QUERY)

    private suspend fun get(base: String, lat: Double, lon: Double, query: String): String = withContext(Dispatchers.IO) {
        val url = URL(String.format(Locale.ROOT, "%s?latitude=%.2f&longitude=%.2f&%s", base, round(lat), round(lon), query))
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("Open-Meteo answered HTTP $status")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun round(value: Double) = (value * 100).roundToLong() / 100.0

    companion object {
        private const val BASE = "https://api.open-meteo.com/v1/forecast"
        private const val AIR_BASE = "https://air-quality-api.open-meteo.com/v1/air-quality"
        private const val TIMEOUT_MS = 15_000

        const val AIR_QUERY = "current=european_aqi&hourly=european_aqi" +
            "&timezone=auto&timeformat=unixtime&forecast_days=2"

        const val QUERY = "current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m," +
            "wind_gusts_10m,wind_direction_10m,relative_humidity_2m" +
            "&hourly=temperature_2m,weather_code,is_day,precipitation_probability,precipitation," +
            "wind_speed_10m,wind_direction_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
            "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant," +
            "sunrise,sunset,uv_index_max" +
            "&timezone=auto&timeformat=unixtime&forecast_days=10&wind_speed_unit=kmh"
    }
}
