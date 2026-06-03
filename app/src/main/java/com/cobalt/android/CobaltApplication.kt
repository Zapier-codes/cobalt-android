package com.cobalt.android

import android.app.Application
import androidx.work.Configuration
import com.cobalt.android.util.NotificationHelper

class CobaltApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this).createChannel()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
