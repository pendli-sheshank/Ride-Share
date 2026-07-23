package com.example.data

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
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latDistance / 2.0) * sin(latDistance / 2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2.0) * sin(lonDistance / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }
}
