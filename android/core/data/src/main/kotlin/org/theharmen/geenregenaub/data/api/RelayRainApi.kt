package org.theharmen.geenregenaub.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.theharmen.geenregenaub.data.FailureReason
import org.theharmen.geenregenaub.data.RelayConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToLong

/** Talks to the relay over HTTPS. Coordinates are rounded to ~1 km before they leave the phone. */
internal class RelayRainApi(
    private val baseUrl: String,
    private val secret: String,
) : RainApi {
    override suspend fun rain(lat: Double, lon: Double): RainApiResponse = withContext(Dispatchers.IO) {
        val url = URL(String.format(Locale.ROOT, "%s/v1/rain?lat=%.2f&lon=%.2f", baseUrl, round(lat), round(lon)))
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty(RelayConfig.SECRET_HEADER, secret)
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw RainApiException.forStatus(status)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            try {
                RainResponseJson.parse(body)
            } catch (e: SerializationException) {
                throw RainApiException(FailureReason.RELAY_UNAVAILABLE, "Unexpected relay response: ${e.message}")
            } catch (e: IllegalArgumentException) {
                throw RainApiException(FailureReason.RELAY_UNAVAILABLE, "Unexpected relay response: ${e.message}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun round(value: Double) = (value * 100).roundToLong() / 100.0

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}
