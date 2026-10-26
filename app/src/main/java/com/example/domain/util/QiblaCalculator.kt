package com.example.domain.util

import android.hardware.GeomagneticField
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object QiblaCalculator {
    // Official coordinates of the Holy Kaaba in Makkah
    const val KAABA_LATITUDE = 21.422487
    const val KAABA_LONGITUDE = 39.826206

    /**
     * Calculates the Great Circle initial bearing (forward azimuth) from (lat, lon)
     * to the Holy Kaaba in degrees [0.0, 360.0) relative to True North.
     *
     * Formula:
     * θ = atan2( sin(Δλ) * cos(φ2), cos(φ1) * sin(φ2) − sin(φ1) * cos(φ2) * cos(Δλ) )
     */
    fun calculateQiblaBearing(latitude: Double, longitude: Double): Double {
        val phi1 = Math.toRadians(latitude)
        val phi2 = Math.toRadians(KAABA_LATITUDE)
        val deltaLambda = Math.toRadians(KAABA_LONGITUDE - longitude)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val thetaRadians = atan2(y, x)
        val bearing = (Math.toDegrees(thetaRadians) + 360.0) % 360.0
        return bearing
    }

    /**
     * Computes Magnetic Declination (difference between True North and Magnetic North)
     * using Android's World Magnetic Model (GeomagneticField) for the given coordinates and time.
     */
    fun computeMagneticDeclination(
        latitude: Double,
        longitude: Double,
        altitudeMeters: Float = 0f,
        timeMillis: Long = System.currentTimeMillis()
    ): Float {
        return try {
            val field = GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                altitudeMeters,
                timeMillis
            )
            field.declination
        } catch (e: Exception) {
            0f
        }
    }

    /**
     * Converts Magnetic North azimuth [0, 360) to True North azimuth [0, 360)
     * by adding the magnetic declination.
     */
    fun magneticToTrueNorth(magneticAzimuth: Float, declination: Float): Float {
        return ((magneticAzimuth + declination) % 360f + 360f) % 360f
    }

    /**
     * Calculates shortest angular difference in degrees [-180, +180] between current true heading
     * and the Qibla bearing. If abs(diff) <= 5 degrees, the device is aligned with Qibla.
     */
    fun shortestAngleDiff(currentTrueHeading: Float, qiblaBearing: Float): Float {
        var diff = (qiblaBearing - currentTrueHeading) % 360f
        if (diff < -180f) diff += 360f
        if (diff > 180f) diff -= 360f
        return diff
    }
}
