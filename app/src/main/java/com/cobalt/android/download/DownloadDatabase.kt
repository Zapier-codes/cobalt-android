package com.cobalt.android.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cobalt.android.db.ResolutionCacheDao
import com.cobalt.android.db.daos.HistoryDao
import com.cobalt.android.db.daos.LikedDao
import com.cobalt.android.db.entities.HistoryEntity
import com.cobalt.android.db.entities.LikedEntity
import com.cobalt.android.db.entities.ResolutionCacheEntity

// Phase 9: History/Liked extend this database rather than ShortsDatabase or a
// new database of their own. ShortsDatabase (Phase 2) is deliberately an
// evictable *cache* — rows there expire and get dropped. History and likes
// are permanent user data, the same durability class as `DownloadRecord`
// and `ResolutionCacheEntity` already living here, so they belong alongside
// them, not next to data that's designed to be thrown away.
@Database(
    entities = [
        DownloadRecord::class,
        ResolutionCacheEntity::class,
        HistoryEntity::class,
        LikedEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(StatusConverters::class)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun resolutionCacheDao(): ResolutionCacheDao
    abstract fun historyDao(): HistoryDao
    abstract fun likedDao(): LikedDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS history (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "itemType TEXT NOT NULL, " +
                        "refId TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "thumbnailUrl TEXT NOT NULL, " +
                        "sourceUrl TEXT NOT NULL, " +
                        "timestamp INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS liked (" +
                        "videoId TEXT NOT NULL PRIMARY KEY, " +
                        "title TEXT NOT NULL, " +
                        "authorName TEXT NOT NULL, " +
                        "thumbnailUrl TEXT NOT NULL, " +
                        "watchUrl TEXT NOT NULL, " +
                        "likedAt INTEGER NOT NULL)"
                )
            }
        }

        fun getInstance(context: Context): DownloadDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "cobalt_downloads.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}

