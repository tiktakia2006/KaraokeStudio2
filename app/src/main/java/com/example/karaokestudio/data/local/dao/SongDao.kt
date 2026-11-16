package com.example.karaokestudio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.karaokestudio.data.local.entity.Song
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SongDao {

    @Query("SELECT * FROM songs ORDER BY id")
    abstract fun observeAll(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    abstract fun observeById(id: Long): Flow<Song?>

    @Query("SELECT * FROM songs WHERE id = :id")
    abstract suspend fun getById(id: Long): Song?

    @Query("SELECT * FROM songs WHERE slug = :slug")
    abstract suspend fun getBySlug(slug: String): Song?

    @Query("SELECT id FROM songs WHERE slug = :slug")
    abstract suspend fun idBySlug(slug: String): Long?

    @Query("SELECT slug FROM songs WHERE id = :id")
    abstract suspend fun slugById(id: Long): String?

    @Query("SELECT * FROM songs WHERE searchKey LIKE '%' || :query || '%' ORDER BY id")
    abstract fun search(query: String): Flow<List<Song>>

    @Query("SELECT COUNT(*) FROM songs")
    abstract suspend fun count(): Int

    @Query("SELECT id FROM songs WHERE isDemo = 1 ORDER BY id")
    abstract suspend fun getDemoSongIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(songs: List<Song>): List<Long>

    @Insert
    abstract suspend fun insert(song: Song): Long

    @Update
    abstract suspend fun update(song: Song)

    @Query("DELETE FROM songs WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM songs WHERE slug = :slug")
    abstract suspend fun deleteBySlug(slug: String)

    @Query("SELECT audioSource FROM songs WHERE isDemo = 0")
    abstract suspend fun getUserAudioSources(): List<String>

    @Query("SELECT COUNT(*) FROM songs WHERE slug IS NOT NULL")
    abstract suspend fun countCatalog(): Int

    @Transaction
    open suspend fun applyCatalog(upserts: List<Song>, deletedSlugs: List<String>) {
        for (incoming in upserts) {
            val slug = incoming.slug ?: continue
            val existing = getBySlug(slug)
            if (existing == null) {
                insert(incoming.copy(id = 0))
            } else {
                update(incoming.copy(id = existing.id, coverSource = incoming.coverSource ?: existing.coverSource))
            }
        }
        for (slug in deletedSlugs) deleteBySlug(slug)
    }
}
