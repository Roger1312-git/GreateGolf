package com.greategolf.data

import com.greategolf.domain.ClubAdvisor
import com.greategolf.domain.ClubStats
import com.greategolf.domain.HoleResult
import com.greategolf.domain.RoundResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GolfRepository(private val db: GolfDatabase) {
    private val roundDao = db.rounds()
    private val clubDao = db.clubs()

    val rounds = roundDao.rounds()
    val roundTotals = roundDao.totals()
    fun round(id: Long) = roundDao.round(id)
    fun scores(roundId: Long) = roundDao.scores(roundId)
    fun greens(courseId: String) = db.greens().greens(courseId)
    val clubs = clubDao.clubs()
    val shots = clubDao.shots()
    val swings = db.swings().records()

    suspend fun startRound(course: Course?, name: String, holes: Int, startedAt: Long): Long {
        val id = roundDao.insert(Round(courseId = course?.id, courseName = name, startedAt = startedAt, holeCount = holes))
        // Default pars: spread the course par over the holes (4 per hole, with par 3/5 to reach the total).
        val pars = defaultPars(holes, course?.par ?: if (holes == 9) 36 else 72)
        roundDao.upsert(pars.mapIndexed { i, p -> HoleScore(id, i + 1, par = p) })
        return id
    }

    suspend fun setScore(score: HoleScore) = roundDao.upsert(score)
    suspend fun finishRound(id: Long) = roundDao.finish(id, System.currentTimeMillis())
    suspend fun latestUnfinishedRoundId(): Long? = roundDao.latestUnfinishedId()

    /** Every round with its hole results, for statistics and handicap. */
    val results: Flow<List<RoundResult>> = combine(roundDao.rounds(), roundDao.allScores()) { rounds, scores ->
        val byRound = scores.groupBy { it.roundId }
        rounds.map { r ->
            RoundResult(r.id, r.startedAt, byRound[r.id].orEmpty().sortedBy { it.hole }
                .map { HoleResult(it.par, it.strokes, it.putts, it.fairway, it.gir) })
        }
    }
    suspend fun deleteRound(id: Long) = roundDao.delete(id)
    suspend fun saveGreen(g: HoleGreen) = db.greens().upsert(g)

    suspend fun addShot(shot: Shot) = clubDao.insertShot(shot)
    suspend fun deleteShot(shot: Shot) = clubDao.deleteShot(shot)
    suspend fun addClub(name: String, sortOrder: Int) = clubDao.insert(Club(name = name, sortOrder = sortOrder))
    suspend fun renameClub(club: Club, name: String) = clubDao.update(club.copy(name = name))
    suspend fun deleteClub(club: Club) = clubDao.delete(club)
    suspend fun addSwing(r: SwingRecord) = db.swings().insert(r)
    suspend fun deleteSwing(r: SwingRecord) = db.swings().delete(r)

    suspend fun seedDefaultBag() {
        if (clubDao.count() > 0) return
        clubDao.insertAll(DEFAULT_BAG.mapIndexed { i, n -> Club(name = n, sortOrder = i) })
    }

    /** Club "mapping": each club with its typical carried distance learned from logged shots. */
    val clubStats: Flow<List<ClubStats>> = combine(clubDao.clubs(), clubDao.shots()) { clubs, shots ->
        val byClub = shots.groupBy { it.clubId }
        clubs.map { c ->
            val d = byClub[c.id].orEmpty().map { it.meters }
            ClubStats(c.id, c.name, c.sortOrder, d.size, ClubAdvisor.typicalDistance(d), d.maxOrNull())
        }
    }

    companion object {
        val DEFAULT_BAG = listOf(
            "Driver", "3 Wood", "5 Wood", "4 Hybrid", "5 Iron", "6 Iron", "7 Iron", "8 Iron", "9 Iron",
            "PW", "GW (52°)", "SW (56°)", "LW (60°)", "Putter",
        )

        private val PARS_18 = listOf(4, 5, 4, 3, 4, 4, 3, 4, 5, 4, 3, 4, 5, 4, 4, 3, 5, 4) // = 72

        /** A plausible par layout summing to [total]; players can edit each hole's par in the scorecard. */
        fun defaultPars(holes: Int, total: Int): List<Int> {
            val pars = MutableList(holes) { PARS_18[it % 18] }
            var diff = total - pars.sum()
            var i = 0
            while (diff != 0 && i < holes * 2) {
                val idx = i % holes
                if (diff > 0 && pars[idx] == 4) { pars[idx] = 5; diff-- }
                else if (diff < 0 && pars[idx] == 4) { pars[idx] = 3; diff++ }
                i++
            }
            return pars
        }
    }
}
