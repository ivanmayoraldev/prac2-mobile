package com.example.soniclens.ui.camera

import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.soniclens.databinding.ActivityGlitchCamBinding
import com.example.soniclens.utils.PermissionHelper
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random

class GlitchCamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGlitchCamBinding

    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private enum class GlitchEffect { NORMAL, GLITCH, SCANLINES, CHROMATIC, INVERT }
    private var currentEffect = GlitchEffect.NORMAL
    private val effectNames = mapOf(
        GlitchEffect.NORMAL to "NORMAL",
        GlitchEffect.GLITCH to "GLITCH",
        GlitchEffect.SCANLINES to "SCANLINES",
        GlitchEffect.CHROMATIC to "ABERRACION",
        GlitchEffect.INVERT to "INVERTIR"
    )

    private var capturedBytes: ByteArray? = null
    private var processedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlitchCamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCycleEffect.setOnClickListener { cycleEffect() }
        binding.btnCapture.setOnClickListener { capturePhoto() }
        binding.btnRetake.setOnClickListener { showCameraPreview() }
        binding.btnSave.setOnClickListener { savePhoto() }

        if (PermissionHelper.hasCamera(this)) {
            startCamera()
        } else {
            PermissionHelper.requestCamera(this)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
                binding.tvStatus.text = "Cámara lista · Efecto: ${effectNames[currentEffect]}"
            } catch (e: Exception) {
                Log.e(TAG, "startCamera error", e)
                binding.tvStatus.text = "Error de cámara"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun cycleEffect() {
        val values = GlitchEffect.entries.toTypedArray()
        currentEffect = values[(currentEffect.ordinal + 1) % values.size]
        val name = effectNames[currentEffect] ?: "?"
        binding.tvEffectBadge.text = name
        binding.tvStatus.text = "Efecto: $name"
        capturedBytes?.let { bytes ->
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            applyEffectAndShow(original)
        }
    }

    private fun capturePhoto() {
        val ic = imageCapture ?: return
        binding.tvStatus.text = "Capturando..."
        ic.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    capturedBytes = bytes
                    image.close()
                    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    applyEffectAndShow(original)
                }
                override fun onError(exc: ImageCaptureException) {
                    binding.tvStatus.text = "Error al capturar"
                    Log.e(TAG, "capture error", exc)
                }
            }
        )
    }

    private fun applyEffectAndShow(source: Bitmap) {
        val result = when (currentEffect) {
            GlitchEffect.NORMAL -> source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
            GlitchEffect.GLITCH -> applyGlitch(source)
            GlitchEffect.SCANLINES -> applyScanlines(source)
            GlitchEffect.CHROMATIC -> applyChromaticAberration(source)
            GlitchEffect.INVERT -> applyInvert(source)
        }
        processedBitmap = result
        binding.ivGlitchResult.setImageBitmap(result)
        showResultPreview()
        binding.tvStatus.text = "✅ Efecto ${effectNames[currentEffect]} aplicado"
    }

    private fun applyGlitch(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val result = IntArray(w * h)
        val rng = Random(System.currentTimeMillis())
        for (y in 0 until h) {
            val shift = if (rng.nextFloat() < 0.20f) rng.nextInt(-w / 4, w / 4) else 0
            for (x in 0 until w) {
                val srcX = ((x + shift + w) % w)
                var pixel = pixels[y * w + srcX]
                if (rng.nextFloat() < 0.05f) {
                    pixel = Color.rgb(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256))
                }
                result[y * w + x] = pixel
            }
        }
        val bmp = Bitmap.createBitmap(w, h, source.config ?: Bitmap.Config.ARGB_8888)
        bmp.setPixels(result, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun applyScanlines(source: Bitmap): Bitmap {
        val bmp = source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { color = Color.argb(120, 0, 0, 0); strokeWidth = 1f }
        var y = 0f
        while (y < bmp.height) {
            canvas.drawLine(0f, y, bmp.width.toFloat(), y, paint)
            y += 2f
        }
        val tintPaint = Paint().apply { color = Color.argb(30, 0, 229, 255) }
        canvas.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), tintPaint)
        return bmp
    }

    private fun applyChromaticAberration(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val result = IntArray(w * h)
        val shift = (w * 0.015f).toInt().coerceAtLeast(4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val rX = (x - shift).coerceIn(0, w - 1)
                val bX = (x + shift).coerceIn(0, w - 1)
                val r = Color.red(pixels[y * w + rX])
                val g = Color.green(pixels[y * w + x])
                val b = Color.blue(pixels[y * w + bX])
                result[y * w + x] = Color.rgb(r, g, b)
            }
        }
        val bmp = Bitmap.createBitmap(w, h, source.config ?: Bitmap.Config.ARGB_8888)
        bmp.setPixels(result, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun applyInvert(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val result = IntArray(w * h) { i ->
            val p = pixels[i]
            Color.argb(Color.alpha(p), 255 - Color.red(p), 255 - Color.green(p), 255 - Color.blue(p))
        }
        val bmp = Bitmap.createBitmap(w, h, source.config ?: Bitmap.Config.ARGB_8888)
        bmp.setPixels(result, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun showResultPreview() {
        binding.cameraPreview.visibility = View.GONE
        binding.ivGlitchResult.visibility = View.VISIBLE
        binding.btnCapture.isEnabled = false
        binding.btnSave.isEnabled = true
        binding.btnRetake.visibility = View.VISIBLE
    }

    private fun showCameraPreview() {
        binding.cameraPreview.visibility = View.VISIBLE
        binding.ivGlitchResult.visibility = View.GONE
        binding.btnCapture.isEnabled = true
        binding.btnSave.isEnabled = false
        binding.btnRetake.visibility = View.GONE
        capturedBytes = null
        processedBitmap = null
        binding.tvStatus.text = "Cámara lista · Efecto: ${effectNames[currentEffect]}"
    }

    private fun savePhoto() {
        val bitmap = processedBitmap ?: run {
            Toast.makeText(this, "Sin imagen para guardar", Toast.LENGTH_SHORT).show()
            return
        }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
        val effectTag = effectNames[currentEffect]?.replace(" ", "_") ?: "FX"
        val fileName = "GLITCH_${effectTag}_$ts.jpg"
        val cv = ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SonicLens")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri: Uri? = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cv.clear()
                cv.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(it, cv, null, null)
            }
            binding.tvStatus.text = "💾 Guardado: $fileName"
            Toast.makeText(this, "✅ $fileName guardado", Toast.LENGTH_LONG).show()
            binding.btnSave.isEnabled = false
        } ?: Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.REQ_CAMERA &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            binding.tvStatus.text = "Permiso de cámara denegado"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "GlitchCamActivity"
    }
}