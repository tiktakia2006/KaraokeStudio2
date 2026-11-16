package com.example.karaokestudio.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    isLenient = false
}

@Serializable
data class UserDto(val id: Long, val username: String, val nickname: String, val role: String)

@Serializable
data class SessionDto(val token: String, val refreshToken: String, val expiresIn: Long, val user: UserDto)

@Serializable
data class UserEnvelope(val user: UserDto)

@Serializable
data class CredentialsRequest(val username: String, val password: String, val nickname: String? = null)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class PasswordChangeRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class PasswordConfirmRequest(val password: String)

@Serializable
data class NicknameRequest(val nickname: String)

@Serializable
data class SongDto(
    val slug: String,
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long = 0,
    val genre: String? = null,
    val lyrics: String? = null,
    val audioSource: String? = null,
    val coverUrl: String? = null,
    val deleted: Boolean = false,
    val updatedAt: Long = 0
)

@Serializable
data class SongPageDto(val items: List<SongDto>, val hasMore: Boolean, val nextSince: Long)

@Serializable
data class ChangeDto(
    val type: String,
    val op: String,
    val at: Long,
    val songSlug: String? = null,
    val clientId: String? = null,
    val performedAt: Long? = null,
    val durationMs: Long? = null,
    val score: Int? = null
)

@Serializable
data class PushRequest(val changes: List<ChangeDto>)

@Serializable
data class PushResultDto(val index: Int, val status: String, val reason: String? = null)

@Serializable
data class PushResponse(val results: List<PushResultDto>, val applied: Int = 0, val serverTime: Long = 0)

@Serializable
data class PullChangeDto(
    val type: String,
    val songSlug: String,
    val deleted: Boolean = false,
    val updatedAt: Long = 0,
    val clientId: String? = null,
    val performedAt: Long? = null,
    val durationMs: Long? = null,
    val score: Int? = null
)

@Serializable
data class PullResponse(val changes: List<PullChangeDto>, val hasMore: Boolean, val nextSince: Long)

@Serializable
data class LeaderboardEntryDto(
    val rank: Int,
    val nickname: String,
    val bestScore: Int,
    val attempts: Int,
    val achievedAt: Long
)

@Serializable
data class LeaderboardDto(
    val items: List<LeaderboardEntryDto>,
    val total: Int,
    val me: LeaderboardEntryDto? = null
)

@Serializable
data class PlayerRankDto(
    val rank: Int,
    val nickname: String,
    val totalScore: Int,
    val songsCount: Int,
    val bestScore: Int,
    val attempts: Int
)

@Serializable
data class GlobalLeaderboardDto(
    val items: List<PlayerRankDto>,
    val total: Int,
    val me: PlayerRankDto? = null
)

@Serializable
data class AttributionDto(val name: String, val url: String)

@Serializable
data class LyricsDto(
    val found: Boolean,
    val source: String? = null,
    val synced: Boolean = false,
    val lyrics: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val durationSec: Int? = null,
    val attribution: AttributionDto? = null,
    val cached: Boolean = false,
    val stale: Boolean = false
)

@Serializable
data class MetadataItemDto(
    val title: String,
    val artist: String,
    val album: String? = null,
    val genre: String? = null,
    val durationMs: Long? = null,
    val coverUrl: String? = null,
    val source: String? = null
)

@Serializable
data class MetadataDto(
    val items: List<MetadataItemDto>,
    val attribution: AttributionDto? = null,
    val cached: Boolean = false,
    val stale: Boolean = false
)

@Serializable
data class RecordingDto(
    val clientId: String,
    val mime: String,
    val sizeBytes: Long,
    val sha256: String,
    val createdAt: Long
)

@Serializable
data class ErrorBody(val code: String = "", val message: String = "")

@Serializable
data class ErrorEnvelope(val error: ErrorBody = ErrorBody(), val requestId: String? = null)
