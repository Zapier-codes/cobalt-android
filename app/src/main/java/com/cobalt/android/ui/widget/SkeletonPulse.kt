package com.cobalt.android.ui.widget

import android.animation.ValueAnimator
import android.view.View

/**
 * Phase 17: a minimal, dependency-free "skeleton loading" pulse.
 *
 * No shimmer library was added for this — the project has no such
 * dependency yet, and Phase 17's DoD only calls for a loading placeholder
 * distinct from a bare spinner, not specifically a moving-gradient shimmer.
 * This animates a shared alpha value between [DIM_ALPHA] and full opacity,
 * reversing forever, and applies it to every view passed in — a "pulsing
 * skeleton" rather than a translating highlight band.
 *
 * Caller owns the returned [ValueAnimator]'s lifecycle: call `cancel()` on
 * it (not just hide the views) when the skeleton is dismissed/torn down,
 * or it keeps animating against detached/invisible views. `cancel()` does
 * not reset alpha on its own, so callers should also reset any view they
 * intend to reuse (skeleton views in this codebase are one-shot — hidden
 * for good once real content arrives — so that hasn't been needed yet).
 */
object SkeletonPulse {
    private const val DIM_ALPHA = 0.35f
    private const val DURATION_MS = 700L

    fun start(vararg views: View): ValueAnimator {
        views.forEach { it.alpha = 1f }
        return ValueAnimator.ofFloat(1f, DIM_ALPHA).apply {
            duration = DURATION_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val alpha = anim.animatedValue as Float
                views.forEach { it.alpha = alpha }
            }
            start()
        }
    }
}
