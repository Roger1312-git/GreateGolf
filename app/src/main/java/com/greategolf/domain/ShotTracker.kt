package com.greategolf.domain

data class GeoFix(
    val point: GeoPoint,
    val timeMs: Long,
    val speedMps: Double? = null,
    val accuracyM: Double? = null,
)

data class DetectedShot(val start: GeoPoint, val end: GeoPoint, val meters: Double)

/**
 * Auto-detects the length of a shot from GPS fixes.
 *
 * Flow: [arm] at the ball's position before hitting (manually, or when the swing sensor fires),
 * then keep feeding [onFix]. Once the player has walked at least [minShotMeters] away and then
 * stands still for [stillFixes] consecutive fixes (i.e. is standing at the ball), the shot is
 * reported. [finishNow] lets the player mark the ball manually.
 */
class ShotTracker(
    private val minShotMeters: Double = 15.0,
    private val stillSpeedMps: Double = 0.6,
    private val stillFixes: Int = 3,
    private val maxAccuracyM: Double = 25.0,
) {
    private var start: GeoPoint? = null
    private var prev: GeoFix? = null
    private var farthest = 0.0
    private var stillCount = 0

    val isArmed get() = start != null
    val startPoint get() = start

    fun arm(at: GeoPoint) {
        start = at
        prev = null
        farthest = 0.0
        stillCount = 0
    }

    fun cancel() { start = null; prev = null }

    /** Straight-line distance from the armed start, for live display. */
    fun currentMeters(fix: GeoFix): Double? = start?.let { GeoMath.distanceMeters(it, fix.point) }

    fun onFix(fix: GeoFix): DetectedShot? {
        val s = start ?: return null
        if (fix.accuracyM != null && fix.accuracyM > maxAccuracyM) return null

        val speed = fix.speedMps ?: prev?.let { p ->
            val dt = (fix.timeMs - p.timeMs) / 1000.0
            if (dt > 0) GeoMath.distanceMeters(p.point, fix.point) / dt else null
        }
        prev = fix

        val d = GeoMath.distanceMeters(s, fix.point)
        if (d > farthest) farthest = d

        if (farthest >= minShotMeters && speed != null && speed < stillSpeedMps) stillCount++ else stillCount = 0

        if (stillCount >= stillFixes) return finish(fix.point)
        return null
    }

    fun finishNow(at: GeoPoint): DetectedShot? = if (start == null) null else finish(at)

    private fun finish(end: GeoPoint): DetectedShot {
        val s = start!!
        start = null; prev = null
        return DetectedShot(s, end, GeoMath.distanceMeters(s, end))
    }
}
