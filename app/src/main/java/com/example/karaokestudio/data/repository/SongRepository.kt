package com.example.karaokestudio.data.repository

import com.example.karaokestudio.data.local.dao.PerformanceDao
import com.example.karaokestudio.data.local.dao.SongDao
import com.example.karaokestudio.data.local.entity.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class SongRepository(
    private val songDao: SongDao,
    private val performanceDao: PerformanceDao
) {

    fun observeAll(): Flow<List<Song>> = songDao.observeAll()

    fun observe(id: Long): Flow<Song?> = songDao.observeById(id)

    suspend fun get(id: Long): Song? = songDao.getById(id)

    fun search(rawQuery: String): Flow<List<Song>> {
        val query = normalizeQuery(rawQuery)
        return if (query.isEmpty()) flowOf(emptyList()) else songDao.search(query)
    }

    suspend fun add(song: Song): Long {
        validate(song)?.let { throw IllegalArgumentException(it) }
        return songDao.insert(song.copy(isDemo = false))
    }

    suspend fun deleteUserSong(song: Song) {
        require(!song.isDemo) { "Тестові пісні видаляти не можна" }
        withContext(Dispatchers.IO) {
            performanceDao.getRecordingPathsForSong(song.id).forEach { File(it).delete() }
            Song.localFileOf(song.audioSource)?.delete()
        }
        songDao.deleteById(song.id)
    }

    suspend fun userSongSources(): List<String> = songDao.getUserAudioSources()

    companion object {
        const val MAX_TITLE = 60

        fun normalizeQuery(raw: String): String =
            raw.trim().lowercase(Locale.ROOT).filterNot { it == '%' || it == '_' }.trim()

        fun validate(song: Song): String? = when {
            song.title.isBlank() -> "Введіть назву пісні"
            song.title.length > MAX_TITLE -> "Назва не може бути довшою за $MAX_TITLE символів"
            song.durationMs <= 0 -> "Тривалість пісні має бути додатною"
            song.audioSource.isBlank() -> "Оберіть аудіофайл"
            else -> null
        }
    }
}
