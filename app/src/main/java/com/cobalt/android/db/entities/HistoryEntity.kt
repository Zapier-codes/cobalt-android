package com.cobalt.android.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What kind of thing this history row records. History (per Phase 10/11)
 * covers two distinct surfaces: watching a Shorts item and completing a
 * download — kept as one table (rather than two) so `HistoryFragment`
 * (Phase 11) can show one merged, most-recent-first timeline instead of
 * stitching two queries together.
 */
enum class HistoryItemType { SHORT_WATCH, DOWNLOAD }

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [HistoryItemType.name] — stored as a plain String, same convention
     *  as `ShortsCacheEntity.streamKind`/`.source`, no TypeConverter needed. */
    val itemType: String,
    /** `ShortItem.videoId` for SHORT_WATCH, `DownloadRecord.id.toString()`
     *  for DOWNLOAD — lets Phase 10/11 join back to the source row if needed. */
    val refId: String,
    val title: String,
    val thumbnailUrl: String = "",
    /** `ShortItem.watchUrl` for SHORT_WATCH, `DownloadRecord.originalUrl`
     *  for DOWNLOAD. */
    val sourceUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
