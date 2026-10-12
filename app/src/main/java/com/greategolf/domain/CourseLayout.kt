package com.greategolf.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class FeatureType { GREEN, FAIRWAY, TEE, BUNKER, WATER, WOOD, ROUGH, CART_PATH }

/** A mapped course element. [ring] is a closed polygon, or an open line when [closed] is false. */
data class GeoFeature(val type: FeatureType, val ring: List<GeoPoint>, val closed: Boolean)

/** One hole's centre line, ordered tee -> green. */
data class Hole(val ref: Int, val par: Int?, val line: List<GeoPoint>, val name: String? = null) {
    val lengthM: Double get() = LayoutOps.polylineLength(line)
    val tee: GeoPoint get() = line.first()
    val greenEnd: GeoPoint get() = line.last()
}

data class CourseLayout(val features: List<GeoFeature>, val holes: List<Hole>) {
    val isEmpty get() = features.isEmpty() && holes.isEmpty()
}

data class GreenInfo(val center: GeoPoint, val front: GeoPoint, val back: GeoPoint)

/** Meters east/north of an origin, rotated so +v points along [bearingDeg] and +u is to the right. */
class LocalFrame(val origin: GeoPoint, val bearingDeg: Double) {
    private val mPerDeg = Math.PI / 180.0 * 6_371_000.0
    private val cosLat = cos(Math.toRadians(origin.lat))
    private val sinB = sin(Math.toRadians(bearingDeg))
    private val cosB = cos(Math.toRadians(bearingDeg))

    /** Returns (u = right of the hole axis, v = forward along it), in meters. */
    fun toLocal(p: GeoPoint): Pair<Double, Double> {
        val e = (p.lng - origin.lng) * mPerDeg * cosLat
        val n = (p.lat - origin.lat) * mPerDeg
        return Pair(e * cosB - n * sinB, e * sinB + n * cosB)
    }
}

object LayoutOps {
    fun polylineLength(pts: List<GeoPoint>): Double =
        pts.zipWithNext().sumOf { (a, b) -> GeoMath.distanceMeters(a, b) }

    fun centroid(pts: List<GeoPoint>): GeoPoint =
        GeoPoint(pts.sumOf { it.lat } / pts.size, pts.sumOf { it.lng } / pts.size)

    /** Initial bearing a -> b in degrees, 0 = north, clockwise. */
    fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val f1 = Math.toRadians(a.lat); val f2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lng - a.lng)
        val y = sin(dl) * cos(f2)
        val x = cos(f1) * sin(f2) - sin(f1) * cos(f2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** Green polygon nearest the hole's end point (within [maxM]); front/back along the approach direction. */
    fun greenOf(layout: CourseLayout, hole: Hole, maxM: Double = 60.0): GreenInfo? {
        val end = hole.greenEnd
        val green = layout.features
            .filter { it.type == FeatureType.GREEN && it.ring.size >= 3 }
            .map { it to GeoMath.distanceMeters(centroid(it.ring), end) }
            .filter { it.second <= maxM }
            .minByOrNull { it.second }?.first
            ?: return hole.takeIf { it.line.size >= 2 }?.let { GreenInfo(end, end, end) }
        val c = centroid(green.ring)
        val prev = hole.line.getOrNull(hole.line.size - 2) ?: hole.tee
        val frame = LocalFrame(c, bearingDeg(prev, end))
        val byV = green.ring.sortedBy { frame.toLocal(it).second }
        return GreenInfo(c, byV.first(), byV.last())
    }

    /** Features lying within [corridorM] of the hole's centre line (any vertex), excluding huge background rough. */
    fun featuresForHole(layout: CourseLayout, hole: Hole, corridorM: Double = 70.0): List<GeoFeature> {
        val frame = LocalFrame(hole.tee, bearingDeg(hole.tee, hole.greenEnd))
        val line = hole.line.map { frame.toLocal(it) }
        return layout.features.filter { f ->
            f.type != FeatureType.ROUGH &&
                f.ring.any { p -> distToPolyline(frame.toLocal(p), line) <= corridorM }
        }
    }

    private fun distToPolyline(p: Pair<Double, Double>, line: List<Pair<Double, Double>>): Double {
        if (line.size == 1) return hypot(p.first - line[0].first, p.second - line[0].second)
        return line.zipWithNext().minOf { (a, b) -> distToSegment(p, a, b) }
    }

    private fun distToSegment(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = b.first - a.first; val dy = b.second - a.second
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((p.first - a.first) * dx + (p.second - a.second) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(p.first - (a.first + t * dx), p.second - (a.second + t * dy))
    }
}
