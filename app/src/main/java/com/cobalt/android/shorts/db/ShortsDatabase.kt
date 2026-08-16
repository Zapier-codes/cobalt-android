package com.cobalt.android.shorts.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * A separate database from `download/DownloadDatabase.kt` on purpose: caching
 * a resolved video feed is a different domain from the download queue, and
 * "Unified enhancement" in ARCHITECTURE.md only requires reusing existing
 * *download* infrastructure — there is no existing Shorts-caching table to
 * extend, so this is genuinely new (see ARCHITECTURE.md Phase 2).
 */
@Database(entities = [ShortsCacheEntity::class], version = 1, exportSchema = false)
abstract class ShortsDatabase : RoomDatabase() {
    abstract fun shortsCacheDao(): ShortsCacheDao

    companion object {
        @Volatile private var INSTANCE: ShortsDatabase? = null

        fun getInstance(context: Context): ShortsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShortsDatabase::class.java,
                    "cobalt_shorts_cache.db"
                ).build().also { INSTANCE = it }
            }
    }
}
