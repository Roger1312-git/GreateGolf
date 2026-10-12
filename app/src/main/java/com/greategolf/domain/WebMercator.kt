package com.greategolf.domain

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

data class TileKey(val z: Int, val x: Int, val y: Int)

/** Slippy-map (XYZ) tile arithmetic. Tile coordinates may be fractional to address points inside a tile. */
object WebMercator {
    fun tileX(lng: Double, z: Int) = floor((lng + 180) / 360 * (1 shl z)).toInt()

    fun tileY(lat: Double, z: Int): Int {
        val r = Math.toRadians(lat)
        return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * (1 shl z)).toInt()
    }

    fun lngAt(fx: Double, z: Int) = fx / (1 shl z) * 360 - 180
    fun latAt(fy: Double, z: Int) = Math.toDegrees(atan(sinh(PI * (1 - 2 * fy / (1 shl z)))))

    /** All tiles at zoom [z] touching the box; empty if it would need more than [maxTiles]. */
    fun tilesCovering(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double, z: Int, maxTiles: Int = 48): List<TileKey> {
        val x0 = tileX(minLng, z); val x1 = tileX(maxLng, z)
        val y0 = tileY(maxLat, z); val y1 = tileY(minLat, z)   // y grows southwards
        if ((x1 - x0 + 1) * (y1 - y0 + 1) > maxTiles) return emptyList()
        return (y0..y1).flatMap { y -> (x0..x1).map { x -> TileKey(z, x, y) } }
    }
}
