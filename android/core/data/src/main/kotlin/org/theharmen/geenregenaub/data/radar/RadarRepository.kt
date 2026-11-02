package org.theharmen.geenregenaub.data.radar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.theharmen.geenregenaub.data.FailureReason
import org.theharmen.geenregenaub.data.RelayConfig
import org.theharmen.geenregenaub.data.api.RainApiException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** Where the radar pictures go on the map: a Web Mercator image stretched between these edges. */
data class RadarBounds(val north: Double, val south: Double, val east: Double, val west: Double)

data class RadarFrame(
    val time: Instant,
    /** Measured rain (the past hour and now) rather than the forecast. */
    val observed: Boolean,
    /** Path on the relay, e.g. /v1/radar/202611022100-202611022105.png */
    val path: String,
)

data class RadarLegendItem(val minMmPerHour: Double, val argb: Int)

data class RadarFrames(
    val issuedAt: Instant,
    val bounds: RadarBounds,
    val frames: List<RadarFrame>,
    val legend: List<RadarLegendItem>,
    val attribution: String,
)

/** Animated radar map data from the relay (`GET /v1/radar`, see docs/api.md). */
interface RadarRepository {
    /** False in builds without a relay key: there is no sample radar. */
    val isAvailable: Boolean

    /** @throws RainApiException for an HTTP error, [IOException] for connection trouble. */
    suspend fun frames(): RadarFrames

    /** PNG bytes of one frame. Same errors as [frames]. */
    suspend fun image(frame: RadarFrame): ByteArray
}

internal class RelayRadarRepository(private val baseUrl: String, private val secret: String) : RadarRepository {
    override val isAvailable: Boolean get() = secret.isNotBlank()

    override suspend fun frames(): RadarFrames = parse(String(get("/v1/radar", "application/json")))

    override suspend fun image(frame: RadarFrame): ByteArray = get(frame.path, "image/png")

    private suspend fun get(path: String, accept: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty(RelayConfig.SECRET_HEADER, secret)
            connection.setRequestProperty("Accept", accept)
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw RainApiException.forStatus(status)
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class Body(
            @SerialName("issued_at") val issuedAt: String,
            val bounds: Bounds,
            val frames: List<Frame>,
            val legend: List<Legend> = emptyList(),
            val attribution: String = "Radardata: KNMI, CC BY 4.0",
        )

        @Serializable
        private data class Bounds(val north: Double, val south: Double, val east: Double, val west: Double)

        @Serializable
        private data class Frame(val time: String, val kind: String, val url: String)

        @Serializable
        private data class Legend(@SerialName("min_mm_per_hour") val min: Double, val color: String)

        /** Parses `GET /v1/radar`. Throws [RainApiException] when the body doesn't fit the contract. */
        internal fun parse(body: String): RadarFrames = try {
            val b = json.decodeFromString(Body.serializer(), body)
            RadarFrames(
                issuedAt = Instant.parse(b.issuedAt),
                bounds = RadarBounds(b.bounds.north, b.bounds.south, b.bounds.east, b.bounds.west),
                frames = b.frames.map { RadarFrame(Instant.parse(it.time), it.kind == "observed", it.url) },
                legend = b.legend.map { RadarLegendItem(it.min, parseColor(it.color)) },
                attribution = b.attribution,
            )
        } catch (e: RuntimeException) {
            throw RainApiException(FailureReason.RELAY_UNAVAILABLE, "Unexpected radar response: ${e.message}")
        }

        /** "#rrggbbaa" → ARGB int. */
        internal fun parseColor(value: String): Int {
            val hex = value.removePrefix("#")
            val rgb = hex.substring(0, 6).toLong(16)
            val alpha = if (hex.length >= 8) hex.substring(6, 8).toLong(16) else 0xFF
            return ((alpha shl 24) or rgb).toInt()
        }
    }
}
