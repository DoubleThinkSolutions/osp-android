package com.doublethinksolutions.osp.tasks

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*

/**
 * A Singleton object responsible for providing the device's real-time location.
 *
 * This provider implements a graceful degradation strategy:
 * 1. It PREFERS using the modern FusedLocationProviderClient for efficient and accurate location.
 * 2. If Google Play Services is unavailable, it FALLS BACK to the legacy LocationManager.
 *
 * This ensures the app functions on the widest range of devices.
 */
object LocationProvider {

    @Volatile
    var latestLocation: Location? = null
        private set

    // --- State properties for both strategies ---
    private var isUsingGms = false

    // GMS (Google Play Services) components
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    // Legacy Android Framework components
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null


    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (fusedLocationClient != null || locationManager != null) {
            Log.d("LocationProvider", "Location updates are already active.")
            return
        }

        if (!hasLocationPermission(context)) {
            Log.w("LocationProvider", "Location permission not granted. Cannot start location updates.")
            return
        }

        // --- STRATEGY SELECTION ---
        val apiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = apiAvailability.isGooglePlayServicesAvailable(context)

        if (resultCode == ConnectionResult.SUCCESS) {
            isUsingGms = true
            startGmsLocationUpdates(context.applicationContext)
        } else {
            isUsingGms = false
            startLegacyLocationUpdates(context.applicationContext)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGmsLocationUpdates(context: Context) {
        Log.i("LocationProvider", "Starting location updates with Google Play Services.")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        // Set an initial value from last known location
        fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
            if (location != null) latestLocation = location
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                latestLocation = locationResult.lastLocation
            }
        }

        fusedLocationClient?.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    @SuppressLint("MissingPermission")
    private fun startLegacyLocationUpdates(context: Context) {
        Log.i("LocationProvider", "Google Play Services not found. Falling back to legacy LocationManager.")
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Set an initial value from last known location
        val lastKnownGps = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val lastKnownNetwork = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        latestLocation = lastKnownGps ?: lastKnownNetwork

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                latestLocation = location
            }
            // Other methods can be left empty for this use case
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // Register for updates from all available providers for better coverage
        val providers = locationManager?.getProviders(true) ?: emptyList()
        providers.forEach { provider ->
            Log.d("LocationProvider", "Requesting legacy updates from provider: $provider")
            locationManager?.requestLocationUpdates(provider, 5000L, 10f, locationListener!!)
        }
    }

    fun stop() {
        if (isUsingGms) {
            fusedLocationClient?.removeLocationUpdates(locationCallback!!)
            fusedLocationClient = null
            locationCallback = null
            Log.i("LocationProvider", "Stopped GMS location updates.")
        } else {
            locationManager?.removeUpdates(locationListener!!)
            locationManager = null
            locationListener = null
            Log.i("LocationProvider", "Stopped legacy location updates.")
        }
        isUsingGms = false
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
