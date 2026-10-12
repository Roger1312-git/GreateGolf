package com.greategolf.domain

import kotlin.math.roundToInt

/** fairway / gir: 0 = not recorded, 1 = hit, 2 = missed. */
data class HoleResult(val par: Int, val strokes: Int, val putts: Int, val fairway: Int, val gir: Int)

data class RoundResult(val id: Long, val startedAt: Long, val holes: List<HoleResult>) {
    val played get() = holes.filter { it.strokes > 0 }
    val isFull18 get() = holes.size == 18 && holes.all { it.strokes > 0 }
    val gross get() = played.sumOf { it.strokes }
    val par get() = played.sumOf { it.par }
}

data class ScoringMix(val eagleOrBetter: Int, val birdie: Int, val par: Int, val bogey: Int, val doubleOrWorse: Int) {
    val total get() = eagleOrBetter + birdie + par + bogey + doubleOrWorse
}

data class StatsSummary(
    val roundsPlayed: Int,
    val fullRounds: Int,
    val avgScore: Double?,
    val bestScore: Int?,
    val puttsPer18: Double?,
    val firPct: Double?,
    val girPct: Double?,
    val avgPar3: Double?,
    val avgPar4: Double?,
    val avgPar5: Double?,
    val mix: ScoringMix,
    /** Gross scores of the most recent full rounds, oldest first (for the trend chart). */
    val trend: List<Int>,
    val handicapIndex: Double?,
)

object StatsCalculator {
    fun summarize(rounds: List<RoundResult>): StatsSummary {
        val withScores = rounds.filter { it.played.isNotEmpty() }
        val full = withScores.filter { it.isFull18 }.sortedBy { it.startedAt }
        val holes = withScores.flatMap { it.played }

        val puttHoles = holes.filter { it.putts > 0 }
        val fwHoles = holes.filter { it.par >= 4 && it.fairway != 0 }
        val girHoles = holes.filter { it.gir != 0 }

        fun avg(par: Int) = holes.filter { it.par == par }.takeIf { it.isNotEmpty() }?.map { it.strokes }?.average()

        val mix = ScoringMix(
            holes.count { it.strokes - it.par <= -2 }, holes.count { it.strokes - it.par == -1 },
            holes.count { it.strokes == it.par }, holes.count { it.strokes - it.par == 1 },
            holes.count { it.strokes - it.par >= 2 },
        )
        return StatsSummary(
            roundsPlayed = withScores.size,
            fullRounds = full.size,
            avgScore = full.takeIf { it.isNotEmpty() }?.map { it.gross }?.average(),
            bestScore = full.minOfOrNull { it.gross },
            puttsPer18 = puttHoles.takeIf { it.size >= 9 }?.let { it.sumOf { h -> h.putts }.toDouble() / it.size * 18 },
            firPct = fwHoles.takeIf { it.isNotEmpty() }?.let { 100.0 * it.count { h -> h.fairway == 1 } / it.size },
            girPct = girHoles.takeIf { it.isNotEmpty() }?.let { 100.0 * it.count { h -> h.gir == 1 } / it.size },
            avgPar3 = avg(3), avgPar4 = avg(4), avgPar5 = avg(5),
            mix = mix,
            trend = full.takeLast(10).map { it.gross },
            handicapIndex = Handicap.index(full.takeLast(20).map { Handicap.differential(it) }),
        )
    }
}

/**
 * Handicap Index estimate in the spirit of the World Handicap System. Real WHS needs each course's
 * rating/slope, which we don't have for Vietnamese courses, so we assume rating = par and slope = 113
 * and cap each hole at par + 5. Treat the result as an estimate, not an official index.
 */
object Handicap {
    fun differential(r: RoundResult, slope: Int = 113): Double {
        val adjusted = r.holes.sumOf { minOf(it.strokes, it.par + 5) }
        val v = (adjusted - r.par) * 113.0 / slope
        return (v * 10).roundToInt() / 10.0
    }

    /** (how many of the lowest differentials to average, adjustment) for n available rounds (WHS table). */
    private fun rule(n: Int): Pair<Int, Double>? = when {
        n < 3 -> null
        n == 3 -> 1 to -2.0
        n == 4 -> 1 to -1.0
        n == 5 -> 1 to 0.0
        n == 6 -> 2 to -1.0
        n <= 8 -> 2 to 0.0
        n <= 11 -> 3 to 0.0
        n <= 14 -> 4 to 0.0
        n <= 16 -> 5 to 0.0
        n <= 18 -> 6 to 0.0
        n == 19 -> 7 to 0.0
        else -> 8 to 0.0
    }

    /** [differentials] = the most recent (up to 20) 18-hole differentials. Null with fewer than 3. */
    fun index(differentials: List<Double>): Double? {
        val (k, adj) = rule(differentials.size.coerceAtMost(20)) ?: return null
        val avg = differentials.takeLast(20).sorted().take(k).average() + adj
        return (avg * 10).roundToInt() / 10.0
    }
}
