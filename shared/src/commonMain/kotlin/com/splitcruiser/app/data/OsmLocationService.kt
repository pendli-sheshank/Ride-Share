package com.splitcruiser.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PhotonPlaceResult(
    val name: String,
    val formattedAddress: String,
    val city: String?,
    val state: String?,
    val country: String?,
    val lat: Double,
    val lon: Double,
    val type: String,
)

data class NominatimReverseResult(
    val displayName: String,
    val road: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val lat: Double,
    val lon: Double,
)

/**
 * Place search and reverse geocoding over OpenStreetMap.
 *
 * Moved out of `:app` and off OkHttp so iOS gets location autocomplete too — it previously had
 * none. The parsing stays defensive: both APIs return sparsely populated features, and a missing
 * field should degrade the result rather than fail the search.
 */
class OsmLocationService(engine: HttpClientEngine?) {

    constructor() : this(null)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http: HttpClient = createPlainHttpClient(engine)

    suspend fun autocompletePhoton(query: String, limit: Int): List<PhotonPlaceResult> =
        fetchAutocomplete(query, limit, biasLat = null, biasLon = null)

    /**
     * [autocompletePhoton], ranked toward [biasLat]/[biasLon] — Photon's `lat`/`lon` params nudge
     * ranking rather than filter results, so a query like "Maryland" from a St. Louis-area bias
     * surfaces "Maryland Heights, MO" ahead of the state of Maryland, without hiding the state for
     * someone who actually wants it.
     */
    suspend fun autocompletePhotonNear(query: String, limit: Int, biasLat: Double, biasLon: Double): List<PhotonPlaceResult> =
        fetchAutocomplete(query, limit, biasLat = biasLat, biasLon = biasLon)

    private suspend fun fetchAutocomplete(
        query: String,
        limit: Int,
        biasLat: Double?,
        biasLon: Double?,
    ): List<PhotonPlaceResult> {
        if (query.isBlank()) return emptyList()
        return runCatching {
            // `location_bias_scale` pulls Photon's own ranking toward the anchor rather than leaving
            // it purely on OSM importance. It is a refinement — the client-side sort in
            // [PlaceRanking] is what actually guarantees nearest-first — so nothing breaks if the
            // parameter is ignored.
            val bias = if (biasLat != null && biasLon != null) {
                "&lat=$biasLat&lon=$biasLon&location_bias_scale=$LOCATION_BIAS_SCALE"
            } else {
                ""
            }
            // No `osm_tag` filter is sent, on purpose. In Photon `osm_tag` is a whitelist: adding
            // e.g. `osm_tag=amenity` (or `place:house`) would return ONLY that tag and hide every
            // other kind of result. Sending no tag is what lets a single query return both
            // commercial places (shops, amenities) AND residential house-number addresses — a
            // rideshare pickup/drop-off is just as often someone's home as a landmark. Residential
            // results are never dropped here; when they look "missing" it is because Photon orders
            // by OSM importance, which buries a house behind same-named cities. That is handled by
            // over-fetching CANDIDATE_LIMIT and re-ranking nearest-first in [searchPlacesRanked],
            // not by tag filtering.
            val url = "https://photon.komoot.io/api/?q=${query.trim().encodeURLParameter()}" +
                "&limit=$limit&lang=en$bias"
            val response = http.get(url) { header("User-Agent", OSM_USER_AGENT) }
            if (!response.status.isSuccess()) return emptyList()

            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val features = root["features"]?.jsonArray ?: return emptyList()

            features.mapNotNull { element ->
                val feature = element as? JsonObject ?: return@mapNotNull null
                val coordinates = feature["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
                val lon = coordinates?.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: 0.0
                val lat = coordinates?.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: 0.0
                val properties = feature["properties"]?.jsonObject ?: return@mapNotNull null

                val name = properties.text("name")
                val street = properties.text("street")
                val houseNumber = properties.text("housenumber")
                val city = properties.firstText("city", "town", "village")
                val state = properties.text("state")
                val country = properties.text("country")
                val type = properties.firstText("type", "osm_value").ifBlank { "Location" }

                val address = listOfNotNull(
                    if (houseNumber.isNotBlank() && street.isNotBlank()) "$houseNumber $street"
                    else street.ifBlank { null },
                    city.ifBlank { null },
                    state.ifBlank { null },
                    country.ifBlank { null },
                ).joinToString(", ")

                val placeName = name.ifBlank { street.ifBlank { city.ifBlank { "Location" } } }

                PhotonPlaceResult(
                    name = placeName,
                    formattedAddress = address.ifBlank { placeName },
                    city = city.ifBlank { null },
                    state = state.ifBlank { null },
                    country = country.ifBlank { null },
                    lat = lat,
                    lon = lon,
                    type = type,
                )
            }
        }.onFailure { logWarn(LOG_TAG, "Photon place search failed", it) }.getOrDefault(emptyList())
    }

    /** Overload without a limit — Kotlin default arguments do not reach Swift. */
    suspend fun autocompletePhoton(query: String): List<PhotonPlaceResult> =
        autocompletePhoton(query, DISPLAY_LIMIT)

    /**
     * Address suggestions ordered nearest-first from ([fromLat], [fromLon]).
     *
     * Fetches [CANDIDATE_LIMIT] rather than the [limit] actually shown, because Photon orders by OSM
     * importance: asking for 8 and displaying 8 meant a house-number address lost its place to every
     * city and county with a similar name, and the address the user was typing never arrived. The
     * wide set is then ordered by real distance and cut back down.
     *
     * Pass `0.0, 0.0` for the anchor when there is no location fix; results come back in Photon's
     * own order with no distance text, rather than sorted against a meaningless point.
     */
    suspend fun searchPlacesRanked(
        query: String,
        limit: Int,
        fromLat: Double,
        fromLon: Double,
    ): List<RankedPlace> {
        val anchored = fromLat != 0.0 || fromLon != 0.0
        val candidates = if (anchored) {
            fetchAutocomplete(query, CANDIDATE_LIMIT, fromLat, fromLon)
        } else {
            fetchAutocomplete(query, limit, null, null)
        }
        return if (anchored) {
            PlaceRanking.rankByDistance(candidates, fromLat, fromLon, limit)
        } else {
            PlaceRanking.unranked(candidates, limit)
        }
    }

    suspend fun reverseGeocodeNominatim(lat: Double, lon: Double): NominatimReverseResult? =
        runCatching {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon" +
                "&zoom=18&addressdetails=1"
            val response = http.get(url) { header("User-Agent", OSM_USER_AGENT) }
            if (!response.status.isSuccess()) return null

            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val address = root["address"]?.jsonObject

            NominatimReverseResult(
                displayName = root.text("display_name").ifBlank { "Unknown Location" },
                road = address?.firstText("road", "pedestrian")?.ifBlank { null },
                city = address?.firstText("city", "town", "suburb")?.ifBlank { null },
                state = address?.text("state")?.ifBlank { null },
                country = address?.text("country")?.ifBlank { null },
                lat = lat,
                lon = lon,
            )
        }.onFailure { logWarn(LOG_TAG, "Nominatim reverse geocode failed", it) }.getOrNull()

    private fun JsonObject.text(key: String): String =
        this[key]?.jsonPrimitive?.contentOrEmpty().orEmpty()

    private fun JsonObject.firstText(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> text(key).ifBlank { null } }.orEmpty()

    /**
     * A process-wide default, so UI code can call this without threading an instance through. The
     * class form still exists so tests can inject a MockEngine.
     */
    companion object {
        private val shared by lazy { OsmLocationService(null) }

        suspend fun autocompletePhoton(query: String, limit: Int): List<PhotonPlaceResult> =
            shared.autocompletePhoton(query, limit)

        suspend fun autocompletePhoton(query: String): List<PhotonPlaceResult> =
            shared.autocompletePhoton(query, 8)

        suspend fun autocompletePhotonNear(
            query: String,
            limit: Int,
            biasLat: Double,
            biasLon: Double,
        ): List<PhotonPlaceResult> = shared.autocompletePhotonNear(query, limit, biasLat, biasLon)

        suspend fun searchPlacesRanked(
            query: String,
            limit: Int,
            fromLat: Double,
            fromLon: Double,
        ): List<RankedPlace> = shared.searchPlacesRanked(query, limit, fromLat, fromLon)

        /** Overload without a limit — Kotlin default arguments do not reach Swift. */
        suspend fun searchPlacesRanked(
            query: String,
            fromLat: Double,
            fromLon: Double,
        ): List<RankedPlace> = shared.searchPlacesRanked(query, DISPLAY_LIMIT, fromLat, fromLon)

        suspend fun reverseGeocodeNominatim(lat: Double, lon: Double): NominatimReverseResult? =
            shared.reverseGeocodeNominatim(lat, lon)

        /**
         * Nominatim's usage policy requires a request to identify itself; an anonymous client gets
         * blocked. Keep this string meaningful if the app is ever renamed again.
         */
        internal const val OSM_USER_AGENT = "SplitCruiser/1.0 (student.carpool@app.com)"

        /** How many suggestions a field shows. */
        const val DISPLAY_LIMIT = 8

        /**
         * How many Photon is asked for before re-ranking. Wide enough that a residential address
         * outranked by same-named cities still makes the candidate set; small enough to stay one
         * quick request.
         */
        const val CANDIDATE_LIMIT = 25

        /** Photon's 0..1 knob for how hard `lat`/`lon` pull on its own ranking. */
        private const val LOCATION_BIAS_SCALE = 0.8
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrEmpty(): String =
    if (this is kotlinx.serialization.json.JsonNull) "" else content

/** A bare Ktor client for the public OSM endpoints; they need no auth and no negotiation. */
internal fun createPlainHttpClient(engine: HttpClientEngine?): HttpClient {
    val configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 15_000
        }
    }
    return if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
}
