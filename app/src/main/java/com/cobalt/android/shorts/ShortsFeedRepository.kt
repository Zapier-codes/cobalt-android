package com.cobalt.android.shorts

import android.content.Context
import android.util.Log
import com.cobalt.android.shorts.db.ShortsCacheDao
import com.cobalt.android.shorts.db.ShortsDatabase
import com.cobalt.android.shorts.db.toCacheEntity
import com.cobalt.android.shorts.db.toShortItem
import com.cobalt.android.shorts.model.ShortItem
import com.cobalt.android.shorts.source.InnertubeShortsSource
import com.cobalt.android.shorts.source.InvidiousShortsSource
import com.cobalt.android.shorts.source.NewPipeShortsSource
import com.cobalt.android.shorts.source.ShortsSource
import com.cobalt.android.util.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
            InvidiousShortsSource(instances = SettingsRepository(context).invidiousInstances)
        )
    )

    /**
     * Fetches a fresh page from all sources, cyclically merges + de-dupes it,
     * persists it to the cache, and returns it. Falls back to cache on total
     * failure. Never throws.
     */
    suspend fun loadFeed(perSourceCount: Int = PER_SOURCE_PAGE_SIZE): List<ShortItem> =
        withContext(Dispatchers.IO) {
            val perSourceResults = coroutineScope {
                sources.map { source ->
                    async {
                        runCatching {
                            withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                                source.fetchShorts(perSourceCount)
                            } ?: run {
                                Log.w(TAG, "Shorts source ${source.type} timed out")
                                emptyList()
                            }
                        }.getOrElse { e ->
                            Log.w(TAG, "Shorts source ${source.type} failed: ${e.message}")
                            emptyList()
                        }
                    }
                }.awaitAll()
            }

            val merged = interleave(perSourceResults)
            val deduped = dedupeById(merged)

            if (deduped.isNotEmpty()) {
                cacheDao.upsertAll(deduped.map { it.toCacheEntity() })
                cacheDao.evictStale(System.currentTimeMillis() - CACHE_TTL_MS)
                deduped
            } else {
                // All three sources failed or returned nothing usable — fall
                // back to whatever's cached rather than an empty feed.
                cacheDao.getRecent(perSourceCount * sources.size).map { it.toShortItem() }
            }
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
        private val SOURCE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(12)
    }
}
