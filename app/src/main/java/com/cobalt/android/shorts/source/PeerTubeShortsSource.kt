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
 * A fourth, independent Shorts source: PeerTube, the open-source, federated
 * (ActivityPub) video platform — free/open in the same sense Invidious and
 * NewPipeExtractor already are here, not a reverse-engineered private API.
 * Real, unauthenticated, public JSON endpoints, both verified live during
 * this phase (see HANDOVER for the exact requests/responses checked):
 *
 * 1. **Discovery**: `sepiasearch.org` — Framasoft's own federated search
 *    index covering ~800+ public PeerTube instances at once
 *    (`GET https://sepiasearch.org/api/v1/search/videos?search=<query>`,
 *    no auth, documented informally but confirmed working — see
 *    https://sepiasearch.org and NewPipeExtractor's own PeerTube support,
 *    which uses the same endpoint). One federated index beats maintaining
 *    a hardcoded per-instance list the way `InvidiousShortsSource` has to
 *    — PeerTube has no single "the" instance and no comparably-complete
 *    alternative to SepiaSearch for this.
 * 2. **Resolution**: each result carries a `uuid` and the origin instance's
 *    own host (`channel.host`/`account.host` — SepiaSearch itself doesn't
 *    serve the raw video file, only metadata). A direct
 *    `GET https://{host}/api/v1/videos/{uuid}` against *that* instance
 *    returns `files[]` (progressive MP4, one per resolution) and/or
 *    `streamingPlaylists[].files[]` (HLS — `fileUrl` ends in
 *    `-fragmented.mp4`; replacing that suffix with `.m3u8` gives the real
 *    HLS playlist URL, since PeerTube doesn't publish that URL directly —
 *    see github.com/Chocobozzz/PeerTube/issues/6615, a still-open feature
 *    request for exactly that).
 *
 * PeerTube is a general video platform, not Shorts-specific (like
 * Invidious/NewPipe's YouTube backing) — the same <=90s duration filter
 * the other two sources use for the same reason is applied here too, on
 * SepiaSearch's `duration` field (seconds) before any per-video resolve
 * call, so long-form talks/lectures (common on PeerTube — see the
 * `sepiasearch.org` doc comment's own search examples) are skipped without
 * costing an extra request.
 */
class PeerTubeShortsSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : ShortsSource {

    override val type = ShortsSourceType.PEERTUBE

    override suspend fun fetchShorts(count: Int): List<ShortItem> = withContext(Dispatchers.IO) {
        val candidates = LinkedHashMap<String, Candidate>() // keyed by uuid

        for (query in ShortsQueryFeeder.nextQueries(QUERIES_PER_FETCH)) {
            if (candidates.size >= count * 2) break
            searchSepia(query, count * 2 - candidates.size).forEach {
                candidates.putIfAbsent(it.uuid, it)
            }
        }

        val results = mutableListOf<ShortItem>()
        for (candidate in candidates.values) {
            if (results.size >= count) break
            resolvePlayable(candidate)?.let { results.add(it) }
        }
        results
    }

    // ── Discovery: SepiaSearch, filtered to Shorts-length results ───────────

    private data class Candidate(
        val uuid: String,
        val host: String,
        val title: String,
        val thumbnailUrl: String,
        val durationSeconds: Long,
        val watchUrl: String
    )

    private fun searchSepia(query: String, limit: Int): List<Candidate> {
        val url = SEPIASEARCH_BASE.toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("count", (limit * 3).coerceAtMost(50).toString())
            .build()

        return runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<Candidate>()
                val json = JSONObject(response.body?.string().orEmpty())
                val data = json.optJSONArray("data") ?: JSONArray()
                val out = mutableListOf<Candidate>()
                for (i in 0 until data.length()) {
                    if (out.size >= limit) break
                    val item = data.optJSONObject(i) ?: continue
                    val durationSeconds = item.optLong("duration", -1)
                    // Shorts-length heuristic, same bar as Innertube/NewPipe/
                    // Invidious — see class doc comment.
                    if (durationSeconds !in 1..90) continue
                    val uuid = item.optString("uuid").takeIf { it.isNotBlank() } ?: continue
                    // Prefer the channel host (the actual publishing instance)
                    // over the account host — they're usually the same, but
                    // channel is the more specific/reliable of the two here.
                    val host = item.optJSONObject("channel")?.optString("host")
                        ?: item.optJSONObject("account")?.optString("host")
                        ?: continue
                    val watchUrl = item.optString("url").takeIf { it.isNotBlank() }
                        ?: "https://$host/videos/watch/$uuid"
                    out += Candidate(
                        uuid = uuid,
                        host = host,
                        title = item.optString("name", "Untitled"),
                        thumbnailUrl = item.optString("thumbnailUrl"),
                        durationSeconds = durationSeconds,
                        watchUrl = watchUrl
                    )
                }
                out
            }
        }.getOrDefault(emptyList())
    }

    // ── Resolution: ask the origin instance for a real playable file ────────

    private fun resolvePlayable(candidate: Candidate): ShortItem? {
        val request = Request.Builder()
            .url("https://${candidate.host}/api/v1/videos/${candidate.uuid}")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string().orEmpty())

                // Prefer progressive MP4 (files[]) — simplest, most broadly
                // compatible, same as Innertube's ANDROID-client formats[].
                val progressiveFiles = json.optJSONArray("files")
                var bestUrl: String? = null
                var bestResolution = -1
                if (progressiveFiles != null) {
                    for (i in 0 until progressiveFiles.length()) {
                        val f = progressiveFiles.getJSONObject(i)
                        val fileUrl = f.optString("fileUrl").takeIf { it.isNotBlank() } ?: continue
                        val resolution = f.optJSONObject("resolution")?.optInt("id", 0) ?: 0
                        if (resolution > bestResolution) {
                            bestResolution = resolution
                            bestUrl = fileUrl
                        }
                    }
                }
                if (bestUrl != null) {
                    return@use buildShortItem(candidate, bestUrl, StreamKind.PROGRESSIVE)
                }

                // Fall back to HLS: streamingPlaylists[0].files[] gives
                // *-fragmented.mp4 URLs, not a playlist URL directly — see
                // class doc comment / PeerTube issue #6615. Swap the known
                // suffix for .m3u8, same workaround NewPipeExtractor and
                // other third-party PeerTube clients use.
                val playlists = json.optJSONArray("streamingPlaylists")
                val hlsFiles = playlists?.optJSONObject(0)?.optJSONArray("files")
                if (hlsFiles != null) {
                    for (i in 0 until hlsFiles.length()) {
                        val f = hlsFiles.getJSONObject(i)
                        val fileUrl = f.optString("fileUrl")
                        if (fileUrl.endsWith("-fragmented.mp4")) {
                            val hlsUrl = fileUrl.removeSuffix("-fragmented.mp4") + ".m3u8"
                            return@use buildShortItem(candidate, hlsUrl, StreamKind.HLS)
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun buildShortItem(candidate: Candidate, streamUrl: String, kind: StreamKind) = ShortItem(
        videoId = "peertube:${candidate.uuid}",
        title = candidate.title,
        authorName = candidate.host,
        streamUrl = streamUrl,
        streamKind = kind,
        thumbnailUrl = candidate.thumbnailUrl,
        durationSeconds = candidate.durationSeconds,
        watchUrl = candidate.watchUrl,
        source = ShortsSourceType.PEERTUBE
    )

    companion object {
        private const val SEPIASEARCH_BASE = "https://sepiasearch.org/api/v1/search/videos"
        private const val QUERIES_PER_FETCH = 3
    }
}
