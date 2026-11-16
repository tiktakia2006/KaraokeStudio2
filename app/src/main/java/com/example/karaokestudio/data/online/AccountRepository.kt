package com.example.karaokestudio.data.online

import com.example.karaokestudio.data.remote.ApiClient
import com.example.karaokestudio.data.remote.ApiException
import com.example.karaokestudio.data.remote.SessionDto
import com.example.karaokestudio.data.remote.SessionState
import com.example.karaokestudio.data.remote.SessionStore
import com.example.karaokestudio.data.sync.SyncCoordinator
import com.example.karaokestudio.data.sync.SyncStore
import kotlinx.coroutines.flow.Flow

class AccountRepository(
    private val api: ApiClient,
    private val session: SessionStore,
    private val syncStore: SyncStore,
    private val coordinator: SyncCoordinator
) {

    val state: Flow<SessionState> = session.state

    suspend fun signIn(username: String, password: String): SessionDto {
        val result = api.login(username.trim(), password)
        afterSignIn()
        return result
    }

    suspend fun register(username: String, password: String, nickname: String): SessionDto {
        val result = api.register(username.trim(), password, nickname.trim().ifEmpty { null })
        afterSignIn()
        return result
    }

    suspend fun signOut() {
        api.logout()
        syncStore.clearOutbox()
        coordinator.setAutoSync(false)
    }

    suspend fun changePassword(current: String, new: String) {
        api.changePassword(current, new)
    }

    suspend fun deleteAccount(password: String) {
        api.deleteAccount(password)
        syncStore.clearOutbox()
        coordinator.setAutoSync(false)
    }

    suspend fun updateNickname(nickname: String) {
        val user = api.updateNickname(nickname)
        session.setNickname(user.nickname)
    }

    fun observePending(): Flow<Int> = syncStore.observePending()

    suspend fun pushNicknameIfSignedIn(nickname: String) {
        if (!session.current().signedIn) return
        try {
            updateNickname(nickname)
        } catch (e: ApiException) {
            return
        }
    }

    suspend fun saveServerUrl(url: String) {
        session.setServerUrl(url)
        syncStore.clearOutbox()
        coordinator.setAutoSync(false)
    }

    suspend fun setOnlineFeatures(enabled: Boolean) {
        session.setOnlineFeatures(enabled)
    }

    private suspend fun afterSignIn() {
        syncStore.enqueueAllLocal()
        coordinator.setAutoSync(true)
        coordinator.syncNow()
    }
}
