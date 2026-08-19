package com.cobalt.android.remoteconfig

import android.content.Context
import com.cobalt.android.util.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches the active cobalt instance URL from a small, public JSON file
 * committed to this project's own GitHub repo (`remote-config.json` at
 * the repo root, served via `raw.githubusercontent.com`) instead of a
 * per-device Settings field. There is no separate backend service behind
 * this — the "dashboard" is `remote-config.json` itself: edit it directly
 * in GitHub's UI, or push a change some other way (the
 * `.github/workflows/update-remote-config.yml` workflow this same change
 * adds lets that "some other way" be a repo variable instead of a manual
 * edit) — and every installed client picks up the new URL the next time
 * it resolves a link, no Play Store release needed.
 *
 * SECURITY NOTE, read before adding anything else to `remote-config.json`
 * or this file: that JSON file lives in a **public** repo — anyone can
 * `curl` it, no auth. Never put the cobalt API key (or any other secret)
 * in it. `SettingsRepository.cobaltApiKey` stays a per-device, user-
 * entered Settings field for exactly this reason — the instance URL alone
 * isn't sensitive (it's just an endpoint address), but a key is.
 *
 * Never lets a slow/unreachable GitHub raw-content fetch block a resolve
 * indefinitely: short timeouts, and a fallback chain of (1) the last
 * successfully fetched URL, cached on-device via
 * [SettingsRepository.cachedRemoteCobaltUrl], then (2) the public
 * `cobalt.tools` default — so a first-ever launch with no network, or any
 * later launch where GitHub is briefly unreachable, still resolves
 * against *something* real instead of failing outright.
 */
class RemoteConfigRepository(context: Context) {

    private val settings = SettingsRepository(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // In-memory freshness window so a burst of resolves in one session
    // (e.g. the user pastes several links in a row) doesn't re-fetch
    // remote-config.json on every single one — same reasoning as
    // LinkResolverRepository's own CACHE_FRESHNESS_MILLIS, just for a
    // different piece of data. Not persisted; resets on process death,
    // which is fine since the on-disk fallback (cachedRemoteCobaltUrl)
    // covers that case already.
    @Volatile private var inMemoryUrl: String? = null
    @Volatile private var inMemoryFetchedAtMillis = 0L

    suspend fun getCobaltInstanceUrl(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val memoized = inMemoryUrl
        if (memoized != null && now - inMemoryFetchedAtMillis < IN_MEMORY_FRESHNESS_MILLIS) {
            return@withContext memoized
        }

        val fetched = runCatching { fetchRemoteUrl() }.getOrNull()
        val resolved = if (fetched != null) {
            settings.cachedRemoteCobaltUrl = fetched
            fetched
        } else {
            settings.cachedRemoteCobaltUrl.ifBlank { DEFAULT_INSTANCE_URL }
        }

        inMemoryUrl = resolved
        inMemoryFetchedAtMillis = now
        resolved
    }

    private fun fetchRemoteUrl(): String? {
        val request = Request.Builder().url(CONFIG_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val url = JSONObject(body).optString("cobaltInstanceUrl").trim()
            return url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }
    }

    companion object {
        private const val CONFIG_URL =
            "https://raw.githubusercontent.com/Zapier-codes/cobalt-android/master/remote-config.json"
        private val IN_MEMORY_FRESHNESS_MILLIS = TimeUnit.MINUTES.toMillis(5)

        /** Same default the old per-device Settings field used. */
        const val DEFAULT_INSTANCE_URL = "https://cobalt.tools"
    }
}
