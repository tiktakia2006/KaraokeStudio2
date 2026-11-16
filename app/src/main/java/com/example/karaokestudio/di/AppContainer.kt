package com.example.karaokestudio.di

import android.content.Context
import android.util.Log
import com.example.karaokestudio.audio.AudioFileManager
import com.example.karaokestudio.audio.ExoKaraokePlayer
import com.example.karaokestudio.audio.KaraokePlayer
import com.example.karaokestudio.audio.MicCalibrator
import com.example.karaokestudio.audio.VoiceRecorder
import com.example.karaokestudio.data.local.KaraokeDatabase
import com.example.karaokestudio.data.network.NetworkMonitor
import com.example.karaokestudio.data.online.AccountRepository
import com.example.karaokestudio.data.online.OnlineSearchRepository
import com.example.karaokestudio.data.online.RankingRepository
import com.example.karaokestudio.data.online.RecordingUploader
import com.example.karaokestudio.data.remote.ApiClient
import com.example.karaokestudio.data.remote.KeystoreTokenProtector
import com.example.karaokestudio.data.remote.SessionStore
import com.example.karaokestudio.data.repository.FavoriteRepository
import com.example.karaokestudio.data.repository.PerformanceRepository
import com.example.karaokestudio.data.repository.SongRepository
import com.example.karaokestudio.data.seed.DatabaseSeeder
import com.example.karaokestudio.data.settings.SettingsRepository
import com.example.karaokestudio.data.sync.RoomSyncStore
import com.example.karaokestudio.data.sync.SyncCoordinator
import com.example.karaokestudio.data.sync.SyncManager
import com.example.karaokestudio.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope

class AppContainer(context: Context, private val appScope: CoroutineScope) {

    private val appContext = context.applicationContext

    private val database: KaraokeDatabase by lazy { KaraokeDatabase.create(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val audioFiles: AudioFileManager by lazy { AudioFileManager(appContext) }

    val sessionStore: SessionStore by lazy { SessionStore(appContext, KeystoreTokenProtector()) }
    val apiClient: ApiClient by lazy { ApiClient(ApiClient.defaultHttpClient(), sessionStore) }
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(appContext) }

    private val syncStore: RoomSyncStore by lazy { RoomSyncStore(database) }
    private val syncScheduler: SyncScheduler by lazy { SyncScheduler(appContext) }
    val syncManager: SyncManager by lazy { SyncManager(apiClient, syncStore, sessionStore) }
    val syncCoordinator: SyncCoordinator by lazy { SyncCoordinator(sessionStore, syncManager, syncScheduler, appScope) }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(apiClient, sessionStore, syncStore, syncCoordinator)
    }
    val onlineSearchRepository: OnlineSearchRepository by lazy { OnlineSearchRepository(apiClient) }
    val rankingRepository: RankingRepository by lazy { RankingRepository(apiClient) }
    val recordingUploader: RecordingUploader by lazy { RecordingUploader(apiClient, syncManager, sessionStore) }

    val songRepository: SongRepository by lazy { SongRepository(database.songDao(), database.performanceDao()) }
    val favoriteRepository: FavoriteRepository by lazy { FavoriteRepository(database.favoriteDao(), syncCoordinator) }
    val performanceRepository: PerformanceRepository by lazy { PerformanceRepository(database.performanceDao(), syncCoordinator) }
    val seeder: DatabaseSeeder by lazy { DatabaseSeeder(database.songDao(), database.performanceDao()) }

    suspend fun cleanupStorage() {
        try {
            audioFiles.cleanupOrphans(performanceRepository.allRecordingPaths(), songRepository.userSongSources())
        } catch (e: Exception) {
            Log.w("AppContainer", "Очищення сховища не вдалося", e)
        }
    }

    fun createPlayer(): KaraokePlayer = ExoKaraokePlayer(appContext)
    fun createRecorder(): VoiceRecorder = VoiceRecorder(appContext)
    fun createCalibrator(): MicCalibrator = MicCalibrator({ VoiceRecorder(appContext) }, appContext.cacheDir)
}
