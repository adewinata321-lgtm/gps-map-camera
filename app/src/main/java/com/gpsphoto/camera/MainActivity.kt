package com.gpsphoto.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.gpsphoto.camera.databinding.ActivityMainBinding
import com.gpsphoto.camera.helper.GeocoderHelper
import com.gpsphoto.camera.helper.LocationHelper
import com.gpsphoto.camera.helper.MapSnapshotHelper
import com.gpsphoto.camera.helper.PhotoStampEngine
import com.gpsphoto.camera.model.LocationStampData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var locationHelper: LocationHelper
    private lateinit var geocoderHelper: GeocoderHelper
    private lateinit var photoStampEngine: PhotoStampEngine
    private lateinit var prefs: SharedPreferences

    private var imageCapture: ImageCapture? = null
    private var cameraControl: CameraControl? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var flashMode = ImageCapture.FLASH_MODE_AUTO

    private var currentStampData = LocationStampData()
    private var lastLocation: Location? = null
    private var lastPhotoUri: Uri? = null
    private var isCapturing = false

    // Realtime clock handler
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateLiveClock()
            clockHandler.postDelayed(this, 1000L)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (cameraGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Izin kamera diperlukan untuk mengambil foto.", Toast.LENGTH_LONG).show()
        }

        if (locationGranted) {
            startLocation()
        } else {
            Toast.makeText(this, "Izin lokasi diperlukan untuk stempel GPS & Peta.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("gps_photo_prefs", MODE_PRIVATE)
        cameraExecutor = Executors.newSingleThreadExecutor()
        geocoderHelper = GeocoderHelper(this)
        photoStampEngine = PhotoStampEngine(this)

        setupUI()
        checkAndRequestPermissions()
    }

    private fun setupUI() {
        // Shutter Button
        binding.btnShutter.setOnClickListener {
            if (!isCapturing) {
                takePhoto()
            }
        }

        // Camera Switch
        binding.btnSwitchCamera.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            bindCameraUseCases()
        }

        // Flash Toggle
        binding.btnFlash.setOnClickListener {
            cycleFlashMode()
        }

        // Gallery Shortcut
        binding.cvGallery.setOnClickListener {
            val intent = Intent(this, GalleryActivity::class.java)
            lastPhotoUri?.let { intent.putExtra("photo_uri", it.toString()) }
            startActivity(intent)
        }

        // Settings Button
        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun cycleFlashMode() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> {
                binding.btnFlash.setImageResource(R.drawable.ic_flash_on)
                ImageCapture.FLASH_MODE_ON
            }
            ImageCapture.FLASH_MODE_ON -> {
                binding.btnFlash.setImageResource(R.drawable.ic_flash_off)
                ImageCapture.FLASH_MODE_OFF
            }
            else -> {
                binding.btnFlash.setImageResource(R.drawable.ic_flash_auto)
                ImageCapture.FLASH_MODE_AUTO
            }
        }
        imageCapture?.flashMode = flashMode
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isEmpty()) {
            startCamera()
            startLocation()
        } else {
            permissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode)
            .build()

        try {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            cameraControl = camera.cameraControl
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startLocation() {
        locationHelper = LocationHelper(this) { location ->
            onNewLocation(location)
        }
        locationHelper.startLocationUpdates()
    }

    private fun onNewLocation(location: Location) {
        val prev = lastLocation
        val isSignificant = prev == null || prev.distanceTo(location) > 15f
        lastLocation = location

        binding.tvGpsStatus.text = "GPS Terkunci (±${location.accuracy.toInt()}m)"

        if (isSignificant) {
            lifecycleScope.launch {
                val geo = geocoderHelper.resolveLocation(location.latitude, location.longitude)
                val mapTypeStr = prefs.getString("map_type", "SATELLITE") ?: "SATELLITE"
                val mapType = when (mapTypeStr) {
                    "STREET" -> MapSnapshotHelper.MapType.STREET
                    "NONE" -> MapSnapshotHelper.MapType.NONE
                    else -> MapSnapshotHelper.MapType.SATELLITE
                }

                val mapBitmap = MapSnapshotHelper.getMapThumbnail(
                    location.latitude,
                    location.longitude,
                    mapType
                )

                val tag = prefs.getString("watermark_tag", "LocSnap - GPS Timestamp Camera")
                    ?: "LocSnap - GPS Timestamp Camera"

                currentStampData = LocationStampData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    accuracy = location.accuracy,
                    districtTitle = geo.districtTitle,
                    fullAddress = geo.fullAddress,
                    formattedLat = geo.formattedLat,
                    formattedLng = geo.formattedLng,
                    formattedDate = geo.formattedDate,
                    formattedTime = geo.formattedTime,
                    timezoneStr = geo.timezoneStr,
                    mapBitmap = mapBitmap,
                    watermarkTag = tag
                )

                updateLiveHUD(currentStampData)
            }
        }
    }

    private fun updateLiveHUD(data: LocationStampData) {
        binding.tvDistrictTitle.text = data.districtTitle
        binding.tvFullAddress.text = data.fullAddress
        binding.tvLatitude.text = data.formattedLat
        binding.tvLongitude.text = data.formattedLng
        binding.tvDate.text = "Date : ${data.formattedDate}"
        binding.tvTime.text = "${data.timezoneStr} : ${data.formattedTime}"
        binding.tvWatermarkTag.text = data.watermarkTag

        val mapTypeStr = prefs.getString("map_type", "SATELLITE") ?: "SATELLITE"
        val brandText = when (mapTypeStr) {
            "STREET" -> "OpenStreetMap"
            "NONE" -> ""
            else -> "ArcGIS World Imagery"
        }
        binding.tvMapBrand.text = brandText
        binding.tvMapBrand.visibility = if (brandText.isNotEmpty()) View.VISIBLE else View.GONE

        if (data.mapBitmap != null) {
            binding.mapFrame.visibility = View.VISIBLE
            binding.ivMapSnapshot.setImageBitmap(data.mapBitmap)
        } else {
            binding.mapFrame.visibility = View.GONE
        }
    }

    private fun updateLiveClock() {
        val now = java.util.Date()
        val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
        val timeFormat = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.US)
        binding.tvDate.text = "Date : ${dateFormat.format(now)}"
        binding.tvTime.text = "${currentStampData.timezoneStr} : ${timeFormat.format(now)}"
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        isCapturing = true
        binding.progressBar.visibility = View.VISIBLE
        binding.btnShutter.alpha = 0.5f

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                @SuppressLint("UnsafeOptInUsageError")
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val buffer = imageProxy.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    imageProxy.close()

                    lifecycleScope.launch {
                        // Stamp and save
                        val savedUri = photoStampEngine.stampAndSavePhoto(bytes, currentStampData)
                        withContext(Dispatchers.Main) {
                            isCapturing = false
                            binding.progressBar.visibility = View.GONE
                            binding.btnShutter.alpha = 1.0f

                            if (savedUri != null) {
                                lastPhotoUri = savedUri
                                Glide.with(this@MainActivity)
                                    .load(savedUri)
                                    .centerCrop()
                                    .into(binding.ivGalleryThumbnail)

                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.photo_saved),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Gagal menyimpan foto.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    binding.progressBar.visibility = View.GONE
                    binding.btnShutter.alpha = 1.0f
                    Toast.makeText(
                        this@MainActivity,
                        "Pengambilan gambar gagal: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockRunnable)
        if (::locationHelper.isInitialized) {
            locationHelper.startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockRunnable)
        if (::locationHelper.isInitialized) {
            locationHelper.stopLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
