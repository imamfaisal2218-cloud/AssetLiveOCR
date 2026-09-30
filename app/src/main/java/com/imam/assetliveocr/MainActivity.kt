package com.imam.assetliveocr

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var count: TextView
    private lateinit var listView: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val busy = AtomicBoolean(false)
    private val results = mutableListOf<String>()

    private var previousCandidate = ""
    private var sameCandidateCount = 0
    private var lastSaved = ""
    private var lastSavedAt = 0L
    private var lastAnalysisAt = 0L
    private var camera: Camera? = null

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else status.text = "Izin kamera diperlukan"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preview = findViewById(R.id.preview)
        status = findViewById(R.id.status)
        count = findViewById(R.id.count)
        listView = findViewById(R.id.list)

        findViewById<Button>(R.id.export).setOnClickListener { exportCsv() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            val p = Preview.Builder().build().also {
                it.surfaceProvider = preview.surfaceProvider
            }

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(executor) { proxy -> analyze(proxy) }

            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, p, analysis)

            // Sedikit zoom membuat angka 5 digit lebih besar di frame OCR.
            camera?.cameraControl?.setZoomRatio(1.5f)

            // Tap preview untuk fokus ulang jika diperlukan.
            preview.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    focusAt(event.x, event.y)
                }
                true
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun focusAt(x: Float, y: Float) {
        val factory: MeteringPointFactory = preview.meteringPointFactory
        val point = factory.createPoint(x, y)
        camera?.cameraControl?.startFocusAndMetering(
            androidx.camera.core.FocusMeteringAction.Builder(point).build()
        )
    }

    private fun analyze(proxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalysisAt < 120L || !busy.compareAndSet(false, true)) {
            proxy.close()
            return
        }
        lastAnalysisAt = now

        val media = proxy.image ?: run {
            busy.set(false)
            proxy.close()
            return
        }

        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)

        recognizer.process(image)
            .addOnSuccessListener { text ->
                findBestCandidate(text)?.let { onCandidate(it) }
            }
            .addOnFailureListener {
                // Abaikan error frame tunggal; kamera harus tetap berjalan.
            }
            .addOnCompleteListener {
                busy.set(false)
                proxy.close()
            }
    }

    private fun findBestCandidate(text: Text): String? {
        val centerX = text.textBlocks.flatMap { it.lines }.mapNotNull { it.boundingBox?.centerX() }.average()
        val lines = text.textBlocks.flatMap { it.lines }

        val candidates = lines.flatMap { line ->
            val normalized = normalize(line.text)
            val found = mutableListOf<Pair<String, Float>>()

            if (normalized.matches(Regex("\\d{5}"))) {
                found += normalized to centerDistance(line.boundingBox, centerX)
            }

            Regex("(?<!\\d)(\\d{5})(?!\\d)").findAll(normalized).forEach {
                found += it.value to centerDistance(line.boundingBox, centerX)
            }
            found
        }

        return candidates.minByOrNull { it.second }?.first
    }

    private fun centerDistance(box: Rect?, fallback: Double): Float {
        if (box == null) return fallback.toFloat()
        val dx = abs(box.centerX() - 640f)
        val dy = abs(box.centerY() - 360f)
        return (dx + dy).toFloat()
    }

    private fun normalize(raw: String): String = raw.uppercase()
        .replace('O', '0')
        .replace('I', '1')
        .replace('L', '1')
        .replace('S', '5')
        .replace('B', '8')
        .replace(" ", "")
        .replace("-", "")

    private fun onCandidate(value: String) {
        runOnUiThread {
            status.text = "Terdeteksi: $value"

            if (value == previousCandidate) {
                sameCandidateCount++
            } else {
                previousCandidate = value
                sameCandidateCount = 1
            }

            val now = SystemClock.elapsedRealtime()
            val stable = sameCandidateCount >= 2
            val cooldownOk = value != lastSaved || now - lastSavedAt > 1400L

            if (stable && cooldownOk) {
                results.add(value)
                lastSaved = value
                lastSavedAt = now
                previousCandidate = ""
                sameCandidateCount = 0
                updateList()
                status.text = "Tersimpan: $value"
            }
        }
    }

    private fun updateList() {
        count.text = "${results.size} nomor tersimpan"
        listView.text = results.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
    }

    private fun exportCsv() {
        if (results.isEmpty()) {
            Toast.makeText(this, "Belum ada nomor asset", Toast.LENGTH_SHORT).show()
            return
        }

        val csv = "No,Nomor Asset\n" + results.mapIndexed { i, s -> "${i + 1},$s" }.joinToString("\n")
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "Asset_Live_OCR_${System.currentTimeMillis()}.csv")
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
            Toast.makeText(this, "CSV tersimpan di Download", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Gagal menyimpan CSV", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        recognizer.close()
    }
}
