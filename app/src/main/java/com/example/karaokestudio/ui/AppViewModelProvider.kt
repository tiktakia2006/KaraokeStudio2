package com.example.karaokestudio.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.karaokestudio.KaraokeApp
import com.example.karaokestudio.di.AppContainer
import com.example.karaokestudio.ui.account.AccountViewModel
import com.example.karaokestudio.ui.addsong.AddSongViewModel
import com.example.karaokestudio.ui.catalog.CatalogViewModel
import com.example.karaokestudio.ui.detail.SongDetailViewModel
import com.example.karaokestudio.ui.favorites.FavoritesViewModel
import com.example.karaokestudio.ui.history.HistoryViewModel
import com.example.karaokestudio.ui.home.HomeViewModel
import com.example.karaokestudio.ui.karaoke.KaraokeViewModel
import com.example.karaokestudio.ui.profile.ProfileViewModel
import com.example.karaokestudio.ui.ranking.RankingViewModel
import com.example.karaokestudio.ui.result.ResultViewModel
import com.example.karaokestudio.ui.search.SearchViewModel
import com.example.karaokestudio.ui.settings.SettingsViewModel
import com.example.karaokestudio.ui.stats.StatsViewModel

object AppViewModelProvider {

    val Factory: ViewModelProvider.Factory = viewModelFactory {

        initializer {
            val c = container()
            HomeViewModel(c.songRepository, c.performanceRepository, c.settingsRepository)
        }
        initializer {
            val c = container()
            CatalogViewModel(c.songRepository, c.favoriteRepository, c.syncCoordinator, c.networkMonitor)
        }
        initializer {
            val c = container()
            SearchViewModel(c.songRepository, c.favoriteRepository)
        }
        initializer {
            val c = container()
            SongDetailViewModel(
                createSavedStateHandle(),
                c.songRepository,
                c.favoriteRepository,
                c.performanceRepository,
                c.onlineSearchRepository,
                c.sessionStore
            )
        }

        initializer {
            val c = container()
            AddSongViewModel(c.songRepository, c.audioFiles, c.onlineSearchRepository, c.sessionStore)
        }

        initializer {
            val c = container()
            KaraokeViewModel(
                createSavedStateHandle(),
                c.songRepository,
                c.performanceRepository,
                c.settingsRepository,
                c.audioFiles,
                c.createPlayer(),
                c.createRecorder()
            )
        }
        initializer {
            val c = container()
            ResultViewModel(createSavedStateHandle(), c.performanceRepository, c.createPlayer(), c.recordingUploader, c.sessionStore)
        }

        initializer { HistoryViewModel(container().performanceRepository) }
        initializer { FavoritesViewModel(container().favoriteRepository) }
        initializer { StatsViewModel(container().performanceRepository) }

        initializer {
            val c = container()
            ProfileViewModel(c.settingsRepository, c.performanceRepository, c.accountRepository)
        }
        initializer {
            val c = container()
            SettingsViewModel(c.settingsRepository, c.performanceRepository, c.favoriteRepository, c.seeder, c.sessionStore, c.createCalibrator())
        }
        initializer {
            val c = container()
            RankingViewModel(c.rankingRepository, c.sessionStore)
        }
        initializer {
            val c = container()
            AccountViewModel(c.accountRepository, c.settingsRepository, c.syncCoordinator, c.networkMonitor)
        }
    }
}

private fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as KaraokeApp).container
