package com.cobalt.android.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cobalt.android.db.entities.ResolutionCacheEntity

@Dao
interface ResolutionCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ResolutionCacheEntity)

    @Query("SELECT * FROM resolution_cache WHERE originalUrl = :url")
    suspend fun getByUrl(url: String): ResolutionCacheEntity?

    @Query("DELETE FROM resolution_cache WHERE resolvedAtMillis < :olderThanMillis")
    suspend fun deleteOlderThan(olderThanMillis: Long)
}
