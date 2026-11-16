package com.example.karaokestudio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.karaokestudio.data.local.entity.OutboxEntry
import com.example.karaokestudio.data.local.entity.Performance
import com.example.karaokestudio.data.local.entity.Recording
import com.example.karaokestudio.data.local.model.PerformanceDetails
import com.example.karaokestudio.data.local.model.PerformanceUpload
import com.example.karaokestudio.data.local.model.StatsSummary
import com.example.karaokestudio.data.local.model.TopSong
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PerformanceDao {

    @Insert
    abstract suspend fun insertPerformance(performance: Performance): Long

    @Insert
    abstract suspend fun insertRecording(recording: Recording): Long

    @Insert
    abstract suspend fun insertPerformances(items: List<Performance>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun enqueue(entry: OutboxEntry)

    @Query("SELECT slug FROM songs WHERE id = :songId")
    abstract suspend fun slugOf(songId: Long): String?

    @Query(
        "SELECT p.clientId FROM performances p INNER JOIN songs s ON s.id = p.songId " +
            "WHERE p.id = :id AND p.isDemo = 0 AND s.slug IS NOT NULL"
    )
    abstract suspend fun syncedClientIdOf(id: Long): String?

    @Query(
        "SELECT p.clientId FROM performances p INNER JOIN songs s ON s.id = p.songId " +
            "WHERE p.isDemo = 0 AND s.slug IS NOT NULL"
    )
    abstract suspend fun allSyncedClientIds(): List<String>

    @Query("SELECT * FROM performances WHERE clientId = :clientId")
    abstract suspend fun getByClientId(clientId: String): Performance?

    @Query(
        "SELECT p.performedAt AS performedAt, p.durationMs AS durationMs, p.score AS score, " +
            "s.slug AS songSlug FROM performances p INNER JOIN songs s ON s.id = p.songId " +
            "WHERE p.clientId = :clientId AND s.slug IS NOT NULL"
    )
    abstract suspend fun uploadPayload(clientId: String): PerformanceUpload?

    @Query(
        "UPDATE performances SET songId = :songId, performedAt = :performedAt, " +
            "durationMs = :durationMs, score = :score WHERE clientId = :clientId"
    )
    abstract suspend fun updateRemote(clientId: String, songId: Long, performedAt: Long, durationMs: Long, score: Int?)

    @Query("DELETE FROM performances WHERE clientId = :clientId")
    abstract suspend fun deleteByClientId(clientId: String)

    @Query(
        "SELECT r.filePath FROM recordings r INNER JOIN performances p ON p.id = r.performanceId " +
            "WHERE p.clientId = :clientId"
    )
    abstract suspend fun recordingPathForClientId(clientId: String): String?

    @Query(
        "SELECT r.filePath FROM recordings r INNER JOIN performances p ON p.id = r.performanceId " +
            "INNER JOIN songs s ON s.id = p.songId WHERE s.slug = :slug"
    )
    abstract suspend fun recordingPathsForSongSlug(slug: String): List<String>

    @Transaction
    open suspend fun saveWithRecording(performance: Performance, recording: Recording?): Long {
        val id = insertPerformance(performance)
        if (recording != null) insertRecording(recording.copy(performanceId = id))
        if (!performance.isDemo && slugOf(performance.songId) != null) {
            enqueue(
                OutboxEntry(
                    type = OutboxEntry.TYPE_PERFORMANCE,
                    entityKey = performance.clientId,
                    op = OutboxEntry.OP_UPSERT,
                    changedAt = System.currentTimeMillis()
                )
            )
        }
        return id
    }

    @Transaction
    open suspend fun deleteTracked(id: Long) {
        syncedClientIdOf(id)?.let { clientId ->
            enqueue(
                OutboxEntry(
                    type = OutboxEntry.TYPE_PERFORMANCE,
                    entityKey = clientId,
                    op = OutboxEntry.OP_DELETE,
                    changedAt = System.currentTimeMillis()
                )
            )
        }
        deleteById(id)
    }

    @Transaction
    open suspend fun clearAllTracked() {
        val at = System.currentTimeMillis()
        for (clientId in allSyncedClientIds()) {
            enqueue(
                OutboxEntry(
                    type = OutboxEntry.TYPE_PERFORMANCE,
                    entityKey = clientId,
                    op = OutboxEntry.OP_DELETE,
                    changedAt = at
                )
            )
        }
        deleteAll()
    }

    @Transaction
    @Query("SELECT * FROM performances ORDER BY performedAt DESC")
    abstract fun observeHistory(): Flow<List<PerformanceDetails>>

    @Transaction
    @Query("SELECT * FROM performances ORDER BY performedAt DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<PerformanceDetails>>

    @Transaction
    @Query("SELECT * FROM performances WHERE id = :id")
    abstract fun observeDetails(id: Long): Flow<PerformanceDetails?>

    @Query("SELECT * FROM performances WHERE songId = :songId ORDER BY performedAt DESC")
    abstract fun observeBySong(songId: Long): Flow<List<Performance>>

    @Query("SELECT filePath FROM recordings WHERE performanceId = :performanceId")
    abstract suspend fun getRecordingPath(performanceId: Long): String?

    @Query("SELECT filePath FROM recordings")
    abstract suspend fun getAllRecordingPaths(): List<String>

    @Query(
        "SELECT r.filePath FROM recordings r INNER JOIN performances p ON p.id = r.performanceId " +
            "WHERE p.songId = :songId"
    )
    abstract suspend fun getRecordingPathsForSong(songId: Long): List<String>

    @Query("DELETE FROM performances WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM performances")
    abstract suspend fun deleteAll()

    @Query("DELETE FROM performances WHERE isDemo = 1")
    abstract suspend fun deleteDemo()

    @Query(
        "SELECT COUNT(*) AS totalCount, " +
            "COALESCE(SUM(durationMs), 0) AS totalDurationMs, " +
            "AVG(score) AS averageScore, " +
            "MAX(score) AS bestScore " +
            "FROM performances"
    )
    abstract fun observeSummary(): Flow<StatsSummary>

    @Query(
        "SELECT s.id AS songId, s.title AS title, s.artist AS artist, " +
            "COUNT(p.id) AS plays, MAX(p.score) AS bestScore " +
            "FROM performances p INNER JOIN songs s ON s.id = p.songId " +
            "GROUP BY s.id ORDER BY plays DESC, bestScore DESC LIMIT :limit"
    )
    abstract fun observeTopSongs(limit: Int): Flow<List<TopSong>>

    @Query(
        "SELECT score FROM performances WHERE score IS NOT NULL " +
            "ORDER BY performedAt DESC LIMIT :limit"
    )
    abstract fun observeRecentScores(limit: Int): Flow<List<Int>>
}
