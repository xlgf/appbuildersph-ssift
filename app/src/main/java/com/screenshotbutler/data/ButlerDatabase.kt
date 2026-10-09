package com.screenshotbutler.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Bump the version on EVERY schema change: an older on-device database otherwise crashes the app at launch.
// The index is rebuildable from the screenshots, so upgrades simply drop it (see fallbackToDestructiveMigration).
@Database(entities = [Shot::class, ShotFts::class], version = 2, exportSchema = false)
abstract class ButlerDatabase : RoomDatabase() {
    abstract fun shots(): ShotDao

    companion object {
        fun create(context: Context): ButlerDatabase =
            Room.databaseBuilder(context, ButlerDatabase::class.java, "butler.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
