package com.cobalt.android.link

import android.content.Context
import com.cobalt.android.util.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Phase 4: resolves a user-pasted link into one or more downloadable
 * formats by calling the configured cobalt instance's API directly — a
 * real network call from the start, not a stub (see ARCHITECTURE.md "No
 * stubs, no placeholders"). Replaces the Phase-3-era placeholder version of
 * this file, which used a blocking `execute()` call with no coroutine
 * support and assumed a `{instance}/api/resolve?url=` GET contract that
 * doesn't match any real cobalt instance.
 *
 * API contract assumed (cobalt v7+, github.com/imputnet/cobalt
 * docs/api.md): POST {instance}/, Content-Type + Accept: application/json,
 * body {"url": "<link>"}. The JSON response's `status` field is one of:
 *  - "error"      -> { "error": { "code": "..." } }
 *  - "picker"     -> { "picker": [ { "type": "photo"|"video"|"gif",
 *                     "url": "..." } ], "audio": "..." (optional) }
 *  - "redirect" / "tunnel" / "stream" / "local-processing" -> a single
 *    direct-download link: { "url": "...", "filename": "..." (optional) }
 * Different cobalt instance versions use different status names for the
 * single-direct-link case ("redirect" on older instances, "tunnel" on
 * newer ones) — all four are handled identically here since there's no way
 * to know in advance which generation a given `cobaltInstanceUrl` is
 * running. Not verified against a live instance this session (no network
 * egress to arbitrary hosts in this sandbox) — see HANDOVER.md.
 */
class LinkResolverRepository(context: Context) {

    /** One downloadable option. Phase 6's picker will list these; Phase 6
     * also drives `DownloadService.startHttps(...)` from `url`/`filename`/
     * `mimeType` directly, so these three fields match that call's shape
     * on purpose. */
    data class ResolvedFormat(
        val url: String,
        val filename: String,
        val mimeType: String,
        /** Human-readable label for Phase 6's picker, e.g. "video", "audio", "photo 1". */
        val label: String
    )

    sealed class ResolveResult {
        data class Success(val originalUrl: String, val formats: List<ResolvedFormat>) : ResolveResult()
        data class Error(val message: String) : ResolveResult()
    }

    private val client = OkHttpClient()
    private val settings = SettingsRepository(context)
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun resolve(url: String): ResolveResult = withContext(Dispatchers.IO) {
        val instance = settings.cobaltInstanceUrl.trimEnd('/')
        val requestBody = JSONObject().put("url", url).toString().toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url(instance)
            .header("Accept", "application/json")
            .post(requestBody)
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            return@withContext ResolveResult.Error(
                "Couldn't reach the cobalt instance ($instance). Check your connection or the instance URL in Settings."
            )
        }

        response.use { resp ->
            val bodyString = try {
                resp.body?.string()
            } catch (e: IOException) {
                null
            }
            if (bodyString.isNullOrBlank()) {
                return@withContext ResolveResult.Error("The cobalt instance returned an empty response.")
            }

            val json = try {
                JSONObject(bodyString)
            } catch (e: JSONException) {
                return@withContext ResolveResult.Error("The cobalt instance returned an unreadable response.")
            }

            if (!resp.isSuccessful) {
                val code = json.optJSONObject("error")?.optString("code")
                val message = if (!code.isNullOrBlank()) code else "The cobalt instance rejected this link (HTTP ${resp.code})."
                return@withContext ResolveResult.Error(message)
            }

            parseBody(url, json)
        }
    }

    private fun parseBody(originalUrl: String, json: JSONObject): ResolveResult {
        return when (json.optString("status")) {
            "error" -> {
                val code = json.optJSONObject("error")?.optString("code")
                ResolveResult.Error(
                    if (!code.isNullOrBlank()) code else "The cobalt instance couldn't resolve this link."
                )
            }
            "rate-limit" -> ResolveResult.Error("Rate limited by the cobalt instance. Try again shortly.")
            "picker" -> parsePicker(originalUrl, json)
            "redirect", "tunnel", "stream", "local-processing" -> parseDirect(originalUrl, json)
            else -> ResolveResult.Error(
                "Unexpected response from the cobalt instance (status: \"${json.optString("status")}\")."
            )
        }
    }

    private fun parsePicker(originalUrl: String, json: JSONObject): ResolveResult {
        val pickerArray = json.optJSONArray("picker")
        val formats = mutableListOf<ResolvedFormat>()
        if (pickerArray != null) {
            for (i in 0 until pickerArray.length()) {
                val item = pickerArray.optJSONObject(i) ?: continue
                val itemUrl = item.optString("url")
                if (itemUrl.isBlank()) continue
                val type = item.optString("type", "video")
                val ext = extensionForType(type)
                formats += ResolvedFormat(
                    url = itemUrl,
                    filename = filenameFromUrl(itemUrl, ext),
                    mimeType = mimeTypeForExtension(ext),
                    label = "$type ${formats.size + 1}"
                )
            }
        }
        val audioUrl = json.optString("audio")
        if (audioUrl.isNotBlank()) {
            formats += ResolvedFormat(
                url = audioUrl,
                filename = filenameFromUrl(audioUrl, "mp3"),
                mimeType = "audio/mpeg",
                label = "audio"
            )
        }
        return if (formats.isEmpty()) {
            ResolveResult.Error("The cobalt instance returned no downloadable formats for this link.")
        } else {
            ResolveResult.Success(originalUrl, formats)
        }
    }

    private fun parseDirect(originalUrl: String, json: JSONObject): ResolveResult {
        val directUrl = json.optString("url")
        if (directUrl.isBlank()) {
            return ResolveResult.Error("The cobalt instance didn't include a download link in its response.")
        }
        val explicitFilename = json.optString("filename")
        val filename = if (explicitFilename.isNotBlank()) explicitFilename else filenameFromUrl(directUrl, "mp4")
        val ext = filename.substringAfterLast('.', "mp4")
        return ResolveResult.Success(
            originalUrl,
            listOf(ResolvedFormat(url = directUrl, filename = filename, mimeType = mimeTypeForExtension(ext), label = "video"))
        )
    }

    private fun filenameFromUrl(url: String, fallbackExt: String): String {
        val lastSegment = url.substringAfterLast('/').substringBefore('?')
        return if (lastSegment.isNotBlank() && lastSegment.contains('.')) lastSegment
        else "cobalt_download_${System.currentTimeMillis()}.$fallbackExt"
    }

    private fun extensionForType(type: String): String = when (type) {
        "photo" -> "jpg"
        "gif" -> "gif"
        else -> "mp4"
    }

    private fun mimeTypeForExtension(ext: String): String = when (ext.lowercase()) {
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        else -> "application/octet-stream"
    }
}
