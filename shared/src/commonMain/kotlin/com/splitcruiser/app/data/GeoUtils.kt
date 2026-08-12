package com.splitcruiser.app.data

import kotlin.math.*

object GeoUtils {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private val BITS = intArrayOf(16, 8, 4, 2, 1)

    fun encodeGeohash(latitude: Double, longitude: Double, precision: Int = 10): String {
        var isEven = true
        val latRange = doubleArrayOf(-90.0, 90.0)
        val lonRange = doubleArrayOf(-180.0, 180.0)
        val geohash = StringBuilder()
        var bit = 0
        var ch = 0

        while (geohash.length < precision) {
            val mid: Double
            if (isEven) {
                mid = (lonRange[0] + lonRange[1]) / 2.0
                if (longitude > mid) {
                    ch = ch or BITS[bit]
                    lonRange[0] = mid
                } else {
                    lonRange[1] = mid
                }
            } else {
                mid = (latRange[0] + latRange[1]) / 2.0
                if (latitude > mid) {
                    ch = ch or BITS[bit]
                    latRange[0] = mid
                } else {
                    latRange[1] = mid
                }
            }

            isEven = !isEven
            if (bit < 4) {
                bit++
            } else {
                geohash.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }
        return geohash.toString()
    }

    // Great circle distance (Haversine formula) in miles
    fun distanceInMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 3958.8 // Radius of the earth in miles
        val latDistance = (lat2 - lat1).toRadians()
        val lonDistance = (lon2 - lon1).toRadians()
        val a = sin(latDistance / 2.0) * sin(latDistance / 2.0) +
                cos(lat1.toRadians()) * cos(lat2.toRadians()) *
                sin(lonDistance / 2.0) * sin(lonDistance / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }

    /**
     * "0.4 mi", "12 mi". Shared so the route card and the address suggestions round identically —
     * `String.format` is JVM-only, so the rounding is done by hand.
     */
    fun formatMiles(miles: Double): String = when {
        miles < 0.1 -> "< 0.1 mi"
        miles < 10 -> "${oneDecimal(miles)} mi"
        else -> "${miles.roundToInt()} mi"
    }

    private fun oneDecimal(value: Double): String {
        val scaled = (value * 10).roundToInt()
        return "${scaled / 10}.${scaled % 10}"
    }

    /** `java.lang.Math` does not exist in commonMain. */
    private fun Double.toRadians(): Double = this * PI / 180.0
}
