package com.greategolf.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Round::class, HoleScore::class, HoleGreen::class, Club::class, Shot::class, SwingRecord::class],
    version = 1,
    exportSchema = false,
)
abstract class GolfDatabase : RoomDatabase() {
    abstract fun rounds(): RoundDao
    abstract fun greens(): GreenDao
    abstract fun clubs(): ClubDao
    abstract fun swings(): SwingDao

    companion object {
        fun create(context: Context): GolfDatabase =
            Room.databaseBuilder(context, GolfDatabase::class.java, "greategolf.db").build()
    }
}
