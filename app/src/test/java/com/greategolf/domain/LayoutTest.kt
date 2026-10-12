package com.greategolf.domain

import com.greategolf.data.OverpassParser
import org.junit.Assert.*
import org.junit.Test

class LayoutTest {
    private val mLat = 1.0 / 111_195.0
    private val mLng = mLat / Math.cos(Math.toRadians(10.0))
    private fun pt(e: Double, n: Double) = """{"lat":${10.0 + n * mLat},"lon":${106.0 + e * mLng}}"""
    private fun ring(vararg p: Pair<Double, Double>): String {
        val l = p.toList() + p.first()
        return "[" + l.joinToString(",") { pt(it.first, it.second) } + "]"
    }

    /** Hole 1: 300 m due north. Green at the end, fairway + bunker beside, water far away, hole 2 elsewhere. */
    private val json = """
    {"elements":[
      {"type":"way","tags":{"golf":"hole","ref":"1","par":"4"},"geometry":[${pt(0.0, 0.0)},${pt(0.0, 300.0)}]},
      {"type":"way","tags":{"golf":"hole","ref":"2a","par":"3"},"geometry":[${pt(1000.0, 0.0)},${pt(1000.0, 150.0)}]},
      {"type":"way","tags":{"golf":"green"},"geometry":${ring(-15.0 to 285.0, 15.0 to 285.0, 15.0 to 315.0, -15.0 to 315.0)}},
      {"type":"way","tags":{"golf":"fairway"},"geometry":${ring(-20.0 to 80.0, 20.0 to 80.0, 20.0 to 260.0, -20.0 to 260.0)}},
      {"type":"relation","tags":{"golf":"bunker"},"members":[{"type":"way","role":"outer","geometry":${ring(25.0 to 270.0, 35.0 to 270.0, 35.0 to 280.0, 25.0 to 280.0)}}]},
      {"type":"way","tags":{"natural":"water"},"geometry":${ring(2000.0 to 0.0, 2010.0 to 0.0, 2010.0 to 10.0, 2000.0 to 10.0)}},
      {"type":"way","tags":{"highway":"service"},"geometry":[${pt(0.0, 0.0)},${pt(0.0, 5.0)}]}
    ]}""".trimIndent()

    @Test fun parsesFeaturesAndHoles() {
        val l = OverpassParser.parseLayout(json)
        assertEquals(listOf(1, 2), l.holes.map { it.ref })
        assertEquals(4, l.holes[0].par)
        assertEquals(300.0, l.holes[0].lengthM, 1.0)
        assertEquals(setOf(FeatureType.GREEN, FeatureType.FAIRWAY, FeatureType.BUNKER, FeatureType.WATER), l.features.map { it.type }.toSet())
        assertEquals(4, l.features.first { it.type == FeatureType.GREEN }.ring.size) // closing point dropped
        assertTrue(OverpassParser.parseLayout("""{"elements":[]}""").isEmpty)
    }

    @Test fun greenGeometryAndCorridor() {
        val l = OverpassParser.parseLayout(json)
        val h = l.holes[0]
        val g = LayoutOps.greenOf(l, h)!!
        val tee = h.tee
        assertEquals(300.0, GeoMath.distanceMeters(tee, g.center), 1.5)
        assertEquals(285.0, GeoMath.distanceMeters(tee, g.front), 1.5)
        assertEquals(315.0, GeoMath.distanceMeters(tee, g.back), 1.5)
        val near = LayoutOps.featuresForHole(l, h).map { it.type }
        assertTrue(FeatureType.FAIRWAY in near && FeatureType.GREEN in near && FeatureType.BUNKER in near)
        assertFalse(FeatureType.WATER in near)
        assertEquals(0.0, LayoutOps.bearingDeg(tee, h.greenEnd), 0.5)
    }

    @Test fun localFrameAxes() {
        val f = LocalFrame(GeoPoint(10.0, 106.0), 90.0) // facing east
        val (u, v) = f.toLocal(GeoPoint(10.0, 106.0 + 100 * mLng))
        assertEquals(0.0, u, 0.5); assertEquals(100.0, v, 0.5)
        val (u2, _) = f.toLocal(GeoPoint(10.0 - 50 * mLat, 106.0)) // south is to the right when facing east
        assertEquals(50.0, u2, 0.5)
    }

    @Test fun pickCourseByNameThenDistance() {
        val els = """{"elements":[
          {"type":"way","bounds":{"minlat":10.0,"minlon":106.0,"maxlat":10.01,"maxlon":106.01},"tags":{"name":"Other Golf Club"}},
          {"type":"way","bounds":{"minlat":10.1,"minlon":106.1,"maxlat":10.11,"maxlon":106.11},"tags":{"name":"Sân golf Sky Lake"}}]}"""
        val b = OverpassParser.pickCourseBounds(els, "Sky Lake Resort & Golf Club", GeoPoint(10.0, 106.0))!!
        assertEquals(10.1, b.minLat, 1e-9)
        assertNull(OverpassParser.pickCourseBounds(els, "Zzz Course", GeoPoint(20.0, 120.0)))
    }

    @Test fun cameraProjection() {
        val cam = OrbitCamera(V3(0.0, 100.0, 0.0), yawDeg = 0.0, pitchDeg = 40.0, distance = 200.0)
        val c = cam.project(V3(0.0, 100.0, 0.0), 1000f, 800f)!!
        assertEquals(500f, c.x, 0.5f); assertEquals(400f, c.y, 0.5f)
        val farther = cam.project(V3(0.0, 200.0, 0.0), 1000f, 800f)!!
        assertTrue("further along the hole appears higher", farther.y < c.y)
        val right = cam.project(V3(50.0, 100.0, 0.0), 1000f, 800f)!!
        assertTrue("right of axis appears to the right", right.x > c.x)
        assertTrue(farther.depth > 0)
        // yaw 90: looking east, so +x (east) is now "forward" and appears higher
        val east = OrbitCamera(V3(0.0, 0.0, 0.0), yawDeg = 90.0, pitchDeg = 40.0, distance = 200.0)
        assertTrue(east.project(V3(50.0, 0.0, 0.0), 1000f, 800f)!!.y < east.project(V3(0.0, 0.0, 0.0), 1000f, 800f)!!.y)
    }

    @Test fun cameraClipsBehindNearPlane() {
        val cam = OrbitCamera(V3(0.0, 0.0, 0.0), yawDeg = 0.0, pitchDeg = 10.0, distance = 50.0)
        assertNull(cam.project(V3(0.0, -500.0, 0.0), 800f, 600f)) // far behind camera
        val poly = listOf(V3(-300.0, -500.0), V3(300.0, -500.0), V3(300.0, 200.0), V3(-300.0, 200.0))
        val out = cam.projectPath(poly, true, 800f, 600f)
        assertTrue(out.size >= 3)
        assertTrue(out.all { it.depth >= 0.99 })
    }
}
