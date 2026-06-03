package com.cobalt.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.cobalt.android.download.DownloadRecord
import com.cobalt.android.download.DownloadRepository
import kotlinx.coroutines.launch

class DownloadQueueViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DownloadRepository(app)

    val allDownloads: LiveData<List<DownloadRecord>> = repo.allDownloads
    val activeDownloads: LiveData<List<DownloadRecord>> = repo.activeDownloads

    fun clearHistory() = viewModelScope.launch { repo.clearHistory() }
}
