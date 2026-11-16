package com.example.karaokestudio.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.karaokestudio.ui.account.AccountScreen
import com.example.karaokestudio.ui.addsong.AddSongScreen
import com.example.karaokestudio.ui.catalog.CatalogScreen
import com.example.karaokestudio.ui.detail.SongDetailScreen
import com.example.karaokestudio.ui.favorites.FavoritesScreen
import com.example.karaokestudio.ui.history.HistoryScreen
import com.example.karaokestudio.ui.home.HomeScreen
import com.example.karaokestudio.ui.karaoke.KaraokeScreen
import com.example.karaokestudio.ui.profile.ProfileScreen
import com.example.karaokestudio.ui.ranking.RankingScreen
import com.example.karaokestudio.ui.result.ResultScreen
import com.example.karaokestudio.ui.search.SearchScreen
import com.example.karaokestudio.ui.settings.SettingsScreen
import com.example.karaokestudio.ui.stats.StatsScreen

fun NavHostController.navigateTab(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val MaxContentWidth = 720.dp
private const val TRANSITION_MS = 220

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val songArg = listOf(navArgument(ARG_SONG_ID) { type = NavType.LongType })
    val performanceArg = listOf(navArgument(ARG_PERFORMANCE_ID) { type = NavType.LongType })

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        NavHost(
            navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxHeight().widthIn(max = MaxContentWidth).fillMaxWidth(),
            enterTransition = { fadeIn(tween(TRANSITION_MS)) + slideInHorizontally(tween(TRANSITION_MS)) { it / 12 } },
            exitTransition = { fadeOut(tween(TRANSITION_MS * 3 / 4)) },
            popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
            popExitTransition = { fadeOut(tween(TRANSITION_MS * 3 / 4)) + slideOutHorizontally(tween(TRANSITION_MS)) { it / 12 } }
        ) {

            composable(Screen.Home.route) {
                HomeScreen(
                    onOpenSong = { navController.navigate(Screen.SongDetail.create(it)) },
                    onOpenSearch = { navController.navigate(Screen.Search.route) },
                    onOpenCatalog = { navController.navigateTab(Screen.Catalog) },
                    onOpenHistory = { navController.navigateTab(Screen.History) },
                    onOpenResult = { navController.navigate(Screen.Result.create(it)) }
                )
            }
            composable(Screen.Catalog.route) {
                CatalogScreen(
                    onOpenSong = { navController.navigate(Screen.SongDetail.create(it)) },
                    onOpenSearch = { navController.navigate(Screen.Search.route) },
                    onAddSong = { navController.navigate(Screen.AddSong.route) }
                )
            }
            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    onOpenSong = { navController.navigate(Screen.SongDetail.create(it)) },
                    onOpenCatalog = { navController.navigateTab(Screen.Catalog) }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onOpenResult = { navController.navigate(Screen.Result.create(it)) },
                    onOpenCatalog = { navController.navigateTab(Screen.Catalog) }
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onOpenStats = { navController.navigate(Screen.Stats.route) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenAccount = { navController.navigate(Screen.Account.route) },
                    onOpenRanking = { navController.navigate(Screen.Ranking.route) }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSong = { navController.navigate(Screen.SongDetail.create(it)) }
                )
            }
            composable(Screen.SongDetail.route, arguments = songArg) {
                SongDetailScreen(
                    onBack = { navController.popBackStack() },
                    onSing = { navController.navigate(Screen.Karaoke.create(it)) },
                    onOpenResult = { navController.navigate(Screen.Result.create(it)) }
                )
            }
            composable(Screen.Karaoke.route, arguments = songArg) {
                KaraokeScreen(
                    onBack = { navController.popBackStack() },
                    onFinished = { performanceId ->
                        navController.navigate(Screen.Result.create(performanceId)) {
                            popUpTo(Screen.Karaoke.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Result.route, arguments = performanceArg) {
                ResultScreen(
                    onBack = { navController.popBackStack() },
                    onSingAgain = { songId ->
                        navController.navigate(Screen.Karaoke.create(songId)) {
                            popUpTo(Screen.Result.route) { inclusive = true }
                        }
                    },
                    onOpenCatalog = { navController.navigateTab(Screen.Catalog) }
                )
            }
            composable(Screen.Stats.route) {
                StatsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSong = { navController.navigate(Screen.SongDetail.create(it)) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAccount = { navController.navigate(Screen.Account.route) }
                )
            }
            composable(Screen.Ranking.route) {
                RankingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAccount = { navController.navigate(Screen.Account.route) }
                )
            }
            composable(Screen.Account.route) {
                AccountScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AddSong.route) {
                AddSongScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { songId ->
                        navController.navigate(Screen.SongDetail.create(songId)) {
                            popUpTo(Screen.AddSong.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
