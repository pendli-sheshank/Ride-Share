package com.splitcruiser.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

data class RouteInfo(
    val distanceMiles: Double,
    val durationMinutes: Double,
    val distanceText: String,
    val durationText: String,
    val coordinates: List<RoutePoint>,
)

/** A named pair: `Pair` exports to Swift as an opaque box that has to be force-cast. */
data class RoutePoint(val lat: Double, val lon: Double)

data class MapsRouteMatrixResult(
    val distanceText: String,
    val durationText: String,
    val routeSummary: String,
    val pickupRecommendation: String,
    val dropoffRecommendation: String,
    val universityContext: String,
    val fullGroundedText: String,
)

/**
 * Driving distance and duration from OSRM.
 *
 * Moved out of `:app` and off OkHttp so iOS can estimate fares too. Throws rather than returning
 * `Result`, which does not survive the Swift export; `androidMain` restores the `Result` shape.
 */
class OsrmRouteService(engine: HttpClientEngine?) {

    constructor() : this(null)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http: HttpClient = createPlainHttpClient(engine)

    suspend fun getRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
    ): RouteInfo {
        // OSRM takes lon,lat — the reverse of the usual order.
        val url = "$OSRM_BASE_URL/driving/$originLon,$originLat;$destLon,$destLat" +
            "?steps=false&geometries=geojson&overview=full"
        val response = http.get(url) { header("User-Agent", OSRM_USER_AGENT) }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("OSRM API error: ${response.status.value}")
        }

        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val code = root["code"]?.jsonPrimitive?.content.orEmpty()
        if (code != "Ok") {
            throw IllegalStateException(
                "OSRM error: ${root["message"]?.jsonPrimitive?.content ?: "Unknown error"}"
            )
        }

        val route = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw IllegalStateException("No route found")

        val distanceMiles = (route["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0) / 1609.34
        val durationMinutes = (route["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0) / 60.0

        val coordinates = route["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
            ?.mapNotNull { element ->
                val pair = element.jsonArray
                val lon = pair.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val lat = pair.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                RoutePoint(lat, lon)
            }.orEmpty()

        return RouteInfo(
            distanceMiles = distanceMiles,
            durationMinutes = durationMinutes,
            distanceText = formatDistance(distanceMiles),
            durationText = formatDuration(durationMinutes),
            coordinates = coordinates,
        )
    }

    /**
     * Falls back to straight-line distance for any pair OSRM cannot route, so one failure does not
     * lose the whole matrix.
     */
    suspend fun getDistanceMatrix(
        origins: List<RoutePoint>,
        destinations: List<RoutePoint>,
    ): List<List<RouteInfo>> = origins.map { origin ->
        destinations.map { destination ->
            runCatching {
                getRoute(origin.lat, origin.lon, destination.lat, destination.lon)
            }.getOrElse {
                val straight = GeoUtils.distanceInMiles(
                    origin.lat, origin.lon, destination.lat, destination.lon
                )
                RouteInfo(
                    distanceMiles = straight,
                    // Roughly 1.3 minutes per mile, as the Android implementation assumed.
                    durationMinutes = straight * 1.3,
                    distanceText = formatDistance(straight),
                    durationText = formatDuration(straight * 1.3),
                    coordinates = emptyList(),
                )
            }
        }
    }

    /** Shared with the address suggestions, so the two never round differently. */
    private fun formatDistance(miles: Double): String = GeoUtils.formatMiles(miles)

    private fun formatDuration(minutes: Double): String = when {
        minutes < 1 -> "< 1 min"
        minutes < 60 -> "${minutes.roundToInt()} min"
        else -> {
            val hours = (minutes / 60).toInt()
            val mins = (minutes % 60).toInt()
            if (mins == 0) "$hours h" else "$hours h $mins min"
        }
    }


    /** Process-wide default; the class form exists so tests can inject a MockEngine. */
    companion object {
        private val shared by lazy { OsrmRouteService(null) }

        suspend fun getRoute(
            originLat: Double,
            originLon: Double,
            destLat: Double,
            destLon: Double,
        ): RouteInfo = shared.getRoute(originLat, originLon, destLat, destLon)

        /** Null instead of throwing, for callers that just want to skip the estimate. */
        suspend fun getRouteOrNull(
            originLat: Double,
            originLon: Double,
            destLat: Double,
            destLon: Double,
        ): RouteInfo? = runCatching { shared.getRoute(originLat, originLon, destLat, destLon) }
            .onFailure { logWarn(LOG_TAG, "OSRM route lookup failed", it) }
            .getOrNull()

        internal const val OSRM_BASE_URL = "https://router.project-osrm.org/route/v1"
        internal const val OSRM_USER_AGENT = "SplitCruiser/1.0"
    }
}
