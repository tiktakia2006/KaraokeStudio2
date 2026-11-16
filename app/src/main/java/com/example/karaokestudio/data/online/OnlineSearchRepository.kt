package com.example.karaokestudio.data.online

import com.example.karaokestudio.data.remote.ApiClient
import com.example.karaokestudio.data.remote.LeaderboardDto

data class LyricsOffer(
    val lyrics: String,
    val synced: Boolean,
    val trackName: String,
    val artistName: String,
    val sourceName: String,
    val sourceUrl: String
)

data class MetadataSuggestion(
    val title: String,
    val artist: String,
    val album: String?,
    val genre: String?,
    val durationMs: Long?,
    val coverUrl: String?
)

data class MetadataResult(
    val items: List<MetadataSuggestion>,
    val sourceName: String,
    val sourceUrl: String
)

class OnlineSearchRepository(private val api: ApiClient) {

    suspend fun findLyrics(title: String, artist: String, durationMs: Long): LyricsOffer? {
        val seconds = if (durationMs > 0) (durationMs / 1000).toInt() else null
        val result = api.lyrics(title.trim(), artist.trim().ifEmpty { null }, seconds)
        val text = result.lyrics
        if (!result.found || text.isNullOrBlank()) return null
        return LyricsOffer(
            lyrics = text,
            synced = result.synced,
            trackName = result.trackName ?: title,
            artistName = result.artistName ?: artist,
            sourceName = result.attribution?.name ?: "LRCLIB",
            sourceUrl = result.attribution?.url ?: "https://lrclib.net"
        )
    }

    suspend fun suggestMetadata(query: String): MetadataResult {
        val result = api.metadata(query.trim(), SUGGESTION_LIMIT)
        return MetadataResult(
            items = result.items.map {
                MetadataSuggestion(it.title, it.artist, it.album, it.genre, it.durationMs, it.coverUrl)
            },
            sourceName = result.attribution?.name ?: "Apple iTunes Search API",
            sourceUrl = result.attribution?.url ?: "https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/"
        )
    }

    suspend fun leaderboard(slug: String): LeaderboardDto = api.leaderboard(slug, LEADERBOARD_LIMIT)

    private companion object {
        const val SUGGESTION_LIMIT = 6
        const val LEADERBOARD_LIMIT = 10
    }
}
