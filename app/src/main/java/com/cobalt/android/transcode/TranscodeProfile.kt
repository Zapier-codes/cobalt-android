package com.cobalt.android.transcode

/**
 * Phase 20: the target quality/format a user can pick in
 * [com.cobalt.android.ui.downloads.QualitySelectionSheet], independent of
 * whatever format `LinkResolverRepository` (Phase 4) actually resolved from
 * the cobalt instance. `ResolvedFormat` (Phase 4) is what the *source*
 * offers; `TranscodeProfile` is what the user wants to end up with on disk.
 * `FfmpegTranscoder` turns the gap between the two into a real ffmpeg
 * command — this file only describes the ladder, it runs nothing itself.
 *
 * Every profile here is a real, runnable ffmpeg target — none of these are
 * placeholders. "Original / no conversion" is modeled as a null
 * `TranscodeProfile` at the call site (see `ResolutionPickerDialog`/
 * `QualitySelectionSheet`), not as a profile in this list, so a null check
 * is the single source of truth for "skip transcoding entirely" rather than
 * a magic sentinel profile that every consumer has to special-case.
 */
sealed class TranscodeProfile {
    /** Stable id — persisted in [com.cobalt.android.util.SettingsRepository]
     *  as a plain string and round-tripped through [encode]/[decode] for
     *  WorkManager `Data` (which only accepts primitives, not sealed
     *  classes), so this must never change for a shipped profile. */
    abstract val id: String
    abstract val label: String
    /** File extension with no leading dot, e.g. "mp4", "flac". */
    abstract val extension: String
    abstract val mimeType: String

    data class Video(
        override val id: String,
        override val label: String,
        /** Target short-side-independent height; scaling uses
         *  `scale=-2:H` so width is derived to preserve aspect ratio and
         *  stays even (required by yuv420p macroblock alignment). Null
         *  means "remux only, no re-encode" (fast container/codec change,
         *  used by [REMUX_MP4]). */
        val targetHeight: Int?,
        /** "libx264" (H.264/AVC, universally compatible, GPL) or
         *  "libvpx-vp9" (VP9, royalty-free, webm only) or "copy" (remux,
         *  requires targetHeight == null since you can't scale a copied
         *  stream). */
        val videoCodec: String,
        val crf: Int,
        val audioCodec: String,
        val audioBitrateKbps: Int
    ) : TranscodeProfile() {
        override val extension get() = if (videoCodec == "libvpx-vp9") "webm" else "mp4"
        override val mimeType get() = if (videoCodec == "libvpx-vp9") "video/webm" else "video/mp4"
    }

    data class Audio(
        override val id: String,
        override val label: String,
        val audioCodec: String,
        /** Null for lossless codecs (FLAC) — there is no bitrate knob to
         *  set; ffmpeg's flac encoder takes a compression *level* instead
         *  (see [FfmpegTranscoder], `-compression_level`). Non-null for
         *  every lossy codec here (MP3/AAC/Opus/Vorbis), where it maps
         *  straight to `-b:a`. */
        val bitrateKbps: Int?,
        val sampleRateHz: Int = 48_000
    ) : TranscodeProfile() {
        override val extension = when (audioCodec) {
            "flac" -> "flac"
            "libmp3lame" -> "mp3"
            "aac" -> "m4a"
            "libopus" -> "opus"
            "libvorbis" -> "ogg"
            else -> "audio"
        }
        override val mimeType = when (audioCodec) {
            "flac" -> "audio/flac"
            "libmp3lame" -> "audio/mpeg"
            "aac" -> "audio/mp4"
            "libopus" -> "audio/opus"
            "libvorbis" -> "audio/ogg"
            else -> "application/octet-stream"
        }
    }

    fun encode(): String = when (this) {
        is Video -> listOf(
            "v", id, label, targetHeight?.toString() ?: "-", videoCodec,
            crf.toString(), audioCodec, audioBitrateKbps.toString()
        ).joinToString("|")
        is Audio -> listOf(
            "a", id, label, audioCodec, bitrateKbps?.toString() ?: "-", sampleRateHz.toString()
        ).joinToString("|")
    }

    companion object {
        /** Inverse of [encode]. Returns null on any malformed input rather
         *  than throwing — a corrupt persisted/extras string should fall
         *  back to "no conversion", never crash the download path. */
        fun decode(s: String): TranscodeProfile? = runCatching {
            val parts = s.split("|")
            when (parts[0]) {
                "v" -> Video(
                    id = parts[1], label = parts[2],
                    targetHeight = parts[3].takeUnless { it == "-" }?.toInt(),
                    videoCodec = parts[4], crf = parts[5].toInt(),
                    audioCodec = parts[6], audioBitrateKbps = parts[7].toInt()
                )
                "a" -> Audio(
                    id = parts[1], label = parts[2], audioCodec = parts[3],
                    bitrateKbps = parts[4].takeUnless { it == "-" }?.toInt(),
                    sampleRateHz = parts[5].toInt()
                )
                else -> null
            }
        }.getOrNull()

        /**
         * The video ladder. CRF (Constant Rate Factor) is used instead of a
         * fixed bitrate for every re-encoded tier — CRF targets a
         * consistent *perceptual* quality and lets the encoder spend
         * however many bits a given frame actually needs, which is the
         * standard modern approach (fixed two-pass bitrate targeting would
         * need a first analysis pass over the whole file, doubling
         * transcode time, for a quality/size tradeoff CRF already covers
         * well for on-device use). Lower CRF = higher quality/bigger file;
         * 18 is visually-lossless-ish for x264, 28 is the encoder's own
         * default. Audio track is always re-encoded to AAC alongside video
         * (stream-copying audio while re-encoding video is possible but
         * adds a second codec-compatibility axis to test against for
         * limited real benefit at these sizes).
         */
        val ALL_VIDEO: List<Video> = listOf(
            Video("v_2160p", "2160p (4K) · H.264", 2160, "libx264", 18, "aac", 256),
            Video("v_1440p", "1440p (2K) · H.264", 1440, "libx264", 19, "aac", 224),
            Video("v_1080p", "1080p · H.264", 1080, "libx264", 20, "aac", 192),
            Video("v_720p", "720p · H.264", 720, "libx264", 21, "aac", 160),
            Video("v_480p", "480p · H.264", 480, "libx264", 23, "aac", 128),
            Video("v_360p", "360p · H.264", 360, "libx264", 25, "aac", 96),
            Video("v_1080p_vp9", "1080p · VP9/WebM (FOSS, royalty-free)", 1080, "libvpx-vp9", 31, "libopus", 160),
            Video("v_720p_vp9", "720p · VP9/WebM (FOSS, royalty-free)", 720, "libvpx-vp9", 33, "libopus", 128),
            Video("v_remux", "Remux only (no re-encode, fastest)", null, "copy", 0, "copy", 0)
        )

        /**
         * The audio ladder. FLAC is lossless — there is no meaningful
         * "320" for it, that number only applies to lossy codecs, so it's
         * offered here as MP3/AAC at 320kbps instead of mislabeling FLAC
         * with a bitrate it doesn't have. `bitrateKbps = null` on the FLAC
         * entry is what [FfmpegTranscoder] reads to know to emit
         * `-compression_level` instead of `-b:a`.
         */
        val ALL_AUDIO: List<Audio> = listOf(
            Audio("a_flac", "FLAC · Lossless (FOSS)", "flac", null, 48_000),
            Audio("a_mp3_320", "MP3 · 320 kbps", "libmp3lame", 320),
            Audio("a_mp3_256", "MP3 · 256 kbps", "libmp3lame", 256),
            Audio("a_mp3_192", "MP3 · 192 kbps", "libmp3lame", 192),
            Audio("a_mp3_128", "MP3 · 128 kbps", "libmp3lame", 128),
            Audio("a_aac_256", "AAC (M4A) · 256 kbps", "aac", 256),
            Audio("a_aac_128", "AAC (M4A) · 128 kbps", "aac", 128),
            Audio("a_opus_160", "Opus · 160 kbps (FOSS, efficient)", "libopus", 160),
            Audio("a_vorbis_192", "Ogg Vorbis · 192 kbps (FOSS)", "libvorbis", 192)
        )

        fun findVideo(id: String): Video? = ALL_VIDEO.find { it.id == id }
        fun findAudio(id: String): Audio? = ALL_AUDIO.find { it.id == id }
        fun find(id: String): TranscodeProfile? = findVideo(id) ?: findAudio(id)
    }
}
