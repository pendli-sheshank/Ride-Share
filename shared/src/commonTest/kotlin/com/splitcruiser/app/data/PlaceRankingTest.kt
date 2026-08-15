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

    private fun place(name: String, lat: Double, lon: Double, hasHouseNumber: Boolean = false) =
        PhotonPlaceResult(
            name = name,
            formattedAddress = name,
            city = null,
            state = null,
            country = null,
            lat = lat,
            lon = lon,
            type = "house",
            hasHouseNumber = hasHouseNumber,
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

    /**
     * The residential-address fix: when the user is typing a street address (query has a digit), a
     * precise house-number result must not be buried under a nearer POI. Photon ranks houses last by
     * importance, so without this a far-but-exact address the user is spelling out never surfaces.
     */
    @Test
    fun preferAddressesLiftsHouseNumberResultsAboveNearerPois() {
        val nearbyPoi = place("Corner Cafe", 42.3385, -71.0879, hasHouseNumber = false)
        val fartherHouse = place("742 Evergreen Terrace", 42.3736, -71.1189, hasHouseNumber = true)

        val withPreference =
            PlaceRanking.rankByDistance(listOf(nearbyPoi, fartherHouse), anchorLat, anchorLon, 10, preferAddresses = true)
        assertEquals(
            listOf("742 Evergreen Terrace", "Corner Cafe"),
            withPreference.map { it.name },
            "a precise address must outrank a nearer POI when the user is typing an address",
        )

        // Default behaviour (no digit in the query) is unchanged: pure nearest-first.
        val withoutPreference =
            PlaceRanking.rankByDistance(listOf(nearbyPoi, fartherHouse), anchorLat, anchorLon, 10)
        assertEquals(listOf("Corner Cafe", "742 Evergreen Terrace"), withoutPreference.map { it.name })
    }

    @Test
    fun preferAddressesKeepsDistanceOrderWithinTheAddressGroup() {
        val nearHouse = place("10 Forsyth St", 42.3379, -71.0899, hasHouseNumber = true)
        val farHouse = place("500 Boylston St", 42.3500, -71.0800, hasHouseNumber = true)
        val poi = place("Museum of Fine Arts", 42.3394, -71.0940, hasHouseNumber = false)

        val ranked = PlaceRanking.rankByDistance(
            listOf(farHouse, poi, nearHouse), anchorLat, anchorLon, 10, preferAddresses = true,
        )

        // Both houses first (nearest of them first), the POI after.
        assertEquals(listOf("10 Forsyth St", "500 Boylston St", "Museum of Fine Arts"), ranked.map { it.name })
    }

    @Test
    fun preferAddressesSurfacesResidentialEvenWithNoLocationFix() {
        // The no-anchor path is the one that hid residential entirely: Photon's importance order puts
        // houses last, so taking the first N showed only POIs. With preferAddresses the house-number
        // result is lifted to the front while POIs keep their relative order behind it.
        val cityPoi = place("Springfield", 39.8, -89.6, hasHouseNumber = false)
        val house = place("123 Main St", 42.1, -71.2, hasHouseNumber = true)
        val stationPoi = place("Union Station", 41.8, -71.4, hasHouseNumber = false)

        val ranked = PlaceRanking.unranked(listOf(cityPoi, house, stationPoi), 10, preferAddresses = true)

        assertEquals("123 Main St", ranked.first().name, "the address must appear even without an anchor")
        assertEquals(listOf("123 Main St", "Springfield", "Union Station"), ranked.map { it.name })
        assertTrue(ranked.all { it.distanceText.isEmpty() }, "no anchor means no distance text")
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
