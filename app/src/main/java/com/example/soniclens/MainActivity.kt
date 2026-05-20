package com.example.soniclens

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.soniclens.databinding.ActivityMainBinding
import com.example.soniclens.ui.audio.ThereminActivity
import com.example.soniclens.ui.camera.GlitchCamActivity
import com.example.soniclens.ui.image.PixelForgeActivity
import com.example.soniclens.ui.video.CinemaVaultActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardTheremin.setOnClickListener {
            startActivity(Intent(this, ThereminActivity::class.java))
        }
        binding.cardGlitch.setOnClickListener {
            startActivity(Intent(this, GlitchCamActivity::class.java))
        }
        binding.cardPixel.setOnClickListener {
            startActivity(Intent(this, PixelForgeActivity::class.java))
        }
        binding.cardCinema.setOnClickListener {
            startActivity(Intent(this, CinemaVaultActivity::class.java))
        }
    }
}
