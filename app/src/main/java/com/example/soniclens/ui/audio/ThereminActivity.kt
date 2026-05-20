package com.example.soniclens.ui.audio

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.soniclens.databinding.ActivityThereminBinding
import com.example.soniclens.utils.PermissionHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ThereminActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityThereminBinding
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var isActive = false
    private var currentFrequency = 440.0f
    private var currentVolume = 0.8f

    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null
    private var audioRunning = false
    private var phase = 0.0
    private val sampleRate = 44100

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var recordingPath: String? = null

    private var mediaPlayer: MediaPlayer? = null

    private val noteNames = arrayOf(
        "C", "C#", "D", "D#", "E", "F",
        "F#", "G", "G#", "A", "A#", "B"
    )

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThereminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnActivate.setOnClickListener { toggleTheremin() }
        binding.btnRecord.setOnClickListener { toggleRecording() }
        binding.btnPlayRecording.setOnClickListener { playRecording() }

        if (accelerometer == null) {
            binding.tvThereminStatus.text = "Sin acelerómetro — modo demo"
        }
    }

    private fun toggleTheremin() {
        if (isActive) stopTheremin() else startTheremin()
    }

    private fun startTheremin() {
        isActive = true
        binding.btnActivate.text = "DESACTIVAR"
        binding.tvThereminStatus.text = "ACTIVO — Inclina el móvil"
        binding.btnRecord.isEnabled = true
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        startAudioSynthesis()
    }

    private fun stopTheremin() {
        isActive = false
        binding.btnActivate.text = "ACTIVAR"
        binding.tvThereminStatus.text = "INACTIVO"
        binding.btnRecord.isEnabled = false
        if (isRecording) stopRecording()
        sensorManager.unregisterListener(this)
        stopAudioSynthesis()
        binding.tvNoteName.text = "—"
        binding.tvFrequency.text = "000.0 Hz"
        binding.tvOctave.text = "OCT —"
    }

    private fun startAudioSynthesis() {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        audioRunning = true
        phase = 0.0

        audioThread = Thread {
            val buffer = ShortArray(bufferSize / 2)
            while (audioRunning) {
                val freq = currentFrequency.toDouble()
                val vol = currentVolume.toDouble()
                val phaseIncrement = 2.0 * PI * freq / sampleRate
                for (i in buffer.indices) {
                    val fundamental = sin(phase)
                    val harmonic2 = 0.3 * sin(2 * phase)
                    val harmonic3 = 0.1 * sin(3 * phase)
                    val sample = (vol * (fundamental + harmonic2 + harmonic3) / 1.4) * 32767
                    buffer[i] = sample.toInt().coerceIn(-32767, 32767).toShort()
                    phase += phaseIncrement
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                }
                audioTrack?.write(buffer, 0, buffer.size)
            }
        }.also { it.start() }
    }

    private fun stopAudioSynthesis() {
        audioRunning = false
        audioThread?.join(500)
        audioThread = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val normalizedX = ((x + 10f) / 20f).coerceIn(0f, 1f)
        val semitones = (normalizedX * 36).toInt()
        val baseFreq = 110.0 * Math.pow(2.0, semitones / 12.0)
        currentFrequency = baseFreq.toFloat()
        val normalizedY = (abs(y) / 10f).coerceIn(0f, 1f)
        currentVolume = (0.1f + normalizedY * 0.9f)
        val freqLocal = currentFrequency
        val pitchProgress = (normalizedX * 100).toInt()
        val timbreProgress = (normalizedY * 100).toInt()
        val noteName = freqToNoteName(freqLocal.toDouble())
        val octave = freqToOctave(freqLocal.toDouble())
        handler.post {
            binding.tvNoteName.text = noteName
            binding.tvFrequency.text = String.format("%.1f Hz", freqLocal)
            binding.tvOctave.text = "OCT $octave"
            binding.seekPitch.progress = pitchProgress
            binding.seekTimbre.progress = timbreProgress
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun toggleRecording() {
        if (isRecording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        if (!PermissionHelper.hasAudio(this)) {
            PermissionHelper.requestAudio(this)
            return
        }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(getExternalFilesDir(null) ?: filesDir, "THEREMIN_$ts.m4a")
        recordingPath = file.absolutePath

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(recordingPath)
                prepare()
                start()
            }
            isRecording = true
            binding.btnRecord.text = "⏹ DETENER REC"
            binding.tvThereminStatus.text = "🔴 GRABANDO + ACTIVO"
            binding.tvRecordingInfo.text = ""
        } catch (e: Exception) {
            Toast.makeText(this, "Error al grabar: ${e.message}", Toast.LENGTH_SHORT).show()
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    private fun stopRecording() {
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        binding.btnRecord.text = "⏺ GRABAR"
        binding.tvThereminStatus.text = "ACTIVO — Inclina el móvil"
        recordingPath?.let { path ->
            val name = File(path).name
            binding.tvRecordingInfo.text = "REC: $name"
            binding.btnPlayRecording.isEnabled = true
            Toast.makeText(this, "✅ Grabado: $name", Toast.LENGTH_LONG).show()
        }
    }

    private fun playRecording() {
        val path = recordingPath ?: return
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            prepare()
            start()
            setOnCompletionListener { binding.tvRecordingInfo.text = "▶ Completado" }
        }
        binding.tvRecordingInfo.text = "▶ Reproduciendo..."
    }

    private fun freqToNoteName(freq: Double): String {
        val midiNote = (12 * Math.log(freq / 440.0) / Math.log(2.0) + 69).toInt()
        return noteNames[((midiNote % 12) + 12) % 12]
    }

    private fun freqToOctave(freq: Double): Int {
        val midiNote = (12 * Math.log(freq / 440.0) / Math.log(2.0) + 69).toInt()
        return midiNote / 12 - 1
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.REQ_AUDIO &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isActive) stopTheremin()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        stopTheremin()
        mediaPlayer?.release()
    }
}