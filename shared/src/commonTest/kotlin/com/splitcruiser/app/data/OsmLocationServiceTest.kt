package com.splitcruiser.app.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Photon's `lat`/`lon` params rank results toward a point rather than filtering to it, which is
 * what lets "Maryland" from a St. Louis-area bias surface "Maryland Heights" ahead of the state —
 * this only checks that the service asks Photon for that ranking, not Photon's actual ordering.
 */
class OsmLocationServiceTest {

    private val samplePlaceResponse = """
        {"features":[{
            "geometry":{"coordinates":[-90.4568, 38.7159]},
            "properties":{"name":"Maryland Heights","city":"Maryland Heights","state":"Missouri","country":"USA","type":"city"}
        }]}
    """.trimIndent()

    private fun service(onRequest: (HttpRequestData) -> Unit): OsmLocationService {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(
                content = ByteReadChannel(samplePlaceResponse),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return OsmLocationService(engine)
    }

    @Test
    fun unbiasedSearchSendsNoLatOrLon() = runTest {
        var url = ""
        val service = service { url = it.url.toString() }

        service.autocompletePhoton("Maryland", 8)

        assertFalse(url.contains("lat="), "an unbiased search must not send a ranking point")
        assertFalse(url.contains("lon="))
    }

    @Test
    fun biasedSearchSendsTheAnchorPoint() = runTest {
        var url = ""
        val service = service { url = it.url.toString() }

        service.autocompletePhotonNear("Maryland", 8, biasLat = 38.7107, biasLon = -90.3559)

        assertTrue(url.contains("lat=38.7107"), "the anchor latitude must reach Photon")
        assertTrue(url.contains("lon=-90.3559"), "the anchor longitude must reach Photon")
    }

    @Test
    fun biasedSearchStillParsesResults() = runTest {
        val service = service { }

        val results = service.autocompletePhotonNear("Maryland", 8, biasLat = 38.7107, biasLon = -90.3559)

        assertEquals(1, results.size)
        assertEquals("Maryland Heights", results.single().name)
    }

    /**
     * The reason a home address never showed up: Photon orders by OSM importance, so asking for 8
     * and showing 8 meant a residential address lost its place to every city and county with a
     * similar name. The ranked search asks for a wide candidate set and cuts it down after sorting.
     */
    @Test
    fun theRankedSearchAsksForMoreCandidatesThanItShows() = runTest {
        var url = ""
        val service = service { url = it.url.toString() }

        service.searchPlacesRanked("Maryland", 8, fromLat = 38.7107, fromLon = -90.3559)

        assertTrue(
            url.contains("limit=${OsmLocationService.CANDIDATE_LIMIT}"),
            "the fetch limit must be the candidate limit, not the display limit — got $url",
        )
        assertTrue(url.contains("location_bias_scale="), "Photon's own ranking should pull the same way")
    }

    @Test
    fun theRankedSearchWithNoFixSendsNoAnchor() = runTest {
        var url = ""
        val service = service { url = it.url.toString() }

        val results = service.searchPlacesRanked("Maryland", 8, fromLat = 0.0, fromLon = 0.0)

        assertFalse(url.contains("lat="), "0,0 is 'no location', not a place to sort against")
        assertEquals(1, results.size)
        assertEquals("", results.single().distanceText, "no anchor means no distance to show")
    }

    @Test
    fun theRankedSearchMeasuresFromTheAnchor() = runTest {
        val service = service { }

        val results = service.searchPlacesRanked("Maryland", 8, fromLat = 38.7107, fromLon = -90.3559)

        assertEquals(1, results.size)
        // Maryland Heights sits a few miles from the anchor, so the row can say so.
        assertTrue(results.single().distanceMiles > 0.0)
        assertTrue(results.single().distanceText.endsWith("away"), results.single().distanceText)
    }
}
