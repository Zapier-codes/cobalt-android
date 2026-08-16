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

    private val SEED_QUERIES = listOf(
        "shorts", "funny shorts", "trending shorts", "viral video",
        "life hack", "satisfying video", "cooking shorts", "gaming clips",
        "music shorts", "dance trend", "sports highlights", "news shorts",
        "tech shorts", "comedy sketch", "diy shorts", "animal shorts",
        "workout shorts", "travel shorts", "art shorts", "science facts",
        "movie clips", "anime shorts", "fashion shorts", "car shorts",
        "prank video", "motivation shorts", "food review", "study tips",
        "beauty tips", "pet shorts"
    )

    private val cursor = AtomicInteger(0)

    /**
     * Returns the next [count] queries in rotation (wrapping around), so
     * repeated calls across refreshes sweep through the whole pool instead
     * of hammering the same 2-3 terms every time.
     */
    fun nextQueries(count: Int): List<String> {
        val start = cursor.getAndAdd(count)
        return (0 until count).map { i ->
            SEED_QUERIES[(start + i).mod(SEED_QUERIES.size)]
        }
    }
}
