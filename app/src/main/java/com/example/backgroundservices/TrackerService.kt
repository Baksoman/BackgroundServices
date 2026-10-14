package com.example.backgroundservices

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.abs

class TrackerService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING = "tracker_service_running"
    private val NOTIFICATION_CHANNEL_ID_JUMP_DETECTED = "tracker_jump_detected"
    private val NOTIFICATION_ID_SERVICE_RUNNING = 1
    private val NOTIFICATION_ID_JUMP_DETECTED = 2

    private var isExerciseMode = false
    private var exerciseJumpCount = 0
    private var exerciseStepCount = 0
    private var lastMoveTime = 0L
    private var moveWindowStart = 0L
    private var movePeak = 0f
    private var exerciseFinished = false

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_EXERCISE = "mode_exercise"

        const val EXERCISE_STEP_THRESHOLD = 2
        const val STEP_ACCEL_THRESHOLD = 12f
        const val JUMP_ACCEL_THRESHOLD = 15f
        const val MOVE_WINDOW_MS = 150L
        const val MOVE_COOLDOWN_MS = 400L

        var countListener: ((Int, Int) -> Unit)? = null
    }

    override fun onCreate(){
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannels()
        Log.d("Tracker Service", "Service onCreate")
    }
    private fun createNotificationChannels() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING,
                "Service Running Channel",
                NotificationManager.IMPORTANCE_LOW
            )

            val jumpChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_JUMP_DETECTED,
                "Jump Detected Channel",
                NotificationManager.IMPORTANCE_HIGH
            )

            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(jumpChannel)

        }
    }

    private fun startJumpDetection() {
        accelerometer?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    private fun buildServiceRunningNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING)
            .setContentTitle("Pelacak Aktivitas Latar")
            .setContentText("Aplikasi Tracker Services sedang berjalan...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("Tracker Service", "Service onStartCommand")

        startForeground(
            NOTIFICATION_ID_SERVICE_RUNNING,
            buildServiceRunningNotification()
        )

        isExerciseMode =
            intent?.getStringExtra(EXTRA_MODE) == MODE_EXERCISE

        if (isExerciseMode) {
            startExerciseDetection()
        } else {
            startJumpDetection()
        }

        return START_STICKY
    }

    private fun showJumpNotification(){
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SERVICE_STOPPED_JUMP
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingMainActivityIntent = PendingIntent.getActivity(
            this, 1, mainActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val jumpNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_JUMP_DETECTED)
            .setContentTitle("Lompatan Terdeteksi!")
            .setContentText("Service Berhenti. Ketuk untuk membuka aplikasi.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingMainActivityIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try{
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_JUMP_DETECTED, jumpNotification)
        } catch (e: SecurityException){
            Log.e("Tracker Service", "Gagal memunculkan notifikasi karena permission POST_NOTIFICATIONS tidak aktif: ${e.message}")
        }
    }

    private fun publishCounts() {
        countListener?.invoke(exerciseStepCount, exerciseJumpCount)
    }

    private fun startExerciseDetection() {
        exerciseJumpCount = 0
        exerciseStepCount = 0
        lastMoveTime = 0L
        moveWindowStart = 0L
        movePeak = 0f
        exerciseFinished = false

        publishCounts()

        accelerometer?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    private fun finishExercise() {
        if (exerciseFinished) return

        exerciseFinished = true
        sensorManager.unregisterListener(this)

        statusLogExerciseFinished()

        stopSelf()
    }

    private fun statusLogExerciseFinished() {
        Log.d(
            "Tracker Service",
            "Latihan selesai. Langkah: $exerciseStepCount, " +
                    "Lompatan: $exerciseJumpCount"
        )
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) {
            return
        }

        if (isExerciseMode) {
            if (exerciseFinished) return

            val absY = abs(event.values[1])
            val now = System.currentTimeMillis()

            if (moveWindowStart == 0L) {
                if (
                    absY > STEP_ACCEL_THRESHOLD &&
                    now - lastMoveTime > MOVE_COOLDOWN_MS
                ) {
                    moveWindowStart = now
                    movePeak = absY
                }
            } else {
                if (absY > movePeak) {
                    movePeak = absY
                }

                if (now - moveWindowStart >= MOVE_WINDOW_MS) {
                    if (movePeak > JUMP_ACCEL_THRESHOLD) {
                        exerciseJumpCount++
                        Log.d(
                            "Tracker Service",
                            "Lompatan ke-$exerciseJumpCount"
                        )
                    } else {
                        exerciseStepCount++
                        Log.d(
                            "Tracker Service",
                            "Langkah ke-$exerciseStepCount"
                        )
                    }

                    lastMoveTime = now
                    moveWindowStart = 0L
                    movePeak = 0f

                    publishCounts()

                    if (exerciseStepCount > EXERCISE_STEP_THRESHOLD) {
                        finishExercise()
                    }
                }
            }

            return
        }

        val y = event.values[1]
        val jumpThreshold = 15.0

        if (abs(y) > jumpThreshold) {
            Log.d("Tracker Service", "LOMPATAN Terdeteksi! y: $y")

            sensorManager.unregisterListener(this)
            showJumpNotification()
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        Log.d("Tracker Service", "Service onDestroy")
    }
}