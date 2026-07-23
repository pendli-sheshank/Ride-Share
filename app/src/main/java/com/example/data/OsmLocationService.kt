package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class PhotonPlaceResult(
    val name: String,
    val formattedAddress: String,
    val city: String?,
    val state: String?,
    val country: String?,
    val lat: Double,
    val lon: Double,
    val type: String
)

data class NominatimReverseResult(
    val displayName: String,
    val road: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val lat: Double,
    val lon: Double
)

object OsmLocationService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Photon Autocomplete API using OpenStreetMap data
     * Endpoint: https://photon.komoot.io/api/?q={query}&limit=8
     */
    suspend fun autocompletePhoton(query: String, limit: Int = 8): List<PhotonPlaceResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://photon.komoot.io/api/?q=$encodedQuery&limit=$limit"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SawaariShare-AndroidApp/1.0 (student.carpool@app.com)")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()

            if (!response.isSuccessful) {
                Log.e("OsmLocationService", "Photon API HTTP ${response.code}: $responseBody")
                return@withContext emptyList()
            }

            val root = JSONObject(responseBody)
            val features = root.optJSONArray("features") ?: return@withContext emptyList()

            val results = mutableListOf<PhotonPlaceResult>()
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val geometry = feature.optJSONObject("geometry")
                val coordinates = geometry?.optJSONArray("coordinates")
                val lon = coordinates?.optDouble(0) ?: 0.0
                val lat = coordinates?.optDouble(1) ?: 0.0

                val properties = feature.optJSONObject("properties") ?: continue
                val name = properties.optString("name", "")
                val street = properties.optString("street", "")
                val housenumber = properties.optString("housenumber", "")
                val city = properties.optString("city", properties.optString("town", properties.optString("village", "")))
                val state = properties.optString("state", "")
                val country = properties.optString("country", "")
                val type = properties.optString("type", properties.optString("osm_value", "Location"))

                val addressParts = listOfNotNull(
                    if (housenumber.isNotBlank() && street.isNotBlank()) "$housenumber $street" else street.ifBlank { null },
                    city.ifBlank { null },
                    state.ifBlank { null },
                    country.ifBlank { null }
                )
                val formattedAddress = addressParts.joinToString(", ")

                val placeName = name.ifBlank { street.ifBlank { city.ifBlank { "Location" } } }

                results.add(
                    PhotonPlaceResult(
                        name = placeName,
                        formattedAddress = if (formattedAddress.isBlank()) placeName else formattedAddress,
                        city = city.ifBlank { null },
                        state = state.ifBlank { null },
                        country = country.ifBlank { null },
                        lat = lat,
                        lon = lon,
                        type = type
                    )
                )
            }
            results
        } catch (e: Exception) {
            Log.e("OsmLocationService", "Error calling Photon API", e)
            emptyList()
        }
    }

    /**
     * Nominatim Reverse Geocoding API
     * Endpoint: https://nominatim.openstreetmap.org/reverse?lat={lat}&lon={lon}&format=json
     */
    suspend fun reverseGeocodeNominatim(lat: Double, lon: Double): NominatimReverseResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18&addressdetails=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SawaariShare-AndroidApp/1.0 (student.carpool@app.com)")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                Log.e("OsmLocationService", "Nominatim API HTTP ${response.code}: $responseBody")
                return@withContext null
            }

            val root = JSONObject(responseBody)
            val displayName = root.optString("display_name", "Unknown Location")
            val address = root.optJSONObject("address")

            val road = address?.optString("road", address.optString("pedestrian", ""))
            val city = address?.optString("city", address.optString("town", address.optString("suburb", "")))
            val state = address?.optString("state", "")
            val country = address?.optString("country", "")

            NominatimReverseResult(
                displayName = displayName,
                road = road?.ifBlank { null },
                city = city?.ifBlank { null },
                state = state?.ifBlank { null },
                country = country?.ifBlank { null },
                lat = lat,
                lon = lon
            )
        } catch (e: Exception) {
            Log.e("OsmLocationService", "Error calling Nominatim API", e)
            null
        }
    }
}
