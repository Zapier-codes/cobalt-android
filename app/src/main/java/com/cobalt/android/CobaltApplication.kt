package com.cobalt.android

import android.app.Application
import androidx.work.Configuration
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
        ThemeApplier.apply(this, SettingsRepository(this).themeMode)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
