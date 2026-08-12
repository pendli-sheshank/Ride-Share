package com.splitcruiser.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ordering half of "address suggestions should work like Google Maps".
 *
 * Photon ranks by OSM importance, so its own order puts cities and states above the street the user
 * is typing. This is what actually makes the list nearest-first, and it is pure math so both
 * platforms are guaranteed to agree.
 */
class PlaceRankingTest {

    private val anchorLat = 42.3383
    private val anchorLon = -71.0881

    private fun place(name: String, lat: Double, lon: Double) = PhotonPlaceResult(
        name = name,
        formattedAddress = name,
        city = null,
        state = null,
        country = null,
        lat = lat,
        lon = lon,
        type = "house",
    )

    @Test
    fun resultsComeBackNearestFirst() {
        val far = place("Providence", 41.8291, -71.4137)
        val near = place("Ruggles", 42.3363, -71.0895)
        val middle = place("Harvard Square", 42.3736, -71.1189)

        val ranked = PlaceRanking.rankByDistance(listOf(far, middle, near), anchorLat, anchorLon, 10)

        assertEquals(listOf("Ruggles", "Harvard Square", "Providence"), ranked.map { it.name })
    }

    /**
     * Photon omits geometry on some features, which decodes as 0.0/0.0 — a point in the Gulf of
     * Guinea. Sorting those on their computed distance would scatter them through the list
     * according to how far the user lives from the prime meridian.
     */
    @Test
    fun resultsWithNoCoordinatesSortLastRatherThanByTheirDistanceFromNullIsland() {
        val unknown = place("Somewhere", 0.0, 0.0)
        val far = place("Providence", 41.8291, -71.4137)

        val ranked = PlaceRanking.rankByDistance(listOf(unknown, far), anchorLat, anchorLon, 10)

        assertEquals(listOf("Providence", "Somewhere"), ranked.map { it.name })
        assertEquals("", ranked.last().distanceText, "an unknown distance shows nothing, not '4000 mi'")
    }

    @Test
    fun theListIsCutToTheDisplayLimitAfterSorting() {
        val places = listOf(
            place("Providence", 41.8291, -71.4137),
            place("Worcester", 42.2618, -71.7957),
            place("Ruggles", 42.3363, -71.0895),
        )

        val ranked = PlaceRanking.rankByDistance(places, anchorLat, anchorLon, 2)

        assertEquals(2, ranked.size)
        assertEquals("Ruggles", ranked.first().name, "the cut happens after the sort, not before")
    }

    @Test
    fun withNoAnchorTheOrderIsLeftAloneAndNoDistanceIsClaimed() {
        val places = listOf(
            place("Providence", 41.8291, -71.4137),
            place("Ruggles", 42.3363, -71.0895),
        )

        val ranked = PlaceRanking.rankByDistance(places, 0.0, 0.0, 10)

        assertEquals(listOf("Providence", "Ruggles"), ranked.map { it.name })
        assertTrue(ranked.all { it.distanceText.isEmpty() })
    }

    @Test
    fun distanceTextReadsSensiblyCloseUpAndFarAway() {
        val nearby = place("Curry", 42.3391, -71.0878)
        val far = place("Providence", 41.8291, -71.4137)

        val ranked = PlaceRanking.rankByDistance(listOf(nearby, far), anchorLat, anchorLon, 10)

        assertTrue(ranked.first().distanceText.endsWith(" away"), ranked.first().distanceText)
        // Sub-mile keeps a decimal; tens of miles round to whole numbers.
        assertTrue(ranked.first().distanceText.startsWith("0.") || ranked.first().distanceText.startsWith("< 0.1"))
        assertTrue(ranked.last().distanceText.first().isDigit())
        assertTrue(ranked.last().distanceText.contains(" mi "))
    }
}
