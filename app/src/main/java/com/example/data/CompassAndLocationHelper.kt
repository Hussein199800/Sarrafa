package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.domain.util.QiblaCalculator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class CompassSensorReading(
    val magneticAzimuthDegrees: Float,
    val accuracy: Int, // SensorManager.SENSOR_STATUS_*
    val isSensorAvailable: Boolean
)

class CompassAndLocationHelper(private val context: Context) {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    fun hasCompassSensor(): Boolean {
        val sm = sensorManager ?: return false
        val rotationVector = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val geomagneticRotation = sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        return rotationVector != null || geomagneticRotation != null || (accel != null && magnet != null)
    }

    /**
     * Streams live compass azimuth readings using Rotation Vector (or Accelerometer + Magnetometer fallback).
     * Only active while collected by UI (e.g. Qibla screen).
     */
    fun observeCompass(): Flow<CompassSensorReading> = callbackFlow {
        val sm = sensorManager
        if (sm == null || !hasCompassSensor()) {
            trySend(
                CompassSensorReading(
                    magneticAzimuthDegrees = 0f,
                    accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
                    isSensorAvailable = false
                )
            )
            awaitClose { }
            return@callbackFlow
        }

        val rotationSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

        val accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var currentAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        var smoothedAzimuth = 0f
        var initialized = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val azimuthRad = orientationAngles[0].toDouble()
                        val rawDeg = ((Math.toDegrees(azimuthRad) + 360.0) % 360.0).toFloat()
                        smoothedAzimuth = if (!initialized) {
                            initialized = true
                            rawDeg
                        } else {
                            val diff = QiblaCalculator.shortestAngleDiff(smoothedAzimuth, rawDeg)
                            ((smoothedAzimuth + 0.2f * diff) % 360f + 360f) % 360f
                        }
                        trySend(
                            CompassSensorReading(
                                magneticAzimuthDegrees = smoothedAzimuth,
                                accuracy = currentAccuracy,
                                isSensorAvailable = true
                            )
                        )
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                        if (hasGeomagnetic && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                            SensorManager.getOrientation(rotationMatrix, orientationAngles)
                            val rawDeg = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
                            smoothedAzimuth = if (!initialized) {
                                initialized = true
                                rawDeg
                            } else {
                                val diff = QiblaCalculator.shortestAngleDiff(smoothedAzimuth, rawDeg)
                                ((smoothedAzimuth + 0.15f * diff) % 360f + 360f) % 360f
                            }
                            trySend(
                                CompassSensorReading(
                                    magneticAzimuthDegrees = smoothedAzimuth,
                                    accuracy = currentAccuracy,
                                    isSensorAvailable = true
                                )
                            )
                        }
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasGeomagnetic = true
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                currentAccuracy = accuracy
            }
        }

        if (rotationSensor != null) {
            sm.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            magSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        }

        awaitClose {
            sm.unregisterListener(listener)
        }
    }

    /**
     * Reads last known approximate location locally from Android's LocationManager without Play Services
     * and without sending any data over the network.
     */
    fun getApproximateDeviceLocation(): Pair<Double, Double>? {
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCoarse) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        return try {
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                    bestLocation = loc
                }
            }
            bestLocation?.let { Pair(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
