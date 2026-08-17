package com.cobalt.android.shorts.source

import java.util.concurrent.atomic.AtomicInteger

/**
 * Neither NewPipeExtractor nor Invidious exposes a working "give me Shorts"
 * endpoint (see NewPipeShortsSource/InvidiousShortsSource doc comments for
 * why). The workaround both use: run *search* against a broad, rotating pool
 * of popular/trending-style query terms, then keep only short-duration
 * results. Search returns a far larger and more varied pool than either
 * service's single trending/popular list — the more distinct queries fed in,
 * the more of the Shorts catalog gets surfaced instead of the same handful of
 * trending items every refresh.
 *
 * This is a static seed list, not a live trending-topics feed — there is no
 * such API to pull real trending search terms from without an official
 * YouTube Data API key (explicitly out of scope: this project talks to
 * YouTube only through Innertube/NewPipe/Invidious, never the official API).
 * The categories below are intentionally broad and evergreen so they surface
 * *some* current Shorts regardless of what's actually trending this week.
 * Making this configurable/expandable from Settings is a natural Phase 7
 * follow-up — noted in HANDOVER.
 */
object ShortsQueryFeeder {

    private val DEFAULT_SEED_QUERIES = listOf(
        "shorts", "funny shorts", "trending shorts", "viral video",
        "life hack", "satisfying video", "cooking shorts", "gaming clips",
        "music shorts", "dance trend", "sports highlights", "news shorts",
        "tech shorts", "comedy sketch", "diy shorts", "animal shorts",
        "workout shorts", "travel shorts", "art shorts", "science facts",
        "movie clips", "anime shorts", "fashion shorts", "car shorts",
        "prank video", "motivation shorts", "food review", "study tips",
        "beauty tips", "pet shorts"
    )

    /**
     * Phase 14: the pool `nextQueries` actually rotates through. Starts as
     * [DEFAULT_SEED_QUERIES]; [applyCustomQueries] swaps it. `@Volatile`
     * because this is written from the main thread (Settings save,
     * `CobaltApplication.onCreate`) and read from `Dispatchers.IO` (every
     * `InvidiousShortsSource`/`NewPipeShortsSource` fetch).
     */
    @Volatile
    private var activeQueries: List<String> = DEFAULT_SEED_QUERIES

    private val cursor = AtomicInteger(0)

    /**
     * Swaps the active query pool. An empty list resets to
     * [DEFAULT_SEED_QUERIES] — this is the fallback for
     * `SettingsRepository.customShortsQueries` being unset, so callers
     * don't need their own empty-check before calling this. Resets the
     * rotation cursor too, so a newly-applied pool starts from its own
     * beginning instead of continuing at whatever offset the previous
     * pool's cursor had reached (which could be out of bounds for a
     * shorter custom pool, or just a confusing starting point).
     */
    fun applyCustomQueries(queries: List<String>) {
        activeQueries = queries.ifEmpty { DEFAULT_SEED_QUERIES }
        cursor.set(0)
    }

    /**
     * Returns the next [count] queries in rotation (wrapping around), so
     * repeated calls across refreshes sweep through the whole pool instead
     * of hammering the same 2-3 terms every time.
     */
    fun nextQueries(count: Int): List<String> {
        val pool = activeQueries
        val start = cursor.getAndAdd(count)
        return (0 until count).map { i ->
            pool[(start + i).mod(pool.size)]
        }
    }
}
