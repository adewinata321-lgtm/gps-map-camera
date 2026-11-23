package com.gpsphoto.camera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.gpsphoto.camera.databinding.ActivityGalleryBinding

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriString = intent.getStringExtra("photo_uri")
        if (!uriString.isNullOrBlank()) {
            photoUri = Uri.parse(uriString)
        }

        setupUI()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        if (photoUri != null) {
            binding.tvNoPhotos.visibility = View.GONE
            binding.ivFullPhoto.visibility = View.VISIBLE
            Glide.with(this)
                .load(photoUri)
                .fitCenter()
                .into(binding.ivFullPhoto)

            binding.btnShare.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, photoUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Bagikan Foto GPS"))
            }

            binding.btnDelete.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(R.string.confirm_delete)
                    .setMessage("Apakah Anda yakin ingin menghapus foto GPS ini?")
                    .setPositiveButton("Hapus") { _, _ ->
                        try {
                            contentResolver.delete(photoUri!!, null, null)
                            Toast.makeText(this, "Foto berhasil dihapus", Toast.LENGTH_SHORT).show()
                            finish()
                        } catch (e: Exception) {
                            Toast.makeText(this, "Gagal menghapus foto", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        } else {
            binding.tvNoPhotos.visibility = View.VISIBLE
            binding.ivFullPhoto.visibility = View.GONE
            binding.btnShare.isEnabled = false
            binding.btnDelete.isEnabled = false
        }
    }
}
