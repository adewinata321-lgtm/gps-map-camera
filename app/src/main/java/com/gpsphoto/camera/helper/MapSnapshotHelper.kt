package com.gpsphoto.camera.helper

import android.graphics.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

object MapSnapshotHelper {

    enum class MapType {
        SATELLITE,
        STREET,
        NONE
    }

    suspend fun getMapThumbnail(
        lat: Double,
        lng: Double,
        mapType: MapType = MapType.SATELLITE,
        targetWidth: Int = 300,
        targetHeight: Int = 300
    ): Bitmap = withContext(Dispatchers.IO) {
        if (mapType == MapType.NONE) {
            return@withContext createFallbackMap(targetWidth, targetHeight, false)
        }

        val zoom = 17
        val (tileX, tileY) = getTileXY(lat, lng, zoom)

        // Select tile provider
        val urlString = when (mapType) {
            MapType.SATELLITE ->
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$tileY/$tileX"
            MapType.STREET ->
                "https://tile.openstreetmap.org/$zoom/$tileX/$tileY.png"
            else -> ""
        }

        var downloadedBitmap: Bitmap? = null
        if (urlString.isNotBlank()) {
            try {
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "GPSPhotoApp/1.0 (Android)")
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.doInput = true
                conn.connect()

                if (conn.responseCode == 200) {
                    val stream = conn.inputStream
                    downloadedBitmap = BitmapFactory.decodeStream(stream)
                    stream.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val finalBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)

        if (downloadedBitmap != null) {
            // Draw downloaded map tile scaled to fit
            val srcRect = Rect(0, 0, downloadedBitmap.width, downloadedBitmap.height)
            val dstRect = Rect(0, 0, targetWidth, targetHeight)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(downloadedBitmap, srcRect, dstRect, paint)
        } else {
            // Offline fallback map
            val fallback = createFallbackMap(targetWidth, targetHeight, mapType == MapType.SATELLITE)
            canvas.drawBitmap(fallback, 0f, 0f, null)
        }

        // Draw Center Red Pin Marker
        drawRedPinMarker(canvas, targetWidth / 2f, targetHeight / 2f, targetWidth * 0.28f)

        // Draw Map Provider branding watermark at bottom-left corner
        val brandText = when (mapType) {
            MapType.SATELLITE -> "ArcGIS World Imagery"
            MapType.STREET -> "OpenStreetMap"
            MapType.NONE -> ""
        }
        if (brandText.isNotBlank()) {
            drawWatermarkBrand(canvas, brandText, targetWidth, targetHeight)
        }

        finalBitmap
    }

    private fun getTileXY(lat: Double, lng: Double, zoom: Int): Pair<Int, Int> {
        val n = 2.0.pow(zoom.toDouble())
        val x = ((lng + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(lat)
        val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toInt()
        return Pair(x, y)
    }

    private fun drawRedPinMarker(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EA4335") // Google Red
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 4f, Color.parseColor("#99000000"))
        }

        val pinPath = Path()
        val radius = size * 0.38f
        val headCenterY = cy - size * 0.25f

        // Draw Pin Head Circle
        pinPath.addCircle(cx, headCenterY, radius, Path.Direction.CW)

        // Draw Pin Pointy Needle downwards
        pinPath.moveTo(cx - radius * 0.82f, headCenterY + radius * 0.45f)
        pinPath.lineTo(cx, cy + size * 0.35f)
        pinPath.lineTo(cx + radius * 0.82f, headCenterY + radius * 0.45f)
        pinPath.close()

        canvas.drawPath(pinPath, pinPaint)

        // Draw Inner Circle Dot
        val innerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B31412") // Darker red inner ring
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, headCenterY, radius * 0.42f, innerCirclePaint)

        val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, headCenterY, radius * 0.25f, centerDotPaint)
    }

    private fun drawWatermarkBrand(canvas: Canvas, text: String, width: Int, height: Int) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#99000000")
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = width * 0.052f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textWidth = textPaint.measureText(text)
        val paddingH = width * 0.025f
        val paddingV = width * 0.015f
        val margin = width * 0.02f
        val rect = RectF(
            margin,
            height - textPaint.textSize - (paddingV * 2.5f) - margin,
            margin + textWidth + (paddingH * 2),
            height - margin
        )
        canvas.drawRoundRect(rect, 4f, 4f, bgPaint)
        canvas.drawText(text, rect.left + paddingH, rect.bottom - paddingV - 1.5f, textPaint)
    }

    private fun createFallbackMap(w: Int, h: Int, isSatellite: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint().apply {
            color = if (isSatellite) Color.parseColor("#1B2A38") else Color.parseColor("#E5E3DF")
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Draw road/grid lines
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSatellite) Color.parseColor("#2C4257") else Color.parseColor("#D4D1CA")
            strokeWidth = 3f
        }
        for (i in 1..4) {
            val y = h * (i / 5f)
            canvas.drawLine(0f, y, w.toFloat(), y, linePaint)
            val x = w * (i / 5f)
            canvas.drawLine(x, 0f, x, h.toFloat(), linePaint)
        }

        return bmp
    }
}
