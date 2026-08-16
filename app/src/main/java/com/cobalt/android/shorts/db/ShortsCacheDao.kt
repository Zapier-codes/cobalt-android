package com.cobalt.android.shorts.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ShortsCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ShortsCacheEntity>)

    @Query("SELECT * FROM shorts_cache ORDER BY cachedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ShortsCacheEntity>

    @Query("UPDATE shorts_cache SET isLiked = :liked WHERE videoId = :videoId")
    suspend fun setLiked(videoId: String, liked: Boolean)

    @Query("DELETE FROM shorts_cache WHERE cachedAt < :olderThan AND isLiked = 0")
    suspend fun evictStale(olderThan: Long)
}
