package com.cobalt.android.db

import android.content.Context
import androidx.lifecycle.LiveData
import com.cobalt.android.db.entities.HistoryEntity
import com.cobalt.android.download.DownloadDatabase

/**
 * Thin wrapper around [com.cobalt.android.db.daos.HistoryDao], following the
 * same shape as `DownloadRepository`. Deliberately takes a pre-built
 * [HistoryEntity] rather than domain objects (`ShortItem`, `DownloadRecord`)
 * — callers (Phase 10: `ShortsViewModel`, `DownloadService`) build the row,
 * this repository just persists it. Keeps this class free of imports from
 * either the `shorts` or `download` package.
 */
class HistoryRepository(context: Context) {
    private val dao = DownloadDatabase.getInstance(context).historyDao()

    val allHistory: LiveData<List<HistoryEntity>> = dao.getAllLive()

    suspend fun record(entity: HistoryEntity) = dao.insert(entity)

    suspend fun clearAll() = dao.clearAll()
}
