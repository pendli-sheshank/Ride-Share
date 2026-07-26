package com.splitcruiser.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RouteInfo(
    val distanceMiles: Double,
    val durationMinutes: Double,
    val distanceText: String,
    val durationText: String,
    val coordinates: List<Pair<Double, Double>> = emptyList()
)

object OsrmRouteService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val OSRM_BASE_URL = "https://router.project-osrm.org/route/v1"

    /**
     * Calculate distance and duration between two locations using OSRM.
     * Endpoint: /route/v1/driving/{lon1},{lat1};{lon2},{lat2}
     *
     * @param originLat Starting latitude
     * @param originLon Starting longitude
     * @param destLat Destination latitude
     * @param destLon Destination longitude
     * @return RouteInfo with distance, duration, and formatted text
     */
    suspend fun getRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double
    ): Result<RouteInfo> = withContext(Dispatchers.IO) {
        try {
            // OSRM uses lon,lat format (note: reversed from standard)
            val url = "$OSRM_BASE_URL/driving/$originLon,$originLat;$destLon,$destLat?steps=false&geometries=geojson&overview=full"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SawaariShare-AndroidApp/1.0")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("OsrmRouteService", "OSRM API error ${response.code}: $responseBody")
                return@withContext Result.failure(Exception("OSRM API error: ${response.code}"))
            }

            val root = JSONObject(responseBody)
            val code = root.optString("code", "")

            if (code != "Ok") {
                val message = root.optString("message", "Unknown error")
                Log.w("OsrmRouteService", "OSRM returned code: $code, message: $message")
                return@withContext Result.failure(Exception("OSRM error: $message"))
            }

            val routes = root.optJSONArray("routes")
            if (routes == null || routes.length() == 0) {
                Log.w("OsrmRouteService", "No routes found")
                return@withContext Result.failure(Exception("No route found"))
            }

            val route = routes.getJSONObject(0)
            val distanceMeters = route.optDouble("distance", 0.0)
            val durationSeconds = route.optDouble("duration", 0.0)

            // Convert to miles and minutes
            val distanceMiles = distanceMeters / 1609.34
            val durationMinutes = durationSeconds / 60.0

            // Extract coordinates for polyline if available
            val coordinates = mutableListOf<Pair<Double, Double>>()
            val geometry = route.optJSONObject("geometry")
            if (geometry != null) {
                val coordinates_arr = geometry.optJSONArray("coordinates")
                if (coordinates_arr != null) {
                    for (i in 0 until coordinates_arr.length()) {
                        val coord = coordinates_arr.getJSONArray(i)
                        val lon = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        coordinates.add(Pair(lat, lon))
                    }
                }
            }

            val routeInfo = RouteInfo(
                distanceMiles = distanceMiles,
                durationMinutes = durationMinutes,
                distanceText = formatDistance(distanceMiles),
                durationText = formatDuration(durationMinutes),
                coordinates = coordinates
            )

            Result.success(routeInfo)
        } catch (e: Exception) {
            Log.e("OsrmRouteService", "Failed to call OSRM API: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Calculate distance matrix for multiple origin-destination pairs.
     * Returns a list of RouteInfo for each origin-destination pair in order.
     */
    suspend fun getDistanceMatrix(
        origins: List<Pair<Double, Double>>,
        destinations: List<Pair<Double, Double>>
    ): Result<List<List<RouteInfo>>> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<List<RouteInfo>>()

            // For each origin, calculate distances to all destinations
            for ((originLat, originLon) in origins) {
                val row = mutableListOf<RouteInfo>()
                for ((destLat, destLon) in destinations) {
                    val routeResult = getRoute(originLat, originLon, destLat, destLon)
                    if (routeResult.isSuccess) {
                        row.add(routeResult.getOrThrow())
                    } else {
                        // Use straight-line distance as fallback
                        val straightDist = GeoUtils.distanceInMiles(originLat, originLon, destLat, destLon)
                        row.add(
                            RouteInfo(
                                distanceMiles = straightDist,
                                durationMinutes = straightDist * 1.3, // Rough estimate: 1.3 min per mile
                                distanceText = formatDistance(straightDist),
                                durationText = formatDuration(straightDist * 1.3)
                            )
                        )
                    }
                }
                results.add(row)
            }

            Result.success(results)
        } catch (e: Exception) {
            Log.e("OsrmRouteService", "Failed to calculate distance matrix: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun formatDistance(miles: Double): String {
        return when {
            miles < 0.1 -> "< 0.1 mi"
            miles < 10 -> "%.1f mi".format(miles)
            else -> "%.0f mi".format(miles)
        }
    }

    private fun formatDuration(minutes: Double): String {
        return when {
            minutes < 1 -> "< 1 min"
            minutes < 60 -> "%.0f min".format(minutes)
            else -> {
                val hours = (minutes / 60).toInt()
                val mins = (minutes % 60).toInt()
                if (mins == 0) "$hours h" else "$hours h $mins min"
            }
        }
    }
}
