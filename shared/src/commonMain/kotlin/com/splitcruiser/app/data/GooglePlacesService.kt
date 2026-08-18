package com.splitcruiser.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Address autocomplete over Google's **Places API (New)**, the paid alternative to Photon.
 *
 * Deliberately REST, not the native Places SDK — same reason the whole backend is REST-over-Ktor:
 * the iOS Places SDK would need a CocoaPods/SPM entry in the generated Xcode project, which this
 * app is built to avoid. Both endpoints authenticate with an `X-Goog-Api-Key` header and (on
 * details) an `X-Goog-FieldMask` naming exactly the fields wanted, which also bounds what is billed.
 *
 * Two calls, on purpose:
 *  - [autocomplete] returns text predictions with a `placeId` but **no coordinates** — that is how
 *    Google's autocomplete works, and asking for coordinates on every keystroke would bill a Place
 *    Details request per prediction.
 *  - [placeDetails] resolves one chosen `placeId` to a coordinate, when the user actually picks it.
 *
 * A session token ties an autocomplete "session" (the keystrokes of one search) to the single
 * details call that follows, so Google bills the pair as one session rather than each request.
 */
internal class GooglePlacesService(
    private val apiKey: String,
    engine: HttpClientEngine?,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http: HttpClient = createPlainHttpClient(engine)

    /** A fresh opaque session token for one autocomplete-then-details cycle. */
    fun newSessionToken(): String = randomHex(32)

    /**
     * Predictions for [query], ranked by Google (already location-biased server-side, so no
     * client-side re-rank is needed). Coordinates are absent — [RankedPlace.providerId] carries the
     * place id and [RankedPlace.sessionToken] the token to resolve it with later.
     */
    suspend fun autocomplete(
        query: String,
        biasLat: Double?,
        biasLon: Double?,
        sessionToken: String,
        limit: Int,
    ): List<RankedPlace> {
        if (query.isBlank()) return emptyList()
        return runCatching {
            val body = buildJsonObject {
                put("input", query.trim())
                put("sessionToken", sessionToken)
                // Bias toward the user rather than filter to them — a bias circle nudges ranking,
                // like Photon's lat/lon, so a far but exact address is still reachable.
                if (biasLat != null && biasLon != null && (biasLat != 0.0 || biasLon != 0.0)) {
                    putJsonObject("locationBias") {
                        putJsonObject("circle") {
                            putJsonObject("center") {
                                put("latitude", biasLat)
                                put("longitude", biasLon)
                            }
                            put("radius", BIAS_RADIUS_METERS)
                        }
                    }
                }
            }
            val response = http.post("$PLACES_BASE/places:autocomplete") {
                header("X-Goog-Api-Key", apiKey)
                contentType(ContentType.Application.Json)
                // The plain OSM client installs no ContentNegotiation, so serialise the body here
                // rather than handing Ktor a JsonObject it has no encoder for.
                setBody(body.toString())
            }
            if (!response.status.isSuccess()) return emptyList()

            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val suggestions = root["suggestions"]?.jsonArray ?: return emptyList()

            suggestions.mapNotNull { element ->
                val prediction = (element as? JsonObject)?.get("placePrediction")?.jsonObject
                    ?: return@mapNotNull null
                val placeId = prediction["placeId"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val fullText = prediction["text"]?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()
                val structured = prediction["structuredFormat"]?.jsonObject
                val mainText = structured?.get("mainText")?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content.orEmpty()
                val secondaryText = structured?.get("secondaryText")?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content.orEmpty()
                val types = prediction["types"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

                val name = mainText.ifBlank { fullText }.ifBlank { "Location" }
                RankedPlace(
                    name = name,
                    // The second line under the name: the locality, falling back to the full text.
                    formattedAddress = secondaryText.ifBlank { fullText.ifBlank { name } },
                    city = null,
                    state = null,
                    country = null,
                    lat = 0.0,
                    lon = 0.0,
                    type = types.firstOrNull() ?: "Location",
                    distanceMiles = -1.0,
                    distanceText = "",
                    // "street_address" / "premise" are precise addresses — the residential case.
                    hasHouseNumber = types.any { it == "street_address" || it == "premise" },
                    providerId = placeId,
                    sessionToken = sessionToken,
                )
            }.take(limit)
        }.onFailure { logWarn(LOG_TAG, "Google Places autocomplete failed", it) }
            .getOrDefault(emptyList())
    }

    /**
     * Resolves one prediction's [placeId] to its coordinates and formatted address, closing the
     * billing session with [sessionToken]. The field mask keeps the response — and the bill — to
     * just what the picker needs.
     */
    suspend fun placeDetails(placeId: String, sessionToken: String): ResolvedPlace? = runCatching {
        val token = if (sessionToken.isBlank()) "" else "?sessionToken=${sessionToken.encodeURLPathPart()}"
        val response = http.get("$PLACES_BASE/places/${placeId.encodeURLPathPart()}$token") {
            header("X-Goog-Api-Key", apiKey)
            header("X-Goog-FieldMask", "id,location,formattedAddress,displayName")
        }
        if (!response.status.isSuccess()) return null

        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val location = root["location"]?.jsonObject ?: return null
        val lat = location["latitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = location["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val formatted = root["formattedAddress"]?.jsonPrimitive?.content.orEmpty()
        val displayName = root["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()

        ResolvedPlace(
            name = displayName.ifBlank { formatted.ifBlank { "Selected location" } },
            formattedAddress = formatted.ifBlank { displayName },
            lat = lat,
            lon = lon,
        )
    }.onFailure { logWarn(LOG_TAG, "Google Places details failed", it) }.getOrNull()

    private companion object {
        const val PLACES_BASE = "https://places.googleapis.com/v1"

        /** Bias, not a hard filter — the same "nudge ranking, don't restrict" posture as Photon. */
        const val BIAS_RADIUS_METERS = 50_000.0
    }
}
