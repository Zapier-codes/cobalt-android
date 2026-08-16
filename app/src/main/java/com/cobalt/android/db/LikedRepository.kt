package com.cobalt.android.db

import android.content.Context
import androidx.lifecycle.LiveData
import com.cobalt.android.db.entities.LikedEntity
import com.cobalt.android.download.DownloadDatabase

class LikedRepository(context: Context) {
    private val dao = DownloadDatabase.getInstance(context).likedDao()

    val allLiked: LiveData<List<LikedEntity>> = dao.getAllLive()

    suspend fun like(entity: LikedEntity) = dao.upsert(entity)

    suspend fun unlike(videoId: String) = dao.delete(videoId)
}
