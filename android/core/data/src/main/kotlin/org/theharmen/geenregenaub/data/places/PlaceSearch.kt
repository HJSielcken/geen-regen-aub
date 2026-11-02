package org.theharmen.geenregenaub.data.places

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.theharmen.geenregenaub.data.Place
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal interface PlaceSearch {
    suspend fun search(query: String): List<Place>
}

/**
 * Searches towns, postcodes and addresses with PDOK Locatieserver, the Dutch government's free,
 * open geocoder (no key). https://api.pdok.nl/bzk/locatieserver/search/v3_1/ui/
 */
internal class PdokPlaceSearch : PlaceSearch {
    override suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val fq = URLEncoder.encode("type:(woonplaats OR postcode OR adres)", "UTF-8")
        val url = URL("$BASE?q=$q&fq=$fq&rows=8&fl=weergavenaam,type,centroide_ll")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext emptyList()
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://api.pdok.nl/bzk/locatieserver/search/v3_1/free"
        private val json = Json { ignoreUnknownKeys = true }
        private val point = Regex("""POINT\(\s*(-?[0-9.]+)\s+(-?[0-9.]+)\s*\)""")

        @Serializable
        private data class Body(val response: Response)

        @Serializable
        private data class Response(val docs: List<Doc> = emptyList())

        @Serializable
        private data class Doc(
            val weergavenaam: String,
            @SerialName("centroide_ll") val centroid: String? = null,
        )

        /** Parses a Locatieserver answer; `centroide_ll` is WKT "POINT(lon lat)". */
        internal fun parse(body: String): List<Place> =
            json.decodeFromString(Body.serializer(), body).response.docs.mapNotNull { doc ->
                val match = doc.centroid?.let { point.find(it) } ?: return@mapNotNull null
                val (lon, lat) = match.destructured
                Place(doc.weergavenaam, lat.toDouble(), lon.toDouble(), isCurrentLocation = false)
            }
    }
}
