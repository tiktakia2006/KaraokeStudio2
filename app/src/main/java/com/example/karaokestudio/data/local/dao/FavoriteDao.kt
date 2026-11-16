package com.example.karaokestudio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.karaokestudio.data.local.entity.FavoriteSong
import com.example.karaokestudio.data.local.entity.OutboxEntry
import com.example.karaokestudio.data.local.entity.Song
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(favorite: FavoriteSong)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun enqueue(entry: OutboxEntry)

    @Query("DELETE FROM favorite_songs WHERE songId = :songId")
    abstract suspend fun delete(songId: Long)

    @Query("DELETE FROM favorite_songs")
    abstract suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE songId = :songId)")
    abstract suspend fun exists(songId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE songId = :songId)")
    abstract fun observeIsFavorite(songId: Long): Flow<Boolean>

    @Query("SELECT songId FROM favorite_songs")
    abstract fun observeIds(): Flow<List<Long>>

    @Query(
        "SELECT s.* FROM songs s INNER JOIN favorite_songs f ON f.songId = s.id " +
            "ORDER BY f.addedAt DESC"
    )
    abstract fun observeSongs(): Flow<List<Song>>

    @Query("SELECT slug FROM songs WHERE id = :songId")
    abstract suspend fun slugOf(songId: Long): String?

    @Query(
        "SELECT s.slug FROM favorite_songs f INNER JOIN songs s ON s.id = f.songId " +
            "WHERE s.slug IS NOT NULL"
    )
    abstract suspend fun syncedSlugs(): List<String>

    @Transaction
    open suspend fun setFavorite(songId: Long, favorite: Boolean) {
        val at = System.currentTimeMillis()
        if (favorite) insert(FavoriteSong(songId = songId, addedAt = at)) else delete(songId)
        track(songId, favorite, at)
    }

    @Transaction
    open suspend fun toggle(songId: Long): Boolean {
        val at = System.currentTimeMillis()
        val nowFavorite = !exists(songId)
        if (nowFavorite) insert(FavoriteSong(songId = songId, addedAt = at)) else delete(songId)
        track(songId, nowFavorite, at)
        return nowFavorite
    }

    @Transaction
    open suspend fun clearAll() {
        val at = System.currentTimeMillis()
        for (slug in syncedSlugs()) {
            enqueue(
                OutboxEntry(
                    type = OutboxEntry.TYPE_FAVORITE,
                    entityKey = slug,
                    op = OutboxEntry.OP_DELETE,
                    changedAt = at
                )
            )
        }
        deleteAll()
    }

    private suspend fun track(songId: Long, favorite: Boolean, at: Long) {
        val slug = slugOf(songId) ?: return
        enqueue(
            OutboxEntry(
                type = OutboxEntry.TYPE_FAVORITE,
                entityKey = slug,
                op = if (favorite) OutboxEntry.OP_UPSERT else OutboxEntry.OP_DELETE,
                changedAt = at
            )
        )
    }
}
