package com.gpsphoto.camera.helper

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.exifinterface.media.ExifInterface
import com.gpsphoto.camera.model.LocationStampData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class PhotoStampEngine(private val context: Context) {

    suspend fun stampAndSavePhoto(
        jpegBytes: ByteArray,
        stampData: LocationStampData
    ): Uri? = withContext(Dispatchers.IO) {
        // 1. Decode raw JPEG and correct EXIF orientation
        val rawBitmap = decodeBitmapWithOrientation(jpegBytes) ?: return@withContext null

        // Make mutable bitmap for canvas drawing
        val resultBitmap = rawBitmap.copy(Bitmap.Config.ARGB_8888, true)
        rawBitmap.recycle()

        val canvas = Canvas(resultBitmap)
        val w = resultBitmap.width.toFloat()
        val h = resultBitmap.height.toFloat()

        // 2. Compute proportional scale factor (baseline width: 1080px)
        val scale = max(w / 1080f, 0.5f)

        // Stamping dimensions
        val cardMarginH = 24f * scale
        val cardMarginBottom = 32f * scale
        val cardPadding = 18f * scale
        val cornerRadius = 24f * scale

        // Map thumbnail dimensions
        val mapSize = 220f * scale

        // Pre-calculate fonts
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val addressPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            textSize = 21f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 19f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        // Available width for address & details on the right side of map
        val contentWidth = w - (cardMarginH * 2) - (cardPadding * 2)
        val textWidth = max(contentWidth - mapSize - (18f * scale), 200f * scale).toInt()

        // Address StaticLayout for clean multiline wrapping
        val addressLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(
                stampData.fullAddress,
                0,
                stampData.fullAddress.length,
                addressPaint,
                textWidth
            ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f * scale, 1.0f)
                .setMaxLines(3)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                stampData.fullAddress,
                addressPaint,
                textWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1.0f,
                2f * scale,
                true
            )
        }

        // Calculate card height dynamically
        val titleHeight = titlePaint.textSize * 1.2f
        val addressHeight = addressLayout.height.toFloat()
        val dividerMargin = 8f * scale
        val metaRowHeight = metaPaint.textSize * 2.3f

        val rightColumnHeight = titleHeight + addressHeight + (dividerMargin * 2) + metaRowHeight
        val cardInnerHeight = max(mapSize, rightColumnHeight)
        val cardHeight = cardInnerHeight + (cardPadding * 2)

        val cardLeft = cardMarginH
        val cardRight = w - cardMarginH
        val cardBottom = h - cardMarginBottom
        val cardTop = cardBottom - cardHeight

        // 3. Draw Watermark Tag above card (top-right of card)
        val tagText = stampData.watermarkTag
        val tagTextWidth = tagPaint.measureText(tagText)
        val tagPaddingH = 14f * scale
        val tagPaddingV = 7f * scale
        val tagHeight = tagPaint.textSize + (tagPaddingV * 2)
        val tagWidth = tagTextWidth + (tagPaddingH * 2) + (20f * scale)
        val tagRight = cardRight
        val tagLeft = tagRight - tagWidth
        val tagBottom = cardTop - (8f * scale)
        val tagTop = tagBottom - tagHeight

        val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B314181E")
            style = Paint.Style.FILL
        }
        val tagRect = RectF(tagLeft, tagTop, tagRight, tagBottom)
        canvas.drawRoundRect(tagRect, 10f * scale, 10f * scale, tagBgPaint)

        // Tag dot icon
        val tagDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3B82F6")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(tagLeft + tagPaddingH + (6f * scale), tagTop + tagHeight / 2f, 5f * scale, tagDotPaint)
        canvas.drawText(tagText, tagLeft + tagPaddingH + (16f * scale), tagBottom - tagPaddingV, tagPaint)

        // 4. Draw Main Card Background (Rounded Dark Translucent Card)
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B314181E") // 70% opacity dark slate
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40FFFFFF") // 25% subtle border
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * scale
        }
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardBgPaint)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardBorderPaint)

        // 5. Draw Map Thumbnail on the left
        val mapLeft = cardLeft + cardPadding
        val mapTop = cardTop + (cardHeight - mapSize) / 2f
        val mapRight = mapLeft + mapSize
        val mapBottom = mapTop + mapSize

        if (stampData.mapBitmap != null) {
            // Draw map thumbnail with rounded corners
            val mapRect = RectF(mapLeft, mapTop, mapRight, mapBottom)
            val mapPath = Path()
            mapPath.addRoundRect(mapRect, 12f * scale, 12f * scale, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(mapPath)
            canvas.drawBitmap(
                stampData.mapBitmap,
                Rect(0, 0, stampData.mapBitmap.width, stampData.mapBitmap.height),
                mapRect,
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            canvas.restore()
        }

        // 6. Draw Right Column Content
        val textLeft = mapRight + (16f * scale)
        var currentY = cardTop + cardPadding + titlePaint.textSize

        // District / Area Title
        canvas.drawText(stampData.districtTitle, textLeft, currentY, titlePaint)
        currentY += 8f * scale

        // Full Address with StaticLayout
        canvas.save()
        canvas.translate(textLeft, currentY)
        addressLayout.draw(canvas)
        canvas.restore()

        currentY += addressHeight + dividerMargin

        // Divider Line
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4DFFFFFF")
            strokeWidth = 1f * scale
        }
        canvas.drawLine(textLeft, currentY, cardRight - cardPadding, currentY, dividerPaint)
        currentY += dividerMargin + (14f * scale)

        // 2-Column Details:
        // Col 1: Lat & Date
        // Col 2: Long & Time
        val colWidth = (cardRight - cardPadding - textLeft) / 2f

        canvas.drawText(stampData.formattedLat, textLeft, currentY, metaPaint)
        canvas.drawText(stampData.formattedLng, textLeft + colWidth, currentY, metaPaint)

        currentY += metaPaint.textSize * 1.3f
        val dateText = "Date : ${stampData.formattedDate}"
        val timeText = "${stampData.timezoneStr} : ${stampData.formattedTime}"
        canvas.drawText(dateText, textLeft, currentY, metaPaint)
        canvas.drawText(timeText, textLeft + colWidth, currentY, metaPaint)

        // 7. Save Stamped Bitmap to MediaStore
        saveBitmapToMediaStore(resultBitmap, stampData)
    }

    private fun decodeBitmapWithOrientation(jpegBytes: ByteArray): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options) ?: return null

        try {
            val exif = ExifInterface(ByteArrayInputStream(jpegBytes))
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }

            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            return rotated
        } catch (e: Exception) {
            return bitmap
        }
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap, stampData: LocationStampData): Uri? {
        val filename = "GPS_PHOTO_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DESCRIPTION, "${stampData.districtTitle} - ${stampData.fullAddress}")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.LATITUDE, stampData.latitude)
                put(MediaStore.Images.Media.LONGITUDE, stampData.longitude)
            } else {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GPS Photo")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        try {
            val outStream: OutputStream? = resolver.openOutputStream(uri)
            if (outStream != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)
                outStream.flush()
                outStream.close()
            }

            // Write EXIF GPS Tags into the saved file
            try {
                resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    exif.setLatLong(stampData.latitude, stampData.longitude)
                    exif.saveAttributes()
                }
            } catch (e: Exception) {
                // EXIF write optional fallback
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            return uri
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            bitmap.recycle()
        }
    }
}
