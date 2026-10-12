package com.greategolf.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class DomainTest {
    private val origin = GeoPoint(10.0, 106.0)
    // ~ meters -> degrees latitude
    private fun north(m: Double) = GeoPoint(origin.lat + m / 111_195.0, origin.lng)

    @Test fun haversine() {
        assertEquals(100.0, GeoMath.distanceMeters(origin, north(100.0)), 0.5)
        assertEquals(109.36, GeoMath.metersToYards(100.0), 0.01)
    }

    @Test fun shotTrackerDetectsShotAfterWalkingAndStopping() {
        val t = ShotTracker()
        t.arm(origin)
        var t0 = 0L
        // standing at the tee: must not fire
        repeat(5) { assertNull(t.onFix(GeoFix(origin, t0, 0.1, 5.0))); t0 += 1000 }
        // walking toward ball
        for (m in listOf(20.0, 60.0, 100.0, 140.0, 180.0)) {
            assertNull(t.onFix(GeoFix(north(m), t0, 1.4, 5.0))); t0 += 1000
        }
        // arrived at ball and standing
        var shot: DetectedShot? = null
        repeat(3) { shot = t.onFix(GeoFix(north(185.0), t0, 0.1, 5.0)) ?: shot; t0 += 1000 }
        assertNotNull(shot)
        assertEquals(185.0, shot!!.meters, 1.0)
        assertFalse(t.isArmed)
    }

    @Test fun shotTrackerIgnoresShortShuffleAndBadAccuracy() {
        val t = ShotTracker()
        t.arm(origin)
        repeat(10) { assertNull(t.onFix(GeoFix(north(5.0), it * 1000L, 0.0, 5.0))) }
        repeat(10) { assertNull(t.onFix(GeoFix(north(200.0), it * 1000L, 0.0, 80.0))) }
    }

    @Test fun clubAdvisor() {
        val clubs = listOf(
            ClubStats(1, "Driver", 0, 5, 220.0, 240.0),
            ClubStats(2, "7I", 5, 5, 130.0, 140.0),
            ClubStats(3, "PW", 8, 5, 100.0, 110.0),
            ClubStats(4, "Putter", 9, 0, null, null),
        )
        assertEquals("7I", ClubAdvisor.recommend(125.0, clubs)!!.name)
        assertEquals("PW", ClubAdvisor.recommend(100.0, clubs)!!.name)
        assertEquals("Driver", ClubAdvisor.recommend(400.0, clubs)!!.name)
        assertNull(ClubAdvisor.recommend(100.0, emptyList()))
        assertEquals(3.0, ClubAdvisor.typicalDistance(listOf(1.0, 3.0, 100.0))!!, 0.0)
    }

    @Test fun swingDetectorFiresOncePerSwing() {
        val d = SwingDetector()
        var events = 0
        var t = 0L
        fun feed(mag: Double) { if (d.onSample(t, mag, 0.0, 0.0) != null) events++; t += 10 }
        repeat(100) { feed(0.5) }
        // one swing: ramp up to 35 and back
        listOf(10.0, 25.0, 35.0, 28.0, 12.0).forEach { repeat(4) { _ -> feed(it) } }
        repeat(100) { feed(0.5) }
        assertEquals(1, events)
        // walking jolts must not trigger
        repeat(50) { feed(12.0) }; repeat(50) { feed(0.5) }
        assertEquals(1, events)
        // second swing within refractory period is ignored, later one counts
        t += 3000
        listOf(30.0).forEach { repeat(4) { _ -> feed(it) } }; repeat(10) { feed(0.5) }
        assertEquals(2, events)
    }

    /** Synthetic face-on swing: hands rise then drop, shoulders narrow at the top. */
    private fun syntheticFrames(): List<PoseFrame> {
        val frames = mutableListOf<PoseFrame>()
        // 30 fps: 10 idle, 30 backswing (1.0s), 10 downswing (0.33s), 10 follow-through
        val total = 60
        for (i in 0 until total) {
            val handY: Double; val turn: Double
            when {
                i < 10 -> { handY = 0.55; turn = 0.0 }
                i < 40 -> { val p = (i - 10) / 30.0; handY = 0.55 - 0.30 * p; turn = 90.0 * p }
                i < 50 -> { val p = (i - 40) / 10.0; handY = 0.25 + 0.30 * p; turn = 90.0 * (1 - p) }
                else -> { handY = 0.55 - 0.1 * ((i - 50) / 10.0); turn = 0.0 }
            }
            val sw = 0.20 * cos(turn * PI / 180) ; val hw = 0.16 * cos((turn / 2) * PI / 180)
            val j = mapOf(
                Joint.NOSE to Pt(0.5, 0.2),
                Joint.L_SHOULDER to Pt(0.5 - sw / 2, 0.32), Joint.R_SHOULDER to Pt(0.5 + sw / 2, 0.32),
                Joint.L_HIP to Pt(0.5 - hw / 2, 0.55), Joint.R_HIP to Pt(0.5 + hw / 2, 0.55),
                Joint.L_WRIST to Pt(0.5 - 0.02, handY), Joint.R_WRIST to Pt(0.5 + 0.02, handY),
                Joint.L_ANKLE to Pt(0.45, 0.9), Joint.R_ANKLE to Pt(0.55, 0.9),
            )
            frames += PoseFrame(i * 33L, j)
        }
        return frames
    }

    @Test fun swingAnalysisFindsPhasesAndTempo() {
        val r = SwingAnalysis.analyze(syntheticFrames())!!
        assertTrue("top=${r.topIndex}", r.topIndex in 37..41)
        assertTrue("impact=${r.impactIndex}", r.impactIndex in 44..50)
        assertTrue("tempo=${r.tempoRatio}", r.tempoRatio in 2.5..4.5)
        assertEquals(90.0, r.shoulderTurnDeg!!, 8.0)
        assertEquals(45.0, r.hipTurnDeg!!, 8.0)
        assertEquals(0.0, r.headSwayPct!!, 0.1)
        assertTrue(r.feedback.isNotEmpty())
        assertNull(SwingAnalysis.analyze(syntheticFrames().take(3)))
    }
}
