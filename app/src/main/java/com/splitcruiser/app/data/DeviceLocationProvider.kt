package com.splitcruiser.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Where the device actually is.
 *
 * Nothing in this app has ever read a real location. The "Use GPS" chip in the address dropdown had
 * Northeastern's campus (42.3383, -71.0881) hardcoded and reverse-geocoded that, and the manifest
 * declared no location permission at all — so address suggestions could not be ranked by distance
 * from the user, and typing a home address returned whatever OSM considered most important.
 *
 * Deliberately not in `:shared`. The iOS half is `CLLocationManager`, which cannot be
 * compile-checked on Linux — the same reason `KeychainStore` was deferred. Only the *ranking* is
 * shared ([PlaceRanking]), so both platforms order results identically without either one needing
 * the other's platform APIs.
 */
object DeviceLocationProvider {

    /**
     * What the permission launcher should request. Coarse alone is enough to rank suggestions, and
     * it is the only location permission declared in the manifest — requesting FINE here as well
     * would silently no-op (an undeclared runtime permission is never granted) and over-ask.
     */
    val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun hasPermission(context: Context): Boolean = PERMISSIONS.any { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * A current fix, or null when permission is missing, location is off, or nothing answers.
     *
     * Returns null rather than throwing: every caller's fallback is "rank by something else", and a
     * denied permission is an ordinary outcome, not an error.
     *
     * `getCurrentLocation` rather than `lastLocation` alone — a device that has not been asked for
     * a location recently has no last one, which is exactly the state a fresh install is in. The
     * cached value is still used as a fallback because it returns instantly.
     */
    suspend fun current(context: Context): DeviceCoordinate? {
        if (!hasPermission(context)) return null
        val client = runCatching { LocationServices.getFusedLocationProviderClient(context) }
            .getOrNull() ?: return null

        val fresh = runCatching {
            val cancellation = CancellationTokenSource()
            suspendCancellableCoroutine { continuation ->
                client
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resume(null) }
                continuation.invokeOnCancellation { cancellation.cancel() }
            }
        }.getOrNull()

        val location = fresh ?: runCatching {
            suspendCancellableCoroutine { continuation ->
                client.lastLocation
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resume(null) }
            }
        }.getOrNull()

        return location?.let { DeviceCoordinate(it.latitude, it.longitude) }
    }
}

/** A plain lat/lon, so callers do not have to depend on `android.location.Location`. */
data class DeviceCoordinate(val lat: Double, val lon: Double)
