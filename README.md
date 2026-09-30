# Asset Live OCR — GitHub Build Edition

Aplikasi Android native untuk membaca **nomor asset 5 digit** langsung dari kamera tanpa foto manual.

## Alur

Kamera live → arahkan nomor asset ke kotak scan → OCR membaca angka → angka otomatis masuk daftar → kamera tetap aktif untuk nomor berikutnya.

## Fitur

- CameraX live preview; tidak perlu ambil/simpan foto.
- Google ML Kit Text Recognition bundled (Latin), sehingga model OCR ikut di APK.
- Fokus utama pada area scan di tengah layar.
- Hanya menerima nomor 5 digit.
- Koreksi OCR umum: O→0, I/L→1, S→5, B→8.
- 2 pembacaan berurutan yang sama sudah cukup untuk menyimpan nomor.
- Cooldown singkat untuk mencegah nomor yang sama tersimpan berulang-ulang.
- Zoom kamera sekitar 1.5x dan fokus ke area tengah untuk membantu angka tercetak kecil.
- Export hasil ke CSV yang bisa dibuka di Excel.

## Cara build tanpa Android Studio

1. Buat repository GitHub baru, misalnya `AssetLiveOCR`.
2. Upload **isi folder project ini** ke repository tersebut (bukan file ZIP-nya sebagai satu-satunya file).
3. Pastikan branch utamanya bernama `main`.
4. Buka tab **Actions** di repository.
5. Pilih workflow **Build APK** lalu jalankan jika belum otomatis berjalan.
6. Setelah selesai, buka hasil workflow tersebut dan cari bagian **Artifacts**.
7. Download `AssetLiveOCR-debug`.
8. Extract ZIP artifact, lalu install `app-debug.apk` di HP Android.

GitHub Actions yang disediakan project ini memasang Gradle dan JDK sendiri, jadi Android Studio tidak diperlukan di komputer lu.

## Catatan

Versi ini adalah prototype untuk pengujian kecepatan/akurasi. Akurasi sangat dipengaruhi ukuran angka di kamera, pencahayaan, fokus, dan kualitas cetakan. Untuk nomor asset 5 digit, usahakan angka memenuhi sebagian besar kotak scan.
