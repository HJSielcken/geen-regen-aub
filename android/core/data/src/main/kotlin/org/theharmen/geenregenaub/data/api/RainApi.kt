package org.theharmen.geenregenaub.data.api

import org.theharmen.geenregenaub.data.FailureReason
import org.theharmen.geenregenaub.domain.RainForecast
import java.io.IOException

/** What the relay's `GET /v1/rain` returns, already mapped to the domain model. */
data class RainApiResponse(
    val forecast: RainForecast,
    val stale: Boolean,
    val attribution: String,
)

interface RainApi {
    /** @throws RainApiException for an HTTP error, [IOException] for connection trouble. */
    suspend fun rain(lat: Double, lon: Double): RainApiResponse
}

class RainApiException(val reason: FailureReason, message: String) : Exception(message) {
    companion object {
        /** Maps a non-200 status per docs/api.md: the app keys on the status code only. */
        fun forStatus(status: Int) = RainApiException(
            reason = when (status) {
                401, 403 -> FailureReason.UNAUTHORIZED
                422 -> FailureReason.OUTSIDE_NL
                else -> FailureReason.RELAY_UNAVAILABLE
            },
            message = "Relay answered HTTP $status",
        )
    }
}
