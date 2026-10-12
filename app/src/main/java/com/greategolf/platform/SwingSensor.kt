package com.greategolf.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.greategolf.domain.SwingDetector
import com.greategolf.domain.SwingEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Emits a [SwingEvent] each time the phone (pocket / armband) feels a golf-swing-like burst. */
class SwingSensor(context: Context) {
    private val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    val isAvailable get() = sensor != null

    fun swings(): Flow<SwingEvent> = callbackFlow {
        val detector = SwingDetector()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                detector.onSample(e.timestamp / 1_000_000, e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble())
                    ?.let { trySend(it) }
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sm.unregisterListener(listener) }
    }
}
