package com.example.karaokestudio.data.sync

import com.example.karaokestudio.data.remote.ApiException
import com.example.karaokestudio.data.remote.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SyncCoordinator(
    private val session: SessionStore,
    private val manager: SyncManager,
    private val scheduler: SyncScheduler,
    private val scope: CoroutineScope
) : SyncTrigger {

    val status: StateFlow<SyncStatus> = manager.status

    override suspend fun onLocalChange() {
        val state = session.current()
        if (state.signedIn && state.autoSync) scheduler.requestSoon()
    }

    fun syncNow() {
        scope.launch {
            try {
                manager.sync()
            } catch (e: ApiException) {
                return@launch
            }
        }
    }

    suspend fun onAppStart() {
        val state = session.current()
        if (state.signedIn && state.autoSync) {
            scheduler.schedulePeriodic()
            scheduler.requestSoon(0)
        } else {
            scheduler.cancelAll()
        }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        session.setAutoSync(enabled)
        if (enabled && session.current().signedIn) {
            scheduler.schedulePeriodic()
            scheduler.requestSoon(0)
        } else {
            scheduler.cancelAll()
        }
    }
}
