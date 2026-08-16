package com.cobalt.android.download

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 5: caches a resolved link's downloadable formats (Phase 4's
 * `LinkResolverRepository.ResolveResult.Success.formats`) so Phase 6's
 * picker doesn't have to re-resolve on every open of the same link.
 *
 * `formatsJson` is a JSON array serialized manually with org.json (same
 * pattern already used by `LinkResolverRepository`/the Shorts sources)
 * rather than a Room relation table — a resolved link's format list is
 * always read and written as a single unit, never queried per-format, so
 * a relation table would add complexity with no real benefit.
 *
 * Deliberately NOT folded into the existing `downloads` table/
 * `DownloadRecord` — a resolution result and a queued/in-progress download
 * are different lifecycles (a link can be resolved, and re-resolved, many
 * times without ever being downloaded). This *is* added to the existing
 * `DownloadDatabase`, per ARCHITECTURE.md Phase 5 — contrast with Phase 2's
 * `ShortsDatabase`, which is a wholly separate database because there was
 * no existing table for that domain at all; here there's an existing
 * database for the "resolve → download" domain, just not this table.
 */
@Entity(tableName = "resolution_cache")
data class ResolutionCacheEntity(
    /** The exact URL the user pasted/shared — the cache key. */
    @PrimaryKey val originalUrl: String,
    /** JSON array of {"url","filename","mimeType","label"} objects — see
     * `ResolutionCacheDao`'s doc comment for why this isn't a relation
     * table, and `LinkResolverRepository`'s doc comment for the freshness
     * window this is read within (resolved formats can be short-lived
     * signed URLs — this is NOT a "cache forever" table). */
    val formatsJson: String,
    val resolvedAtMillis: Long = System.currentTimeMillis()
)
