package com.cobalt.android

import android.app.Application
import androidx.work.Configuration
import com.cobalt.android.shorts.source.ShortsQueryFeeder
import com.cobalt.android.util.NotificationHelper
import com.cobalt.android.util.SettingsRepository
import com.cobalt.android.util.ThemeApplier

class CobaltApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this).createChannel()
        // Phase 13: apply the persisted theme mode before any Activity is
        // created — AppCompatDelegate.setDefaultNightMode() and
        // DynamicColors.applyToActivitiesIfAvailable() both need to run
        // ahead of the first Activity's onCreate() to take effect on
        // launch without a visible flash/recreate.
        val settings = SettingsRepository(this)
        ThemeApplier.apply(this, settings.themeMode)
        // Phase 14: apply any persisted custom Shorts seed-query pool
        // before the Shorts tab can possibly be opened. An empty stored
        // list is handled inside applyCustomQueries (falls back to the
        // shipped default), so no empty-check is needed here.
        ShortsQueryFeeder.applyCustomQueries(settings.customShortsQueries)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
