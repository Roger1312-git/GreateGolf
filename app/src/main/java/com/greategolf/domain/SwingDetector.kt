package com.greategolf.domain

import kotlin.math.sqrt

data class SwingEvent(val timeMs: Long, val peakAccel: Double)

/**
 * Detects a golf swing from the phone's linear acceleration (gravity removed, m/s²) — e.g. phone
 * in a pocket or armband. A swing is a short, violent burst; we require the peak to exceed
 * [peakThreshold], the burst to be short, and a [refractoryMs] quiet period between swings so a
 * single swing (and its follow-through) fires once.
 */
class SwingDetector(
    private val peakThreshold: Double = 22.0,
    private val burstFloor: Double = 8.0,
    private val maxBurstMs: Long = 1500,
    private val refractoryMs: Long = 3000,
) {
    private var burstStart = -1L
    private var burstPeak = 0.0
    private var lastSwing = Long.MIN_VALUE / 2

    fun onSample(timeMs: Long, x: Double, y: Double, z: Double): SwingEvent? {
        val mag = sqrt(x * x + y * y + z * z)

        if (mag >= burstFloor) {
            if (burstStart < 0) { burstStart = timeMs; burstPeak = 0.0 }
            if (mag > burstPeak) burstPeak = mag
            return null
        }

        // burst ended
        if (burstStart >= 0) {
            val dur = timeMs - burstStart
            val peak = burstPeak
            burstStart = -1
            if (peak >= peakThreshold && dur <= maxBurstMs && timeMs - lastSwing >= refractoryMs) {
                lastSwing = timeMs
                return SwingEvent(timeMs, peak)
            }
        }
        return null
    }
}
