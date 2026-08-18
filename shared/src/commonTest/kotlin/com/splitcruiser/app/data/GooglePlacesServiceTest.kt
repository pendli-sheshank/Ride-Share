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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Google Places (New) provider and how [OsmLocationService] switches onto it when a key is set.
 * MockEngine stands in for both Google endpoints, so this checks the wire shape and the provider
 * routing without a network or a billable request.
 */
class GooglePlacesServiceTest {

    private val autocompleteBody = """
        {"suggestions":[
          {"placePrediction":{
            "placeId":"place_123",
            "text":{"text":"340 Huntington Ave, Boston, MA, USA"},
            "structuredFormat":{"mainText":{"text":"340 Huntington Avenue"},"secondaryText":{"text":"Boston, MA, USA"}},
            "types":["street_address"]
          }},
          {"placePrediction":{
            "placeId":"place_456",
            "text":{"text":"Museum of Fine Arts, Boston"},
            "structuredFormat":{"mainText":{"text":"Museum of Fine Arts"},"secondaryText":{"text":"Boston, MA"}},
            "types":["tourist_attraction"]
          }}
        ]}
    """.trimIndent()

    private val detailsBody = """
        {"id":"place_123","location":{"latitude":42.3383,"longitude":-71.0881},
         "formattedAddress":"340 Huntington Ave, Boston, MA 02115, USA",
         "displayName":{"text":"340 Huntington Avenue"}}
    """.trimIndent()

    /** Routes each request to a canned response by URL, capturing the requests for assertions. */
    private fun engine(requests: MutableList<HttpRequestData>) = MockEngine { request ->
        requests += request
        val url = request.url.toString()
        val body = when {
            url.contains("places:autocomplete") -> autocompleteBody
            url.contains("/places/") -> detailsBody
            else -> "{}"
        }
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    @Test
    fun autocompleteParsesPredictionsWithoutCoordinatesButWithPlaceIds() = runTest {
        val service = GooglePlacesService("test-key", engine(mutableListOf()))

        val results = service.autocomplete("340 Hunt", biasLat = null, biasLon = null, sessionToken = "tok", limit = 8)

        assertEquals(2, results.size)
        val first = results.first()
        assertEquals("340 Huntington Avenue", first.name)
        assertEquals("Boston, MA, USA", first.formattedAddress)
        assertEquals("place_123", first.providerId, "the place id must be carried for the details call")
        assertEquals("tok", first.sessionToken)
        assertEquals(0.0, first.lat, "an autocomplete prediction has no coordinates yet")
        assertEquals(0.0, first.lon)
        assertTrue(first.hasHouseNumber, "a street_address prediction is a precise address")
        assertFalse(results[1].hasHouseNumber, "a tourist_attraction is not")
    }

    @Test
    fun autocompleteSendsTheKeyAndSessionTokenAndBias() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val service = GooglePlacesService("secret-key", engine(requests))

        service.autocomplete("Boston", biasLat = 42.3, biasLon = -71.0, sessionToken = "sess-1", limit = 8)

        val call = requests.single { it.url.toString().contains("places:autocomplete") }
        assertEquals("secret-key", call.headers["X-Goog-Api-Key"], "the key travels in a header, not the URL")
        val body = (call.body as io.ktor.http.content.TextContent).text
        assertTrue(body.contains("\"sessionToken\":\"sess-1\""), "the session token must be sent: $body")
        assertTrue(body.contains("locationBias"), "a bias point should be sent when provided: $body")
    }

    @Test
    fun placeDetailsResolvesCoordinates() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val service = GooglePlacesService("test-key", engine(requests))

        val resolved = service.placeDetails("place_123", "sess-1")

        assertEquals(42.3383, resolved?.lat)
        assertEquals(-71.0881, resolved?.lon)
        val call = requests.single { it.url.toString().contains("/places/place_123") }
        assertTrue(
            call.headers["X-Goog-FieldMask"]?.contains("location") == true,
            "the field mask must request location so the response — and the bill — stays minimal",
        )
        assertTrue(call.url.toString().contains("sessionToken=sess-1"), "the session token closes the billing session")
    }

    // --- Provider routing on OsmLocationService -------------------------------------------------

    @Test
    fun searchPlacesRankedUsesGoogleWhenAKeyIsConfigured() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val service = OsmLocationService(engine(requests), mapsApiKey = "test-key")

        val results = service.searchPlacesRanked("340 Hunt", 8, fromLat = 42.3, fromLon = -71.0)

        assertTrue(
            requests.any { it.url.toString().contains("places:autocomplete") },
            "with a key set the search must hit Google, not Photon",
        )
        assertTrue(requests.none { it.url.toString().contains("photon.komoot.io") }, "Photon must not be called")
        assertEquals("place_123", results.first().providerId)
    }

    @Test
    fun searchPlacesRankedUsesPhotonWhenNoKeyIsConfigured() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        // No maps key -> the Google client is never constructed; Photon handles the search.
        val service = OsmLocationService(engine(requests), mapsApiKey = "")

        service.searchPlacesRanked("Boston", 8, fromLat = 0.0, fromLon = 0.0)

        assertTrue(requests.any { it.url.toString().contains("photon.komoot.io") }, "the free path must be used")
        assertTrue(requests.none { it.url.toString().contains("places.googleapis.com") })
    }

    @Test
    fun resolvePlaceIsANoOpForAResultThatAlreadyHasCoordinates() = runTest {
        val service = OsmLocationService(engine(mutableListOf()), mapsApiKey = "test-key")
        // A Photon result / seed place carries no provider id: nothing to resolve.
        assertNull(service.resolvePlace(providerId = "", sessionToken = ""))
    }

    @Test
    fun googleFailureFallsBackToPhotonSoTheFieldIsNeverEmpty() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        // Google returns an error; Photon returns one usable result.
        val photonBody = """
            {"features":[{
                "geometry":{"coordinates":[-71.06, 42.35]},
                "properties":{"name":"Boston Common","city":"Boston","state":"Massachusetts","type":"park"}
            }]}
        """.trimIndent()
        val mock = MockEngine { request ->
            requests += request
            val url = request.url.toString()
            if (url.contains("places.googleapis.com")) {
                respond(ByteReadChannel("{}"), HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(ByteReadChannel(photonBody), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val service = OsmLocationService(mock, mapsApiKey = "test-key")

        val results = service.searchPlacesRanked("Boston", 8, fromLat = 0.0, fromLon = 0.0)

        assertTrue(requests.any { it.url.toString().contains("places.googleapis.com") }, "Google was tried first")
        assertTrue(requests.any { it.url.toString().contains("photon.komoot.io") }, "then fell back to Photon")
        assertEquals("Boston Common", results.single().name)
    }
}
