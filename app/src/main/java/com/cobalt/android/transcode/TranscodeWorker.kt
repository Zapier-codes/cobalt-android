package com.cobalt.android.transcode

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.work.*
import com.cobalt.android.download.DownloadRecord
import com.cobalt.android.download.DownloadRepository
import com.cobalt.android.download.DownloadStatus
import com.cobalt.android.download.MediaStoreWriter
import com.cobalt.android.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 20: runs after `DownloadService.processHttps()` finishes fetching
 * the *source* file — this worker never touches the network itself, it
 * only re-encodes a file that is already fully downloaded and
 * MediaStore-finalized. See `DownloadService`'s call to [enqueue] for why
 * this is WorkManager (background, survives process death, doesn't hold
 * the download Service's foreground notification for a multi-minute 4K
 * re-encode) rather than running inline in `DownloadService`.
 *
 * Produces a brand-new [DownloadRecord] row (status COMPLETE on success,
 * FAILED on failure) rather than mutating the source row in place — see
 * `DownloadRecord.sourceDownloadId` KDoc for why. The source row itself is
 * never touched by this worker; it stays COMPLETE with the raw file
 * whether or not the conversion succeeds.
 *
 * Reuses `bytesDownloaded`/`totalBytes` on the *new* row as a 0..100
 * progress percentage (bytesDownloaded = percent, totalBytes = 100) while
 * status == CONVERTING, instead of adding a dedicated progress column —
 * `DownloadAdapter`'s existing progress-bar rendering already reads those
 * two fields for DOWNLOADING, so CONVERTING reusing them means the queue
 * UI needs only a label change (see `DownloadAdapter`), not new binding
 * logic, consistent with ARCHITECTURE.md's "unified enhancement, not a
 * parallel rebuild" rule.
 */
class TranscodeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val sourceRecordId = inputData.getLong(KEY_SOURCE_RECORD_ID, -1L)
        val sourceUriString = inputData.getString(KEY_SOURCE_URI) ?: return Result.failure()
        val profile = inputData.getString(KEY_PROFILE)?.let { TranscodeProfile.decode(it) }
            ?: return Result.failure()

        val repository = DownloadRepository(applicationContext)
        val notificationHelper = NotificationHelper(applicationContext)
        val mediaStoreWriter = MediaStoreWriter(applicationContext)
        val transcoder = FfmpegTranscoder(applicationContext)

        val sourceRecord = repository.getById(sourceRecordId)
        val sourceUri = Uri.parse(sourceUriString)

        val baseName = (sourceRecord?.filename ?: "download")
            .substringBeforeLast('.').ifBlank { "download" }
        val outputFilename = "$baseName.${profile.extension}"

        val outputRow = DownloadRecord(
            originalUrl = sourceRecord?.originalUrl ?: "",
            cobaltUrl = "",
            filename = outputFilename,
            mimeType = profile.mimeType,
            status = DownloadStatus.CONVERTING,
            sourceDownloadId = sourceRecordId,
            transcodeProfileLabel = profile.label
        )
        val outputRowId = repository.insert(outputRow)

        val opened = mediaStoreWriter.open(outputFilename, profile.mimeType)
        if (opened == null) {
            repository.updateStatus(outputRowId, DownloadStatus.FAILED)
            notificationHelper.showFailed(outputRowId, outputFilename)
            return Result.failure()
        }
        // FFmpegKit needs a real writable fd (via its SAF bridge) rather
        // than the OutputStream MediaStoreWriter.open() also returns —
        // close that stream immediately so nothing else holds the fd open
        // while ffmpeg writes through its own descriptor to the same Uri.
        opened.stream.close()

        val durationMs = probeDurationMs(sourceUri)

        return try {
            val result = transcoder.transcode(
                inputUri = sourceUri,
                outputUri = opened.uri,
                profile = profile,
                totalDurationMs = durationMs
            ) { percent ->
                // This lambda is FFmpegKit's own statistics-callback thread,
                // not part of doWork()'s coroutine — onProgress is plain
                // (Int) -> Unit (see FfmpegTranscoder.transcode, driven by
                // FFmpegKit's own native callback, not a suspend context),
                // so updateTranscodeProgress (a suspend Room DAO call) can't
                // be called directly here. runBlocking is deliberate, not a
                // shortcut: it keeps writes in the same order FFmpegKit
                // emits them (matters for a percent value — a stray
                // out-of-order write would show progress jumping backward),
                // and only blocks FFmpegKit's own background thread, never
                // the Worker's or the main thread.
                kotlinx.coroutines.runBlocking {
                    repository.updateTranscodeProgress(outputRowId, percent.toLong())
                }
                notificationHelper.updateProgress(outputRowId, percent, 100, label = "Converting…")
            }

            if (result.success) {
                mediaStoreWriter.finalize(opened.uri)
                repository.updateMediaStoreUri(outputRowId, opened.uri.toString())
                repository.updateStatus(outputRowId, DownloadStatus.COMPLETE)
                notificationHelper.showComplete(outputRowId, outputFilename, opened.uri, profile.mimeType)
                Result.success()
            } else {
                Log.e(TAG, "Transcode failed for row $outputRowId: ${result.failureReason}")
                mediaStoreWriter.delete(opened.uri)
                repository.updateStatus(outputRowId, DownloadStatus.FAILED)
                notificationHelper.showFailed(outputRowId, outputFilename)
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcode threw for row $outputRowId", e)
            mediaStoreWriter.delete(opened.uri)
            repository.updateStatus(outputRowId, DownloadStatus.FAILED)
            notificationHelper.showFailed(outputRowId, outputFilename)
            Result.failure()
        }
    }

    /** Best-effort — a source file that MediaMetadataRetriever can't probe
     *  (rare, but some audio-only cobalt tunnels serve containers it
     *  doesn't recognize) still transcodes fine; it just can't show a
     *  percentage, only an indeterminate "Converting…" state, mirroring
     *  how DownloadAdapter already falls back to indeterminate when
     *  totalBytes <= 0 for an ordinary download. */
    private suspend fun probeDurationMs(uri: Uri): Long = withContext(Dispatchers.IO) {
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(applicationContext, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
        }.getOrDefault(0L)
    }

    private inline fun <T : MediaMetadataRetriever, R> T.use(block: (T) -> R): R =
        try { block(this) } finally { release() }

    companion object {
        private const val TAG = "TranscodeWorker"
        private const val KEY_SOURCE_RECORD_ID = "sourceRecordId"
        private const val KEY_SOURCE_URI = "sourceUri"
        private const val KEY_PROFILE = "profile"

        fun enqueue(ctx: Context, sourceRecordId: Long, sourceUri: Uri, profile: TranscodeProfile) {
            val data = workDataOf(
                KEY_SOURCE_RECORD_ID to sourceRecordId,
                KEY_SOURCE_URI to sourceUri.toString(),
                KEY_PROFILE to profile.encode()
            )
            val request = OneTimeWorkRequestBuilder<TranscodeWorker>()
                .setInputData(data)
                // No NetworkType constraint, unlike RetryDownloadWorker —
                // transcoding is entirely local (SAF read/write against a
                // file already on disk), it has no network dependency.
                .addTag("transcode_$sourceRecordId")
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "transcode_$sourceRecordId",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
