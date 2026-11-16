package com.example.karaokestudio.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.karaokestudio.data.local.dao.FavoriteDao
import com.example.karaokestudio.data.local.dao.OutboxDao
import com.example.karaokestudio.data.local.dao.PerformanceDao
import com.example.karaokestudio.data.local.dao.SongDao
import com.example.karaokestudio.data.local.entity.FavoriteSong
import com.example.karaokestudio.data.local.entity.OutboxEntry
import com.example.karaokestudio.data.local.entity.Performance
import com.example.karaokestudio.data.local.entity.Recording
import com.example.karaokestudio.data.local.entity.Song

@Database(
    entities = [Song::class, Performance::class, Recording::class, FavoriteSong::class, OutboxEntry::class],
    version = 2,
    exportSchema = true
)
abstract class KaraokeDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun performanceDao(): PerformanceDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        const val NAME = "karaoke.db"

        fun create(context: Context): KaraokeDatabase =
            Room.databaseBuilder(context.applicationContext, KaraokeDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
