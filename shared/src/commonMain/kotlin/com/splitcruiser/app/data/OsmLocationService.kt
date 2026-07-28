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

    suspend fun autocompletePhoton(query: String, limit: Int): List<PhotonPlaceResult> {
        if (query.isBlank()) return emptyList()
        return runCatching {
            val url = "https://photon.komoot.io/api/?q=${query.trim().encodeURLParameter()}&limit=$limit"
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
        autocompletePhoton(query, 8)

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

        suspend fun reverseGeocodeNominatim(lat: Double, lon: Double): NominatimReverseResult? =
            shared.reverseGeocodeNominatim(lat, lon)

        /**
         * Nominatim's usage policy requires a request to identify itself; an anonymous client gets
         * blocked. Keep this string meaningful if the app is ever renamed again.
         */
        internal const val OSM_USER_AGENT = "SplitCruiser/1.0 (student.carpool@app.com)"
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
