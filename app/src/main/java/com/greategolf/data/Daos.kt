package com.greategolf.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoundDao {
    @Query("SELECT * FROM rounds ORDER BY startedAt DESC")
    fun rounds(): Flow<List<Round>>

    @Query("SELECT * FROM rounds WHERE id = :id")
    fun round(id: Long): Flow<Round?>

    @Query("SELECT * FROM hole_scores WHERE roundId = :roundId ORDER BY hole")
    fun scores(roundId: Long): Flow<List<HoleScore>>

    @Query("SELECT roundId, SUM(strokes) AS strokes, SUM(par) AS par FROM hole_scores WHERE strokes > 0 GROUP BY roundId")
    fun totals(): Flow<List<RoundTotal>>

    @Query("SELECT * FROM hole_scores")
    fun allScores(): Flow<List<HoleScore>>

    @Query("SELECT id FROM rounds WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestUnfinishedId(): Long?

    @Query("UPDATE rounds SET finishedAt = :t WHERE id = :id")
    suspend fun finish(id: Long, t: Long)

    @Insert suspend fun insert(round: Round): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(scores: List<HoleScore>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(score: HoleScore)
    @Query("DELETE FROM rounds WHERE id = :id") suspend fun delete(id: Long)
}

data class RoundTotal(val roundId: Long, val strokes: Int, val par: Int)

@Dao
interface GreenDao {
    @Query("SELECT * FROM hole_greens WHERE courseId = :courseId")
    fun greens(courseId: String): Flow<List<HoleGreen>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(g: HoleGreen)
}

@Dao
interface ClubDao {
    @Query("SELECT * FROM clubs ORDER BY sortOrder")
    fun clubs(): Flow<List<Club>>

    @Query("SELECT COUNT(*) FROM clubs") suspend fun count(): Int
    @Insert suspend fun insert(club: Club): Long
    @Insert suspend fun insertAll(clubs: List<Club>)
    @Update suspend fun update(club: Club)
    @Delete suspend fun delete(club: Club)

    @Query("SELECT * FROM shots ORDER BY createdAt DESC")
    fun shots(): Flow<List<Shot>>

    @Insert suspend fun insertShot(shot: Shot): Long
    @Delete suspend fun deleteShot(shot: Shot)
}

@Dao
interface SwingDao {
    @Query("SELECT * FROM swing_records ORDER BY createdAt DESC")
    fun records(): Flow<List<SwingRecord>>

    @Insert suspend fun insert(r: SwingRecord): Long
    @Delete suspend fun delete(r: SwingRecord)
}
