package com.cobalt.android.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resolution_cache")
data class ResolutionCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalUrl: String,
    val resolvedUrl: String,
    val title: String
)
