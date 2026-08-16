package com.cobalt.android.shorts.source

import com.cobalt.android.shorts.model.ShortItem

/**
 * One backend capable of producing a page of [ShortItem]s. Each implementation
 * is independently failable — a source throwing must never take down the
 * merged feed, only shrink it for that refresh (see
 * `ShortsFeedRepository.fetchMergedPage`, which catches per-source).
 */
interface ShortsSource {
    val type: com.cobalt.android.shorts.model.ShortsSourceType

    /**
     * Returns up to [count] fresh items. Implementations should avoid
     * returning items already seen this session where practical (e.g. by
     * paging), but the repository also de-dupes by videoId as a backstop.
     */
    suspend fun fetchShorts(count: Int): List<ShortItem>
}
