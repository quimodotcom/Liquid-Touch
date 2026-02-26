package com.quimodotcom.lqlauncher.helpers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

class GyroscopeHelper(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    // Prefer Gravity sensor for tilt, fall back to Accelerometer
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun startListening(onTiltChanged: (Offset) -> Unit): SensorEventListener? {
        if (sensor == null) return null

        val listener = object : SensorEventListener {
            // Simple low-pass filter to smooth out jitter
            private var lastX = 0f
            private var lastY = 0f
            private val alpha = 0.1f // Smoothing factor

            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    // Standard Android coordinate system:
                    // When device is flat on table: X=0, Y=0, Z=9.8
                    val rawX = it.values[0]
                    val rawY = it.values[1]

                    // Apply smoothing
                    lastX = lastX + alpha * (rawX - lastX)
                    lastY = lastY + alpha * (rawY - lastY)

                    onTiltChanged(Offset(lastX, lastY))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        return listener
    }

    fun stopListening(listener: SensorEventListener?) {
        listener?.let { sensorManager.unregisterListener(it) }
    }
}

@Composable
fun rememberTiltState(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(enabled) {
        if (enabled) {
            val helper = GyroscopeHelper(context)
            val listener = helper.startListening { raw ->
                // Just pass raw tilt (Gravity vector components)
                // Range is roughly -9.8 to 9.8
                tilt.value = raw
            }
            onDispose {
                if (listener != null) {
                    helper.stopListening(listener)
                }
            }
        } else {
            tilt.value = Offset.Zero
            onDispose { }
        }
    }
    return tilt
}
