package com.cobalt.android.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cobalt.android.db.entities.ResolutionCacheEntity

@Dao
interface ResolutionCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: ResolutionCacheEntity)

    @Query("SELECT * FROM resolution_cache WHERE originalUrl = :originalUrl LIMIT 1")
    fun getByOriginalUrl(originalUrl: String): ResolutionCacheEntity?
}
