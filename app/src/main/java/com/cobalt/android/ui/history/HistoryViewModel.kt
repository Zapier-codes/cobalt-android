package com.cobalt.android.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.cobalt.android.db.HistoryRepository
import com.cobalt.android.db.LikedRepository
import com.cobalt.android.db.entities.HistoryEntity
import com.cobalt.android.db.entities.HistoryItemType
import com.cobalt.android.db.entities.LikedEntity
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepository = HistoryRepository(application)
    private val likedRepository = LikedRepository(application)

    /** Phase 9's `HistoryEntity` already merges SHORT_WATCH + DOWNLOAD rows
     *  into one most-recent-first table — this just maps to display rows. */
    val historyRows: LiveData<List<HistoryRow>> = MediatorLiveData<List<HistoryRow>>().apply {
        addSource(historyRepository.allHistory) { entities ->
            value = entities.map { it.toRow() }
        }
    }

    val likedRows: LiveData<List<HistoryRow>> = MediatorLiveData<List<HistoryRow>>().apply {
        addSource(likedRepository.allLiked) { entities ->
            value = entities.map { it.toRow() }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clearAll() }
    }

    private fun HistoryEntity.toRow() = HistoryRow(
        id = "h$id",
        title = title,
        subtitle = "${if (itemType == HistoryItemType.DOWNLOAD.name) "downloaded" else "watched"} · " +
            HistoryRowAdapter.formatTimestamp(timestamp),
        sourceUrl = sourceUrl
    )

    private fun LikedEntity.toRow() = HistoryRow(
        id = "l$videoId",
        title = title,
        subtitle = "$authorName · ${HistoryRowAdapter.formatTimestamp(likedAt)}",
        sourceUrl = watchUrl
    )
}
