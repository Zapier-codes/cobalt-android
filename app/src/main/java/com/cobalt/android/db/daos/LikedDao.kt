package com.cobalt.android.db.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cobalt.android.db.entities.LikedEntity

@Dao
interface LikedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LikedEntity)

    @Query("DELETE FROM liked WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("SELECT * FROM liked WHERE videoId = :videoId")
    suspend fun getByVideoId(videoId: String): LikedEntity?

    @Query("SELECT * FROM liked ORDER BY likedAt DESC")
    fun getAllLive(): LiveData<List<LikedEntity>>
}
