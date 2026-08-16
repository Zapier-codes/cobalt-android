package com.cobalt.android.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A liked Shorts item, keyed by the same `videoId` `ShortsCacheEntity` and
 * `ShortItem` use. `ShortsCacheEntity.isLiked` (Phase 2) is a cache-local
 * flag that gets evicted along with the rest of the cache row — this table
 * is the durable record, connected in Phase 10 rather than this phase (data-
 * layer only, per this phase's own scope).
 */
@Entity(tableName = "liked")
data class LikedEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val authorName: String,
    val thumbnailUrl: String,
    val watchUrl: String,
    val likedAt: Long = System.currentTimeMillis()
)
