package com.cobalt.android.link

import android.content.Context
import com.cobalt.android.util.SettingsRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Minimal repository that resolves a user‑provided hyperlink into a downloadable media link.
 * It contacts the Cobalt instance URL stored in SettingsRepository and expects a JSON
 * response containing `direct_url` and `title` fields. The endpoint is assummed to be
 * `{instance}/api/resolve?url={url}`.
 */
class LinkResolverRepository(context: Context) {
    private val client = OkHttpClient()
    private val apiUrl: String = SettingsRepository(context).cobaltInstanceUrl

    data class Resolved(val directUrl: String, val title: String)

    fun resolve(url: String): Resolved? {
        val escaped = okhttp3.HttpUrl.parse(apiUrl)?.newBuilder()
            ?.addQueryParameter("url", url)
            ?.build()?.toString() ?: return null
        val request = Request.Builder().url(escaped).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)
            val direct = json.optString("direct_url", "")
            val title = json.optString("title", "")
            if (direct.isBlank()) return null
            return Resolved(direct, title)
        }
    }
}
