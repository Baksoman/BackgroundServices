package com.example.backgroundservices

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.TimeZone
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
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

    private val calendarPermissions = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    companion object {
        const val ACTION_SERVICE_STOPPED_JUMP = "com.example.backgroundservices.ACTION_SERVICE_STOPPED_JUMP"
        const val REQUEST_CODE_PERMISSIONS = 101

        const val ACTION_EXERCISE_FINISHED =
            "com.example.backgroundservices.ACTION_EXERCISE_FINISHED"

        const val EXTRA_JUMP_COUNT = "extra_jump_count"
        const val EXTRA_STEP_COUNT = "extra_step_count"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_END_TIME = "extra_end_time"

        const val NOTIFICATION_ID_EXERCISE_FINISHED = 3
        const val REQUEST_CODE_EXERCISE_PERMISSIONS = 102
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
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        when (requestCode) {
            REQUEST_CODE_PERMISSIONS -> {
                if (hasAllPermissions()) {
                    startTrackerService()
                } else {
                    statusTextView.text =
                        "Izin ditolak, service tidak dapat dijalankan."
                }
            }

            REQUEST_CODE_EXERCISE_PERMISSIONS -> {
                if (hasAllPermissions()) {
                    startExerciseService()
                } else {
                    statusTextView.text =
                        "Izin ditolak, latihan tidak dapat dijalankan."
                }
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SERVICE_STOPPED_JUMP -> {
                statusTextView.text =
                    "Background services sudah berhenti karena Anda melompat."
            }

            ACTION_EXERCISE_FINISHED -> {
                val jumps = intent.getIntExtra(EXTRA_JUMP_COUNT, 0)
                val steps = intent.getIntExtra(EXTRA_STEP_COUNT, 0)

                val startTime = intent.getLongExtra(
                    EXTRA_START_TIME,
                    System.currentTimeMillis()
                )

                val endTime = intent.getLongExtra(
                    EXTRA_END_TIME,
                    System.currentTimeMillis()
                )

                intent.action = null

                NotificationManagerCompat.from(this)
                    .cancel(NOTIFICATION_ID_EXERCISE_FINISHED)

                statusTextView.text =
                    "Latihan selesai. Background service sudah dihentikan."

                showCounts(steps, jumps)

                createExerciseCalendarEvent(
                    startTime,
                    endTime,
                    jumps,
                    steps
                )
            }
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

    private fun hasCalendarPermissions(): Boolean {
        return calendarPermissions.all {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getWritableCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)

        val selection =
            "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
                    "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
                    "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"

        val sortOrder =
            "${CalendarContract.Calendars.IS_PRIMARY} DESC"

        return try {
            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else {
                    null
                }
            }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun openCalendarInsertScreen(
        startTime: Long,
        endTime: Long,
        title: String,
        description: String
    ) {
        val calendarIntent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI

            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
        }

        try {
            startActivity(calendarIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Aplikasi kalender tidak ditemukan.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun createExerciseCalendarEvent(
        startTime: Long,
        endTime: Long,
        jumps: Int,
        steps: Int
    ) {
        val title = "Latihan Hari Ini"
        val description =
            "Jumlah lompatan: $jumps, jumlah langkah: $steps"

        if (hasCalendarPermissions()) {
            val calendarId = getWritableCalendarId()

            if (calendarId != null) {
                val values = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, startTime)
                    put(CalendarContract.Events.DTEND, endTime)
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DESCRIPTION, description)
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(
                        CalendarContract.Events.EVENT_TIMEZONE,
                        TimeZone.getDefault().id
                    )
                }

                try {
                    val uri = contentResolver.insert(
                        CalendarContract.Events.CONTENT_URI,
                        values
                    )

                    if (uri != null) {
                        Toast.makeText(
                            this,
                            "Event latihan ditambahkan ke kalender.",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }
                } catch (e: SecurityException) {
                    // Lanjutkan ke layar pembuatan event.
                }
            }
        }

        openCalendarInsertScreen(
            startTime,
            endTime,
            title,
            description
        )
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
                    REQUEST_CODE_EXERCISE_PERMISSIONS
                )
            }
        }

        handleIntent(intent)
    }
}