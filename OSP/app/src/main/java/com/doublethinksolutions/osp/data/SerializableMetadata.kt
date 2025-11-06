package com.doublethinksolutions.osp.data

import android.location.Location
import kotlinx.serialization.Serializable

/**
 * A serializable wrapper for the Android Location class.
 */
@Serializable
data class SerializableLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val bearing: Float,
    val speed: Float,
    val time: Long,
    val provider: String?
)

/**
 * Extension function to convert a standard Android Location object to our serializable version.
 */
fun Location.toSerializable(): SerializableLocation {
    return SerializableLocation(
        latitude = this.latitude,
        longitude = this.longitude,
        altitude = this.altitude,
        accuracy = this.accuracy,
        bearing = this.bearing,
        speed = this.speed,
        time = this.time,
        provider = this.provider
    )
}

/**
 * A serializable data class for device orientation.
 */
@Serializable
data class SerializableDeviceOrientation(
    val azimuth: Float,
    val pitch: Float,
    val roll: Float
)

/**
 * A serializable version of the PhotoMetadata class. This is the object that will be
 * converted to JSON and hashed.
 */
@Serializable
data class SerializablePhotoMetadata(
    val timestamp: Long,
    val location: SerializableLocation?,
    val deviceOrientation: SerializableDeviceOrientation?
)
