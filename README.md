# GPS Map Camera (LocSnap GPS Timestamp Camera)

Aplikasi Android untuk mengambil foto dengan stempel (watermark/stamp) GPS dan peta satelit secara langsung dan otomatis, persis seperti contoh foto.

---

## 📸 Fitur Utama

1. **Stempel Peta Satelit / Peta Jalan (Live Map Thumbnail)**:
   - Menampilkan cuplikan peta satelit aktual (*Esri World Imagery*) atau peta jalan (*OpenStreetMap*) dari lokasi foto diambil.
   - Dilengkapi dengan **Red Map Pin Marker** (pin penanda merah lokasi) tepat di tengah koordinat.
   - Watermark resmi penyedia peta aktual (**ArcGIS World Imagery** untuk satelit, atau **OpenStreetMap** untuk peta jalan) di sudut kiri bawah peta.
   - Peta dapat diaktifkan dalam mode Satelit, Peta Jalan, atau dimatikan via menu Pengaturan.

2. **Deteksi Lokasi GPS Akurat & Reverse Geocoding**:
   - Mendeteksi koordinat GPS secara presisi (*FusedLocationProviderClient* + fallback *LocationManager*).
   - Menghasilkan format koordinat dengan simbol derajat:
     - `Lat : 6,22725°S` (atau `°N`)
     - `Long : 107,02466°E` (atau `°W`)
   - Otomatis menerjemahkan koordinat menjadi alamat lengkap berbahasa Indonesia:
     - **Judul Wilayah**: Contoh: `Kecamatan Bekasi Timur, Jawa Barat`
     - **Alamat Detail**: Contoh: `Jl. Wijaya I Blok F25 No.11, RT.003/RW.015, Duren Jaya, Kec. Bekasi Tim., Kota Bks, Jawa Barat 17111, Indonesia`

3. **Stempel Tanggal & Waktu Real-Time**:
   - Format Tanggal: `Date : 23-11-2026`
   - Format Waktu & Zona: `GMT : 08:17:23 AM` (atau `WIB`, `WITA`, `WIT` sesuai zona waktu perangkat).

4. **HUD Live Preview**:
   - Tampilan stempel muncul secara langsung di layar kamera sebelum tombol foto ditekan, sehingga pengguna bisa melihat hasil stempel sebelum memotret.

5. **Resolusi Penuh & Skala Otomatis (Proportional Engine)**:
   - Stempel digambar langsung ke resolusi asli sensor kamera (1080p, 4K, 12MP, 48MP, dst.) dengan ukuran kartu, font, dan thumbnail peta yang otomatis proporsional dan tidak pecah.

6. **Penyimpanan Langsung ke Galeri**:
   - Foto otomatis tersimpan di folder galeri: `Pictures/GPS Photo`.
   - Terintegrasi dengan Android MediaStore dan langsung muncul di Galeri HP, Google Photos, serta bisa langsung dikirim via WhatsApp.

7. **Galeri Internal & Berbagi (Share)**:
   - Shortcut thumbnail foto terakhir di pojok kiri bawah kamera.
   - Tampilan preview foto resolusi penuh.
   - Tombol **Bagikan (Share)** ke WhatsApp, Telegram, Email, dll.
   - Tombol **Hapus (Delete)** jika foto tidak sesuai.

---

## 🛠️ Persyaratan Sistem & Spesifikasi Teknis

- **Bahasa**: Kotlin
- **Build System**: Gradle 8.5 (Kotlin DSL `build.gradle.kts`)
- **Android SDK**:
  - `minSdk`: 24 (Android 7.0 Nougat ke atas)
  - `targetSdk`: 34 (Android 14)
  - `compileSdk`: 34
- **Komponen Utama**:
  - `androidx.camera` (CameraX) untuk preview dan pengambilan foto cepat berkualitas tinggi.
  - `com.google.android.gms:play-services-location` untuk Fused GPS Location.
  - `android.location.Geocoder` untuk reverse geocoding alamat lokal.
  - `androidx.exifinterface` untuk orientasi foto yang akurat.
  - `com.github.bumptech.glide` untuk manajemen memori gambar.

---

## 🚀 Cara Menjalankan Aplikasi

### Opsi 1: Menggunakan Android Studio (Sangat Direkomendasikan)
1. Buka aplikasi **Android Studio**.
2. Pilih **Open** lalu arahkan ke folder:
   `D:\Website Created\Android Apps\GPS Photo`
3. Tunggu proses *Gradle Sync* selesai (semua konfigurasi `build.gradle.kts` dan `local.properties` sudah siap).
4. Sambungkan HP Android Anda via kabel USB (pastikan *USB Debugging* aktif) atau gunakan Emulator Android Studio.
5. Klik tombol **Run** (segitiga hijau) atau tekan `Shift + F10`.
6. Saat pertama kali dibuka, izinkan hak akses **Kamera** dan **Lokasi (Tepat/Fine Location)**.

### Opsi 2: Build APK via Terminal / CMD
Jalankan perintah berikut di folder proyek:
```powershell
cd "D:\Website Created\Android Apps\GPS Photo"
.\gradlew.bat assembleDebug
```
File APK yang dihasilkan akan berada di:
`app\build\outputs\apk\debug\app-debug.apk`

---

## ⚙️ Pengaturan & Kustomisasi
Melalui ikon gerigi (Settings) di pojok kanan atas:
1. **Tipe Peta**: Pilih Satelit, Peta Jalan, atau Tanpa Peta.
2. **Watermark / Tag**: Anda dapat mengganti teks bawaan `LocSnap - GPS Timestamp Camera` dengan nama perusahaan, proyek, atau nama instansi Anda sendiri.
