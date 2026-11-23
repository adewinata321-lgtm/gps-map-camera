package com.gpsphoto.camera.helper

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.google.android.gms.location.*

class LocationHelper(
    private val context: Context,
    private val onLocationUpdated: (Location) -> Unit
) {
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationManager: LocationManager? = null
    private var fallbackListener: LocationListener? = null

    init {
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        // First try Google Play Services Fused Location
        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2000L
            ).setMinUpdateIntervalMillis(1000L).build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { onLocationUpdated(it) }
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )?.addOnFailureListener {
                // Fallback to LocationManager if Google Play Services fails
                startFallbackLocation()
            }

            // Also get last known location immediately
            fusedLocationClient?.lastLocation?.addOnSuccessListener { loc ->
                if (loc != null) {
                    onLocationUpdated(loc)
                }
            }
        } catch (e: Exception) {
            startFallbackLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startFallbackLocation() {
        val lm = locationManager ?: return
        fallbackListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocationUpdated(location)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L,
                    1f,
                    fallbackListener!!,
                    Looper.getMainLooper()
                )
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocationUpdated(it) }
            } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    1f,
                    fallbackListener!!,
                    Looper.getMainLooper()
                )
                lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { onLocationUpdated(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
        fallbackListener?.let {
            locationManager?.removeUpdates(it)
        }
    }
}
