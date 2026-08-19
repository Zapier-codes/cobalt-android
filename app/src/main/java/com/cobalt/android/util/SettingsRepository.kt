package com.cobalt.android.util

import android.content.Context
import com.cobalt.android.shorts.source.InvidiousShortsSource

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE)

    /**
     * Phase 12: the app's own cobalt integration (see
     * `LinkResolverRepository.ResolvedFormat`) never exposes numeric
     * quality tiers (1080p/720p/...) — the cobalt API contract this app
     * talks to only returns a format *type* (video / audio / photo) per
     * `picker` entry, no resolution ladder. ARCHITECTURE.md's Phase 12
     * spec calls this key "default download resolution"; it's persisted
     * here as a format-type preference instead, since that's the only
     * axis of choice that actually exists in `ResolveResult.Success`.
     * Phase 14 is responsible for reading this to pre-select in
     * `ResolutionPickerDialog` (Phase 6) — not wired yet.
     */
    enum class DownloadFormatPreference { ASK, VIDEO, AUDIO }

    /**
     * Phase 12/13: single 3-way theme setting. LIGHT/DARK map straight to
     * `AppCompatDelegate`'s night mode; DYNAMIC follows the system's
     * light/dark setting *and* turns on Material You dynamic color via
     * `DynamicColors.applyToActivitiesIfAvailable()` (API 31+; a no-op
     * below that). See CobaltApplication for where this is read and
     * ARCHITECTURE.md's Phase 13 entry for a known limitation: LIGHT
     * currently looks identical to DARK, because `colors.xml` is a single
     * hardcoded dark palette with no `values-night` counterpart — a
     * pre-existing Phase 1 gap this phase surfaces but doesn't fix.
     */
    enum class ThemeMode { LIGHT, DARK, DYNAMIC }

    /**
     * Internal cache only — NOT a user-facing setting anymore. Holds the
     * last cobalt instance URL [com.cobalt.android.remoteconfig.RemoteConfigRepository]
     * successfully fetched from `remote-config.json`, so a later launch
     * with GitHub briefly unreachable still has something better than the
     * hardcoded default to fall back to. See that class's doc comment for
     * the full fallback chain. Not shown or editable in Settings — the
     * instance URL is centrally managed now, not per-device.
     */
    var cachedRemoteCobaltUrl: String
        get() = prefs.getString("cobalt_url", "") ?: ""
        set(v) { prefs.edit().putString("cobalt_url", v).apply() }

    /**
     * Optional. cobalt's real auth scheme (github.com/imputnet/cobalt,
     * docs/api.md) is `Authorization: Api-Key <value>` on every request —
     * only required if the configured instance (see
     * `RemoteConfigRepository.getCobaltInstanceUrl()`) has
     * API-key auth turned on server-side; the public cobalt.tools default
     * doesn't. Blank means "send no Authorization header", which
     * [LinkResolverRepository] treats as the no-auth-required case.
     */
    var cobaltApiKey: String
        get() = prefs.getString("cobalt_api_key", "") ?: ""
        set(v) { prefs.edit().putString("cobalt_api_key", v.trim()).apply() }

    var audioOnlyMode: Boolean
        get() = prefs.getBoolean("audio_only", false)
        set(v) { prefs.edit().putBoolean("audio_only", v).apply() }

    var clipboardTriggerEnabled: Boolean
        get() = prefs.getBoolean("clipboard_trigger", true)
        set(v) { prefs.edit().putBoolean("clipboard_trigger", v).apply() }

    var firstLaunchDone: Boolean
        get() = prefs.getBoolean("first_launch_done", false)
        set(v) { prefs.edit().putBoolean("first_launch_done", v).apply() }

    /** See [DownloadFormatPreference] KDoc above for the "resolution" naming gap. */
    var defaultDownloadFormat: DownloadFormatPreference
        get() = runCatching {
            DownloadFormatPreference.valueOf(
                prefs.getString("default_download_format", DownloadFormatPreference.ASK.name)
                    ?: DownloadFormatPreference.ASK.name
            )
        }.getOrDefault(DownloadFormatPreference.ASK)
        set(v) { prefs.edit().putString("default_download_format", v.name).apply() }

    /**
     * Relative path under the public Downloads collection. Defaults to the
     * same value `MediaStoreWriter` currently hardcodes ("Download/Cobalt"),
     * so persisting this key doesn't change existing behavior until a
     * later phase reads it from `MediaStoreWriter.open()` instead of the
     * literal.
     */
    var downloadLocation: String
        get() = prefs.getString("download_location", DEFAULT_DOWNLOAD_LOCATION) ?: DEFAULT_DOWNLOAD_LOCATION
        set(v) {
            val cleaned = v.trim().trim('/').ifBlank { DEFAULT_DOWNLOAD_LOCATION }
            prefs.edit().putString("download_location", cleaned).apply()
        }

    /** See [ThemeMode] KDoc above. Defaults to DYNAMIC. */
    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.DYNAMIC.name) ?: ThemeMode.DYNAMIC.name)
        }.getOrDefault(ThemeMode.DYNAMIC)
        set(v) { prefs.edit().putString("theme_mode", v.name).apply() }

    /**
     * Phase 14: Invidious instance pool
     * ([InvidiousShortsSource.DEFAULT_INSTANCES] is the shipped default,
     * used whenever this key is unset or ends up empty after cleaning).
     * Persisted newline-separated so a multi-line EditText in Settings can
     * round-trip it directly with no extra parsing UI. Read once at
     * `ShortsFeedRepository`'s `constructor(context)` — same "takes effect
     * next time the feed/repository is (re)constructed, not live-patched
     * into an already-running feed" contract [downloadLocation] documents
     * above for `MediaStoreWriter`.
     */
    var invidiousInstances: List<String>
        get() = prefs.getString("invidious_instances", null)
            ?.split("\n")
            ?.map { it.trim().trimEnd('/') }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: InvidiousShortsSource.DEFAULT_INSTANCES
        set(v) {
            val cleaned = v.map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }
            prefs.edit().putString("invidious_instances", cleaned.joinToString("\n")).apply()
        }

    /**
     * Phase 14 (the spec's optional-but-recommended half): user-editable
     * seed-query pool for
     * [com.cobalt.android.shorts.source.ShortsQueryFeeder]. Empty means
     * "use the shipped default pool" — same null-coalescing shape as
     * [invidiousInstances], except the empty-list fallback lives in
     * `ShortsQueryFeeder.applyCustomQueries` itself rather than here, so
     * this getter can just return what's stored (possibly empty).
     * Applied at process start (`CobaltApplication`) and again immediately
     * on save from Settings, so a change is visible on the very next feed
     * refresh without requiring an app restart.
     */
    var customShortsQueries: List<String>
        get() = prefs.getString("custom_shorts_queries", null)
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(v) {
            val cleaned = v.map { it.trim() }.filter { it.isNotBlank() }
            prefs.edit().putString("custom_shorts_queries", cleaned.joinToString("\n")).apply()
        }

    companion object {
        const val DEFAULT_DOWNLOAD_LOCATION = "Download/Cobalt"
    }
}
