package org.theharmen.geenregenaub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LocationSettingsTest {
    private val now = Instant.parse("2026-11-18T11:00:00Z")
    private val leusden = Place("Leusden", 52.13, 5.43, isCurrentLocation = false)

    @Test
    fun temporaryPlaceCountsUntilItEnds() {
        val settings = LocationSettings(temporaryPlace = leusden, temporaryUntil = now.plusSeconds(60))
        assertEquals(leusden, settings.activeTemporary(now))
        assertEquals(leusden, settings.activeTemporary(now.plusSeconds(59)))
    }

    @Test
    fun temporaryPlaceIsGoneAfterItsEnd() {
        val settings = LocationSettings(temporaryPlace = leusden, temporaryUntil = now.plusSeconds(60))
        assertNull(settings.activeTemporary(now.plusSeconds(60)))
        assertNull(settings.activeTemporary(now.plusSeconds(3600)))
    }

    @Test
    fun noEndTimeMeansNoTemporaryPlace() {
        assertNull(LocationSettings(temporaryPlace = leusden).activeTemporary(now))
        assertNull(LocationSettings().activeTemporary(now))
    }

    private val utrecht = Place("Utrecht", 52.09, 5.12, isCurrentLocation = false)
    private val here = Place("Zeist", 52.09, 5.23, isCurrentLocation = true)

    @Test
    fun shownIsTheDefaultWithoutATemporaryPlace() {
        assertEquals(utrecht, LocationSettings().shown(now, utrecht, leusden) { it })
    }

    @Test
    fun shownIsTheTemporaryForecastForThatPlace() {
        val settings = LocationSettings(temporaryPlace = leusden, temporaryUntil = now.plusSeconds(60))
        assertEquals(leusden, settings.shown(now, utrecht, leusden) { it })
        // An older temporary forecast for another place does not count.
        assertNull(settings.shown(now, utrecht, here) { it })
        assertNull(settings.shown(now, utrecht, null) { it })
        // Once it runs out, the default is back.
        assertEquals(utrecht, settings.shown(now.plusSeconds(60), utrecht, leusden) { it })
    }

    @Test
    fun aTemporaryHereShowsThePhonesForecast() {
        val settings = LocationSettings(
            useCurrentLocation = false,
            chosenPlace = utrecht,
            temporaryPlace = Place("Huidige locatie", 0.0, 0.0, isCurrentLocation = true),
            temporaryUntil = now.plusSeconds(60),
        )
        assertEquals(here, settings.shown(now, utrecht, here) { it })
        assertNull(settings.shown(now, utrecht, leusden) { it })
    }
}
