package com.example.soniclens.ui.image

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.soniclens.R
import com.example.soniclens.databinding.ActivityPixelForgeBinding
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class PixelForgeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPixelForgeBinding

    private var originalBitmap: Bitmap? = null
    private var resultBitmap: Bitmap? = null
    private var originalMime: String = ""

    private var targetFormat = Bitmap.CompressFormat.JPEG
    private var targetExt = "jpg"

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPixelForgeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnLoad.setOnClickListener { pickImage.launch("image/*") }
        binding.btnToJpg.setOnClickListener { convertTo(Bitmap.CompressFormat.JPEG, "jpg") }
        binding.btnToPng.setOnClickListener { convertTo(Bitmap.CompressFormat.PNG, "png") }
        binding.btnPixelate.setOnClickListener { applyPixelart() }
        binding.btnSave.setOnClickListener { saveResult() }
    }

    private fun loadFromUri(uri: Uri) {
        try {
            originalMime = contentResolver.getType(uri) ?: "image/jpeg"
            val stream = contentResolver.openInputStream(uri)
            originalBitmap = BitmapFactory.decodeStream(stream)
            stream?.close()

            originalBitmap?.let { bmp ->
                binding.ivOriginal.setImageBitmap(bmp)
                val fmt = when {
                    originalMime.contains("png") -> "PNG"
                    originalMime.contains("webp") -> "WEBP"
                    else -> "JPG"
                }
                binding.tvOriginalInfo.text =
                    getString(R.string.image_info, fmt, bmp.width, bmp.height, estimatedSize(bmp))

                // Activar botones (todos excepto el formato actual)
                binding.btnToJpg.isEnabled = !originalMime.contains("jpeg") && !originalMime.contains("jpg")
                binding.btnToPng.isEnabled = !originalMime.contains("png")
                binding.btnPixelate.isEnabled = true

                // Ocultar resultado previo
                binding.cardResult.visibility = View.GONE
                resultBitmap = null

                Toast.makeText(this, "✅ Imagen cargada", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun convertTo(format: Bitmap.CompressFormat, ext: String) {
        val source = originalBitmap ?: run {
            Toast.makeText(this, "Carga una imagen primero", Toast.LENGTH_SHORT).show()
            return
        }
        targetFormat = format
        targetExt = ext

        val out = ByteArrayOutputStream()
        val quality = if (format == Bitmap.CompressFormat.JPEG) 90 else 100
        source.compress(format, quality, out)
        val bytes = out.toByteArray()

        resultBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        showResult(ext.uppercase(), bytes.size / 1024)
    }

    /**
     * Efecto pixel art: reduce la imagen a bloques grandes y re-escala.
     * Crea el clásico efecto retro de píxeles visibles.
     */
    private fun applyPixelart() {
        val source = originalBitmap ?: run {
            Toast.makeText(this, "Carga una imagen primero", Toast.LENGTH_SHORT).show()
            return
        }

        val pixelSize = (source.width / 40).coerceAtLeast(4)   // ~40 bloques de ancho
        val smallW = source.width / pixelSize
        val smallH = source.height / pixelSize

        // 1. Reducir drásticamente (sin filtro = pixelado)
        val small = Bitmap.createScaledBitmap(source, smallW, smallH, false)

        // 2. Re-escalar al tamaño original (sin filtro para mantener píxeles cuadrados)
        val pixelated = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(pixelated)
        val paint = Paint().apply { isFilterBitmap = false }
        val srcRect = Rect(0, 0, small.width, small.height)
        val dstRect = Rect(0, 0, source.width, source.height)
        canvas.drawBitmap(small, srcRect, dstRect, paint)
        small.recycle()

        resultBitmap = pixelated
        targetFormat = Bitmap.CompressFormat.PNG
        targetExt = "png"

        // Calcular tamaño aproximado
        val out = ByteArrayOutputStream()
        pixelated.compress(Bitmap.CompressFormat.PNG, 100, out)
        showResult("PIXEL ART", out.toByteArray().size / 1024)
    }

    private fun showResult(label: String, sizeKb: Int) {
        val bmp = resultBitmap ?: return
        binding.ivResult.setImageBitmap(bmp)
        binding.tvResultInfo.text = getString(R.string.converted_info, label, sizeKb)
        binding.cardResult.visibility = View.VISIBLE
        binding.btnSave.isEnabled = true
    }

    private fun saveResult() {
        val bitmap = resultBitmap ?: return
        val mimeType = if (targetExt == "png") "image/png" else "image/jpeg"
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(System.currentTimeMillis())
        val fileName = "PIXEL_${targetExt.uppercase()}_$ts.$targetExt"

        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SonicLens")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { stream ->
                val quality = if (targetFormat == Bitmap.CompressFormat.JPEG) 90 else 100
                bitmap.compress(targetFormat, quality, stream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cv.clear()
                cv.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(it, cv, null, null)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, it))
            }
            Toast.makeText(this, "✅ $fileName guardado", Toast.LENGTH_LONG).show()
            binding.btnSave.isEnabled = false
        } ?: Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
    }

    /** Estima el tamaño en KB de un bitmap en memoria */
    private fun estimatedSize(bmp: Bitmap): Int {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray().size / 1024
    }
}
