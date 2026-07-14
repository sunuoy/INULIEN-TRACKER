package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.database.AppDatabase
import com.example.data.model.StepCountRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Calendar

class StepCounterService : Service(), SensorEventListener {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private lateinit var database: AppDatabase

    private var lastStepTimeNs: Long = 0
    private var magnitudePrevious: Float = 0f
    private val STEP_THRESHOLD = 12.0f // acceleration magnitude threshold in m/s^2
    private val STEP_DELAY_NS = 350000000L // 350ms lockout between steps

    private val CHANNEL_ID = "step_tracker_channel"
    private val NOTIFICATION_ID = 888

    override fun onCreate() {
        super.onCreate()
        Log.d("StepCounterService", "onCreate called")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        database = AppDatabase.getDatabase(applicationContext)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))

        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
            Log.d("StepCounterService", "Step sensor registered successfully")
        } else if (accelSensor != null) {
            sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI)
            Log.d("StepCounterService", "Step sensor missing, registered Accelerometer instead for movement step counting")
        } else {
            Log.e("StepCounterService", "No step counter or accelerometer sensor found on this device")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("StepCounterService", "onStartCommand called")
        updateNotificationWithCurrentSteps()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val currentSensorSteps = event.values[0].toInt()
            Log.d("StepCounterService", "Sensor steps reading: $currentSensorSteps")
            scope.launch {
                processSensorSteps(currentSensorSteps)
            }
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = kotlin.math.sqrt(x*x + y*y + z*z)

            // Dynamic peak detection with time lockout to filter hand shakes
            if (magnitude > STEP_THRESHOLD && (event.timestamp - lastStepTimeNs) > STEP_DELAY_NS) {
                lastStepTimeNs = event.timestamp
                Log.d("StepCounterService", "Movement step detected! Magnitude: $magnitude")
                scope.launch {
                    addDirectSteps(1)
                }
            }
            magnitudePrevious = magnitude
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private suspend fun addDirectSteps(count: Int) {
        val calendar = Calendar.getInstance()
        val todayYear = calendar.get(Calendar.YEAR)
        val todayDay = calendar.get(Calendar.DAY_OF_YEAR)

        // Get all step records
        val allRecords = database.stepDao().getAllStepRecords().firstOrNull() ?: emptyList()
        val todayRecord = allRecords.find { record ->
            val recCal = Calendar.getInstance().apply { timeInMillis = record.dateTimeMillis }
            recCal.get(Calendar.YEAR) == todayYear && recCal.get(Calendar.DAY_OF_YEAR) == todayDay
        }

        if (todayRecord != null) {
            val updatedRecord = todayRecord.copy(
                steps = todayRecord.steps + count,
                dateTimeMillis = System.currentTimeMillis()
            )
            database.stepDao().insertStepRecord(updatedRecord)
        } else {
            val newRecord = StepCountRecord(
                steps = count,
                dateTimeMillis = System.currentTimeMillis()
            )
            database.stepDao().insertStepRecord(newRecord)
        }
        updateNotificationWithCurrentSteps()
    }

    private suspend fun processSensorSteps(currentSensorSteps: Int) {
        val sharedPrefs = getSharedPreferences("step_tracker_prefs", Context.MODE_PRIVATE)
        val lastSensorSteps = sharedPrefs.getInt("last_sensor_steps", -1)

        if (lastSensorSteps == -1 || currentSensorSteps < lastSensorSteps) {
            // Baseline initialization or device reboot detected
            sharedPrefs.edit().putInt("last_sensor_steps", currentSensorSteps).apply()
            Log.d("StepCounterService", "Initialized last_sensor_steps to $currentSensorSteps")
            return
        }

        val diff = currentSensorSteps - lastSensorSteps
        if (diff > 0) {
            addDirectSteps(diff)
            // Save new sensor reading to prefs
            sharedPrefs.edit().putInt("last_sensor_steps", currentSensorSteps).apply()
        }
    }

    private fun updateNotificationWithCurrentSteps() {
        scope.launch {
            val calendar = Calendar.getInstance()
            val todayYear = calendar.get(Calendar.YEAR)
            val todayDay = calendar.get(Calendar.DAY_OF_YEAR)

            val allRecords = database.stepDao().getAllStepRecords().firstOrNull() ?: emptyList()
            val todaySteps = allRecords.filter { record ->
                val recCal = Calendar.getInstance().apply { timeInMillis = record.dateTimeMillis }
                recCal.get(Calendar.YEAR) == todayYear && recCal.get(Calendar.DAY_OF_YEAR) == todayDay
            }.sumOf { it.steps }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildNotification(todaySteps))
        }
    }

    private fun buildNotification(steps: Int): Notification {
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pedometer Step Tracking")
            .setContentText("Walking: $steps steps taken today")
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Standard system compass/navigation icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Step Tracker Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors fitness walk steps in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        Log.d("StepCounterService", "onDestroy called")
        sensorManager.unregisterListener(this)
        job.cancel()
        super.onDestroy()
    }
}
