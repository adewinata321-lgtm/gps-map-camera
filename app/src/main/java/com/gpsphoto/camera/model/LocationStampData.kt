package com.gpsphoto.camera.model

import android.graphics.Bitmap

data class LocationStampData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val districtTitle: String = "Mencari Lokasi...",
    val fullAddress: String = "Sedang mengambil data alamat GPS...",
    val formattedLat: String = "Lat : -",
    val formattedLng: String = "Long : -",
    val formattedDate: String = "",
    val formattedTime: String = "",
    val timezoneStr: String = "GMT",
    val mapBitmap: Bitmap? = null,
    val watermarkTag: String = "LocSnap - GPS Timestamp Camera"
)
