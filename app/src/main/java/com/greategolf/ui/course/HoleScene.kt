package com.greategolf.ui.course

import com.greategolf.domain.CourseLayout
import com.greategolf.domain.FeatureType
import com.greategolf.domain.Geometry2D
import com.greategolf.domain.GeoPoint
import com.greategolf.domain.GreenInfo
import com.greategolf.domain.Hole
import com.greategolf.domain.LayoutOps
import com.greategolf.domain.LocalFrame
import com.greategolf.domain.TileKey
import com.greategolf.domain.V3
import com.greategolf.domain.WebMercator
import kotlin.math.cos

/** A hole in local meters: x = right of the tee→green axis, y = along it (green is "up"), z = up. */
internal class HoleScene(
    val hole: Hole,
    val frame: LocalFrame,
    val length: Double,
    val green: V3,
    val greenRing: List<V3>,
    val pin: V3?,
    /** Closed polygons per type, in drawing order. */
    val layers: List<Pair<FeatureType, List<List<V3>>>>,
    val paths: List<List<V3>>,
    val ob: List<List<V3>>,
    val trees: List<V3>,
    val woodTrees: List<V3>,
    val centerLine: List<V3>,
    val zoom: Int,
    val tiles: List<TileKey>,
) {
    private fun polys(vararg types: FeatureType) = layers.filter { it.first in types }.flatMap { it.second }

    /** [minU, minV, maxU, maxV] of the playable corridor (no woods), or of the green only. */
    fun bounds(greenOnly: Boolean): DoubleArray {
        val pts = if (greenOnly) greenRing + green
        else polys(FeatureType.FAIRWAY, FeatureType.TEE, FeatureType.BUNKER, FeatureType.WATER, FeatureType.GREEN).flatten() + centerLine
        val p = pts.ifEmpty { centerLine + green }
        return doubleArrayOf(p.minOf { it.x }, p.minOf { it.y }, p.maxOf { it.x }, p.maxOf { it.y })
    }
}

internal fun buildHoleScene(layout: CourseLayout, hole: Hole, green: GreenInfo?, corridorM: Double = 70.0): HoleScene {
    val greenPt = green?.center ?: hole.greenEnd
    val frame = LocalFrame(hole.tee, LayoutOps.bearingDeg(hole.tee, greenPt))
    fun v(p: GeoPoint) = frame.toLocal(p).let { V3(it.first, it.second) }
    val near = LayoutOps.featuresForHole(layout, hole, corridorM)
    val order = listOf(FeatureType.WOOD, FeatureType.WATER, FeatureType.FAIRWAY, FeatureType.TEE, FeatureType.BUNKER, FeatureType.GREEN)
    val layers = order.map { t -> t to near.filter { it.type == t && it.closed }.map { f -> f.ring.map(::v) } }
    val gv = v(greenPt)

    val greenRing = layers.first { it.first == FeatureType.GREEN }.second
        .minByOrNull { r -> r.minOf { Math.hypot(it.x - gv.x, it.y - gv.y) } }.orEmpty()
    val pin = near.filter { it.type == FeatureType.PIN }.map { v(it.ring[0]) }
        .filter { Math.hypot(it.x - gv.x, it.y - gv.y) < 40 }.minByOrNull { Math.hypot(it.x - gv.x, it.y - gv.y) }

    val trees = near.filter { it.type == FeatureType.TREE }.map { v(it.ring[0]) }.take(1500)
    val rowTrees = near.filter { it.type == FeatureType.TREE_ROW }.flatMap { f ->
        f.ring.zipWithNext().flatMap { (a, b) ->
            val pa = v(a); val pb = v(b)
            val n = (Math.hypot(pb.x - pa.x, pb.y - pa.y) / 6).toInt().coerceAtLeast(1)
            (0 until n).map { i -> V3(pa.x + (pb.x - pa.x) * i / n, pa.y + (pb.y - pa.y) * i / n) }
        }
    }
    // Woods are drawn as dark patches with individual trees scattered inside.
    val woodTrees = layers.first { it.first == FeatureType.WOOD }.second.flatMap { Geometry2D.scatterInPolygon(it, 9.0, 400) }.take(1500)

    // imagery tiles covering the hole corridor (+60 m margin), for the satellite view
    val pts = hole.line + near.filter { it.ring.size > 1 }.flatMap { it.ring } + listOfNotNull(green?.front, green?.back, green?.center)
    val mLat = 60.0 / 111_195.0
    val mLng = mLat / cos(Math.toRadians(pts.first().lat))
    var z = 18
    var tiles = emptyList<TileKey>()
    while (tiles.isEmpty() && z >= 15) {
        tiles = WebMercator.tilesCovering(pts.minOf { it.lat } - mLat, pts.minOf { it.lng } - mLng,
            pts.maxOf { it.lat } + mLat, pts.maxOf { it.lng } + mLng, z)
        if (tiles.isEmpty()) z--
    }
    return HoleScene(
        hole, frame, gv.y, gv, greenRing, pin, layers,
        near.filter { it.type == FeatureType.CART_PATH }.map { f -> f.ring.map(::v) },
        near.filter { it.type == FeatureType.OB }.map { f -> f.ring.map(::v) },
        trees + rowTrees, woodTrees, hole.line.map(::v), z, tiles,
    )
}
