package com.example.karaokestudio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.karaokestudio.data.local.entity.OutboxEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: OutboxEntry)

    @Query("SELECT * FROM sync_outbox WHERE attempts < :maxAttempts ORDER BY id LIMIT :limit")
    suspend fun next(limit: Int, maxAttempts: Int): List<OutboxEntry>

    @Query("DELETE FROM sync_outbox WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("UPDATE sync_outbox SET attempts = attempts + 1 WHERE id IN (:ids)")
    suspend fun markAttempt(ids: List<Long>)

    @Query("DELETE FROM sync_outbox WHERE attempts >= :maxAttempts")
    suspend fun deleteExhausted(maxAttempts: Int): Int

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT type || ':' || entityKey FROM sync_outbox")
    suspend fun pendingKeys(): List<String>

    @Query("DELETE FROM sync_outbox")
    suspend fun clear()

    @Query(
        "INSERT OR REPLACE INTO sync_outbox (type, entityKey, op, changedAt, attempts) " +
            "SELECT 'favorite', s.slug, 'upsert', f.addedAt, 0 FROM favorite_songs f " +
            "INNER JOIN songs s ON s.id = f.songId WHERE s.slug IS NOT NULL"
    )
    suspend fun enqueueAllFavorites()

    @Query(
        "INSERT OR REPLACE INTO sync_outbox (type, entityKey, op, changedAt, attempts) " +
            "SELECT 'performance', p.clientId, 'upsert', p.performedAt, 0 FROM performances p " +
            "INNER JOIN songs s ON s.id = p.songId WHERE s.slug IS NOT NULL AND p.isDemo = 0"
    )
    suspend fun enqueueAllPerformances()
}
