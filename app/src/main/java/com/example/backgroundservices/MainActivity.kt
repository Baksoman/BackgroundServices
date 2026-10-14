package com.example.backgroundservices

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private lateinit var statusTextView: TextView
    private lateinit var btnStart: Button

    private lateinit var jumpCountTextView: TextView
    private lateinit var btnStartExercise: Button

    companion object {
        const val ACTION_SERVICE_STOPPED_JUMP = "com.example.backgroundservices.ACTION_SERVICE_STOPPED_JUMP"
        const val REQUEST_CODE_PERMISSIONS = 101
    }

    private fun hasAllPermissions(): Boolean {
        return permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startTrackerService() {
        val serviceIntent = Intent(this, TrackerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        statusTextView.text = "Background service sedang berjalan..."
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }
            if (allPermissionsGranted) {
                val serviceIntent = Intent(this, TrackerService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
                finish()
            } else {
                statusTextView.text = "Izin ditolak, service tidak dapat dijalankan."
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_SERVICE_STOPPED_JUMP) {
            statusTextView.text = "Background services sudah berhenti karena Anda melompat."
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun startExerciseService() {
        val serviceIntent = Intent(this, TrackerService::class.java).apply {
            putExtra(
                TrackerService.EXTRA_MODE,
                TrackerService.MODE_EXERCISE
            )
        }

        ContextCompat.startForegroundService(this, serviceIntent)

        showCounts(0, 0)

        statusTextView.text =
            "Latihan dimulai. Lompat sebanyak mungkin, lalu berjalan lebih dari " +
                    "${TrackerService.EXERCISE_STEP_THRESHOLD} langkah."
    }

    private fun showCounts(steps: Int, jumps: Int) {
        jumpCountTextView.text =
            "Jumlah Langkah: $steps\nJumlah Lompatan: $jumps"

        jumpCountTextView.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()

        TrackerService.countListener = { steps, jumps ->
            showCounts(steps, jumps)
        }
    }

    override fun onStop() {
        super.onStop()
        TrackerService.countListener = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnStart = findViewById(R.id.btnStart)
        statusTextView = findViewById(R.id.statusTextView)

        btnStart.setOnClickListener {
            if (hasAllPermissions()) {
                startTrackerService()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        }

        btnStartExercise = findViewById(R.id.btnStartExercise)
        jumpCountTextView = findViewById(R.id.jumpCountTextView)

        btnStartExercise.setOnClickListener {
            if (hasAllPermissions()) {
                startExerciseService()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        }

        handleIntent(intent)

    }
}