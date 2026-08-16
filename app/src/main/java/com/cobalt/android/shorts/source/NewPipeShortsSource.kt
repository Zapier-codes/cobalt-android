package com.cobalt.android.shorts.source

import com.cobalt.android.shorts.model.ShortItem
import com.cobalt.android.shorts.model.ShortsSourceType
import com.cobalt.android.shorts.model.StreamKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Uses NewPipeExtractor's YouTube service. NewPipeExtractor does not expose a
 * dedicated "Shorts" kiosk (verified against upstream — YouTube's
 * NewPipeExtractor service registers exactly one kiosk, "Trending"; see
 * `YoutubeService.getKioskList()` in TeamNewPipe/NewPipeExtractor), so a
 * kiosk alone caps this source at whatever fraction of one Trending page
 * happens to be short. To actually populate the feed, this source instead
 * runs `getSearchExtractor` against a rotating pool of query terms
 * ([ShortsQueryFeeder]) — search returns a far larger, more varied pool of
 * candidates than the single Trending list — and keeps only results whose
 * duration is Shorts-length. The Trending kiosk is kept as a small secondary
 * top-up per fetch, not the primary source anymore.
 *
 * The <=90s duration filter is the actual "is this a Short" signal here,
 * since NewPipeExtractor's `StreamInfoItem` has no `isShort` flag to key off.
 */
class NewPipeShortsSource : ShortsSource {

    override val type = ShortsSourceType.NEWPIPE

    override suspend fun fetchShorts(count: Int): List<ShortItem> = withContext(Dispatchers.IO) {
        NewPipeInit.ensureInitialized()

        val candidates = LinkedHashMap<String, StreamInfoItem>() // keyed by url, de-dupes across queries

        // Primary: search across several rotating query terms.
        val queries = ShortsQueryFeeder.nextQueries(QUERIES_PER_FETCH)
        for (query in queries) {
            if (candidates.size >= count * 2) break
            searchShortsCandidates(query).forEach { candidates.putIfAbsent(it.url, it) }
        }

        // Secondary top-up: whatever short-length items are on the current
        // Trending page, in case a slow news day means search alone comes up
        // short.
        if (candidates.size < count * 2) {
            runCatching { trendingShortsCandidates() }.getOrElse { emptyList() }
                .forEach { candidates.putIfAbsent(it.url, it) }
        }

        val results = mutableListOf<ShortItem>()
        for (item in candidates.values) {
            if (results.size >= count) break
            resolve(item)?.let { results.add(it) }
        }
        results
    }

    private fun searchShortsCandidates(query: String): List<StreamInfoItem> = runCatching {
        val youtube = ServiceList.YouTube
        val extractor = youtube.getSearchExtractor(query, emptyList(), "")
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .filter { it.duration in 1..90 }
    }.getOrDefault(emptyList())

    private fun trendingShortsCandidates(): List<StreamInfoItem> {
        val youtube = ServiceList.YouTube
        val trending = youtube.kioskList.defaultKioskExtractor
        trending.fetchPage()
        return trending.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .filter { it.duration in 1..90 }
    }

    private fun resolve(item: StreamInfoItem): ShortItem? = runCatching {
        val info = StreamInfo.getInfo(item.url)
        val videoId = extractVideoId(info.url ?: item.url) ?: return null

        // Prefer a progressive (single-file, audio+video) stream if one
        // exists so the player doesn't need to juggle separate audio/video
        // tracks; otherwise fall back to the best available HLS/DASH stream,
        // which NewPipeExtractor also exposes on StreamInfo.
        val progressive = info.videoStreams
            .filter { !it.isVideoOnly }
            .maxByOrNull { it.getResolution()?.removeSuffix("p")?.toIntOrNull() ?: 0 }

        val (streamUrl, kind) = when {
            progressive != null -> progressive.content to StreamKind.PROGRESSIVE
            !info.hlsUrl.isNullOrBlank() -> info.hlsUrl to StreamKind.HLS
            !info.dashMpdUrl.isNullOrBlank() -> info.dashMpdUrl to StreamKind.DASH
            else -> return null
        }

        ShortItem(
            videoId = videoId,
            title = info.name.orEmpty(),
            authorName = info.uploaderName.orEmpty(),
            streamUrl = streamUrl,
            streamKind = kind,
            thumbnailUrl = info.thumbnails.lastOrNull()?.url
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            durationSeconds = info.duration,
            source = ShortsSourceType.NEWPIPE
        )
    }.getOrNull()

    private fun extractVideoId(url: String): String? =
        Regex("[?&]v=([\\w-]{11})").find(url)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([\\w-]{11})").find(url)?.groupValues?.get(1)
            ?: Regex("shorts/([\\w-]{11})").find(url)?.groupValues?.get(1)

    companion object {
        private const val QUERIES_PER_FETCH = 3
    }
}
