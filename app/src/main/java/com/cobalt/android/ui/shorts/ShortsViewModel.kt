package com.cobalt.android.ui.shorts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.cobalt.android.download.DownloadService
import com.cobalt.android.shorts.ShortsFeedRepository
import com.cobalt.android.shorts.model.ShortItem
import kotlinx.coroutines.launch

class ShortsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ShortsFeedRepository(application)

    private val _shorts = MutableLiveData<List<ShortItem>>(emptyList())
    val shorts: LiveData<List<ShortItem>> = _shorts

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isLoadingMore = MutableLiveData(false)
    val isLoadingMore: LiveData<Boolean> = _isLoadingMore

    init {
        refresh()
    }

    /** Clears the feed and loads a fresh cyclically-merged page. */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val fresh = repository.loadFeed()
            _shorts.value = fresh
            _isLoading.value = false
        }
    }

    /** Appends another page, de-duped against what's already shown. */
    fun loadMore() {
        if (_isLoadingMore.value == true) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            val existingIds = _shorts.value.orEmpty().map { it.videoId }.toHashSet()
            val more = repository.loadFeed().filter { it.videoId !in existingIds }
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
        viewModelScope.launch { repository.setLiked(item.videoId, newLiked) }
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
