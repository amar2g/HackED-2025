package com.uofa.arcampusnav.data.models

import com.google.firebase.firestore.GeoPoint
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Building model representing campus buildings
 */
@Entity(tableName = "buildings")
data class Building(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val description: String,
    val rooms: List<String> = emptyList(),
    val imageUrl: String? = null,
    val accessibilityFeatures: List<String> = emptyList(),
    val popularTimes: Map<String, Int> = emptyMap() // Hour to crowd level
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
    
    fun getDistance(userLat: Double, userLng: Double): Double {
        val earthRadius = 6371e3 // meters
        val lat1Rad = Math.toRadians(userLat)
        val lat2Rad = Math.toRadians(latitude)
        val deltaLat = Math.toRadians(latitude - userLat)
        val deltaLng = Math.toRadians(longitude - userLng)
        
        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }
}

/**
 * Navigation route from Google Directions API
 */
data class NavigationRoute(
    val steps: List<RouteStep>,
    val totalDistance: Int, // meters
    val totalDuration: Int, // seconds
    val polyline: String,
    val bounds: RouteBounds
)

data class RouteStep(
    val instruction: String,
    val distance: Int,
    val duration: Int,
    val startLocation: Location,
    val endLocation: Location,
    val polyline: String,
    val maneuver: String? = null
)

data class Location(
    val latitude: Double,
    val longitude: Double
)

data class RouteBounds(
    val northeast: Location,
    val southwest: Location
)

/**
 * AR Node representing virtual objects in AR space
 */
data class ARNode(
    val id: String,
    val type: ARNodeType,
    val position: ARPosition,
    val rotation: ARRotation = ARRotation(0f, 0f, 0f),
    val scale: Float = 1f,
    val metadata: Map<String, Any> = emptyMap()
)

enum class ARNodeType {
    ARROW,
    MARKER,
    INFO_PANEL,
    WAYPOINT,
    BUILDING_LABEL
}

data class ARPosition(
    val x: Float,
    val y: Float,
    val z: Float
)

data class ARRotation(
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * Device sensor data
 */
data class SensorData(
    val accelerometer: FloatArray,
    val gyroscope: FloatArray,
    val magnetometer: FloatArray,
    val gpsLocation: android.location.Location?,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SensorData

        if (!accelerometer.contentEquals(other.accelerometer)) return false
        if (!gyroscope.contentEquals(other.gyroscope)) return false
        if (!magnetometer.contentEquals(other.magnetometer)) return false
        if (gpsLocation != other.gpsLocation) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = accelerometer.contentHashCode()
        result = 31 * result + gyroscope.contentHashCode()
        result = 31 * result + magnetometer.contentHashCode()
        result = 31 * result + (gpsLocation?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * User preferences
 */
data class UserPreferences(
    val navigationMode: NavigationMode = NavigationMode.WALKING,
    val arOverlayStyle: AROverlayStyle = AROverlayStyle.STANDARD,
    val showCrowdData: Boolean = true,
    val voiceGuidance: Boolean = false,
    val preferAccessibleRoutes: Boolean = false
)

enum class NavigationMode {
    WALKING,
    ACCESSIBLE,
    FASTEST
}

enum class AROverlayStyle {
    MINIMAL,
    STANDARD,
    DETAILED
}
