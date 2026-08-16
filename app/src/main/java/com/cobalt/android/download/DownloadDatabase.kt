package com.cobalt.android.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cobalt.android.db.ResolutionCacheDao
import com.cobalt.android.db.entities.ResolutionCacheEntity

@Database(entities = [DownloadRecord::class, ResolutionCacheEntity::class], version = 2, exportSchema = false)
@TypeConverters(StatusConverters::class)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun resolutionCacheDao(): ResolutionCacheDao

    companion object {
        @Volatile private var INSTANCE: DownloadDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS resolution_cache")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS resolution_cache (" +
                        "originalUrl TEXT NOT NULL PRIMARY KEY, " +
                        "formatsJson TEXT NOT NULL, " +
                        "resolvedAtMillis INTEGER NOT NULL)"
                )
            }
        }

        fun getInstance(context: Context): DownloadDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "cobalt_downloads.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
