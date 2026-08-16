package com.cobalt.android.download

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cobalt.android.download.DownloadRecord
import com.cobalt.android.db.entities.ResolutionCacheEntity

@Database(entities = [DownloadRecord::class, ResolutionCacheEntity::class], version = 1, exportSchema = false)
@TypeConverters(StatusConverters::class)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun resolutionCacheDao(): ResolutionCacheDao

    companion object {
        @Volatile private var INSTANCE: DownloadDatabase? = null

        fun getInstance(context: Context): DownloadDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "cobalt_downloads.db"
                ).build().also { INSTANCE = it }
            }
    }
}
