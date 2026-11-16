package com.example.karaokestudio.ui.navigation

const val ARG_SONG_ID = "songId"
const val ARG_PERFORMANCE_ID = "performanceId"

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Catalog : Screen("catalog")
    data object Favorites : Screen("favorites")
    data object History : Screen("history")
    data object Profile : Screen("profile")

    data object Search : Screen("search")
    data object Stats : Screen("stats")
    data object Settings : Screen("settings")
    data object AddSong : Screen("add-song")
    data object Account : Screen("account")
    data object Ranking : Screen("ranking")

    data object SongDetail : Screen("song/{$ARG_SONG_ID}") {
        fun create(songId: Long) = "song/$songId"
    }

    data object Karaoke : Screen("karaoke/{$ARG_SONG_ID}") {
        fun create(songId: Long) = "karaoke/$songId"
    }

    data object Result : Screen("result/{$ARG_PERFORMANCE_ID}") {
        fun create(performanceId: Long) = "result/$performanceId"
    }
}
