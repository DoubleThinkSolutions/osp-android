package com.doublethinksolutions.osp.tasks

import android.location.Location
import com.doublethinksolutions.osp.data.*

/**
 * A task responsible for synchronously collecting all required metadata just before
 * a photo is taken and converting it to a serializable format.
 */
class MetadataCollectionTask {

    /**
     * Collects all metadata and returns it in a SerializablePhotoMetadata object.
     *
     * @return A populated [SerializablePhotoMetadata] object.
     */
    fun collect(): SerializablePhotoMetadata {
        val timestamp = System.currentTimeMillis()
        val location = collectLocation()?.toSerializable()
        val orientation = collectDeviceOrientation()?.let {
            SerializableDeviceOrientation(it.azimuth, it.pitch, it.roll)
        }

        return SerializablePhotoMetadata(
            timestamp = timestamp,
            location = location,
            deviceOrientation = orientation
        )
    }

    private fun collectLocation(): Location? {
        return LocationProvider.latestLocation
    }

    private fun collectDeviceOrientation(): DeviceOrientation? {
        return OrientationProvider.latestOrientation
    }
}
