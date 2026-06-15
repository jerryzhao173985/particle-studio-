package com.example.myapplication1.studio

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
import kotlin.math.PI

/**
 * A smoothed device-tilt offset in roughly [-1, 1] on each axis, from the rotation-vector
 * sensor (falling back to the accelerometer). Used to parallax the depth layers so tilting
 * the phone shifts foreground vs background — a quietly native touch. Returns [Offset.Zero]
 * when disabled (reduced-motion) or when no sensor is present; the listener is unregistered
 * when the composable leaves (no battery drain off-screen).
 */
@Composable
fun rememberTiltOffset(enabled: Boolean): State<Offset> {
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current

    DisposableEffect(context, enabled) {
        if (!enabled) {
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || sensor == null) {
            return@DisposableEffect onDispose { }
        }

        val quarterTurn = (PI / 4.0).toFloat()
        val listener = object : SensorEventListener {
            private val rotation = FloatArray(9)
            private val orientation = FloatArray(3)
            override fun onSensorChanged(event: SensorEvent) {
                val targetX: Float
                val targetY: Float
                if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                    SensorManager.getOrientation(rotation, orientation)
                    targetX = (orientation[2] / quarterTurn).coerceIn(-1f, 1f) // roll
                    targetY = (orientation[1] / quarterTurn).coerceIn(-1f, 1f) // pitch
                } else {
                    targetX = (-event.values[0] / 9.8f).coerceIn(-1f, 1f)
                    targetY = (event.values[1] / 9.8f).coerceIn(-1f, 1f)
                }
                // Low-pass filter so the parallax glides rather than jitters.
                val prev = tilt.value
                tilt.value = Offset(
                    prev.x + (targetX - prev.x) * 0.08f,
                    prev.y + (targetY - prev.y) * 0.08f,
                )
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    return tilt
}
