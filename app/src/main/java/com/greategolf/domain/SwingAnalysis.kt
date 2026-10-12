package com.greategolf.domain

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

enum class Joint { NOSE, L_SHOULDER, R_SHOULDER, L_HIP, R_HIP, L_WRIST, R_WRIST, L_KNEE, R_KNEE, L_ANKLE, R_ANKLE }

/** Normalised image coordinates (x right, y down, both 0..1). */
data class Pt(val x: Double, val y: Double)

data class PoseFrame(val timeMs: Long, val joints: Map<Joint, Pt>) {
    fun mid(a: Joint, b: Joint): Pt? {
        val p = joints[a] ?: return null
        val q = joints[b] ?: return null
        return Pt((p.x + q.x) / 2, (p.y + q.y) / 2)
    }
    fun width(a: Joint, b: Joint): Double? {
        val p = joints[a] ?: return null
        val q = joints[b] ?: return null
        return hypot(p.x - q.x, p.y - q.y)
    }
}

data class SwingReport(
    val takeawayIndex: Int,
    val topIndex: Int,
    val impactIndex: Int,
    val finishIndex: Int,
    val backswingMs: Long,
    val downswingMs: Long,
    val tempoRatio: Double,
    val shoulderTurnDeg: Double?,
    val hipTurnDeg: Double?,
    val xFactorDeg: Double?,
    val headSwayPct: Double?,
    val spineTiltChangeDeg: Double?,
    val feedback: List<String>,
)

/**
 * Heuristic swing analysis from 2D pose frames of a single golfer filmed face-on (or roughly so)
 * from a tripod at the driving range. It is a coaching aid, not a measurement instrument:
 * rotation angles are estimated from the apparent shoulder/hip width (acos(w/w0)), which is only
 * meaningful for a face-on camera.
 */
object SwingAnalysis {
    fun analyze(frames: List<PoseFrame>): SwingReport? {
        if (frames.size < 8) return null

        val hands = frames.map { it.mid(Joint.L_WRIST, Joint.R_WRIST) }
        if (hands.count { it == null } > frames.size / 2) return null
        // Fill gaps with the previous known value so a dropped detection doesn't break indices.
        val h = ArrayList<Pt>(frames.size)
        var last = hands.firstOrNull { it != null } ?: return null
        for (p in hands) { last = p ?: last; h.add(last) }

        val bodyH = bodyHeight(frames[0]) ?: 0.5

        // Fastest hand movement (the downswing) bounds where the top of the backswing can be.
        var impact = 1
        var best = -1.0
        for (i in 1 until h.size) {
            val dt = (frames[i].timeMs - frames[i - 1].timeMs).coerceAtLeast(1) / 1000.0
            val v = hypot(h[i].x - h[i - 1].x, h[i].y - h[i - 1].y) / dt
            if (v > best) { best = v; impact = i }
        }
        // Top = hands highest (smallest y) before impact.
        var top = 0
        for (i in 0..impact) if (h[i].y < h[top].y) top = i
        if (top >= impact) top = (impact - 1).coerceAtLeast(0)
        // Impact = hands back down to address height after the top (fallback: fastest frame).
        for (i in top + 1 until h.size) {
            if (h[i].y >= h[0].y - 0.05 * bodyH) { impact = i; break }
        }
        // Takeaway = last frame before the hands have moved noticeably from address.
        var takeaway = 0
        for (i in 0..top) {
            if (hypot(h[i].x - h[0].x, h[i].y - h[0].y) > 0.05 * bodyH) break
            takeaway = i
        }
        if (takeaway >= top) takeaway = 0

        val backswing = frames[top].timeMs - frames[takeaway].timeMs
        val downswing = (frames[impact].timeMs - frames[top].timeMs).coerceAtLeast(1)
        val tempo = backswing.toDouble() / downswing

        val addr = frames[0]
        val sw0 = addr.width(Joint.L_SHOULDER, Joint.R_SHOULDER)
        val hw0 = addr.width(Joint.L_HIP, Joint.R_HIP)
        val shoulderTurn = sw0?.let { w0 ->
            frames[top].width(Joint.L_SHOULDER, Joint.R_SHOULDER)?.let { turn(it, w0) }
        }
        val hipTurn = hw0?.let { w0 ->
            frames[top].width(Joint.L_HIP, Joint.R_HIP)?.let { turn(it, w0) }
        }
        val xFactor = if (shoulderTurn != null && hipTurn != null) shoulderTurn - hipTurn else null

        val headSway = sw0?.let { w0 ->
            val a = addr.joints[Joint.NOSE]; val t = frames[top].joints[Joint.NOSE]
            if (a != null && t != null && w0 > 0) abs(t.x - a.x) / w0 * 100 else null
        }

        val spineChange = run {
            val a = spineTilt(addr); val i = spineTilt(frames[impact])
            if (a != null && i != null) i - a else null
        }

        return SwingReport(
            takeaway, top, impact, frames.lastIndex,
            backswing, downswing, tempo,
            shoulderTurn, hipTurn, xFactor, headSway, spineChange,
            feedback(tempo, shoulderTurn, hipTurn, xFactor, headSway, spineChange),
        )
    }

    private fun turn(w: Double, w0: Double): Double =
        Math.toDegrees(acos((w / w0).coerceIn(0.0, 1.0)))

    private fun bodyHeight(f: PoseFrame): Double? {
        val head = f.joints[Joint.NOSE] ?: return null
        val ankle = f.mid(Joint.L_ANKLE, Joint.R_ANKLE) ?: return null
        return abs(ankle.y - head.y).takeIf { it > 0.05 }
    }

    /** Angle (deg) of the hip→shoulder line from vertical; sign shows lean direction. */
    private fun spineTilt(f: PoseFrame): Double? {
        val s = f.mid(Joint.L_SHOULDER, Joint.R_SHOULDER) ?: return null
        val hip = f.mid(Joint.L_HIP, Joint.R_HIP) ?: return null
        return Math.toDegrees(atan2(s.x - hip.x, hip.y - s.y))
    }

    private fun feedback(
        tempo: Double, shoulder: Double?, hip: Double?, x: Double?, sway: Double?, spine: Double?,
    ): List<String> {
        val out = mutableListOf<String>()
        out += when {
            tempo < 2.0 -> "Nhịp swing nhanh (%.1f:1). Backswing hơi vội — thử đếm \"một-hai-ba\" lúc lên gậy, \"một\" lúc xuống.".format(tempo)
            tempo > 4.0 -> "Nhịp swing chậm (%.1f:1). Có thể đang dừng ở đỉnh backswing; hãy chuyển tiếp liên tục hơn.".format(tempo)
            else -> "Nhịp swing tốt (%.1f:1), gần tỉ lệ lý tưởng 3:1.".format(tempo)
        }
        if (shoulder != null) out += when {
            shoulder < 60 -> "Xoay vai ở đỉnh còn ít (~%.0f°). Thử xoay vai nhiều hơn để tạo lực (mục tiêu 80–100°).".format(shoulder)
            shoulder > 105 -> "Xoay vai rất lớn (~%.0f°); coi chừng mất kiểm soát ở đỉnh backswing.".format(shoulder)
            else -> "Xoay vai ở đỉnh tốt (~%.0f°).".format(shoulder)
        }
        if (x != null) out += when {
            x < 25 -> "Chênh lệch xoay vai–hông (X-factor ~%.0f°) thấp; giữ hông xoay ít hơn vai để tích lũy lực.".format(x)
            else -> "X-factor ~%.0f° — vai xoay nhiều hơn hông, tốt cho lực đánh.".format(x)
        }
        if (sway != null) out += when {
            sway > 25 -> "Đầu dịch ngang nhiều (~%.0f%% chiều rộng vai). Giữ đầu ổn định hơn trong backswing.".format(sway)
            else -> "Đầu giữ ổn định tốt (dịch ~%.0f%% chiều rộng vai).".format(sway)
        }
        if (spine != null) out += when {
            abs(spine) > 12 -> "Góc cột sống thay đổi ~%.0f° từ address đến impact — giữ nguyên độ nghiêng thân người.".format(abs(spine))
            else -> "Giữ được góc cột sống ổn định đến impact."
        }
        return out
    }
}
