package com.example.karaokestudio.data.seed

import android.util.Log
import com.example.karaokestudio.data.local.dao.PerformanceDao
import com.example.karaokestudio.data.local.dao.SongDao
import com.example.karaokestudio.data.local.entity.Performance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DatabaseSeeder(
    private val songDao: SongDao,
    private val performanceDao: PerformanceDao
) {
    private val _isReady = MutableStateFlow(false)

    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    suspend fun seedIfEmpty() {
        try {
            if (songDao.count() == 0) {
                songDao.insertAll(DemoData.songs)
                insertDemoPerformances()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не вдалося наповнити базу даних", e)
        } finally {
            _isReady.value = true
        }
    }

    suspend fun reloadDemoPerformances() {
        performanceDao.deleteDemo()
        insertDemoPerformances()
    }

    private suspend fun insertDemoPerformances() {
        val songIds = songDao.getDemoSongIds()
        val now = System.currentTimeMillis()
        val items = DemoData.performances.mapNotNull { demo ->
            val songId = songIds.getOrNull(demo.songIndex) ?: return@mapNotNull null
            Performance(
                songId = songId,
                performedAt = now - demo.daysAgo * DAY_MS - demo.songIndex * 37 * 60_000L,
                durationMs = demo.durationMs,
                score = demo.score,
                isDemo = true
            )
        }
        performanceDao.insertPerformances(items)
    }

    private companion object {
        const val TAG = "DatabaseSeeder"
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
