package com.greategolf.domain

/**
 * Orthographic top-down view of a hole in local meters: +x (u) right, +y (v) up the screen (towards the green).
 * [panX]/[panY] are extra screen-pixel offsets from user panning.
 */
class Ortho2D(
    val cu: Double, val cv: Double, val scale: Double,
    val w: Float, val h: Float,
    val panX: Float = 0f, val panY: Float = 0f,
) {
    fun sx(u: Double): Float = (w / 2 + panX + (u - cu) * scale).toFloat()
    fun sy(v: Double): Float = (h / 2 + panY - (v - cv) * scale).toFloat()
    fun u(x: Float): Double = cu + (x - w / 2 - panX) / scale
    fun v(y: Float): Double = cv - (y - h / 2 - panY) / scale

    companion object {
        /** Scale and centre that fit the box [minU,maxU]x[minV,maxV] (plus [marginM]) into w x h. */
        fun fit(minU: Double, minV: Double, maxU: Double, maxV: Double, w: Float, h: Float, marginM: Double): Triple<Double, Double, Double> {
            val bw = (maxU - minU) + 2 * marginM
            val bh = (maxV - minV) + 2 * marginM
            val scale = minOf(w / bw, h / bh)
            return Triple((minU + maxU) / 2, (minV + maxV) / 2, scale)
        }
    }
}

object Geometry2D {
    fun pointInPolygon(x: Double, y: Double, poly: List<V3>): Boolean {
        var inside = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    /** Deterministic jittered grid of points inside [poly] — used to draw individual trees in a wood. */
    fun scatterInPolygon(poly: List<V3>, spacing: Double, maxPoints: Int): List<V3> {
        if (poly.size < 3) return emptyList()
        val minX = poly.minOf { it.x }; val maxX = poly.maxOf { it.x }
        val minY = poly.minOf { it.y }; val maxY = poly.maxOf { it.y }
        val out = ArrayList<V3>()
        var gy = minY
        var row = 0
        while (gy <= maxY && out.size < maxPoints) {
            var gx = minX + if (row % 2 == 0) 0.0 else spacing / 2
            var col = 0
            while (gx <= maxX && out.size < maxPoints) {
                val h = (row * 73856093) xor (col * 19349663)
                val jx = ((h and 0xFF) / 255.0 - 0.5) * spacing * 0.6
                val jy = (((h shr 8) and 0xFF) / 255.0 - 0.5) * spacing * 0.6
                val p = V3(gx + jx, gy + jy)
                if (pointInPolygon(p.x, p.y, poly)) out += p
                gx += spacing; col++
            }
            gy += spacing * 0.85; row++
        }
        return out
    }
}
