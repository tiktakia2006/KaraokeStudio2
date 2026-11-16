package com.example.karaokestudio.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaokestudio.data.network.NetworkMonitor
import com.example.karaokestudio.data.online.AccountRepository
import com.example.karaokestudio.data.online.AccountValidator
import com.example.karaokestudio.data.remote.ApiErrorMessages
import com.example.karaokestudio.data.remote.ApiException
import com.example.karaokestudio.data.remote.ServerUrl
import com.example.karaokestudio.data.remote.SessionState
import com.example.karaokestudio.data.settings.SettingsRepository
import com.example.karaokestudio.data.sync.SyncCoordinator
import com.example.karaokestudio.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SIGN_IN, REGISTER }

data class AccountForm(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val username: String = "",
    val password: String = "",
    val nickname: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val serverUrlDraft: String? = null,
    val serverUrlError: String? = null,
    val message: String? = null
)

data class AccountUiState(
    val session: SessionState? = null,
    val form: AccountForm = AccountForm(),
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val pending: Int = 0,
    val online: Boolean = true
)

class AccountViewModel(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val coordinator: SyncCoordinator,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val form = MutableStateFlow(AccountForm())

    val state: StateFlow<AccountUiState> = combine(
        accountRepository.state,
        form,
        coordinator.status,
        accountRepository.observePending(),
        networkMonitor.isOnline
    ) { session, formState, status, pending, online ->
        AccountUiState(session, formState, status, pending, online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    fun setMode(mode: AuthMode) = form.update { it.copy(mode = mode, error = null) }

    fun onUsername(value: String) = form.update { it.copy(username = value.take(24), error = null) }

    fun onPassword(value: String) = form.update { it.copy(password = value.take(128), error = null) }

    fun onNickname(value: String) = form.update { it.copy(nickname = value.take(24), error = null) }

    fun onServerUrl(value: String) = form.update { it.copy(serverUrlDraft = value, serverUrlError = null) }

    fun clearMessage() = form.update { it.copy(message = null) }

    fun saveServerUrl() {
        val draft = form.value.serverUrlDraft ?: return
        val normalized = ServerUrl.normalize(draft)
        if (normalized == null) {
            form.update { it.copy(serverUrlError = "Введіть адресу виду https://example.com або http://10.0.2.2:8080") }
            return
        }
        viewModelScope.launch {
            accountRepository.saveServerUrl(normalized)
            form.update { it.copy(serverUrlDraft = null, serverUrlError = null, message = "Адресу сервера збережено. Увійдіть знову.") }
        }
    }

    fun submit() {
        val current = form.value
        if (current.busy) return
        val problem = AccountValidator.username(current.username)
            ?: AccountValidator.password(current.password)
            ?: if (current.mode == AuthMode.REGISTER && current.nickname.isNotBlank()) AccountValidator.nickname(current.nickname) else null
        if (problem != null) {
            form.update { it.copy(error = problem) }
            return
        }
        form.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val session = if (current.mode == AuthMode.REGISTER) {
                    val nickname = current.nickname.trim().ifEmpty { settingsRepository.settings.first().nickname }
                    accountRepository.register(current.username, current.password, nickname)
                } else {
                    accountRepository.signIn(current.username, current.password)
                }
                settingsRepository.update { it.copy(nickname = session.user.nickname) }
                form.update { AccountForm(message = "Вітаємо, ${session.user.nickname}! Синхронізацію запущено.") }
            } catch (e: ApiException) {
                form.update { it.copy(busy = false, error = ApiErrorMessages.describe(e)) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            accountRepository.signOut()
            form.update { it.copy(message = "Ви вийшли з облікового запису. Локальні дані залишилися на пристрої.") }
        }
    }

    fun syncNow() = coordinator.syncNow()

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch { coordinator.setAutoSync(enabled) }
    }

    fun changePassword(current: String, new: String, onDone: (String?) -> Unit) {
        val problem = AccountValidator.password(new)
        if (problem != null) {
            onDone(problem)
            return
        }
        viewModelScope.launch {
            try {
                accountRepository.changePassword(current, new)
                form.update { it.copy(message = "Пароль змінено.") }
                onDone(null)
            } catch (e: ApiException) {
                onDone(ApiErrorMessages.describe(e))
            }
        }
    }

    fun deleteAccount(password: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                accountRepository.deleteAccount(password)
                form.update { it.copy(message = "Обліковий запис видалено разом із даними на сервері.") }
                onDone(null)
            } catch (e: ApiException) {
                onDone(ApiErrorMessages.describe(e))
            }
        }
    }
}
