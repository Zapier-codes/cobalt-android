package com.cobalt.android.download

import android.content.Context
import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadRepository(context: Context) {
    private val dao = DownloadDatabase.getInstance(context).downloadDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    val allDownloads: LiveData<List<DownloadRecord>> = dao.getAllLive()
    val activeDownloads: LiveData<List<DownloadRecord>> = dao.getActiveLive()

    suspend fun insert(record: DownloadRecord): Long = dao.insert(record)

    fun updateStatusAsync(id: Long, status: DownloadStatus) =
        scope.launch { dao.updateStatus(id, status) }

    suspend fun updateStatus(id: Long, status: DownloadStatus) =
        dao.updateStatus(id, status)

    suspend fun updateProgress(id: Long, bytes: Long, total: Long) =
        dao.updateProgress(id, bytes, total, DownloadStatus.DOWNLOADING)

    suspend fun updateMediaStoreUri(id: Long, uri: String) =
        dao.updateMediaStoreUri(id, uri)

    suspend fun incrementRetry(id: Long) = dao.incrementRetry(id)

    suspend fun getById(id: Long): DownloadRecord? = dao.getById(id)

    suspend fun clearHistory() = dao.clearHistory()
}
