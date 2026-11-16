package com.example.karaokestudio.data.repository

import com.example.karaokestudio.data.local.dao.PerformanceDao
import com.example.karaokestudio.data.local.entity.Performance
import com.example.karaokestudio.data.local.entity.Recording
import com.example.karaokestudio.data.local.model.PerformanceDetails
import com.example.karaokestudio.data.local.model.StatsSummary
import com.example.karaokestudio.data.local.model.UserStats
import com.example.karaokestudio.data.sync.SyncTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File

class PerformanceRepository(
    private val dao: PerformanceDao,
    private val syncTrigger: SyncTrigger = SyncTrigger.None
) {

    fun observeHistory(): Flow<List<PerformanceDetails>> = dao.observeHistory()

    fun observeRecent(limit: Int): Flow<List<PerformanceDetails>> = dao.observeRecent(limit)

    fun observeDetails(id: Long): Flow<PerformanceDetails?> = dao.observeDetails(id)

    fun observeBySong(songId: Long): Flow<List<Performance>> = dao.observeBySong(songId)

    fun observeSummary(): Flow<StatsSummary> = dao.observeSummary()

    fun observeStats(): Flow<UserStats> = combine(
        dao.observeSummary(),
        dao.observeTopSongs(5),
        dao.observeRecentScores(10)
    ) { summary, top, recent -> UserStats(summary, top, recent.reversed()) }

    suspend fun save(performance: Performance, recording: Recording?): Long {
        require(performance.durationMs > 0) { "Тривалість виконання має бути додатною" }
        require(performance.score == null || performance.score in 0..100) { "Оцінка має бути в межах 0..100" }
        val id = dao.saveWithRecording(performance, recording)
        syncTrigger.onLocalChange()
        return id
    }

    suspend fun delete(performanceId: Long) {
        dao.getRecordingPath(performanceId)?.let { deleteFile(it) }
        dao.deleteTracked(performanceId)
        syncTrigger.onLocalChange()
    }

    suspend fun allRecordingPaths(): List<String> = dao.getAllRecordingPaths()

    suspend fun clearHistory() {
        dao.getAllRecordingPaths().forEach { deleteFile(it) }
        dao.clearAllTracked()
        syncTrigger.onLocalChange()
    }

    private suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) { File(path).delete() }
}
