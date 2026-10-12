package com.greategolf.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Round::class, HoleScore::class, HoleGreen::class, HoleTee::class, Club::class, Shot::class, SwingRecord::class],
    version = 3,
    exportSchema = false,
)
abstract class GolfDatabase : RoomDatabase() {
    abstract fun rounds(): RoundDao
    abstract fun greens(): GreenDao
    abstract fun clubs(): ClubDao
    abstract fun swings(): SwingDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hole_scores ADD COLUMN fairway INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE hole_scores ADD COLUMN gir INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE hole_scores ADD COLUMN penalties INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rounds ADD COLUMN finishedAt INTEGER")
                // rounds created before this version are treated as finished
                db.execSQL("UPDATE rounds SET finishedAt = startedAt")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS hole_tees (courseId TEXT NOT NULL, hole INTEGER NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, PRIMARY KEY(courseId, hole))")
            }
        }

        fun create(context: Context): GolfDatabase =
            Room.databaseBuilder(context, GolfDatabase::class.java, "greategolf.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
