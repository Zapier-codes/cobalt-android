package com.cobalt.android.download

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

// Phase 20: CONVERTING is a real, distinct lifecycle state — a row sits
// here while FfmpegTranscoder (via TranscodeWorker) is producing the
// user's chosen quality/format from an already-downloaded source file. It
// is deliberately its own DownloadStatus rather than reusing DOWNLOADING,
// so DownloadQueueSheet's "active" query and DownloadAdapter's rendering
// can tell "still fetching bytes over the network" apart from "re-encoding
// a file already on disk" — they have different failure modes and neither
// should look like the other in the queue UI.
enum class DownloadStatus { QUEUED, DOWNLOADING, CONVERTING, COMPLETE, FAILED, FAILED_NETWORK }

class StatusConverters {
    @TypeConverter fun fromStatus(s: DownloadStatus): String = s.name
    @TypeConverter fun toStatus(s: String): DownloadStatus = DownloadStatus.valueOf(s)
}

@Entity(tableName = "downloads")
@TypeConverters(StatusConverters::class)
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalUrl: String = "",
    val cobaltUrl: String = "",
    val filename: String = "",
    val mimeType: String = "application/octet-stream",
    val cookies: String = "",
    val userAgent: String = "",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val timestamp: Long = System.currentTimeMillis(),
    val isBlobDownload: Boolean = false,
    val tempFilePath: String = "",
    val retryCount: Int = 0,
    val mediaStoreUriString: String = "",
    // Phase 20: 0 means "this row is a plain download, not a transcode
    // output." Non-zero points at the DownloadRecord.id of the raw file
    // FfmpegTranscoder read from. Transcoding always produces a *new* row
    // rather than overwriting the source row in place, so the original
    // download survives even if the conversion fails, and so both the raw
    // file and the converted one remain independently visible/openable in
    // the queue/library.
    val sourceDownloadId: Long = 0L,
    // Empty means "not a transcode output" (mirrors sourceDownloadId == 0L
    // — kept as a separate field rather than deriving one from the other
    // so a transcode row is self-describing without a join back to its
    // source row just to render a label in DownloadAdapter).
    val transcodeProfileLabel: String = ""
)
