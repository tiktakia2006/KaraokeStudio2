package com.example.karaokestudio

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.karaokestudio.data.local.KaraokeDatabase
import com.example.karaokestudio.data.local.entity.FavoriteSong
import com.example.karaokestudio.data.local.entity.OutboxEntry
import com.example.karaokestudio.data.local.entity.Performance
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.data.remote.PullChangeDto
import com.example.karaokestudio.data.remote.SongDto
import com.example.karaokestudio.data.sync.RoomSyncStore
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
class SyncDaoTest {

    private lateinit var db: KaraokeDatabase
    private lateinit var store: RoomSyncStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KaraokeDatabase::class.java).allowMainThreadQueries().build()
        store = RoomSyncStore(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun catalogSong(slug: String): Long =
        db.songDao().insert(
            Song(title = slug, artist = "a", durationMs = 1000, lyrics = "", audioSource = "demo:$slug", isDemo = true, slug = slug)
        )

    private suspend fun userSong(): Long =
        db.songDao().insert(Song(title = "Моя", artist = "Я", durationMs = 1000, lyrics = "", audioSource = "file:///x.m4a"))

    @Test
    fun togglingFavoriteOfCatalogSongWritesOutboxInSameTransaction() = runBlocking {
        val id = catalogSong("neon-dance")

        assertTrue(db.favoriteDao().toggle(id))
        var entries = db.outboxDao().next(10, 5)
        assertEquals(1, entries.size)
        assertEquals(OutboxEntry.TYPE_FAVORITE, entries[0].type)
        assertEquals("neon-dance", entries[0].entityKey)
        assertEquals(OutboxEntry.OP_UPSERT, entries[0].op)

        assertFalse(db.favoriteDao().toggle(id))
        entries = db.outboxDao().next(10, 5)
        assertEquals(1, entries.size)
        assertEquals(OutboxEntry.OP_DELETE, entries[0].op)
    }

    @Test
    fun favoriteOfUserSongIsNotQueued() = runBlocking {
        val id = userSong()

        db.favoriteDao().toggle(id)

        assertEquals(0, db.outboxDao().count())
        assertTrue(db.favoriteDao().exists(id))
    }

    @Test
    fun savingPerformanceQueuesItOnlyForCatalogSongsAndNotForDemoHistory() = runBlocking {
        val catalog = catalogSong("night-city")
        val own = userSong()

        db.performanceDao().saveWithRecording(Performance(songId = catalog, performedAt = 10, durationMs = 5000, score = 80), null)
        db.performanceDao().saveWithRecording(Performance(songId = catalog, performedAt = 11, durationMs = 5000, score = 70, isDemo = true), null)
        db.performanceDao().saveWithRecording(Performance(songId = own, performedAt = 12, durationMs = 5000, score = 60), null)

        val entries = db.outboxDao().next(10, 5)
        assertEquals(1, entries.size)
        assertEquals(OutboxEntry.TYPE_PERFORMANCE, entries[0].type)
        val change = store.nextBatch(10).single().change
        assertEquals("night-city", change.songSlug)
        assertEquals(80, change.score)
    }

    @Test
    fun deletingPerformanceReplacesUpsertWithDelete() = runBlocking {
        val catalog = catalogSong("summer-road")
        val performance = Performance(songId = catalog, performedAt = 10, durationMs = 5000, score = 80)
        val id = db.performanceDao().saveWithRecording(performance, null)

        db.performanceDao().deleteTracked(id)

        val entries = db.outboxDao().next(10, 5)
        assertEquals(1, entries.size)
        assertEquals(OutboxEntry.OP_DELETE, entries[0].op)
        assertEquals(performance.clientId, entries[0].entityKey)
        assertNull(db.performanceDao().getByClientId(performance.clientId))
    }

    @Test
    fun enqueueAllLocalSkipsDemoHistoryAndUserSongs() = runBlocking {
        val catalog = catalogSong("rain-window")
        val own = userSong()
        db.favoriteDao().insert(FavoriteSong(catalog, 1))
        db.favoriteDao().insert(FavoriteSong(own, 1))
        db.performanceDao().insertPerformance(Performance(songId = catalog, performedAt = 1, durationMs = 1, score = 1, isDemo = true))
        db.performanceDao().insertPerformance(Performance(songId = catalog, performedAt = 2, durationMs = 1, score = 2))
        db.performanceDao().insertPerformance(Performance(songId = own, performedAt = 3, durationMs = 1, score = 3))

        store.enqueueAllLocal()

        val keys = db.outboxDao().pendingKeys()
        assertEquals(2, keys.size)
        assertTrue(keys.contains("favorite:rain-window"))
        assertEquals(1, keys.count { it.startsWith("performance:") })
    }

    @Test
    fun catalogUpdateKeepsRowIdAndAppliesDeletion() = runBlocking {
        val id = catalogSong("quiet-river")
        db.favoriteDao().insert(FavoriteSong(id, 1))

        store.applyCatalog(
            listOf(SongDto(slug = "quiet-river", title = "Нова назва", artist = "b", durationMs = 2000, audioSource = "demo:quiet-river", updatedAt = 5)),
            emptyList()
        )

        val updated = db.songDao().getBySlug("quiet-river")
        assertNotNull(updated)
        assertEquals(id, updated!!.id)
        assertEquals("Нова назва", updated.title)
        assertTrue(db.favoriteDao().exists(id))

        store.applyCatalog(emptyList(), listOf("quiet-river"))
        assertNull(db.songDao().getBySlug("quiet-river"))
        assertFalse(db.favoriteDao().exists(id))
    }

    @Test
    fun remoteChangesAreAppliedButSkippedForEntitiesWithPendingLocalChanges() = runBlocking {
        val a = catalogSong("morning-wave")
        val b = catalogSong("neon-dance")
        db.favoriteDao().toggle(a)

        val applied = store.applyUserChanges(
            listOf(
                PullChangeDto(type = "favorite", songSlug = "morning-wave", deleted = true, updatedAt = 50),
                PullChangeDto(type = "favorite", songSlug = "neon-dance", deleted = false, updatedAt = 51),
                PullChangeDto(
                    type = "performance",
                    songSlug = "neon-dance",
                    clientId = "remote-perf-0001",
                    performedAt = 777,
                    durationMs = 4000,
                    score = 95,
                    updatedAt = 52
                )
            )
        )

        assertEquals(2, applied)
        assertTrue(db.favoriteDao().exists(a))
        assertTrue(db.favoriteDao().exists(b))
        val performance = db.performanceDao().getByClientId("remote-perf-0001")
        assertNotNull(performance)
        assertEquals(95, performance!!.score)
        assertEquals(0, db.outboxDao().pendingKeys().count { it.startsWith("performance:") })

        store.applyUserChanges(
            listOf(PullChangeDto(type = "performance", songSlug = "neon-dance", clientId = "remote-perf-0001", deleted = true, updatedAt = 60))
        )
        assertNull(db.performanceDao().getByClientId("remote-perf-0001"))
    }

    @Test
    fun outboxCoalescesAndDropsExhaustedEntries() = runBlocking {
        db.outboxDao().upsert(OutboxEntry(type = "favorite", entityKey = "a", op = "upsert", changedAt = 1))
        db.outboxDao().upsert(OutboxEntry(type = "favorite", entityKey = "a", op = "delete", changedAt = 2))
        db.outboxDao().upsert(OutboxEntry(type = "favorite", entityKey = "b", op = "upsert", changedAt = 3, attempts = 5))

        assertEquals(2, db.outboxDao().count())
        assertTrue(store.hasPending())
        assertEquals(1, db.outboxDao().count())
        assertEquals(OutboxEntry.OP_DELETE, db.outboxDao().next(10, 5).single().op)
    }
}
