package com.cobalt.android.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ResolutionCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ResolutionCacheEntity)

    @Query("SELECT * FROM resolution_cache WHERE originalUrl = :url")
    suspend fun getByUrl(url: String): ResolutionCacheEntity?

    /** Housekeeping so this table doesn't grow unbounded — every link a
     * user has ever pasted would otherwise stay forever. Called from
     * `LinkResolverRepository` before each cache read; see its doc
     * comment for the actual freshness window used for cache *hits*,
     * which is much shorter than this deletion threshold. */
    @Query("DELETE FROM resolution_cache WHERE resolvedAtMillis < :olderThanMillis")
    suspend fun deleteOlderThan(olderThanMillis: Long)
}
