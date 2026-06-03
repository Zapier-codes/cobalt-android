package com.cobalt.android.util

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE)

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
}
