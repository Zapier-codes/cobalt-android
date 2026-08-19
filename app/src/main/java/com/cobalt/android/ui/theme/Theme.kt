package com.cobalt.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Phase 22: cobalt-android's own Compose theme — the foundation every
 * screen migrated off Views lands on. Adapted from a verified real-world
 * pattern (phoenix-boss/echo-music's `ui/theme/Theme.kt`, cloned and read
 * directly this phase — see ARCHITECTURE.md Phase 22 for the full
 * research trail), not copied: echo-music is GPLv3 and this project has
 * no LICENSE file yet (a real open question flagged in HANDOVER.md, not
 * silently resolved by this file), and echo-music's version pulls in
 * `com.materialkolor` (a third-party seed-to-M3-scheme generator) plus
 * per-track album-art palette extraction — both genuinely music-specific
 * (a video app has no per-item "art" to re-theme around the way a now-
 * playing screen does), so neither was pulled in here. What *is* the same
 * verified-real pattern: Android 12+ system dynamic color when available,
 * falling back to a fixed brand-color scheme in the same historical
 * cobalt_* accent (`cobalt_accent_blue`, already used throughout every
 * existing XML screen — see /values/colors.xml) rather than inventing a
 * new brand color no other screen in this app uses.
 */

private val CobaltDarkColorScheme = darkColorScheme(
    primary = Color(0xFF2A7CE1), // cobalt_accent_blue — same brand accent as every existing XML screen
    background = Color(0xFF000000), // cobalt_background
    surface = Color(0xFF191919), // cobalt_surface
    surfaceVariant = Color(0xFF282828), // cobalt_surface_elevated
    onBackground = Color(0xFFE1E1E1), // cobalt_text_primary
    onSurface = Color(0xFFE1E1E1),
    onSurfaceVariant = Color(0xFF818181), // cobalt_text_secondary
    error = Color(0xFFED2236), // cobalt_error_red
    outline = Color(0xFF383838) // cobalt_input_border
)

// Light scheme exists for completeness/system-theme-switching correctness
// (MaterialTheme always needs *a* light scheme even if this app currently
// has no shipped light-mode screens outside Settings' theme picker — see
// ARCHITECTURE.md Phase 13's note on that same historical gap, which this
// file doesn't attempt to resolve).
private val CobaltLightColorScheme = lightColorScheme(
    primary = Color(0xFF2A7CE1),
    error = Color(0xFFED2236)
)

@Composable
fun CobaltTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Every screen this app has shipped so far (Home, Shorts, Downloads,
    // Settings) is a near-pure-black dark surface already
    // (cobalt_background = #000000) — defaulting true keeps a
    // Compose-migrated screen visually matching its still-XML neighbors
    // in the same nav graph, not a mismatched dark-grey-not-black insert.
    pureBlack: Boolean = true,
    // Real system dynamic color (Android 12+/API 31+) when available —
    // same verified condition echo-music's Theme.kt gates on
    // (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S). Below API 31,
    // and for the deliberate default above, this app's own fixed brand
    // scheme is used instead of pulling in `com.materialkolor` just to
    // generate a scheme from a seed color this app already has a
    // designed fixed palette for.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        darkTheme -> CobaltDarkColorScheme
        else -> CobaltLightColorScheme
    }
    val finalScheme = remember(colorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) {
            colorScheme.copy(background = Color.Black, surface = Color.Black)
        } else {
            colorScheme
        }
    }

    MaterialTheme(
        colorScheme = finalScheme,
        content = content
    )
}
