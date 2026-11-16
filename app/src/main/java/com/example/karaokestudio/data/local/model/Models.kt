package com.example.karaokestudio.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.karaokestudio.data.local.entity.Performance
import com.example.karaokestudio.data.local.entity.Recording
import com.example.karaokestudio.data.local.entity.Song

data class PerformanceDetails(
    @Embedded val performance: Performance,
    @Relation(parentColumn = "songId", entityColumn = "id") val song: Song,
    @Relation(parentColumn = "id", entityColumn = "performanceId") val recording: Recording?
)

data class StatsSummary(
    val totalCount: Int,
    val totalDurationMs: Long,
    val averageScore: Double?,
    val bestScore: Int?
)

data class TopSong(
    val songId: Long,
    val title: String,
    val artist: String,
    val plays: Int,
    val bestScore: Int?
)

data class UserStats(
    val summary: StatsSummary,
    val topSongs: List<TopSong>,
    val recentScores: List<Int>
)

data class PerformanceUpload(
    val performedAt: Long,
    val durationMs: Long,
    val score: Int?,
    val songSlug: String
)
