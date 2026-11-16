package com.example.karaokestudio

import android.app.Application
import com.example.karaokestudio.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KaraokeApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
        appScope.launch {
            container.seeder.seedIfEmpty()
            container.cleanupStorage()
            container.syncCoordinator.onAppStart()
        }
    }
}
