package com.example.karaokestudio.data.sync

import com.example.karaokestudio.data.remote.ApiErrorMessages
import com.example.karaokestudio.data.remote.ApiException
import com.example.karaokestudio.data.remote.RemoteApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncManager(
    private val api: RemoteApi,
    private val store: SyncStore,
    private val cursors: SyncCursors,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val lock = Mutex()
    private val mutableStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()

    suspend fun sync(): SyncReport = lock.withLock {
        mutableStatus.value = SyncStatus.Running
        try {
            val report = runSync()
            val finishedAt = clock()
            cursors.recordResult(finishedAt, null)
            mutableStatus.value = SyncStatus.Success(finishedAt, report)
            report
        } catch (e: CancellationException) {
            mutableStatus.value = SyncStatus.Idle
            throw e
        } catch (e: ApiException) {
            val message = ApiErrorMessages.describe(e)
            cursors.recordResult(clock(), message)
            mutableStatus.value = SyncStatus.Failed(message, e.isRetryable)
            throw e
        }
    }

    private suspend fun runSync(): SyncReport {
        val catalog = pullCatalog()
        if (!cursors.isSignedIn()) return SyncReport(catalogUpdated = catalog)
        val tally = pushPending()
        if (store.hasPending()) {
            return SyncReport(catalog, tally.pushed, tally.stale, tally.rejected, 0, true)
        }
        val pulled = pullUserChanges()
        return SyncReport(catalog, tally.pushed, tally.stale, tally.rejected, pulled, false)
    }

    private suspend fun pullCatalog(): Int {
        var since = cursors.catalogSince()
        var total = 0
        repeat(MAX_PAGES) {
            val page = api.fetchSongs(since, CATALOG_PAGE)
            val upserts = page.items.filter { !it.deleted }
            val removed = page.items.filter { it.deleted }.map { it.slug }
            if (upserts.isNotEmpty() || removed.isNotEmpty()) store.applyCatalog(upserts, removed)
            total += page.items.size
            val advanced = page.nextSince > since
            if (advanced) {
                since = page.nextSince
                cursors.setCatalogSince(since)
            }
            if (!page.hasMore || !advanced) return total
        }
        return total
    }

    private suspend fun pushPending(): PushTally {
        val tally = PushTally()
        repeat(MAX_PUSH_ROUNDS) {
            val batch = store.nextBatch(MAX_BATCH)
            if (batch.isEmpty()) return tally
            val response = api.push(batch.map { it.change })
            val settled = ArrayList<Long>()
            val retry = ArrayList<Long>()
            val answered = HashSet<Int>()
            for (result in response.results) {
                val item = batch.getOrNull(result.index) ?: continue
                answered.add(result.index)
                when (result.status) {
                    STATUS_APPLIED -> {
                        tally.pushed++
                        settled.add(item.entryId)
                    }
                    STATUS_STALE -> {
                        tally.stale++
                        settled.add(item.entryId)
                    }
                    else -> if (result.reason == REASON_FUTURE) {
                        retry.add(item.entryId)
                    } else {
                        tally.rejected++
                        settled.add(item.entryId)
                    }
                }
            }
            batch.forEachIndexed { index, item -> if (index !in answered) retry.add(item.entryId) }
            if (settled.isNotEmpty()) store.settle(settled)
            if (retry.isNotEmpty()) {
                store.markAttempt(retry)
                return tally
            }
        }
        return tally
    }

    private suspend fun pullUserChanges(): Int {
        var since = cursors.pullSince()
        var total = 0
        repeat(MAX_PAGES) {
            val page = api.pull(since)
            total += store.applyUserChanges(page.changes)
            val advanced = page.nextSince > since
            if (advanced) {
                since = page.nextSince
                cursors.setPullSince(since)
            }
            if (!page.hasMore || !advanced) return total
        }
        return total
    }

    private class PushTally(var pushed: Int = 0, var stale: Int = 0, var rejected: Int = 0)

    companion object {
        const val CATALOG_PAGE = 200
        const val MAX_BATCH = 200
        const val MAX_PAGES = 500
        const val MAX_PUSH_ROUNDS = 50
        private const val STATUS_APPLIED = "applied"
        private const val STATUS_STALE = "stale"
        private const val REASON_FUTURE = "timestamp_in_future"
    }
}
