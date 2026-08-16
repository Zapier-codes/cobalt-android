package com.cobalt.android.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resolution_cache")
data class ResolutionCacheEntity(
    @PrimaryKey val originalUrl: String,
    val formatsJson: String,
    val resolvedAtMillis: Long = System.currentTimeMillis()
)
