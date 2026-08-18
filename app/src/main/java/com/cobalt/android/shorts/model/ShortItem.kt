package com.cobalt.android.shorts.model

/**
 * One playable short-form video, normalized from whichever backend produced it
 * (direct Innertube call, NewPipeExtractor, or a public Invidious instance).
 *
 * [videoId] is always the raw YouTube video ID (11-char) so items from all
 * three sources can be de-duplicated against each other before merging.
 */
data class ShortItem(
    val videoId: String,
    val title: String,
    val authorName: String,
    /** Direct, immediately playable URL — progressive, HLS, or DASH manifest. */
    val streamUrl: String,
    val streamKind: StreamKind,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val watchUrl: String = "https://www.youtube.com/watch?v=$videoId",
    val source: ShortsSourceType,
    var isLiked: Boolean = false
)

enum class StreamKind { PROGRESSIVE, HLS, DASH }

enum class ShortsSourceType { INNERTUBE, NEWPIPE, INVIDIOUS, PEERTUBE, CACHE }
