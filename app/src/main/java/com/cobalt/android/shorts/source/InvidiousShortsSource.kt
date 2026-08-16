package com.cobalt.android.shorts.source

import com.cobalt.android.shorts.model.ShortItem
import com.cobalt.android.shorts.model.ShortsSourceType
import com.cobalt.android.shorts.model.StreamKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pulls from public Invidious instances — a third, independent path to the
 * same underlying YouTube catalog that doesn't touch youtube.com directly at
 * all.
 *
 * Two things this deliberately does NOT do, both for documented reasons:
 * - Filter `/api/v1/trending` by `type=Shorts` — that query param is a known,
 *   long-standing upstream bug (iv-org/invidious#2982: instances ignore
 *   `type` and always return the default trending list regardless of value).
 * - Rely on `/api/v1/popular` alone as the primary source — like YouTube's
 *   own trending list, it's one small fixed page, so on its own it caps how
 *   much of the catalog ever surfaces.
 *
 * Instead, same approach as [NewPipeShortsSource]: run `/api/v1/search` with
 * `type=video` against a rotating pool of query terms ([ShortsQueryFeeder]),
 * keep only <=90s results (Invidious has no reliable Shorts flag either), and
 * use `/api/v1/popular` only as a small top-up if search alone falls short.
 * Instances are tried in order per query batch and the first that responds
 * successfully wins for that batch — real failover, not a single hardcoded
 * instance that silently returns nothing when it's down.
 */
class InvidiousShortsSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val instances: List<String> = DEFAULT_INSTANCES
) : ShortsSource {

    override val type = ShortsSourceType.INVIDIOUS

    override suspend fun fetchShorts(count: Int): List<ShortItem> = withContext(Dispatchers.IO) {
        val candidates = LinkedHashMap<String, JSONObject>() // keyed by videoId

        val queries = ShortsQueryFeeder.nextQueries(QUERIES_PER_FETCH)
        for (query in queries) {
            if (candidates.size >= count * 2) break
            searchOnFirstWorkingInstance(query).forEach { candidates.putIfAbsent(it.first, it.second) }
        }

        if (candidates.size < count * 2) {
            popularOnFirstWorkingInstance().forEach { candidates.putIfAbsent(it.first, it.second) }
        }

        val results = mutableListOf<ShortItem>()
        for ((videoId, listing) in candidates) {
            if (results.size >= count) break
            resolvePlayable(videoId, listing)?.let { results.add(it) }
        }
        results
    }

    private fun searchOnFirstWorkingInstance(query: String): List<Pair<String, JSONObject>> {
        for (instance in instances) {
            val result = runCatching {
                val url = "$instance/api/v1/search".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("type", "video")
                    .build()
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    parseVideoListing(JSONArray(response.body?.string().orEmpty()))
                }
            }.getOrNull()
            if (result != null) return result
        }
        return emptyList()
    }

    private fun popularOnFirstWorkingInstance(): List<Pair<String, JSONObject>> {
        for (instance in instances) {
            val result = runCatching {
                val request = Request.Builder().url("$instance/api/v1/popular").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    parseVideoListing(JSONArray(response.body?.string().orEmpty()))
                }
            }.getOrNull()
            if (result != null) return result
        }
        return emptyList()
    }

    private fun parseVideoListing(array: JSONArray): List<Pair<String, JSONObject>> =
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            // Search results include non-video types (playlist, channel); skip those.
            if (obj.has("type") && obj.optString("type") != "video") return@mapNotNull null
            val length = obj.optLong("lengthSeconds", 0L)
            if (length !in 1..90) return@mapNotNull null
            val id = obj.optString("videoId").takeIf { it.length == 11 } ?: return@mapNotNull null
            id to obj
        }

    /** The listing doesn't include a direct stream URL; fetch /videos/:id for that. */
    private fun resolvePlayable(videoId: String, listing: JSONObject): ShortItem? = runCatching {
        for (instance in instances) {
            val item = runCatching {
                val request = Request.Builder().url("$instance/api/v1/videos/$videoId").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val detail = JSONObject(response.body?.string().orEmpty())

                    val formatStreams = detail.optJSONArray("formatStreams")
                    var bestUrl: String? = null
                    var bestHeight = -1
                    if (formatStreams != null) {
                        for (i in 0 until formatStreams.length()) {
                            val f = formatStreams.getJSONObject(i)
                            val height = f.optInt("height", 0)
                            val url = f.optString("url").takeIf { it.isNotBlank() } ?: continue
                            if (height > bestHeight) {
                                bestHeight = height
                                bestUrl = url
                            }
                        }
                    }
                    val hlsUrl = detail.optString("hlsUrl").takeIf { it.isNotBlank() }
                    val (streamUrl, kind) = when {
                        bestUrl != null -> bestUrl to StreamKind.PROGRESSIVE
                        hlsUrl != null -> hlsUrl to StreamKind.HLS
                        else -> return@use null
                    }

                    val thumbs = detail.optJSONArray("videoThumbnails")
                    val thumbUrl = thumbs?.let { arr ->
                        if (arr.length() == 0) null else arr.getJSONObject(arr.length() - 1).optString("url")
                    } ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    ShortItem(
                        videoId = videoId,
                        title = listing.optString("title", detail.optString("title", "Untitled")),
                        authorName = listing.optString("author", detail.optString("author", "")),
                        streamUrl = streamUrl,
                        streamKind = kind,
                        thumbnailUrl = thumbUrl,
                        durationSeconds = listing.optLong("lengthSeconds", 0L),
                        source = ShortsSourceType.INVIDIOUS
                    )
                }
            }.getOrNull()
            if (item != null) return item
        }
        null
    }.getOrNull()

    companion object {
        private const val QUERIES_PER_FETCH = 3

        // Instances pulled from the official public list (docs.invidious.io/instances),
        // filtered to ones that have historically had good uptime. This list
        // will drift — public instances come and go. See HANDOVER for how to
        // refresh it, and Phase 7 (Settings) for making it user-configurable
        // instead of hardcoded.
        val DEFAULT_INSTANCES = listOf(
            "https://invidious.nerdvpn.de",
            "https://yewtu.be",
            "https://invidious.jing.rocks",
            "https://iv.ggtyler.dev"
        )
    }
}
