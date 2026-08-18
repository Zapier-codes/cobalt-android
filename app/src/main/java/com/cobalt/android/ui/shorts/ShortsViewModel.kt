package com.cobalt.android.ui.shorts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.cobalt.android.db.HistoryRepository
import com.cobalt.android.db.LikedRepository
import com.cobalt.android.db.entities.HistoryEntity
import com.cobalt.android.db.entities.HistoryItemType
import com.cobalt.android.db.entities.LikedEntity
import com.cobalt.android.download.DownloadService
import com.cobalt.android.shorts.ShortsFeedRepository
import com.cobalt.android.shorts.model.ShortItem
import kotlinx.coroutines.launch

class ShortsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ShortsFeedRepository(application)
    private val historyRepository = HistoryRepository(application)
    private val likedRepository = LikedRepository(application)

    private val _shorts = MutableLiveData<List<ShortItem>>(emptyList())
    val shorts: LiveData<List<ShortItem>> = _shorts

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isLoadingMore = MutableLiveData(false)
    val isLoadingMore: LiveData<Boolean> = _isLoadingMore

    /** Phase 16: true when the most recent `refresh()`/`loadMore()` came
     * from the Room cache fallback rather than a live merge — see
     * `ShortsFeedRepository.FeedPage`'s doc comment for exactly what this
     * does and doesn't mean. Drives a banner in `ShortsFragment`; sticky
     * across calls (stays true until a subsequent live fetch actually
     * succeeds) rather than tied to a one-shot connectivity check, so it
     * reflects what the user is actually looking at right now. */
    private val _isOffline = MutableLiveData(false)
    val isOffline: LiveData<Boolean> = _isOffline

    init {
        refresh()
    }

    /** Clears the feed and loads a fresh cyclically-merged page. */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val page = repository.loadFeed()
            _shorts.value = page.items
            _isOffline.value = page.isFromCache
            _isLoading.value = false
        }
    }

    /** Appends another page, de-duped against what's already shown. */
    fun loadMore() {
        if (_isLoadingMore.value == true) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            val existingIds = _shorts.value.orEmpty().map { it.videoId }.toHashSet()
            val page = repository.loadFeed()
            _isOffline.value = page.isFromCache
            val more = page.items.filter { it.videoId !in existingIds }
            if (more.isNotEmpty()) {
                _shorts.value = _shorts.value.orEmpty() + more
            }
            _isLoadingMore.value = false
        }
    }

    fun toggleLike(item: ShortItem) {
        val newLiked = !item.isLiked
        _shorts.value = _shorts.value.orEmpty().map {
            if (it.videoId == item.videoId) it.copy(isLiked = newLiked) else it
        }
        viewModelScope.launch {
            repository.setLiked(item.videoId, newLiked)
            // Phase 10: ShortsCacheEntity.isLiked (above) is cache-local and
            // gets evicted with the rest of the cache row — LikedEntity is
            // the durable record, connecting Phase 2's flag to Phase 9's
            // table rather than leaving two disconnected "liked" concepts.
            if (newLiked) {
                likedRepository.like(
                    LikedEntity(
                        videoId = item.videoId,
                        title = item.title,
                        authorName = item.authorName,
                        thumbnailUrl = item.thumbnailUrl,
                        watchUrl = item.watchUrl
                    )
                )
            } else {
                likedRepository.unlike(item.videoId)
            }
        }
    }

    /**
     * Phase 10: called from `ShortsFragment.playAt()` when a Short actually
     * starts playing, writing the History row Phase 9's table exists for.
     *
     * Phase 16: `playAt()` itself now only calls this when the position
     * actually changed — verifying this phase's DoD found that `onResume()`
     * re-invoking `playAt()` for the *same already-playing* item (e.g. the
     * user backgrounds the app and returns) was writing a fresh duplicate
     * History row every time, not just on a genuine new watch. See
     * `ShortsFragment.playAt()`'s doc comment for the fix; this method
     * itself is unchanged — it always records what it's told to.
     */
    fun recordWatch(item: ShortItem) {
        viewModelScope.launch {
            historyRepository.record(
                HistoryEntity(
                    itemType = HistoryItemType.SHORT_WATCH.name,
                    refId = item.videoId,
                    title = item.title,
                    thumbnailUrl = item.thumbnailUrl,
                    sourceUrl = item.watchUrl
                )
            )
        }
    }

    /**
     * "Save" on a Short reuses the existing, real download pipeline
     * (`DownloadService.startHttps`) — the same one Phase 3/4 will drive from
     * the resolution picker — rather than a second, parallel save mechanism.
     * See ARCHITECTURE.md "Unified enhancement, NOT a parallel rebuild".
     */
    fun downloadToDevice(item: ShortItem) {
        val extension = if (item.streamKind.name == "PROGRESSIVE") "mp4" else "m3u8"
        val filename = "${sanitizeFilename(item.title)}_${item.videoId}.$extension"
        DownloadService.startHttps(
            ctx = getApplication(),
            cobaltUrl = item.streamUrl,
            filename = filename,
            mimeType = if (extension == "mp4") "video/mp4" else "application/vnd.apple.mpegurl",
            cookies = "",
            userAgent = "",
            originalUrl = item.watchUrl
        )
    }

    private fun sanitizeFilename(title: String): String =
        title.take(60).replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "short" }
}

