package com.greategolf.domain

data class ClubStats(
    val clubId: Long,
    val name: String,
    val sortOrder: Int,
    val shots: Int,
    val avgMeters: Double?,
    val maxMeters: Double?,
)

object ClubAdvisor {
    /** Median is more robust than the mean against mishits / GPS glitches. */
    fun typicalDistance(distances: List<Double>): Double? {
        if (distances.isEmpty()) return null
        val s = distances.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }

    /**
     * Picks the club whose typical distance is closest to [targetMeters], preferring a club that
     * reaches the target (up to 3% short) over one that falls short. Only clubs with data count.
     */
    fun recommend(targetMeters: Double, clubs: List<ClubStats>): ClubStats? {
        val known = clubs.filter { it.avgMeters != null && it.shots > 0 }
        if (known.isEmpty()) return null
        val reaching = known.filter { it.avgMeters!! >= targetMeters * 0.97 }
        return if (reaching.isNotEmpty()) reaching.minBy { it.avgMeters!! }
        else known.maxBy { it.avgMeters!! }
    }
}
