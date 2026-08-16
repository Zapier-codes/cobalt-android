package com.cobalt.android.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors

/**
 * Phase 13: single place that turns a persisted [SettingsRepository.ThemeMode]
 * into real `AppCompatDelegate`/`DynamicColors` calls, shared by
 * `CobaltApplication` (applied once at process start, before any Activity
 * exists) and `SettingsFragment` (applied immediately when the user changes
 * the setting, followed by `activity.recreate()`).
 *
 * Known limitation (see ARCHITECTURE.md Phase 13): LIGHT and DARK currently
 * render identically. `AppCompatDelegate.setDefaultNightMode()` only
 * changes anything visually if the app ships a `values-night` resource set
 * distinct from `values` — this app's `colors.xml`/`themes.xml` is a single
 * hardcoded dark palette (`Theme.Cobalt` extends `Theme.Material3.Dark`
 * unconditionally, no `values-night` directory exists). That's a
 * pre-existing gap from Phase 1 (which described "Material You dynamic
 * color" but never actually added a light theme or called
 * `DynamicColors.applyToActivitiesIfAvailable()`), not something this phase
 * introduces. DYNAMIC is the one option that visibly does something today,
 * since dynamic color re-tints `colorPrimary`/`colorSurface` etc. from the
 * device wallpaper on API 31+ regardless of night mode. Giving LIGHT a real
 * distinct appearance means designing and shipping an actual light
 * `values-night`-inverted palette across every screen — real design work,
 * out of scope for a settings-plumbing phase, and left as a follow-up.
 */
object ThemeApplier {
    fun apply(context: Context, mode: SettingsRepository.ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                SettingsRepository.ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                SettingsRepository.ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                SettingsRepository.ThemeMode.DYNAMIC -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        if (mode == SettingsRepository.ThemeMode.DYNAMIC) {
            DynamicColors.applyToActivitiesIfAvailable(context.applicationContext as android.app.Application)
        }
    }
}
