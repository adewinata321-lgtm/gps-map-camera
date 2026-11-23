package com.gpsphoto.camera.helper

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class GeocoderHelper(private val context: Context) {

    data class GeoResult(
        val districtTitle: String,
        val fullAddress: String,
        val formattedLat: String,
        val formattedLng: String,
        val formattedDate: String,
        val formattedTime: String,
        val timezoneStr: String
    )

    suspend fun resolveLocation(lat: Double, lng: Double): GeoResult = withContext(Dispatchers.IO) {
        val now = Date()
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.US)
        
        val formattedDate = dateFormat.format(now)
        val formattedTime = timeFormat.format(now)

        // Timezone calculation
        val tz = TimeZone.getDefault()
        val tzName = when {
            tz.id.contains("Jakarta") || tz.rawOffset == 7 * 3600 * 1000 -> "WIB"
            tz.id.contains("Makassar") || tz.rawOffset == 8 * 3600 * 1000 -> "WITA"
            tz.id.contains("Jayapura") || tz.rawOffset == 9 * 3600 * 1000 -> "WIT"
            else -> "GMT"
        }

        // Decimal formatting with comma separator (e.g. 6,22725°S / 107,02466°E)
        val latDirection = if (lat >= 0) "°N" else "°S"
        val lngDirection = if (lng >= 0) "°E" else "°W"
        val formattedLat = String.format(Locale.GERMANY, "Lat : %.5f%s", abs(lat), latDirection)
        val formattedLng = String.format(Locale.GERMANY, "Long : %.5f%s", abs(lng), lngDirection)

        var title = ""
        var address = ""

        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale("id", "ID"))
                @Suppress("DEPRECATION")
                val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var resultList: List<Address>? = null
                    geocoder.getFromLocation(lat, lng, 1) { resultList = it }
                    // Wait brief moment for async callback if needed or use synchronous fallback
                    resultList ?: geocoder.getFromLocation(lat, lng, 1)
                } else {
                    geocoder.getFromLocation(lat, lng, 1)
                }

                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    title = buildDistrictTitle(addr)
                    address = addr.getAddressLine(0) ?: buildAddressFromComponents(addr)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If Geocoder failed or gave empty results, try Nominatim OpenStreetMap fallback
        if (address.isBlank()) {
            val fallback = fetchNominatimFallback(lat, lng)
            if (fallback != null) {
                title = fallback.first
                address = fallback.second
            }
        }

        // Ultimate fallback
        if (title.isBlank()) {
            title = String.format(Locale.US, "Koordinat GPS: %.4f, %.4f", lat, lng)
        }
        if (address.isBlank()) {
            address = "Lokasi terdeteksi pada Lat $formattedLat, Long $formattedLng"
        }

        GeoResult(
            districtTitle = title,
            fullAddress = address,
            formattedLat = formattedLat,
            formattedLng = formattedLng,
            formattedDate = formattedDate,
            formattedTime = formattedTime,
            timezoneStr = tzName
        )
    }

    private fun buildDistrictTitle(addr: Address): String {
        val parts = mutableListOf<String>()
        val district = addr.locality ?: addr.subLocality ?: addr.subAdminArea
        if (!district.isNullOrBlank()) {
            if (!district.startsWith("Kecamatan", ignoreCase = true) && !district.startsWith("Kec.", ignoreCase = true)) {
                parts.add("Kecamatan $district")
            } else {
                parts.add(district)
            }
        }
        val admin = addr.adminArea
        if (!admin.isNullOrBlank()) {
            parts.add(admin)
        }
        return if (parts.isNotEmpty()) parts.joinToString(", ") else "Lokasi Terkini"
    }

    private fun buildAddressFromComponents(addr: Address): String {
        val parts = mutableListOf<String>()
        addr.thoroughfare?.let { parts.add(it) }
        addr.subThoroughfare?.let { parts.add("No. $it") }
        addr.subLocality?.let { parts.add(it) }
        addr.locality?.let { parts.add(it) }
        addr.subAdminArea?.let { parts.add(it) }
        addr.adminArea?.let { parts.add(it) }
        addr.postalCode?.let { parts.add(it) }
        addr.countryName?.let { parts.add(it) }
        return parts.joinToString(", ")
    }

    private fun fetchNominatimFallback(lat: Double, lng: Double): Pair<String, String>? {
        return try {
            val url = URL(String.format(Locale.US, "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f&zoom=18&addressdetails=1", lat, lng))
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "GPSPhotoApp/1.0 (Android)")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                val json = JSONObject(response)
                val displayName = json.optString("display_name", "")
                val addressObj = json.optJSONObject("address")

                var title = "Lokasi Terkini"
                if (addressObj != null) {
                    val suburb = addressObj.optString("suburb", addressObj.optString("village", ""))
                    val state = addressObj.optString("state", "")
                    title = if (suburb.isNotBlank() && state.isNotBlank()) "$suburb, $state" else displayName
                }
                Pair(title, displayName)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
