package com.example.karaokestudio.data.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.karaokestudio.BuildConfig
import com.example.karaokestudio.data.sync.SyncCursors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = "karaoke_session")

data class SessionState(
    val serverUrl: String,
    val signedIn: Boolean = false,
    val userId: Long? = null,
    val username: String? = null,
    val nickname: String? = null,
    val role: String? = null,
    val autoSync: Boolean = false,
    val onlineFeatures: Boolean = true,
    val catalogSince: Long = 0,
    val pullSince: Long = 0,
    val lastSyncAt: Long = 0,
    val lastSyncError: String? = null
)

class SessionStore(
    context: Context,
    private val protector: TokenProtector,
    private val defaultServerUrl: String = BuildConfig.DEFAULT_SERVER_URL
) : TokenStore, SyncCursors {

    private val store = context.applicationContext.sessionStore

    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val accessToken = stringPreferencesKey("access_token")
        val refreshToken = stringPreferencesKey("refresh_token")
        val userId = longPreferencesKey("user_id")
        val username = stringPreferencesKey("username")
        val nickname = stringPreferencesKey("nickname")
        val role = stringPreferencesKey("role")
        val autoSync = booleanPreferencesKey("auto_sync")
        val onlineFeatures = booleanPreferencesKey("online_features")
        val catalogSince = longPreferencesKey("catalog_since")
        val pullSince = longPreferencesKey("pull_since")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncError = stringPreferencesKey("last_sync_error")
    }

    private val preferences: Flow<Preferences> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val state: Flow<SessionState> = preferences.map { it.toState() }

    suspend fun current(): SessionState = state.first()

    override suspend fun serverUrl(): String = current().serverUrl

    override suspend fun accessToken(): String? =
        preferences.first()[Keys.accessToken]?.let { protector.unprotect(it) }

    override suspend fun refreshToken(): String? =
        preferences.first()[Keys.refreshToken]?.let { protector.unprotect(it) }

    override suspend fun saveSession(session: SessionDto) {
        val access = protector.protect(session.token)
        val refresh = protector.protect(session.refreshToken)
        store.edit {
            it[Keys.accessToken] = access
            it[Keys.refreshToken] = refresh
            it[Keys.userId] = session.user.id
            it[Keys.username] = session.user.username
            it[Keys.nickname] = session.user.nickname
            it[Keys.role] = session.user.role
        }
    }

    override suspend fun clearSession() {
        store.edit {
            it.remove(Keys.accessToken)
            it.remove(Keys.refreshToken)
            it.remove(Keys.userId)
            it.remove(Keys.username)
            it.remove(Keys.nickname)
            it.remove(Keys.role)
            it.remove(Keys.pullSince)
            it.remove(Keys.lastSyncError)
        }
    }

    suspend fun setNickname(nickname: String) {
        store.edit { it[Keys.nickname] = nickname }
    }

    suspend fun setServerUrl(url: String) {
        store.edit {
            it[Keys.serverUrl] = url
            it.remove(Keys.accessToken)
            it.remove(Keys.refreshToken)
            it.remove(Keys.userId)
            it.remove(Keys.username)
            it.remove(Keys.nickname)
            it.remove(Keys.role)
            it.remove(Keys.catalogSince)
            it.remove(Keys.pullSince)
            it.remove(Keys.lastSyncAt)
            it.remove(Keys.lastSyncError)
        }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        store.edit { it[Keys.autoSync] = enabled }
    }

    suspend fun setOnlineFeatures(enabled: Boolean) {
        store.edit { it[Keys.onlineFeatures] = enabled }
    }

    override suspend fun isSignedIn(): Boolean = current().signedIn

    override suspend fun catalogSince(): Long = current().catalogSince

    override suspend fun setCatalogSince(value: Long) {
        store.edit { it[Keys.catalogSince] = value }
    }

    override suspend fun pullSince(): Long = current().pullSince

    override suspend fun setPullSince(value: Long) {
        store.edit { it[Keys.pullSince] = value }
    }

    override suspend fun recordResult(at: Long, error: String?) {
        store.edit {
            if (error == null) {
                it[Keys.lastSyncAt] = at
                it.remove(Keys.lastSyncError)
            } else {
                it[Keys.lastSyncError] = error
            }
        }
    }

    private fun Preferences.toState(): SessionState {
        val access = this[Keys.accessToken]
        return SessionState(
            serverUrl = this[Keys.serverUrl] ?: defaultServerUrl,
            signedIn = access != null && this[Keys.refreshToken] != null,
            userId = this[Keys.userId],
            username = this[Keys.username],
            nickname = this[Keys.nickname],
            role = this[Keys.role],
            autoSync = this[Keys.autoSync] ?: false,
            onlineFeatures = this[Keys.onlineFeatures] ?: true,
            catalogSince = this[Keys.catalogSince] ?: 0,
            pullSince = this[Keys.pullSince] ?: 0,
            lastSyncAt = this[Keys.lastSyncAt] ?: 0,
            lastSyncError = this[Keys.lastSyncError]
        )
    }
}
