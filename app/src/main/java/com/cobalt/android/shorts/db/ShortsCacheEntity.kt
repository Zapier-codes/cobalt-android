package com.cobalt.android.shorts.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cobalt.android.shorts.model.ShortItem
import com.cobalt.android.shorts.model.ShortsSourceType
import com.cobalt.android.shorts.model.StreamKind

@Entity(tableName = "shorts_cache")
data class ShortsCacheEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val authorName: String,
    val streamUrl: String,
    val streamKind: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val watchUrl: String,
    val source: String,
    val isLiked: Boolean,
    /** epoch millis; used to evict stale cached stream URLs, which expire server-side. */
    val cachedAt: Long
)

fun ShortItem.toCacheEntity(cachedAt: Long = System.currentTimeMillis()) = ShortsCacheEntity(
    videoId = videoId,
    title = title,
    authorName = authorName,
    streamUrl = streamUrl,
    streamKind = streamKind.name,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    watchUrl = watchUrl,
    source = source.name,
    isLiked = isLiked,
    cachedAt = cachedAt
)

fun ShortsCacheEntity.toShortItem() = ShortItem(
    videoId = videoId,
    title = title,
    authorName = authorName,
    streamUrl = streamUrl,
    streamKind = StreamKind.valueOf(streamKind),
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    watchUrl = watchUrl,
    source = ShortsSourceType.valueOf(source),
    isLiked = isLiked
)
