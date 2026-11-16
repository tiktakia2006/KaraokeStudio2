package com.example.karaokestudio.data.repository

import com.example.karaokestudio.data.local.dao.FavoriteDao
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.data.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FavoriteRepository(
    private val dao: FavoriteDao,
    private val syncTrigger: SyncTrigger = SyncTrigger.None
) {

    fun observeIds(): Flow<Set<Long>> = dao.observeIds().map { it.toSet() }

    fun observeSongs(): Flow<List<Song>> = dao.observeSongs()

    fun observeIsFavorite(songId: Long): Flow<Boolean> = dao.observeIsFavorite(songId)

    suspend fun add(songId: Long) {
        dao.setFavorite(songId, true)
        syncTrigger.onLocalChange()
    }

    suspend fun remove(songId: Long) {
        dao.setFavorite(songId, false)
        syncTrigger.onLocalChange()
    }

    suspend fun toggle(songId: Long): Boolean {
        val favorite = dao.toggle(songId)
        syncTrigger.onLocalChange()
        return favorite
    }

    suspend fun clear() {
        dao.clearAll()
        syncTrigger.onLocalChange()
    }
}
