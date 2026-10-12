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

class StatsTest {
    private fun hole(par: Int, strokes: Int, putts: Int = 0, fw: Int = 0, gir: Int = 0) = HoleResult(par, strokes, putts, fw, gir)
    private fun round(id: Long, t: Long, strokesPerHole: Int, par: Int = 4) =
        RoundResult(id, t, List(18) { hole(par, strokesPerHole, 2, 1, if (it % 2 == 0) 1 else 2) })

    @Test fun summaryBasics() {
        val s = StatsCalculator.summarize(listOf(round(1, 1, 5), round(2, 2, 4)))
        assertEquals(2, s.fullRounds)
        assertEquals(81.0, s.avgScore!!, 0.001)
        assertEquals(72, s.bestScore)
        assertEquals(36.0, s.puttsPer18!!, 0.001)
        assertEquals(100.0, s.firPct!!, 0.001)
        assertEquals(50.0, s.girPct!!, 0.001)
        assertEquals(18, s.mix.par); assertEquals(18, s.mix.bogey)
        assertEquals(listOf(90, 72), s.trend)
        assertNull(s.handicapIndex) // fewer than 3 rounds
    }

    @Test fun partialRoundsCountForHoleStatsOnly() {
        val partial = RoundResult(3, 3, listOf(hole(4, 3, 1), hole(3, 0), hole(5, 7)))
        val s = StatsCalculator.summarize(listOf(partial))
        assertEquals(0, s.fullRounds); assertNull(s.avgScore)
        assertEquals(1, s.mix.birdie); assertEquals(1, s.mix.doubleOrWorse)
    }

    @Test fun handicapFollowsWhsTable() {
        // 3 rounds of 80, 85, 90 on par 72 -> differentials 8, 13, 18 -> lowest 8 - 2.0 = 6.0
        fun r(g: Int) = RoundResult(0, 0, List(18) { HoleResult(4, g / 18, 0, 0, 0) }.let { l ->
            // distribute remainder
            l.mapIndexed { i, h -> if (i < g % 18) h.copy(strokes = h.strokes + 1) else h }
        })
        val diffs = listOf(80, 85, 90).map { Handicap.differential(r(it)) }
        assertEquals(listOf(8.0, 13.0, 18.0), diffs)
        assertEquals(6.0, Handicap.index(diffs)!!, 0.001)
        assertNull(Handicap.index(listOf(5.0, 6.0)))
        // 20 rounds: average of best 8
        assertEquals(10.5, Handicap.index((1..20).map { it + 6.0 })!!, 0.001) // best 8 = 7..14 -> 10.5
        // blow-up holes are capped at par+5
        assertEquals(1.0, Handicap.differential(RoundResult(0, 0, List(18) { if (it == 0) HoleResult(4, 15, 0, 0, 0) else HoleResult(4, 4, 0, 0, 0) })) - 4.0, 1e-9)
    }

    @Test fun hazardsAheadOfPlayer() {
        val mLat = 1.0 / 111_195.0
        fun gp(n: Double, e: Double = 0.0) = GeoPoint(10.0 + n * mLat, 106.0 + e * mLat / Math.cos(Math.toRadians(10.0)))
        fun sq(n: Double, e: Double) = listOf(gp(n, e), gp(n, e + 10), gp(n + 10, e + 10), gp(n + 10, e))
        val hole = Hole(1, 4, listOf(gp(0.0), gp(300.0)))
        val layout = CourseLayout(
            listOf(
                GeoFeature(FeatureType.BUNKER, sq(140.0, 12.0), true),     // 140-150 ahead of tee
                GeoFeature(FeatureType.WATER, sq(220.0, -25.0), true),     // 220-230
                GeoFeature(FeatureType.BUNKER, sq(30.0, 12.0), true),      // behind a player at 100 m
                GeoFeature(FeatureType.BUNKER, sq(150.0, 300.0), true),    // other hole, far from corridor
            ), listOf(hole),
        )
        val fromTee = LayoutOps.hazardsAhead(layout, hole, gp(0.0))
        assertEquals(listOf(FeatureType.BUNKER, FeatureType.WATER, FeatureType.BUNKER).size, fromTee.size)
        assertEquals(30.0, fromTee[0].frontM, 3.0)
        val from100 = LayoutOps.hazardsAhead(layout, hole, gp(100.0))
        assertEquals(2, from100.size)
        assertEquals(40.0, from100[0].frontM, 3.0)      // bunker 140 m from tee
        assertEquals(FeatureType.WATER, from100[1].type)
    }
}

class Geometry3DTest {
    @Test fun unprojectInvertsProject() {
        val cam = OrbitCamera(V3(0.0, 150.0, 0.0), yawDeg = 25.0, pitchDeg = 50.0, distance = 260.0)
        for (p in listOf(V3(0.0, 150.0), V3(30.0, 220.0), V3(-40.0, 90.0), V3(10.0, 300.0))) {
            val s = cam.project(p, 900f, 1600f)!!
            val back = cam.unproject(s.x, s.y, 900f, 1600f)!!
            assertEquals(p.x, back.x, 0.01); assertEquals(p.y, back.y, 0.01)
        }
        // a ray above the horizon never hits the ground
        assertNull(OrbitCamera(V3(0.0, 0.0, 0.0), pitchDeg = 10.0, distance = 100.0).unproject(450f, -2000f, 900f, 1600f))
    }

    @Test fun localFrameRoundTrip() {
        val f = LocalFrame(GeoPoint(21.03, 105.85), 137.0)
        val p = GeoPoint(21.0312, 105.8527)
        val (u, v) = f.toLocal(p)
        val back = f.toGeo(u, v)
        assertEquals(p.lat, back.lat, 1e-9); assertEquals(p.lng, back.lng, 1e-9)
    }

    @Test fun mercatorTiles() {
        assertEquals(1, WebMercator.tileX(0.0, 1)); assertEquals(1, WebMercator.tileY(-0.0001, 1))
        val z = 18; val lat = 21.0285; val lng = 105.8542
        val x = WebMercator.tileX(lng, z); val y = WebMercator.tileY(lat, z)
        assertTrue(lng >= WebMercator.lngAt(x.toDouble(), z) && lng <= WebMercator.lngAt(x + 1.0, z))
        assertTrue(lat <= WebMercator.latAt(y.toDouble(), z) && lat >= WebMercator.latAt(y + 1.0, z))
        // ~150 m x 100 m box at z18 needs only a few tiles
        val t = WebMercator.tilesCovering(lat, lng, lat + 0.001, lng + 0.0015, z)
        assertTrue(t.size in 1..9)
        assertTrue(WebMercator.tilesCovering(0.0, 0.0, 1.0, 1.0, 18).isEmpty()) // too large
    }
}
