package com.linernotes.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyric_offsets")
data class LyricOffsetEntity(
    @PrimaryKey val trackId: Long, // FK → tracks.id
    val offsetMs: Long = 0
)
