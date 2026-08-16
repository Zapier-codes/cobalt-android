package com.cobalt.android.shorts.source

import com.cobalt.android.shorts.model.ShortItem
import com.cobalt.android.shorts.model.ShortsSourceType
import com.cobalt.android.shorts.model.StreamKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to YouTube's internal "Innertube" API directly over OkHttp — the same
 * API youtube.com and the official apps call internally. No YouTube Data API
 * key is required; this uses the same public, unauthenticated client keys the
 * web and Android clients themselves ship with.
 *
 * Candidate discovery, per refresh, combines two calls:
 * 1. `browse` with the WEB client against the home feed (`FEwhat_to_watch`),
 *    where `reelItemRenderer` entries (YouTube's actual internal renderer for
 *    Shorts shelves) appear directly — these are confirmed Shorts, not a
 *    duration guess.
 * 2. `search` with the WEB client, run against a rotating pool of query terms
 *    ([ShortsQueryFeeder]) exactly like [NewPipeShortsSource] and
 *    [InvidiousShortsSource] do — this is what actually drives volume, since
 *    the home-feed Shorts shelf on its own is one small fixed list. Search
 *    results (`videoRenderer`) have no Shorts flag, so those are kept only if
 *    their `lengthText` is <=90s, the same heuristic the other two sources
 *    use.
 *
 * Then, per candidate ID: `player` with the ANDROID client, which — unlike
 * the WEB client — returns progressive `streamingData.formats[]` URLs that
 * are already fully resolved (no JS signature-cipher step needed), because
 * the ANDROID client's player response format predates YouTube's cipher
 * requirement for that client.
 *
 * Maintenance note (see HANDOVER for the fuller version): the WEB_CLIENT_KEY
 * and ANDROID_CLIENT_VERSION below are YouTube-internal values that do drift
 * over time. If this source starts returning empty pages or 403s, that's the
 * first thing to check — search "youtube innertube client version" for the
 * current values used by yt-dlp/NewPipe and update the constants here.
 */
class InnertubeShortsSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : ShortsSource {

    override val type = ShortsSourceType.INNERTUBE

    override suspend fun fetchShorts(count: Int): List<ShortItem> = withContext(Dispatchers.IO) {
        val videoIds = LinkedHashSet<String>()

        // Primary volume driver: search across several rotating query terms.
        for (query in ShortsQueryFeeder.nextQueries(QUERIES_PER_FETCH)) {
            if (videoIds.size >= count * 2) break
            videoIds.addAll(searchShortsVideoIds(query, count * 2 - videoIds.size))
        }

        // Secondary: confirmed Shorts-shelf entries from the home feed —
        // smaller in number but genuinely flagged as Shorts by YouTube
        // itself, so worth mixing in even once search has filled most of the
        // quota.
        if (videoIds.size < count * 2) {
            videoIds.addAll(discoverShelfShortsVideoIds(count * 2 - videoIds.size))
        }

        val results = mutableListOf<ShortItem>()
        for (id in videoIds) {
            if (results.size >= count) break
            resolvePlayableShort(id)?.let { results.add(it) }
        }
        results
    }

    // ── Discovery path A: confirmed Shorts shelf on the home feed ───────────

    private fun discoverShelfShortsVideoIds(limit: Int): List<String> {
        val body = JSONObject().apply {
            put("browseId", "FEwhat_to_watch")
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20240701.00.00")
                })
            })
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/browse?key=$WEB_CLIENT_KEY")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<String>()
                val json = JSONObject(response.body?.string().orEmpty())
                val ids = LinkedHashSet<String>()
                collectByKey(json, "reelItemRenderer", ids, limit) { it.optString("videoId") }
                ids.toList()
            }
        }.getOrDefault(emptyList())
    }

    // ── Discovery path B: search, filtered to Shorts-length results ─────────

    private fun searchShortsVideoIds(query: String, limit: Int): List<String> {
        val body = JSONObject().apply {
            put("query", query)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20240701.00.00")
                })
            })
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/search?key=$WEB_CLIENT_KEY")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<String>()
                val json = JSONObject(response.body?.string().orEmpty())
                val ids = LinkedHashSet<String>()
                collectByKey(json, "videoRenderer", ids, limit) { renderer ->
                    val lengthText = renderer.optJSONObject("lengthText")
                        ?.optString("simpleText").orEmpty()
                    val seconds = parseDurationText(lengthText)
                    if (seconds in 1..90) renderer.optString("videoId") else ""
                }
                ids.toList()
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Recursively walks a JSON tree looking for objects nested under [key]
     * (e.g. `reelItemRenderer`, `videoRenderer`), running [extractId] on each
     * match and collecting non-blank, 11-char results into [out].
     */
    private fun collectByKey(
        node: Any?,
        key: String,
        out: MutableSet<String>,
        limit: Int,
        extractId: (JSONObject) -> String
    ) {
        if (out.size >= limit) return
        when (node) {
            is JSONObject -> {
                val match = node.optJSONObject(key)
                if (match != null) {
                    extractId(match).takeIf { it.length == 11 }?.let { out.add(it) }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    if (out.size >= limit) return
                    collectByKey(node.opt(keys.next()), key, out, limit, extractId)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    if (out.size >= limit) return
                    collectByKey(node.opt(i), key, out, limit, extractId)
                }
            }
        }
    }

    /** Parses "0:45" / "3:12" / "1:02:33" into total seconds; returns -1 if unparseable. */
    private fun parseDurationText(text: String): Long {
        if (text.isBlank()) return -1
        val parts = text.split(":").mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty() || parts.size != text.count { it == ':' } + 1) return -1
        return parts.fold(0L) { acc, p -> acc * 60 + p }
    }

    // ── Step 2: resolve one video ID into a directly playable stream ────────

    private fun resolvePlayableShort(videoId: String): ShortItem? {
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", ANDROID_CLIENT_VERSION)
                    put("androidSdkVersion", 34)
                })
            })
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?key=$ANDROID_CLIENT_KEY")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string().orEmpty())

                val playability = json.optJSONObject("playabilityStatus")
                if (playability?.optString("status") != "OK") return null

                val details = json.optJSONObject("videoDetails") ?: return null
                val durationSeconds = details.optString("lengthSeconds", "0").toLongOrNull() ?: 0L
                // Heuristic (see HANDOVER): Innertube has no dedicated
                // "is this a Short" flag on the player response, so a <= 90s
                // runtime is used as the Shorts signal, matching what the
                // NewPipe and Invidious sources also use for consistency.
                if (durationSeconds !in 1..90) return null

                val streamingData = json.optJSONObject("streamingData") ?: return null
                val formats = streamingData.optJSONArray("formats")
                var bestUrl: String? = null
                var bestBitrate = -1
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val f = formats.getJSONObject(i)
                        val url = f.optString("url").takeIf { it.isNotBlank() } ?: continue
                        val bitrate = f.optInt("bitrate", 0)
                        if (bitrate > bestBitrate) {
                            bestBitrate = bitrate
                            bestUrl = url
                        }
                    }
                }
                val hlsUrl = streamingData.optString("hlsManifestUrl").takeIf { it.isNotBlank() }
                val (streamUrl, kind) = when {
                    bestUrl != null -> bestUrl to StreamKind.PROGRESSIVE
                    hlsUrl != null -> hlsUrl to StreamKind.HLS
                    else -> return null
                }

                val thumbs = details.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumbUrl = thumbs?.let { arr ->
                    if (arr.length() == 0) null else arr.getJSONObject(arr.length() - 1).optString("url")
                } ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                ShortItem(
                    videoId = videoId,
                    title = details.optString("title", "Untitled"),
                    authorName = details.optString("author", ""),
                    streamUrl = streamUrl,
                    streamKind = kind,
                    thumbnailUrl = thumbUrl,
                    durationSeconds = durationSeconds,
                    source = ShortsSourceType.INNERTUBE
                )
            }
        }.getOrNull()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        // Public, unauthenticated Innertube keys embedded in youtube.com's own
        // web client — not a private/secret credential. Same key used by
        // numerous open-source Innertube clients (Piped, FreeTube, etc).
        private const val WEB_CLIENT_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        private const val ANDROID_CLIENT_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"
        private const val ANDROID_CLIENT_VERSION = "19.09.37"
        private const val QUERIES_PER_FETCH = 3
    }
}
