package com.example.saferoute.risk

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeohashUtils {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(lat: Double, lng: Double, precision: Int = 6): String {
        var latMin = -90.0
        var latMax = 90.0
        var lngMin = -180.0
        var lngMax = 180.0

        val bits = intArrayOf(16, 8, 4, 2, 1)
        var bit = 0
        var ch = 0
        var isEven = true
        val geohash = StringBuilder()

        while (geohash.length < precision) {
            if (isEven) {
                val mid = (lngMin + lngMax) / 2.0
                if (lng >= mid) {
                    ch = ch or bits[bit]
                    lngMin = mid
                } else {
                    lngMax = mid
                }
            } else {
                val mid = (latMin + latMax) / 2.0
                if (lat >= mid) {
                    ch = ch or bits[bit]
                    latMin = mid
                } else {
                    latMax = mid
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

    /**
     * Returns the 8 adjacent neighboring geohashes plus the center geohash.
     */
    fun getNeighbors(geohash: String): List<String> {
        val decoded = decode(geohash)
        val lat = decoded.first
        val lng = decoded.second
        val latErr = decoded.third
        val lngErr = decoded.fourth

        val neighbors = mutableSetOf<String>()
        neighbors.add(geohash)

        val dLats = doubleArrayOf(-latErr * 2.0, 0.0, latErr * 2.0)
        val dLngs = doubleArrayOf(-lngErr * 2.0, 0.0, lngErr * 2.0)

        for (dLat in dLats) {
            for (dLng in dLngs) {
                val nLat = (lat + dLat).coerceIn(-89.9, 89.9)
                val nLng = (lng + dLng).coerceIn(-179.9, 179.9)
                neighbors.add(encode(nLat, nLng, geohash.length))
            }
        }
        return neighbors.toList()
    }

    private fun decode(geohash: String): Quadruple<Double, Double, Double, Double> {
        var isEven = true
        var latMin = -90.0
        var latMax = 90.0
        var lngMin = -180.0
        var lngMax = 180.0

        for (c in geohash) {
            val cd = BASE32.indexOf(c)
            if (cd == -1) continue

            for (mask in intArrayOf(16, 8, 4, 2, 1)) {
                if (isEven) {
                    val mid = (lngMin + lngMax) / 2.0
                    if ((cd and mask) != 0) {
                        lngMin = mid
                    } else {
                        lngMax = mid
                    }
                } else {
                    val mid = (latMin + latMax) / 2.0
                    if ((cd and mask) != 0) {
                        latMin = mid
                    } else {
                        latMax = mid
                    }
                }
                isEven = !isEven
            }
        }
        val lat = (latMin + latMax) / 2.0
        val lng = (lngMin + lngMax) / 2.0
        val latErr = (latMax - latMin) / 2.0
        val lngErr = (lngMax - lngMin) / 2.0
        return Quadruple(lat, lng, latErr, lngErr)
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val bearing = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        return bearing.toFloat()
    }

    fun headingDifference(h1: Float, h2: Float): Float {
        var diff = Math.abs(h1 - h2) % 360f
        if (diff > 180f) {
            diff = 360f - diff
        }
        return diff
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
