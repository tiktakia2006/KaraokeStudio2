package com.example.karaokestudio.data.sync

import androidx.room.withTransaction
import com.example.karaokestudio.data.local.KaraokeDatabase
import com.example.karaokestudio.data.local.entity.FavoriteSong
import com.example.karaokestudio.data.local.entity.OutboxEntry
import com.example.karaokestudio.data.local.entity.Performance
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.data.remote.ChangeDto
import com.example.karaokestudio.data.remote.PullChangeDto
import com.example.karaokestudio.data.remote.SongDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class RoomSyncStore(private val database: KaraokeDatabase) : SyncStore {

    private val outbox = database.outboxDao()
    private val songs = database.songDao()
    private val favorites = database.favoriteDao()
    private val performances = database.performanceDao()

    override suspend fun nextBatch(limit: Int): List<PendingChange> {
        outbox.deleteExhausted(MAX_ATTEMPTS)
        while (true) {
            val entries = outbox.next(limit, MAX_ATTEMPTS)
            if (entries.isEmpty()) return emptyList()
            val ready = ArrayList<PendingChange>()
            val orphaned = ArrayList<Long>()
            for (entry in entries) {
                val change = toChange(entry)
                if (change == null) orphaned.add(entry.id) else ready.add(PendingChange(entry.id, change))
            }
            if (orphaned.isNotEmpty()) outbox.delete(orphaned)
            if (ready.isNotEmpty()) return ready
        }
    }

    override suspend fun settle(entryIds: List<Long>) {
        if (entryIds.isNotEmpty()) outbox.delete(entryIds)
    }

    override suspend fun markAttempt(entryIds: List<Long>) {
        if (entryIds.isNotEmpty()) outbox.markAttempt(entryIds)
    }

    override suspend fun hasPending(): Boolean {
        outbox.deleteExhausted(MAX_ATTEMPTS)
        return outbox.count() > 0
    }

    override suspend fun applyCatalog(upserts: List<SongDto>, deletedSlugs: List<String>) {
        val entities = upserts.mapNotNull { it.toSong() }
        val staleFiles = ArrayList<String>()
        for (slug in deletedSlugs) staleFiles.addAll(performances.recordingPathsForSongSlug(slug))
        songs.applyCatalog(entities, deletedSlugs)
        deleteFiles(staleFiles)
    }

    override suspend fun applyUserChanges(changes: List<PullChangeDto>): Int {
        if (changes.isEmpty()) return 0
        val staleFiles = ArrayList<String>()
        var applied = 0
        database.withTransaction {
            val pending = outbox.pendingKeys().toSet()
            for (change in changes) {
                val done = when (change.type) {
                    MergeRules.FAVORITE -> applyFavorite(change, pending)
                    MergeRules.PERFORMANCE -> applyPerformance(change, pending, staleFiles)
                    else -> false
                }
                if (done) applied++
            }
        }
        deleteFiles(staleFiles)
        return applied
    }

    override suspend fun enqueueAllLocal() {
        database.withTransaction {
            outbox.enqueueAllFavorites()
            outbox.enqueueAllPerformances()
        }
    }

    override suspend fun clearOutbox() {
        outbox.clear()
    }

    override fun observePending(): Flow<Int> = outbox.observeCount()

    private suspend fun toChange(entry: OutboxEntry): ChangeDto? = when (entry.type) {
        OutboxEntry.TYPE_FAVORITE -> ChangeDto(
            type = MergeRules.FAVORITE,
            op = entry.op,
            at = entry.changedAt,
            songSlug = entry.entityKey
        )
        OutboxEntry.TYPE_PERFORMANCE -> performanceChange(entry)
        else -> null
    }

    private suspend fun performanceChange(entry: OutboxEntry): ChangeDto? {
        if (entry.op == OutboxEntry.OP_DELETE) {
            return ChangeDto(
                type = MergeRules.PERFORMANCE,
                op = OutboxEntry.OP_DELETE,
                at = entry.changedAt,
                clientId = entry.entityKey
            )
        }
        val payload = performances.uploadPayload(entry.entityKey) ?: return null
        return ChangeDto(
            type = MergeRules.PERFORMANCE,
            op = OutboxEntry.OP_UPSERT,
            at = entry.changedAt,
            songSlug = payload.songSlug,
            clientId = entry.entityKey,
            performedAt = payload.performedAt,
            durationMs = payload.durationMs,
            score = payload.score
        )
    }

    private suspend fun applyFavorite(change: PullChangeDto, pending: Set<String>): Boolean {
        val songId = songs.idBySlug(change.songSlug) ?: return false
        val exists = favorites.exists(songId)
        return when (MergeRules.decide(change, pending, exists, sameAsLocal = true)) {
            MergeAction.APPLY_UPSERT -> {
                val addedAt = if (change.updatedAt > 0) change.updatedAt else System.currentTimeMillis()
                favorites.insert(FavoriteSong(songId = songId, addedAt = addedAt))
                true
            }
            MergeAction.APPLY_DELETE -> {
                favorites.delete(songId)
                true
            }
            MergeAction.SKIP_PENDING, MergeAction.SKIP_UNCHANGED -> false
        }
    }

    private suspend fun applyPerformance(change: PullChangeDto, pending: Set<String>, staleFiles: MutableList<String>): Boolean {
        val clientId = change.clientId ?: return false
        val local = performances.getByClientId(clientId)
        val songId = if (change.deleted) null else songs.idBySlug(change.songSlug)
        if (!change.deleted && songId == null) return false
        val same = local != null && local.songId == songId &&
            local.performedAt == change.performedAt &&
            local.durationMs == change.durationMs &&
            local.score == change.score
        return when (MergeRules.decide(change, pending, local != null, same)) {
            MergeAction.APPLY_UPSERT -> {
                val performedAt = change.performedAt ?: return false
                val durationMs = change.durationMs ?: return false
                if (local == null) {
                    performances.insertPerformance(
                        Performance(
                            songId = songId ?: return false,
                            performedAt = performedAt,
                            durationMs = durationMs,
                            score = change.score,
                            isDemo = false,
                            clientId = clientId
                        )
                    )
                } else {
                    performances.updateRemote(clientId, songId ?: return false, performedAt, durationMs, change.score)
                }
                true
            }
            MergeAction.APPLY_DELETE -> {
                performances.recordingPathForClientId(clientId)?.let { staleFiles.add(it) }
                performances.deleteByClientId(clientId)
                true
            }
            MergeAction.SKIP_PENDING, MergeAction.SKIP_UNCHANGED -> false
        }
    }

    private suspend fun deleteFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        withContext(Dispatchers.IO) { paths.forEach { File(it).delete() } }
    }

    private fun SongDto.toSong(): Song? {
        val cleanTitle = title?.trim().orEmpty()
        val source = audioSource?.trim().orEmpty()
        if (cleanTitle.isEmpty() || source.isEmpty()) return null
        return Song(
            title = cleanTitle,
            artist = artist?.trim().orEmpty().ifEmpty { "Невідомий виконавець" },
            durationMs = durationMs,
            lyrics = lyrics.orEmpty(),
            audioSource = source,
            coverSource = coverUrl?.takeIf { it.startsWith("https://") } ?: "gradient:${Math.floorMod(slug.hashCode(), 6)}",
            genre = genre?.trim().orEmpty().ifEmpty { "Інше" },
            isDemo = source.startsWith("demo:"),
            slug = slug,
            remoteUpdatedAt = updatedAt
        )
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
