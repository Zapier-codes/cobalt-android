package com.cobalt.android.util

import android.content.Context

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

    var cobaltInstanceUrl: String
        get() = prefs.getString("cobalt_url", "https://cobalt.tools") ?: "https://cobalt.tools"
        set(v) { prefs.edit().putString("cobalt_url", v).apply() }

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

    companion object {
        const val DEFAULT_DOWNLOAD_LOCATION = "Download/Cobalt"
    }
}
