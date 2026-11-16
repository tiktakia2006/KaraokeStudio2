package com.example.karaokestudio.data.sync

import com.example.karaokestudio.data.remote.ChangeDto
import com.example.karaokestudio.data.remote.PullChangeDto
import com.example.karaokestudio.data.remote.SongDto
import kotlinx.coroutines.flow.Flow

data class PendingChange(val entryId: Long, val change: ChangeDto)

data class SyncReport(
    val catalogUpdated: Int = 0,
    val pushed: Int = 0,
    val stale: Int = 0,
    val rejected: Int = 0,
    val pulled: Int = 0,
    val pullDeferred: Boolean = false
)

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Success(val at: Long, val report: SyncReport) : SyncStatus
    data class Failed(val message: String, val retryable: Boolean) : SyncStatus
}

interface SyncCursors {
    suspend fun isSignedIn(): Boolean
    suspend fun catalogSince(): Long
    suspend fun setCatalogSince(value: Long)
    suspend fun pullSince(): Long
    suspend fun setPullSince(value: Long)
    suspend fun recordResult(at: Long, error: String?)
}

interface SyncStore {
    suspend fun nextBatch(limit: Int): List<PendingChange>
    suspend fun settle(entryIds: List<Long>)
    suspend fun markAttempt(entryIds: List<Long>)
    suspend fun hasPending(): Boolean
    suspend fun applyCatalog(upserts: List<SongDto>, deletedSlugs: List<String>)
    suspend fun applyUserChanges(changes: List<PullChangeDto>): Int
    suspend fun enqueueAllLocal()
    suspend fun clearOutbox()
    fun observePending(): Flow<Int>
}

interface SyncTrigger {
    suspend fun onLocalChange()

    object None : SyncTrigger {
        override suspend fun onLocalChange() = Unit
    }
}
