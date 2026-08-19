package com.cobalt.android.transcode

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Phase 20: wraps `ffmpeg-kit` (see ARCHITECTURE.md Phase 20 for the
 * dependency/licensing tradeoff — this is
 * `com.antonkarpenko:ffmpeg-kit-full-gpl`, a community-maintained fork of
 * the now-retired `com.arthenica:ffmpeg-kit-full-gpl` that keeps the same
 * `com.arthenica.ffmpegkit.*` Java API — chosen because it's the only
 * currently-resolvable FFmpeg wrapper for Android with a real prebuilt
 * binary; see the in-file KDoc on [DEPENDENCY_NOTE] below before touching
 * this file).
 *
 * Reads and writes Android `content://` MediaStore URIs directly via
 * FFmpegKit's SAF (Storage Access Framework) bridge —
 * `FFmpegKitConfig.getSafParameterForRead/Write` opens the URI's file
 * descriptor and hands ffmpeg a `saf:<fd>` pseudo-path — instead of first
 * copying the source file into the app's private cache. This works for any
 * scoped-storage MediaStore URI (source and destination both go through
 * [com.cobalt.android.download.MediaStoreWriter]/`ContentResolver`, never a
 * raw filesystem path), so there is no separate "legacy storage" code path
 * to maintain for API < 29 vs >= 29.
 */
class FfmpegTranscoder(private val context: Context) {

    data class Result(val success: Boolean, val failureReason: String?)

    /**
     * Transcodes [inputUri] into [outputUri] per [profile], reporting
     * progress via [onProgress] (0..100, best-effort — see note below).
     * Suspends until the ffmpeg session finishes; safe to call from a
     * `CoroutineWorker.doWork()` (see `TranscodeWorker`).
     *
     * Progress is computed from ffmpeg's own per-frame statistics
     * (processed output duration so far) against [totalDurationMs],
     * which the caller must supply — this class does not itself probe the
     * input file's duration; `TranscodeWorker` does that once via
     * `MediaMetadataRetriever` before calling in, so a single probe is
     * shared across the whole transcode rather than re-probed per
     * statistics callback.
     */
    suspend fun transcode(
        inputUri: Uri,
        outputUri: Uri,
        profile: TranscodeProfile,
        totalDurationMs: Long,
        onProgress: (Int) -> Unit
    ): Result = suspendCancellableCoroutine { cont ->
        val resumed = AtomicBoolean(false)
        val safInput = FFmpegKitConfig.getSafParameterForRead(context, inputUri)
        val safOutput = FFmpegKitConfig.getSafParameterForWrite(context, outputUri)
        val args = buildArgs(safInput, safOutput, profile)

        Log.i(TAG, "ffmpeg ${args.joinToString(" ")}")

        val session: FFmpegSession = FFmpegKit.executeWithArgumentsAsync(
            args.toTypedArray(),
            { completedSession ->
                if (!resumed.compareAndSet(false, true)) return@executeWithArgumentsAsync
                val code = completedSession.returnCode
                val result = if (ReturnCode.isSuccess(code)) {
                    Result(true, null)
                } else if (ReturnCode.isCancel(code)) {
                    Result(false, "Conversion cancelled")
                } else {
                    // getOutput()/getFailStackTrace() are the two real,
                    // documented FFmpegSession accessors for diagnosing a
                    // non-success/non-cancel return code (verified against
                    // ffmpeg-kit's own Android wiki examples — an earlier
                    // draft of this file called a `getAllLogsAsString()`
                    // that isn't part of the documented API and was never
                    // in a build that ran, so it's not used here).
                    // failStackTrace is usually null unless FFmpegKit
                    // itself threw before/around the native call; the
                    // actual "why did the encode fail" text is almost
                    // always in the last non-blank line of console output.
                    val lastOutputLine = completedSession.output
                        ?.trim()?.lines()?.lastOrNull { it.isNotBlank() }
                    val reason = completedSession.failStackTrace ?: lastOutputLine
                        ?: "ffmpeg exited with code $code"
                    Result(false, reason)
                }
                if (cont.isActive) cont.resume(result)
            },
            { /* log callback — full per-line output already captured via completedSession.output on completion */ },
            { statistics: Statistics ->
                if (totalDurationMs > 0) {
                    val pct = ((statistics.time.toDouble() / totalDurationMs) * 100)
                        .toInt().coerceIn(0, 100)
                    onProgress(pct)
                }
            }
        )

        cont.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
        }
    }

    private fun buildArgs(safInput: String, safOutput: String, profile: TranscodeProfile): List<String> {
        val args = mutableListOf("-y", "-i", safInput)
        when (profile) {
            is TranscodeProfile.Video -> {
                args += "-map"; args += "0:v:0"
                args += "-map"; args += "0:a:0?"
                if (profile.videoCodec == "copy") {
                    args += listOf("-c:v", "copy", "-c:a", "copy")
                } else {
                    args += listOf("-c:v", profile.videoCodec)
                    profile.targetHeight?.let { h -> args += listOf("-vf", "scale=-2:$h") }
                    args += listOf("-crf", profile.crf.toString())
                    if (profile.videoCodec == "libx264") {
                        // -pix_fmt yuv420p: guarantees playback on every
                        // stock Android/iOS/desktop player. Without it, an
                        // odd-chroma source (e.g. yuv444p from some
                        // screen-recorded sources) can produce a file that
                        // only plays back correctly in ffplay/VLC.
                        args += listOf("-preset", "medium", "-pix_fmt", "yuv420p")
                    } else if (profile.videoCodec == "libvpx-vp9") {
                        // -b:v 0 tells libvpx-vp9 to run in pure CRF mode
                        // (no bitrate cap) — required, libvpx ignores -crf
                        // alone and falls back to its own default bitrate
                        // targeting without this.
                        args += listOf("-b:v", "0", "-row-mt", "1")
                    }
                    args += listOf("-c:a", profile.audioCodec, "-b:a", "${profile.audioBitrateKbps}k")
                }
                // Deliberately NOT adding "-movflags +faststart" here. It's
                // the usual move for a web-servable MP4 (moves the moov
                // atom to the front so playback can start before the full
                // file downloads), but it requires ffmpeg to seek back
                // through the *output* after writing it — and this app's
                // output is always a SAF pseudo-path (`getSafParameterForWrite`
                // against a MediaStore Uri), which is fd-based and doesn't
                // support that seek-back. Upstream confirms this fails
                // ("Error writing trailer... Bad file descriptor",
                // arthenica/ffmpeg-kit#167) rather than degrading quietly,
                // so it's left out entirely instead of shipping a flag that
                // breaks every SAF-written MP4 this app produces.
            }
            is TranscodeProfile.Audio -> {
                args += "-map"; args += "0:a:0"
                args += "-vn" // strip video/attached-pic streams, audio output only
                args += listOf("-c:a", profile.audioCodec, "-ar", profile.sampleRateHz.toString())
                if (profile.bitrateKbps != null) {
                    args += listOf("-b:a", "${profile.bitrateKbps}k")
                } else if (profile.audioCodec == "flac") {
                    // FLAC has no bitrate knob (lossless) — 8 is libFLAC's
                    // own max compression level (smallest file for the
                    // same lossless content, at the cost of slower
                    // encoding; on-device this is a one-shot background
                    // job so the extra CPU time is an acceptable tradeoff
                    // for smaller output).
                    args += listOf("-compression_level", "8")
                }
            }
        }
        args += listOf("-f", extensionToMuxer(profile.extension), safOutput)
        return args
    }

    /** ffmpeg's `-f` muxer name doesn't always match the file extension
     *  (flac/mp3/ogg/opus do; mp4/webm/m4a need an explicit muxer name). */
    private fun extensionToMuxer(extension: String): String = when (extension) {
        "mp4" -> "mp4"
        "webm" -> "webm"
        "m4a" -> "ipod" // ffmpeg's muxer name for M4A/AAC-in-MP4-container
        "flac" -> "flac"
        "mp3" -> "mp3"
        "opus" -> "opus"
        "ogg" -> "ogg"
        else -> extension
    }

    companion object {
        private const val TAG = "FfmpegTranscoder"

        /**
         * DEPENDENCY NOTE (read before changing the ffmpeg-kit version or
         * package variant in app/build.gradle.kts):
         *
         * `arthenica/ffmpeg-kit` was archived by its maintainer in 2025 —
         * "FFmpegKit has been officially retired," no further releases —
         * and Maven Central removed all `com.arthenica:*` ffmpeg-kit
         * binaries on 2025-04-01. This app originally pinned
         * `com.arthenica:ffmpeg-kit-full-gpl:6.0-2`; CI eventually failed
         * with `Could not find com.arthenica:ffmpeg-kit-full-gpl:6.0-2`,
         * confirming the artifact is genuinely gone, not a transient
         * resolution hiccup.
         *
         * FIXED: swapped to `com.antonkarpenko:ffmpeg-kit-full-gpl:2.2.1`
         * — sk3llo's actively-maintained fork
         * (github.com/sk3llo/ffmpeg-kit-flutter), rebuilt against FFmpeg
         * v8.1.1. Verified before pinning, not assumed:
         *   1. Its Maven POM declares dual GPL-3.0/LGPL-3.0 licensing and
         *      the README explicitly lists x264/x265/vid.stab/xvidcore as
         *      included GPL libraries — confirms this is genuinely the
         *      full-gpl variant (H.264/H.265 encode intact for
         *      `TranscodeProfile.ALL_VIDEO`), not a stripped-down rebrand
         *      that would silently drop those tiers.
         *   2. A real production crash log from this fork's own issue
         *      tracker (sk3llo/ffmpeg_kit_flutter#71) shows a stack trace
         *      running through `com.arthenica.ffmpegkit.NativeLoader` and
         *      `com.arthenica.ffmpegkit.FFmpegKitConfig` — proof the fork
         *      kept the ORIGINAL Java package namespace and only
         *      republished under new Maven coordinates. That's why this
         *      file's imports (`com.arthenica.ffmpegkit.*`, top of file)
         *      needed zero changes for the swap.
         *
         * Real risk, stated plainly: this is still a community-run fork,
         * not an official release — releases have been regular (monthly
         * or better) through mid-2026, but if `ffmpeg-kit-full-gpl:2.2.1`
         * (or whatever version this line is pinned to by the time you
         * read this) stops resolving, check
         * https://central.sonatype.com/artifact/com.antonkarpenko/ffmpeg-kit-full-gpl
         * for the latest published version before assuming the fork is
         * dead — bump the version first, only look for a different
         * replacement if the whole `com.antonkarpenko` group is gone.
         * Building FFmpegKitNext from source (the official retired
         * project's suggested successor, source-only, no prebuilt
         * Android .aar) remains the durable long-term fix but is out of
         * scope without Android NDK access to build/test it.
         */
        const val DEPENDENCY_NOTE = "" // anchor for the KDoc above; not read at runtime
    }
}
