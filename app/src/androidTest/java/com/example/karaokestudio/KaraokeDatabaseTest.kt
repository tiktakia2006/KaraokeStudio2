package com.example.karaokestudio

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.karaokestudio.data.local.KaraokeDatabase
import com.example.karaokestudio.data.local.entity.Performance
import com.example.karaokestudio.data.local.entity.Recording
import com.example.karaokestudio.data.local.entity.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KaraokeDatabaseTest {

    private lateinit var db: KaraokeDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KaraokeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    private fun song(title: String, artist: String = "Виконавець") = Song(
        title = title,
        artist = artist,
        durationMs = 30_000,
        lyrics = "[00:01.00]Рядок",
        audioSource = "demo:test"
    )

    @Test
    fun searchIgnoresCaseForCyrillic() = runBlocking<Unit> {
        db.songDao().insertAll(listOf(song("Ранкова хвиля"), song("Нічне місто")))
        val found = db.songDao().search("ранк").first()
        assertEquals(listOf("Ранкова хвиля"), found.map { it.title })
    }

    @Test
    fun favoriteToggleAddsAndRemoves() = runBlocking<Unit> {
        val id = db.songDao().insertAll(listOf(song("Пісня"))).first()
        assertTrue(db.favoriteDao().toggle(id))
        assertEquals(setOf(id), db.favoriteDao().observeIds().first().toSet())
        assertFalse(db.favoriteDao().toggle(id))
        assertTrue(db.favoriteDao().observeIds().first().isEmpty())
    }

    @Test
    fun performanceAndRecordingAreSavedTogether() = runBlocking<Unit> {
        val songId = db.songDao().insertAll(listOf(song("Пісня"))).first()
        val performance = Performance(songId = songId, performedAt = 1_000, durationMs = 30_000, score = 80)
        val recording = Recording(performanceId = 0, filePath = "/tmp/a.m4a", sizeBytes = 10, durationMs = 30_000, createdAt = 1_000)
        val id = db.performanceDao().saveWithRecording(performance, recording)

        val details = db.performanceDao().observeDetails(id).first()
        assertNotNull(details)
        assertEquals("Пісня", details!!.song.title)
        assertEquals(id, details.recording!!.performanceId)
    }

    @Test
    fun summaryAggregatesScoresInSql() = runBlocking<Unit> {
        val songId = db.songDao().insertAll(listOf(song("Пісня"))).first()
        listOf(60, 80, null).forEachIndexed { i, score ->
            db.performanceDao().saveWithRecording(
                Performance(songId = songId, performedAt = i.toLong(), durationMs = 10_000, score = score),
                null
            )
        }
        val summary = db.performanceDao().observeSummary().first()
        assertEquals(3, summary.totalCount)
        assertEquals(30_000L, summary.totalDurationMs)
        assertEquals(70.0, summary.averageScore!!, 0.001)
        assertEquals(80, summary.bestScore)
    }

    @Test
    fun deletingPerformanceCascadesToRecording() = runBlocking<Unit> {
        val songId = db.songDao().insertAll(listOf(song("Пісня"))).first()
        val id = db.performanceDao().saveWithRecording(
            Performance(songId = songId, performedAt = 1, durationMs = 10_000, score = 50),
            Recording(performanceId = 0, filePath = "/tmp/b.m4a", sizeBytes = 1, durationMs = 10_000, createdAt = 1)
        )
        db.performanceDao().deleteById(id)
        assertNull(db.performanceDao().getRecordingPath(id))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun performanceWithUnknownSongIsRejected() = runBlocking<Unit> {
        db.performanceDao().insertPerformance(Performance(songId = 999, performedAt = 1, durationMs = 1_000))
    }

    @Test
    fun dataSurvivesReopeningDatabase() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-test.db"
        context.deleteDatabase(name)

        val first = Room.databaseBuilder(context, KaraokeDatabase::class.java, name).build()
        first.songDao().insertAll(listOf(song("Збережена пісня")))
        first.close()

        val second = Room.databaseBuilder(context, KaraokeDatabase::class.java, name).build()
        assertEquals(1, second.songDao().count())
        second.close()
        context.deleteDatabase(name)
    }

    @Test
    fun userSongCanBeAddedAndDeletedWithCascade() = runBlocking<Unit> {
        val id = db.songDao().insert(song("Власна пісня").copy(audioSource = "file:///data/x.mp3"))
        assertEquals(listOf("file:///data/x.mp3"), db.songDao().getUserAudioSources())
        db.favoriteDao().toggle(id)
        db.performanceDao().saveWithRecording(
            Performance(songId = id, performedAt = 1, durationMs = 10_000, score = 70),
            Recording(performanceId = 0, filePath = "/tmp/c.m4a", sizeBytes = 1, durationMs = 10_000, createdAt = 1)
        )
        assertEquals(listOf("/tmp/c.m4a"), db.performanceDao().getRecordingPathsForSong(id))

        db.songDao().deleteById(id)

        assertNull(db.songDao().getById(id))
        assertTrue(db.favoriteDao().observeIds().first().isEmpty())
        assertTrue(db.performanceDao().getAllRecordingPaths().isEmpty())
    }

    @Test
    fun demoSongsAreNotListedAsUserSongs() = runBlocking<Unit> {
        db.songDao().insertAll(listOf(song("Тестова").copy(isDemo = true), song("Власна")))
        assertEquals(1, db.songDao().getUserAudioSources().size)
    }
}
