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
 * Phase 20: wraps `ffmpeg-kit` (see ARCHITECTURE.md Phase 20, and the
 * in-file KDoc on [DEPENDENCY_NOTE] below, for the full dependency
 * history — the package has changed twice since Phase 20 first landed,
 * for two different real reasons, not once). Currently
 * `io.github.jamaismagic.ffmpeg:ffmpeg-kit-lts-full-gpl-16kb`, a
 * from-source fork of the now-retired `com.arthenica:ffmpeg-kit-full-gpl`
 * that keeps the same `com.arthenica.ffmpegkit.*` Java API — read
 * [DEPENDENCY_NOTE] before touching this file or `app/build.gradle.kts`'s
 * ffmpeg-kit line, especially before assuming a same-named class in some
 * other fork's crash log proves that fork exposes the same public API.
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
         * FIRST FIX ATTEMPT (WRONG — kept here as a warning, not a
         * recommendation): a prior session swapped to
         * `com.antonkarpenko:ffmpeg-kit-full-gpl`, reasoning that a crash
         * log from that fork's issue tracker referencing
         * `com.arthenica.ffmpegkit.NativeLoader`/`FFmpegKitConfig` proved
         * the original Java API survived. It didn't hold up: CI still
         * failed, now with `Unresolved reference: arthenica` on every
         * import in this file (`FFmpegKit`, `FFmpegKitConfig`,
         * `FFmpegSession`, `ReturnCode`, `Statistics` — all of them). The
         * dependency itself resolved fine (native `.so` files packaged
         * into the APK without error) — the AAR just doesn't expose these
         * classes for direct Kotlin/Java import. Root cause, confirmed via
         * that fork's own listing (pub.dev/packages/ffmpeg_kit_flutter_new,
         * repo `sk3llo/ffmpeg-kit-flutter`): it's a **Flutter plugin**,
         * built to be consumed through Flutter's Dart bridge, not as a
         * plain Android library — the crash log's class references don't
         * mean the *public* Java wrapper API this file needs is exported
         * the same way. Lesson for next time: a matching package name in
         * someone else's stack trace is not proof of a matching public
         * API surface — check what the artifact is actually *for*
         * (Flutter plugin vs. plain Android library) before trusting that.
         *
         * ACTUAL FIX: swapped to
         * `io.github.jamaismagic.ffmpeg:ffmpeg-kit-lts-full-gpl-16kb:6.1.7`
         * — `JamaisMagic/ffmpeg-kit-16KB`, a fork of the real
         * `arthenica/ffmpeg-kit` source tree (not a Flutter plugin wrapper
         * around it), rebuilt for Android 16KB page-size compatibility
         * (a real, separate requirement — see developer.android.com/guide/
         * practices/page-sizes — not related to the retirement above).
         * Verified before pinning:
         *   1. Its `android/README.md` is the **unmodified original**
         *      arthenica Android docs — same `import
         *      com.arthenica.ffmpegkit.FFmpegKit;`, same `FFmpegKit`/
         *      `FFmpegKitConfig`/`FFmpegSession`/`ReturnCode`/`Statistics`
         *      API this file already calls. This is a genuine drop-in:
         *      zero import changes needed, unlike the antonkarpenko dead
         *      end above.
         *   2. `full-gpl` is a real, separately-published artifact under
         *      this group (`ffmpeg-kit-lts-full-gpl-16kb`, distinct from
         *      `ffmpeg-kit-lts-16kb`/`ffmpeg-kit-lts-min-16kb`/etc.) —
         *      confirmed against Maven Central's own listing for the
         *      group, not assumed from the plain `ffmpeg-kit-lts-16kb`
         *      artifact's LGPL-only license metadata (which does NOT
         *      include x264/x265 — picking that one instead would have
         *      silently dropped `TranscodeProfile.ALL_VIDEO`'s MP4 tiers,
         *      the same class of mistake the naming note below describes).
         *   3. `lts` (not `main`) chosen for wider device compatibility
         *      (API 16+ vs Main's API 24+) — matches this project's own
         *      `minSdk`; revisit if that ever changes.
         *
         * VERSION NOTE — CORRECTED (was a guess, now confirmed): the
         * artifact/name choice above (`ffmpeg-kit-lts-full-gpl-16kb`) was
         * always right — CI's `Could not find
         * io.github.jamaismagic.ffmpeg:ffmpeg-kit-lts-full-gpl-16kb:6.1.7`
         * error was about the *version* pinned, not the artifact name. The
         * prior version, `6.1.7`, was inferred from a sibling artifact in
         * this group (`ffmpeg-kit-lts-16kb`, which genuinely is at 6.1.7)
         * rather than read off this specific artifact's own page — stated
         * honestly in that note at the time, and it turned out to matter:
         * different artifacts in the same Maven group are not guaranteed to
         * share a version number, and here they don't.
         *
         * Fetched `ffmpeg-kit-lts-full-gpl-16kb`'s own mvnrepository.com
         * page directly (not a search snippet, not a sibling artifact's
         * page) and confirmed it has exactly **one** published version:
         * **6.1.4** (Feb 27, 2026) — now what's pinned above. Also
         * independently re-confirmed the license/codec content this
         * artifact needs to provide: `FfmpegTranscoder.buildArgs()` above
         * genuinely emits `-c:v libx264` for `TranscodeProfile.Video`
         * entries with `videoCodec == "libx264"`, so an LGPL-only sibling
         * (`ffmpeg-kit-lts-16kb`, or the entirely different
         * `com.moizhassan.ffmpeg:ffmpeg-kit-16kb` package that turned up
         * during this same research pass) would silently fail every MP4
         * tier in `TranscodeProfile.ALL_VIDEO` at runtime with "Unknown
         * encoder 'libx264'" — a `-gpl` package genuinely is required here,
         * not just a naming preference.
         *
         * If a future version bump is needed, fetch
         * https://mvnrepository.com/artifact/io.github.jamaismagic.ffmpeg/ffmpeg-kit-lts-full-gpl-16kb
         * directly and read the version table on *that* page — do not infer
         * a version from `ffmpeg-kit-lts-16kb`, `ffmpeg-kit-main-full-gpl-16kb`,
         * or any other sibling artifact's page, even though they're in the
         * same Maven group. That cross-artifact assumption is exactly what
         * produced this bug.
         *
         * NAMING NOTE (kept from the previous fix attempt as a general
         * caution, even though it no longer applies to the coordinate
         * actually pinned above): a POM's `<url>`/"HomePage" metadata field
         * is not authoritative for whether a repo or artifact is real —
         * verify by fetching the URL directly, not by trusting what a
         * package index displays.
         */
        const val DEPENDENCY_NOTE = "" // anchor for the KDoc above; not read at runtime
    }
}
