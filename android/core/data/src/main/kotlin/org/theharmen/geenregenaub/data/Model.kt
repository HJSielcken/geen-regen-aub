package org.theharmen.geenregenaub.data

import org.theharmen.geenregenaub.domain.RainForecast
import java.time.Instant

/** A point the forecast is for: either the phone's own (coarse) location or a place the user picked. */
data class Place(
    val name: String,
    val lat: Double,
    val lon: Double,
    val isCurrentLocation: Boolean,
)

/** The last forecast the app fetched, as kept in the on-device cache. */
data class CachedForecast(
    val forecast: RainForecast,
    val place: Place,
    val fetchedAt: Instant,
    /** The relay could not get a fresh forecast and served an older one. */
    val stale: Boolean,
    /** Licence text to show near the data, e.g. "Radardata: KNMI, CC BY 4.0". */
    val attribution: String,
    /** Made-up sample data, used when the build has no relay key. Never shown as real. */
    val isDemo: Boolean = false,
)

enum class FailureReason {
    /** No current location and no chosen place. */
    NO_LOCATION,

    /** The point lies outside the radar area (relay answered 422). */
    OUTSIDE_NL,

    /** The relay rejected the shared secret (401). Retrying will not help. */
    UNAUTHORIZED,

    /** The relay answered but has no forecast yet (5xx). Retry after a minute or more. */
    RELAY_UNAVAILABLE,

    /** No connection, DNS or TLS trouble, timeout. */
    NETWORK,
}

sealed interface RefreshResult {
    data class Success(val forecast: CachedForecast) : RefreshResult

    /** [cached] is the last good forecast, if any, so callers can keep showing it. */
    data class Failure(val reason: FailureReason, val cached: CachedForecast?) : RefreshResult
}

/** Where the forecast should be for. */
data class LocationSettings(
    /** Follow the phone's location; when off, always use [chosenPlace]. */
    val useCurrentLocation: Boolean = true,
    /** Fallback (and the fixed place when [useCurrentLocation] is off). */
    val chosenPlace: Place? = null,
)
