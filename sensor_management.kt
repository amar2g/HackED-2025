package com.uofa.arcampusnav.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager as AndroidSensorManager
import android.location.Location
import com.google.android.gms.location.*
import com.uofa.arcampusnav.data.models.SensorData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Manages device sensors for AR navigation
 * Implements sensor fusion for accurate 6DOF tracking
 */
class DeviceSensorManager(context: Context) {
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as AndroidSensorManager
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    
    // Sensors
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    
    // Sensor data storage
    private val accelerometerData = FloatArray(3)
    private val gyroscopeData = FloatArray(3)
    private val magnetometerData = FloatArray(3)
    private var currentLocation: Location? = null
    
    // Rotation matrices for sensor fusion
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    
    /**
     * Start listening to all sensors
     */
    fun observeSensorData(): Flow<SensorData> = callbackFlow {
        
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, accelerometerData, 0, 3)
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        System.arraycopy(event.values, 0, gyroscopeData, 0, 3)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, magnetometerData, 0, 3)
                    }
                }
                
                // Send updated sensor data
                trySend(
                    SensorData(
                        accelerometer = accelerometerData.copyOf(),
                        gyroscope = gyroscopeData.copyOf(),
                        magnetometer = magnetometerData.copyOf(),
                        gpsLocation = currentLocation
                    )
                )
            }
            
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // Handle accuracy changes if needed
            }
        }
        
        // Register sensor listeners
        accelerometer?.let {
            sensorManager.registerListener(
                sensorListener,
                it,
                AndroidSensorManager.SENSOR_DELAY_GAME
            )
        }
        
        gyroscope?.let {
            sensorManager.registerListener(
                sensorListener,
                it,
                AndroidSensorManager.SENSOR_DELAY_GAME
            )
        }
        
        magnetometer?.let {
            sensorManager.registerListener(
                sensorListener,
                it,
                AndroidSensorManager.SENSOR_DELAY_GAME
            )
        }
        
        // Location updates
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // 1 second interval
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setMaxUpdateDelayMillis(2000L)
        }.build()
        
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                currentLocation = locationResult.lastLocation
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null
            )
        } catch (e: SecurityException) {
            // Handle permission error
            close(e)
        }
        
        awaitClose {
            sensorManager.unregisterListener(sensorListener)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
    
    /**
     * Calculate device orientation from sensor data
     * Returns azimuth, pitch, and roll in radians
     */
    fun calculateOrientation(
        accelerometerValues: FloatArray,
        magnetometerValues: FloatArray
    ): FloatArray {
        val rotationMatrixSuccess = AndroidSensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerValues,
            magnetometerValues
        )
        
        return if (rotationMatrixSuccess) {
            AndroidSensorManager.getOrientation(rotationMatrix, orientationAngles)
            floatArrayOf(
                Math.toDegrees(orientationAngles[0].toDouble()).toFloat(), // Azimuth (Z)
                Math.toDegrees(orientationAngles[1].toDouble()).toFloat(), // Pitch (X)
                Math.toDegrees(orientationAngles[2].toDouble()).toFloat()  // Roll (Y)
            )
        } else {
            floatArrayOf(0f, 0f, 0f)
        }
    }
    
    /**
     * Apply complementary filter for gyroscope and accelerometer fusion
     * Reduces noise and drift
     */
    fun applyComplementaryFilter(
        gyroData: FloatArray,
        accelData: FloatArray,
        alpha: Float = 0.98f,
        dt: Float = 0.01f
    ): FloatArray {
        val result = FloatArray(3)
        
        for (i in 0..2) {
            result[i] = alpha * (gyroData[i] * dt) + (1 - alpha) * accelData[i]
        }
        
        return result
    }
    
    /**
     * Calculate bearing to destination
     */
    fun calculateBearing(
        currentLat: Double,
        currentLng: Double,
        destLat: Double,
        destLng: Double
    ): Float {
        val lat1 = Math.toRadians(currentLat)
        val lat2 = Math.toRadians(destLat)
        val deltaLng = Math.toRadians(destLng - currentLng)
        
        val y = Math.sin(deltaLng) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLng)
        
        val bearing = Math.toDegrees(Math.atan2(y, x))
        
        return ((bearing + 360) % 360).toFloat()
    }
    
    /**
     * Get adaptive polling rate based on movement
     * Saves battery when device is stationary
     */
    fun getAdaptivePollingRate(accelerometerValues: FloatArray): Int {
        val magnitude = Math.sqrt(
            (accelerometerValues[0] * accelerometerValues[0] +
             accelerometerValues[1] * accelerometerValues[1] +
             accelerometerValues[2] * accelerometerValues[2]).toDouble()
        )
        
        return when {
            magnitude > 12.0 -> AndroidSensorManager.SENSOR_DELAY_GAME // Fast movement
            magnitude > 10.5 -> AndroidSensorManager.SENSOR_DELAY_UI    // Normal movement
            else -> AndroidSensorManager.SENSOR_DELAY_NORMAL            // Stationary
        }
    }
}
