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

data class HazardInfo(
    val type: FeatureType, val frontM: Double, val backM: Double,
    /** Nearest / farthest vertex of the hazard from the reference point. */
    val front: GeoPoint, val back: GeoPoint,
)

data class GreenInfo(val center: GeoPoint, val front: GeoPoint, val back: GeoPoint)

/** Meters east/north of an origin, rotated so +v points along [bearingDeg] and +u is to the right. */
class LocalFrame(val origin: GeoPoint, val bearingDeg: Double) {
    private val mPerDeg = Math.PI / 180.0 * 6_371_000.0
    private val cosLat = cos(Math.toRadians(origin.lat))
    private val sinB = sin(Math.toRadians(bearingDeg))
    private val cosB = cos(Math.toRadians(bearingDeg))

    /** Inverse of [toLocal]. */
    fun toGeo(u: Double, v: Double): GeoPoint {
        val e = u * cosB + v * sinB
        val n = -u * sinB + v * cosB
        return GeoPoint(origin.lat + n / mPerDeg, origin.lng + e / (mPerDeg * cosLat))
    }

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

    /**
     * Bunkers / water ahead of the player along [hole] with the distance to their near edge (front, the
     * "carry" you need to clear) and far edge (back). Nearest first.
     */
    fun hazardsAhead(layout: CourseLayout, hole: Hole, player: GeoPoint, maxM: Double = 350.0, corridorM: Double = 60.0, limit: Int = 6): List<HazardInfo> {
        val target = hole.greenEnd
        val frame = LocalFrame(player, bearingDeg(player, target))
        val axis = LocalFrame(hole.tee, bearingDeg(hole.tee, target))
        val line = hole.line.map { axis.toLocal(it) }
        return layout.features
            .filter { (it.type == FeatureType.BUNKER || it.type == FeatureType.WATER) && it.closed }
            .filter { f -> f.ring.any { distToPolyline(axis.toLocal(it), line) <= corridorM } }
            .filter { f -> f.ring.any { frame.toLocal(it).second > 0 } }
            .map { f ->
                val d = f.ring.map { GeoMath.distanceMeters(player, it) }
                HazardInfo(f.type, d.min(), d.max(), f.ring[d.indexOf(d.min())], f.ring[d.indexOf(d.max())])
            }
            .filter { it.frontM <= maxM }
            .sortedBy { it.frontM }
            .take(limit)
    }
}
