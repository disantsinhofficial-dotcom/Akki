package com.example.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class HybridSensorManager(
    private val context: Context,
    private val onShakeDetected: () -> Unit
) : SensorEventListener {

    private val tag = "HybridSensorManager"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _isHybridModeActive = MutableStateFlow(false) // OFF by default as requested
    val isHybridModeActive: StateFlow<Boolean> = _isHybridModeActive.asStateFlow()

    private val _shakeSensitivity = MutableStateFlow(14.0f) // Threshold in m/s^2 above gravity
    val shakeSensitivity: StateFlow<Float> = _shakeSensitivity.asStateFlow()

    private val _lastShakeTime = MutableStateFlow(0L)
    val lastShakeTime: StateFlow<Long> = _lastShakeTime.asStateFlow()

    private var lastUpdate: Long = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    fun setHybridMode(enabled: Boolean) {
        _isHybridModeActive.value = enabled
        if (enabled) {
            registerListener()
        } else {
            unregisterListener()
        }
    }

    fun setSensitivity(threshold: Float) {
        _shakeSensitivity.value = threshold.coerceIn(8.0f, 25.0f)
    }

    fun registerListener() {
        if (!_isHybridModeActive.value) return
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            Log.d(tag, "Accelerometer registered for shake detection")
        }
    }

    fun unregisterListener() {
        sensorManager?.unregisterListener(this)
        Log.d(tag, "Accelerometer unregistered")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_isHybridModeActive.value) return

        val curTime = System.currentTimeMillis()
        // Only check every 100ms
        if ((curTime - lastUpdate) > 100) {
            val diffTime = curTime - lastUpdate
            lastUpdate = curTime

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val deltaX = x - lastX
            val deltaY = y - lastY
            val deltaZ = z - lastZ

            val speed = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()) / diffTime * 10000

            lastX = x
            lastY = y
            lastZ = z

            // Threshold checking with cooldown of 1500ms
            if (speed > (_shakeSensitivity.value * 50) && (curTime - _lastShakeTime.value) > 1500) {
                _lastShakeTime.value = curTime
                Log.d(tag, "Shake gesture detected! Triggering voice listening. Speed=$speed")
                triggerHapticFeedback()
                onShakeDetected()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(80)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Haptic vibration failed: ${e.message}")
        }
    }
}
