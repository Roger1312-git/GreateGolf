package com.greategolf.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "rounds")
data class Round(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: String?,
    val courseName: String,
    val startedAt: Long,
    val holeCount: Int = 18,
    val finishedAt: Long? = null,
)

@Entity(
    tableName = "hole_scores",
    primaryKeys = ["roundId", "hole"],
    foreignKeys = [ForeignKey(Round::class, ["id"], ["roundId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("roundId")],
)
data class HoleScore(
    val roundId: Long,
    val hole: Int,
    val par: Int = 4,
    val strokes: Int = 0, // 0 = not played yet
    val putts: Int = 0,
    /** 0 = not recorded, 1 = hit, 2 = missed */
    @ColumnInfo(defaultValue = "0") val fairway: Int = 0,
    @ColumnInfo(defaultValue = "0") val gir: Int = 0,
    @ColumnInfo(defaultValue = "0") val penalties: Int = 0,
)

/** Green position of a hole, recorded by the player standing on the green. */
@Entity(tableName = "hole_greens", primaryKeys = ["courseId", "hole"])
data class HoleGreen(val courseId: String, val hole: Int, val lat: Double, val lng: Double)

@Entity(tableName = "clubs")
data class Club(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "shots",
    foreignKeys = [ForeignKey(Club::class, ["id"], ["clubId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("clubId")],
)
data class Shot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clubId: Long,
    val meters: Double,
    val createdAt: Long,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val auto: Boolean,
)

@Entity(tableName = "swing_records")
data class SwingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val videoUri: String,
    val tempo: Double,
    val shoulderTurn: Double?,
    val hipTurn: Double?,
    val xFactor: Double?,
    val headSway: Double?,
    val summary: String,
)
