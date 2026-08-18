package com.cobalt.android.shorts

import android.content.Context
import android.util.Log
import com.cobalt.android.shorts.db.ShortsCacheDao
import com.cobalt.android.shorts.db.ShortsDatabase
import com.cobalt.android.shorts.db.toCacheEntity
import com.cobalt.android.shorts.db.toShortItem
import com.cobalt.android.shorts.model.ShortItem
import com.cobalt.android.shorts.model.ShortsSourceType
import com.cobalt.android.shorts.source.InnertubeShortsSource
import com.cobalt.android.shorts.source.InvidiousShortsSource
import com.cobalt.android.shorts.source.NewPipeShortsSource
import com.cobalt.android.shorts.source.PeerTubeShortsSource
import com.cobalt.android.shorts.source.ShortsSource
import com.cobalt.android.util.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Merges Innertube, NewPipeExtractor, and Invidious into one feed so a single
 * source going down (YouTube tightening Innertube, an Invidious instance
 * dying, NewPipeExtractor's parsing breaking on a YouTube layout change)
 * doesn't empty the Shorts tab.
 *
 * Two layers of "the more it feeds it, the more populated the list is":
 * 1. Within each source, candidates come from *search* against a rotating
 *    pool of query terms ([com.cobalt.android.shorts.source.ShortsQueryFeeder]),
 *    not just a single small trending/kiosk/popular list — see the doc
 *    comments on `InnertubeShortsSource`, `NewPipeShortsSource`, and
 *    `InvidiousShortsSource` for why neither NewPipeExtractor nor Invidious
 *    exposes a real Shorts-only endpoint, and how search-plus-duration-filter
 *    fills that gap.
 * 2. Across sources, this repository queries all three in parallel each
 *    refresh, then interleaves one item from Innertube, one from NewPipe,
 *    one from Invidious, repeat ("cyclically merged" / round-robin) — so the
 *    feed doesn't visibly run dry mid-scroll just because one source's page
 *    happened to be shorter than the others', and no single source dominates
 *    the order.
 *
 * A Room cache (`ShortsCacheEntity`) backs the feed: every successful merge
 * is persisted, and if *all three* live sources fail on a given refresh
 * (e.g. no network), the repository falls back to the most recently cached
 * items instead of returning nothing.
 *
 * Phase 15 hardening: see the doc comments on `loadFeed`'s per-source
 * dispatch below (backoff) and on the `SOURCE_TIMEOUT_MS`/
 * `InvidiousShortsSource` timeout constants (why they're set where they
 * are) for what changed and why. Also see HANDOVER.md for a documented,
 * *not yet fixed* pagination-exhaustion finding from this phase's DoD-1
 * verification pass.
 *
 * Phase 16: `loadFeed` now returns [FeedPage] (items + whether they came
 * from the cache fallback) instead of a bare list, so callers can show a
 * "showing cached Shorts" indicator instead of a live feed silently
 * looking identical to a cached one.
 */
class ShortsFeedRepository(
    private val cacheDao: ShortsCacheDao,
    private val sources: List<ShortsSource>
) {
    constructor(context: Context) : this(
        cacheDao = ShortsDatabase.getInstance(context).shortsCacheDao(),
        sources = listOf(
            InnertubeShortsSource(),
            NewPipeShortsSource(),
            // Phase 14: instance pool is user-configurable via
            // SettingsRepository.invidiousInstances, falling back to
            // InvidiousShortsSource.DEFAULT_INSTANCES when unset. Read
            // once here at construction time — see the KDoc on
            // invidiousInstances for the "takes effect on next
            // construction, not live-patched" contract.
            InvidiousShortsSource(instances = SettingsRepository(context).invidiousInstances),
            // New source: PeerTube via SepiaSearch's federated index — see
            // PeerTubeShortsSource's doc comment. Unlike Invidious, this
            // doesn't need a hardcoded/configurable instance list: one
            // federated search index covers ~800+ public instances at once.
            PeerTubeShortsSource()
        )
    )

    /** Phase 15: per-source-type consecutive-failure count and the
     * timestamp before which that source should be skipped entirely
     * rather than retried. Keyed by [ShortsSourceType] rather than by
     * `ShortsSource` instance so backoff state survives this
     * repository's lifetime even though `sources` could theoretically be
     * swapped (it isn't today, but nothing here should assume identity).
     * `ConcurrentHashMap` because `loadFeed`'s per-source coroutines run
     * concurrently via `async` and all read/write this map. */
    private val backoff = ConcurrentHashMap<ShortsSourceType, SourceBackoff>()

    private data class SourceBackoff(val consecutiveFailures: Int, val retryAfterMillis: Long)

    /**
     * Phase 16: pairs a page's items with whether it came from a live
     * merge or the Room cache fallback. [isFromCache] is what
     * `ShortsViewModel`/`ShortsFragment` use to show a "showing cached
     * Shorts" banner instead of a live feed silently looking identical to
     * one — named for what actually happened (a cache fallback), not
     * assumed-cause "offline", since the same fallback path also fires
     * when every source is simultaneously backed off (Phase 15) with
     * network present, not only on a genuinely offline device.
     */
    data class FeedPage(val items: List<ShortItem>, val isFromCache: Boolean)

    /**
     * Fetches a fresh page from all sources, cyclically merges + de-dupes it,
     * persists it to the cache, and returns it. Falls back to cache on total
     * failure. Never throws.
     */
    suspend fun loadFeed(perSourceCount: Int = PER_SOURCE_PAGE_SIZE): FeedPage =
        withContext(Dispatchers.IO) {
            val perSourceResults = coroutineScope {
                sources.map { source ->
                    async {
                        fetchFromSourceWithBackoff(source, perSourceCount)
                    }
                }.awaitAll()
            }

            val merged = interleave(perSourceResults)
            val deduped = dedupeById(merged)

            if (deduped.isNotEmpty()) {
                cacheDao.upsertAll(deduped.map { it.toCacheEntity() })
                cacheDao.evictStale(System.currentTimeMillis() - CACHE_TTL_MS)
                FeedPage(deduped, isFromCache = false)
            } else {
                // All three sources failed, backed off, or returned nothing
                // usable — fall back to whatever's cached rather than an
                // empty feed. `ShortsViewModel.loadMore()` further filters
                // this against IDs already shown, so a cache-fallback that
                // happens to return items already on screen is a correct
                // no-op there, not a duplicate-showing bug.
                FeedPage(
                    items = cacheDao.getRecent(perSourceCount * sources.size).map { it.toShortItem() },
                    isFromCache = true
                )
            }
        }

    /**
     * Phase 15: wraps a single source's fetch with exponential backoff on
     * *repeated* failure (timeout or thrown exception — an empty, non-
     * exceptional result is treated as a legitimate "nothing new right
     * now" outcome, not a failure, since all three sources' aggressive
     * duration/relevance filtering can genuinely yield zero results for a
     * given query batch even when the source itself is healthy).
     *
     * Schedule: 30s, 60s, 120s, ... doubling per consecutive failure,
     * capped at 15 minutes. Resets to no backoff on the next success. This
     * exists so a source that's actually down (dead Invidious instance,
     * Innertube key/version drift causing 403s — see HANDOVER) stops being
     * hit on every single `loadMore()` scroll trigger, which otherwise
     * costs a real network round-trip (and, for Invidious, potentially
     * several — see the per-instance-failover doc comment on
     * `InvidiousShortsSource`) for a call that was never going to succeed.
     */
    private suspend fun fetchFromSourceWithBackoff(source: ShortsSource, count: Int): List<ShortItem> {
        val existing = backoff[source.type]
        if (existing != null && System.currentTimeMillis() < existing.retryAfterMillis) {
            Log.d(TAG, "Skipping ${source.type}, backing off for another " +
                "${existing.retryAfterMillis - System.currentTimeMillis()}ms " +
                "after ${existing.consecutiveFailures} consecutive failures")
            return emptyList()
        }

        val timedOutOrFailed = runCatching {
            withTimeoutOrNull(SOURCE_TIMEOUT_MS) { source.fetchShorts(count) }
        }

        return timedOutOrFailed.fold(
            onSuccess = { result ->
                if (result == null) {
                    Log.w(TAG, "Shorts source ${source.type} timed out")
                    recordFailure(source.type)
                    emptyList()
                } else {
                    // A real response, even an empty one, means the source
                    // is reachable and behaving — clear any prior backoff.
                    backoff.remove(source.type)
                    result
                }
            },
            onFailure = { e ->
                Log.w(TAG, "Shorts source ${source.type} failed: ${e.message}")
                recordFailure(source.type)
                emptyList()
            }
        )
    }

    private fun recordFailure(type: ShortsSourceType) {
        val failures = (backoff[type]?.consecutiveFailures ?: 0) + 1
        val delayMs = (BASE_BACKOFF_MS shl (failures - 1).coerceAtMost(MAX_BACKOFF_SHIFT))
            .coerceAtMost(MAX_BACKOFF_MS)
        backoff[type] = SourceBackoff(failures, System.currentTimeMillis() + delayMs)
        Log.w(TAG, "$type backing off for ${delayMs}ms ($failures consecutive failures)")
    }

    suspend fun setLiked(videoId: String, liked: Boolean) = withContext(Dispatchers.IO) {
        cacheDao.setLiked(videoId, liked)
    }

    private fun interleave(perSource: List<List<ShortItem>>): List<ShortItem> {
        val result = mutableListOf<ShortItem>()
        val maxLen = perSource.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) {
            for (sourceItems in perSource) {
                if (i < sourceItems.size) result.add(sourceItems[i])
            }
        }
        return result
    }

    private fun dedupeById(items: List<ShortItem>): List<ShortItem> {
        val seen = LinkedHashMap<String, ShortItem>()
        for (item in items) seen.putIfAbsent(item.videoId, item)
        return seen.values.toList()
    }

    companion object {
        private const val TAG = "ShortsFeedRepository"
        private const val PER_SOURCE_PAGE_SIZE = 8
        private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(6)

        // Phase 15: raised from the original 12s placeholder. Reasoning
        // (see HANDOVER for the full trace): InvidiousShortsSource fails
        // over across up to 4 instances *sequentially* per query, up to 3
        // queries per fetch — a single slow-but-not-fast-failing instance
        // at the front of that list could plausibly consume most of a 12s
        // budget on its own before failover even reaches a healthy
        // instance. 20s gives real failover a realistic chance to
        // complete for `loadMore()` (a background append, not a blocking
        // initial paint) without stalling the UI indefinitely — it is
        // still a judgment call, not a measurement against live instances
        // (no network egress to them from this sandbox), and is capped
        // well short of that failover's true worst case (see
        // InvidiousShortsSource's own timeout tuning in this same phase).
        private val SOURCE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20)

        private const val BASE_BACKOFF_MS = 30_000L
        private const val MAX_BACKOFF_MS = 15 * 60_000L
        // 2^8 * 30s ≈ 128 minutes, already past MAX_BACKOFF_MS — bounds the
        // shift so the delay calculation itself can't overflow/misbehave
        // on a source that's been down a very long time.
        private const val MAX_BACKOFF_SHIFT = 8
    }
}
