package com.greategolf.domain

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** World coordinates in meters: x = right, y = forward along the hole, z = up. */
data class V3(val x: Double, val y: Double, val z: Double = 0.0)

data class ScreenPt(val x: Float, val y: Float, val depth: Double)

/**
 * Perspective camera orbiting a [target]. [yawDeg] 0 looks along +y (from behind the tee towards the
 * green); [pitchDeg] is the camera elevation above the horizon (90 = straight down).
 */
class OrbitCamera(
    var target: V3,
    var yawDeg: Double = 0.0,
    var pitchDeg: Double = 35.0,
    var distance: Double = 300.0,
    private val fovDeg: Double = 50.0,
    private val near: Double = 1.0,
) {
    private class Basis(val cx: Double, val cy: Double, val cz: Double,
                        val r: V3, val up: V3, val f: V3)

    private fun basis(): Basis {
        val psi = Math.toRadians(yawDeg); val phi = Math.toRadians(pitchDeg)
        val dir = V3(sin(psi), cos(psi))                       // horizontal look direction
        val cx = target.x - distance * cos(phi) * dir.x
        val cy = target.y - distance * cos(phi) * dir.y
        val cz = target.z + distance * sin(phi)
        val f = norm(V3(target.x - cx, target.y - cy, target.z - cz))
        val r = V3(cos(psi), -sin(psi), 0.0)
        val up = V3(r.y * f.z - r.z * f.y, r.z * f.x - r.x * f.z, r.x * f.y - r.y * f.x) // r × f
        return Basis(cx, cy, cz, r, up, f)
    }

    private fun norm(v: V3): V3 { val l = sqrt(v.x * v.x + v.y * v.y + v.z * v.z); return V3(v.x / l, v.y / l, v.z / l) }
    private fun dot(a: V3, b: V3) = a.x * b.x + a.y * b.y + a.z * b.z

    private class Cam(val x: Double, val y: Double, val d: Double)

    private fun toCam(b: Basis, p: V3): Cam {
        val rel = V3(p.x - b.cx, p.y - b.cy, p.z - b.cz)
        return Cam(dot(rel, b.r), dot(rel, b.up), dot(rel, b.f))
    }

    private fun toScreen(c: Cam, w: Float, h: Float): ScreenPt {
        val focal = h / (2 * tan(Math.toRadians(fovDeg) / 2))
        return ScreenPt((w / 2 + focal * c.x / c.d).toFloat(), (h / 2 - focal * c.y / c.d).toFloat(), c.d)
    }

    /** Null if the point is behind the near plane. */
    fun project(p: V3, w: Float, h: Float): ScreenPt? {
        val c = toCam(basis(), p)
        return if (c.d < near) null else toScreen(c, w, h)
    }

    /** Projects a polygon (closed=true) or polyline, clipped against the near plane. */
    fun projectPath(pts: List<V3>, closed: Boolean, w: Float, h: Float): List<ScreenPt> {
        val b = basis()
        val cam = pts.map { toCam(b, it) }
        val clipped = if (closed) clipPolygon(cam) else clipLine(cam)
        return clipped.map { toScreen(it, w, h) }
    }

    private fun cut(a: Cam, b: Cam): Cam {
        val t = (near - a.d) / (b.d - a.d)
        return Cam(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y), near)
    }

    private fun clipPolygon(p: List<Cam>): List<Cam> {
        val out = ArrayList<Cam>()
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]
            val ain = a.d >= near; val bin = b.d >= near
            if (ain) out += a
            if (ain != bin) out += cut(a, b)
        }
        return out
    }

    private fun clipLine(p: List<Cam>): List<Cam> {
        // Single visible run only; adequate for rings/lines in front of the camera.
        val out = ArrayList<Cam>()
        for (i in p.indices) {
            val a = p[i]
            if (a.d >= near) {
                if (i > 0 && p[i - 1].d < near) out += cut(p[i - 1], a)
                out += a
            } else if (i > 0 && p[i - 1].d >= near) out += cut(p[i - 1], a)
        }
        return out
    }
}
