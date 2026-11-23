package com.gpsphoto.camera

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gpsphoto.camera.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("gps_photo_prefs", MODE_PRIVATE)
        loadSettings()

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Pengaturan berhasil disimpan!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadSettings() {
        val mapType = prefs.getString("map_type", "SATELLITE") ?: "SATELLITE"
        when (mapType) {
            "STREET" -> binding.rbStreet.isChecked = true
            "NONE" -> binding.rbNone.isChecked = true
            else -> binding.rbSatellite.isChecked = true
        }

        val tag = prefs.getString("watermark_tag", "LocSnap - GPS Timestamp Camera")
        binding.etWatermark.setText(tag)
    }

    private fun saveSettings() {
        val mapType = when (binding.rgMapType.checkedRadioButtonId) {
            R.id.rbStreet -> "STREET"
            R.id.rbNone -> "NONE"
            else -> "SATELLITE"
        }

        val watermark = binding.etWatermark.text.toString().trim()
            .ifBlank { "LocSnap - GPS Timestamp Camera" }

        prefs.edit()
            .putString("map_type", mapType)
            .putString("watermark_tag", watermark)
            .apply()
    }
}
