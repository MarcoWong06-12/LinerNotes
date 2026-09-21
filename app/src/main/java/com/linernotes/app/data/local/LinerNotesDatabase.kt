package com.linernotes.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.linernotes.app.data.local.dao.AlbumDao
import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.local.entity.TrackEntity

@Database(
    entities = [AlbumEntity::class, TrackEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LinerNotesDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
}
