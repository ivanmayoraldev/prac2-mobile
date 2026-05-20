package com.example.soniclens.ui.video

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.soniclens.databinding.ActivityCinemaVaultBinding

class CinemaVaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCinemaVaultBinding

    private var videoUri: Uri? = null
    private val handler = Handler(Looper.getMainLooper())

    private val progressUpdater = object : Runnable {
        override fun run() {
            if (binding.videoView.isPlaying) {
                val pos = binding.videoView.currentPosition
                binding.seekBar.progress = pos
                binding.tvCurrentTime.text = formatTime(pos)
            }
            handler.postDelayed(this, 250)
        }
    }

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadVideo(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCinemaVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnLoad.setOnClickListener { pickVideo.launch("video/*") }
        binding.btnPlay.setOnClickListener { playVideo() }
        binding.btnPause.setOnClickListener { pauseVideo() }
        binding.btnStop.setOnClickListener { stopVideo() }

        setupSeekBar()
        setupVideoCallbacks()
    }

    private fun setupVideoCallbacks() {
        binding.videoView.setOnPreparedListener { mp ->
            val duration = mp.duration
            binding.seekBar.max = duration
            binding.seekBar.isEnabled = true
            binding.tvDuration.text = formatTime(duration)
            binding.tvVideoTitle.text = "▶ Listo · ${formatTime(duration)}"
            binding.layoutNoVideo.visibility = View.GONE
            setPlaybackButtonsEnabled(true)
            handler.post(progressUpdater)
        }

        binding.videoView.setOnCompletionListener {
            binding.tvVideoTitle.text = "✅ Fin del vídeo"
            handler.removeCallbacks(progressUpdater)
            binding.seekBar.progress = 0
            binding.tvCurrentTime.text = "0:00"
        }

        binding.videoView.setOnErrorListener { _, what, extra ->
            Toast.makeText(this, "Error de vídeo ($what/$extra)", Toast.LENGTH_LONG).show()
            binding.tvVideoTitle.text = "❌ Error"
            true
        }
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.videoView.seekTo(progress)
                    binding.tvCurrentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                handler.removeCallbacks(progressUpdater)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (binding.videoView.isPlaying) handler.post(progressUpdater)
            }
        })
    }

    private fun loadVideo(uri: Uri) {
        videoUri = uri

        // Extraer metadatos
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: uri.lastPathSegment ?: "Vídeo"
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "?"
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "?"
            retriever.release()

            binding.tvMetadata.text = "${width}x${height}"
            binding.tvVideoTitle.text = title

        } catch (_: Exception) {
            binding.tvVideoTitle.text = uri.lastPathSegment ?: "Vídeo"
        }

        // Reiniciar UI
        binding.seekBar.progress = 0
        binding.seekBar.isEnabled = false
        binding.tvCurrentTime.text = "0:00"
        binding.tvDuration.text = "0:00"
        setPlaybackButtonsEnabled(false)
        handler.removeCallbacks(progressUpdater)

        binding.videoView.setVideoURI(uri)
        binding.videoView.requestFocus()
        Toast.makeText(this, "✅ Vídeo cargado", Toast.LENGTH_SHORT).show()
    }

    private fun playVideo() {
        if (!binding.videoView.isPlaying) {
            binding.videoView.start()
            binding.tvVideoTitle.let { tv ->
                val current = tv.text.toString().removePrefix("⏸ ")
                tv.text = "▶ $current"
            }
            handler.post(progressUpdater)
        }
    }

    private fun pauseVideo() {
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
            handler.removeCallbacks(progressUpdater)
            val title = binding.tvVideoTitle.text.toString().removePrefix("▶ ")
            binding.tvVideoTitle.text = "⏸ $title"
        }
    }

    private fun stopVideo() {
        binding.videoView.pause()
        binding.videoView.seekTo(0)
        handler.removeCallbacks(progressUpdater)
        binding.seekBar.progress = 0
        binding.tvCurrentTime.text = "0:00"
        val title = videoUri?.lastPathSegment ?: "Vídeo"
        binding.tvVideoTitle.text = "⏹ $title"
    }

    private fun setPlaybackButtonsEnabled(enabled: Boolean) {
        binding.btnPlay.isEnabled = enabled
        binding.btnPause.isEnabled = enabled
        binding.btnStop.isEnabled = enabled
    }

    private fun formatTime(ms: Int): String {
        val totalSecs = ms / 1000
        val m = totalSecs / 60
        val s = totalSecs % 60
        return String.format("%d:%02d", m, s)
    }

    override fun onPause() {
        super.onPause()
        if (binding.videoView.isPlaying) pauseVideo()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        binding.videoView.stopPlayback()
    }
}
