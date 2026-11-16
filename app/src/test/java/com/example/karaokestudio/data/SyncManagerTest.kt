package com.example.karaokestudio.data

import com.example.karaokestudio.data.remote.ApiException
import com.example.karaokestudio.data.remote.ChangeDto
import com.example.karaokestudio.data.remote.PullChangeDto
import com.example.karaokestudio.data.remote.PullResponse
import com.example.karaokestudio.data.remote.PushResponse
import com.example.karaokestudio.data.remote.PushResultDto
import com.example.karaokestudio.data.remote.RemoteApi
import com.example.karaokestudio.data.remote.SongDto
import com.example.karaokestudio.data.remote.SongPageDto
import com.example.karaokestudio.data.sync.PendingChange
import com.example.karaokestudio.data.sync.SyncCursors
import com.example.karaokestudio.data.sync.SyncManager
import com.example.karaokestudio.data.sync.SyncStatus
import com.example.karaokestudio.data.sync.SyncStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class FakeApi : RemoteApi {
    val songPages = ArrayDeque<SongPageDto>()
    val pullPages = ArrayDeque<PullResponse>()
    val songRequests = mutableListOf<Long>()
    val pullRequests = mutableListOf<Long>()
    val pushBatches = mutableListOf<List<ChangeDto>>()
    var pushHandler: (List<ChangeDto>) -> PushResponse = { batch ->
        PushResponse(batch.indices.map { PushResultDto(it, "applied") }, batch.size, 0)
    }
    var failure: ApiException? = null

    override suspend fun fetchSongs(since: Long, limit: Int): SongPageDto {
        failure?.let { throw it }
        songRequests.add(since)
        return songPages.removeFirstOrNull() ?: SongPageDto(emptyList(), false, since)
    }

    override suspend fun push(changes: List<ChangeDto>): PushResponse {
        failure?.let { throw it }
        pushBatches.add(changes)
        return pushHandler(changes)
    }

    override suspend fun pull(since: Long): PullResponse {
        failure?.let { throw it }
        pullRequests.add(since)
        return pullPages.removeFirstOrNull() ?: PullResponse(emptyList(), false, since)
    }
}

private class FakeStore : SyncStore {
    val queue = mutableListOf<PendingChange>()
    val settled = mutableListOf<Long>()
    val attempts = mutableListOf<Long>()
    val catalogUpserts = mutableListOf<SongDto>()
    val catalogDeleted = mutableListOf<String>()
    val appliedUserChanges = mutableListOf<PullChangeDto>()

    override suspend fun nextBatch(limit: Int): List<PendingChange> = queue.take(limit)

    override suspend fun settle(entryIds: List<Long>) {
        settled.addAll(entryIds)
        queue.removeAll { it.entryId in entryIds }
    }

    override suspend fun markAttempt(entryIds: List<Long>) {
        attempts.addAll(entryIds)
    }

    override suspend fun hasPending(): Boolean = queue.isNotEmpty()

    override suspend fun applyCatalog(upserts: List<SongDto>, deletedSlugs: List<String>) {
        catalogUpserts.addAll(upserts)
        catalogDeleted.addAll(deletedSlugs)
    }

    override suspend fun applyUserChanges(changes: List<PullChangeDto>): Int {
        appliedUserChanges.addAll(changes)
        return changes.size
    }

    override suspend fun enqueueAllLocal() = Unit

    override suspend fun clearOutbox() {
        queue.clear()
    }

    override fun observePending(): Flow<Int> = MutableStateFlow(queue.size)
}

private class FakeCursors(var signedIn: Boolean = true) : SyncCursors {
    var catalog = 0L
    var pull = 0L
    var lastAt = 0L
    var lastError: String? = null

    override suspend fun isSignedIn(): Boolean = signedIn
    override suspend fun catalogSince(): Long = catalog
    override suspend fun setCatalogSince(value: Long) {
        catalog = value
    }

    override suspend fun pullSince(): Long = pull
    override suspend fun setPullSince(value: Long) {
        pull = value
    }

    override suspend fun recordResult(at: Long, error: String?) {
        lastAt = at
        lastError = error
    }
}

class SyncManagerTest {

    private val api = FakeApi()
    private val store = FakeStore()
    private val cursors = FakeCursors()
    private val manager = SyncManager(api, store, cursors, clock = { 123_000L })

    private fun song(slug: String, deleted: Boolean = false, updatedAt: Long = 1) =
        SongDto(slug = slug, title = slug, artist = "a", durationMs = 1000, audioSource = "demo:$slug", deleted = deleted, updatedAt = updatedAt)

    private fun pending(id: Long) =
        PendingChange(id, ChangeDto(type = "favorite", op = "upsert", at = id, songSlug = "song-$id"))

    @Test
    fun guestSyncsOnlyTheCatalogAcrossPages() = runTest {
        cursors.signedIn = false
        api.songPages.add(SongPageDto(listOf(song("a"), song("b")), true, 5))
        api.songPages.add(SongPageDto(listOf(song("c"), song("a", deleted = true, updatedAt = 9)), false, 9))

        val report = manager.sync()

        assertEquals(listOf(0L, 5L), api.songRequests)
        assertEquals(9L, cursors.catalog)
        assertEquals(listOf("a", "b", "c"), store.catalogUpserts.map { it.slug })
        assertEquals(listOf("a"), store.catalogDeleted)
        assertEquals(4, report.catalogUpdated)
        assertTrue(api.pushBatches.isEmpty())
        assertTrue(api.pullRequests.isEmpty())
    }

    @Test
    fun stopsWhenServerKeepsSayingThereIsMoreButCursorDoesNotMove() = runTest {
        cursors.signedIn = false
        cursors.catalog = 7
        api.songPages.add(SongPageDto(emptyList(), true, 7))
        api.songPages.add(SongPageDto(listOf(song("late")), false, 8))

        manager.sync()

        assertEquals(listOf(7L), api.songRequests)
        assertTrue(store.catalogUpserts.isEmpty())
    }

    @Test
    fun signedInUserPushesThenPullsAndStoresCursors() = runTest {
        store.queue.addAll(listOf(pending(1), pending(2), pending(3)))
        api.pushHandler = {
            PushResponse(
                listOf(
                    PushResultDto(0, "applied"),
                    PushResultDto(1, "stale"),
                    PushResultDto(2, "rejected", "unknown_song")
                ),
                1,
                0
            )
        }
        api.pullPages.add(
            PullResponse(listOf(PullChangeDto(type = "favorite", songSlug = "x", updatedAt = 40)), true, 40)
        )
        api.pullPages.add(PullResponse(listOf(PullChangeDto(type = "favorite", songSlug = "y", updatedAt = 50)), false, 50))

        val report = manager.sync()

        assertEquals(listOf(1L, 2L, 3L), store.settled)
        assertTrue(store.queue.isEmpty())
        assertEquals(1, report.pushed)
        assertEquals(1, report.stale)
        assertEquals(1, report.rejected)
        assertEquals(listOf(0L, 40L), api.pullRequests)
        assertEquals(50L, cursors.pull)
        assertEquals(2, report.pulled)
        assertFalse(report.pullDeferred)
        assertEquals(123_000L, cursors.lastAt)
        assertEquals(null, cursors.lastError)
    }

    @Test
    fun sendsLargeQueueInBatchesOfTwoHundred() = runTest {
        store.queue.addAll((1L..450L).map { pending(it) })

        val report = manager.sync()

        assertEquals(listOf(200, 200, 50), api.pushBatches.map { it.size })
        assertEquals(450, report.pushed)
        assertTrue(store.queue.isEmpty())
    }

    @Test
    fun doesNotApplyForeignChangesWhileOwnOnesAreStillPending() = runTest {
        store.queue.add(pending(1))
        api.pushHandler = { PushResponse(listOf(PushResultDto(0, "rejected", "timestamp_in_future")), 0, 0) }
        api.pullPages.add(PullResponse(listOf(PullChangeDto(type = "favorite", songSlug = "x")), false, 10))

        val report = manager.sync()

        assertTrue(report.pullDeferred)
        assertEquals(0, report.pulled)
        assertTrue(api.pullRequests.isEmpty())
        assertEquals(listOf(1L), store.attempts)
        assertTrue(store.settled.isEmpty())
        assertEquals(1, store.queue.size)
    }

    @Test
    fun keepsEntriesTheServerDidNotAnswer() = runTest {
        store.queue.addAll(listOf(pending(1), pending(2)))
        api.pushHandler = { PushResponse(listOf(PushResultDto(0, "applied")), 1, 0) }

        val report = manager.sync()

        assertEquals(listOf(1L), store.settled)
        assertEquals(listOf(2L), store.attempts)
        assertTrue(report.pullDeferred)
        assertEquals(1, api.pushBatches.size)
    }

    @Test
    fun reportsFailureAndRethrows() = runTest {
        api.failure = ApiException.NoNetwork()

        try {
            manager.sync()
            fail("очікувався виняток")
        } catch (e: ApiException.NoNetwork) {
            assertNotNull(e)
        }

        val status = manager.status.value
        assertTrue(status is SyncStatus.Failed)
        assertTrue((status as SyncStatus.Failed).retryable)
        assertNotNull(cursors.lastError)
    }

    @Test
    fun authorizationFailureIsNotRetryable() = runTest {
        api.failure = ApiException.Unauthorized("Сесію завершено")

        try {
            manager.sync()
            fail("очікувався виняток")
        } catch (e: ApiException.Unauthorized) {
            assertNotNull(e)
        }

        assertFalse((manager.status.value as SyncStatus.Failed).retryable)
    }

    @Test
    fun successfulRunPublishesSuccessStatus() = runTest {
        cursors.signedIn = false

        manager.sync()

        val status = manager.status.value
        assertTrue(status is SyncStatus.Success)
        assertEquals(123_000L, (status as SyncStatus.Success).at)
    }
}
