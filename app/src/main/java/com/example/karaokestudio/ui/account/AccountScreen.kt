package com.example.karaokestudio.ui.account

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.data.remote.SessionState
import com.example.karaokestudio.data.sync.SyncStatus
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.components.ConfirmDialog
import com.example.karaokestudio.ui.components.ScreenTopBar
import com.example.karaokestudio.ui.components.formatDateTime
import com.example.karaokestudio.ui.theme.Danger

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var passwordDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var signOutDialog by remember { mutableStateOf(false) }
    val session = ui.session

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar("Обліковий запис", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ui.form.message?.let { MessageCard(it, viewModel::clearMessage) }
            if (session != null) {
                if (session.signedIn) {
                    ProfileCard(
                        session = session,
                        onChangePassword = { passwordDialog = true },
                        onSignOut = { signOutDialog = true },
                        onDelete = { deleteDialog = true }
                    )
                } else {
                    AuthCard(ui.form, viewModel)
                }
                SyncCard(ui, session, viewModel)
                ServerCard(ui.form, session, viewModel)
                LegalCard(session.serverUrl)
            }
        }
    }

    if (passwordDialog) {
        PasswordDialog(
            onSubmit = { current, new, done -> viewModel.changePassword(current, new, done) },
            onDismiss = { passwordDialog = false }
        )
    }
    if (deleteDialog) {
        DeleteDialog(
            onSubmit = { password, done -> viewModel.deleteAccount(password, done) },
            onDismiss = { deleteDialog = false }
        )
    }
    if (signOutDialog) {
        ConfirmDialog(
            title = "Вийти з облікового запису?",
            text = "Дані на пристрої залишаться. Незавершені зміни буде відкинуто, а після повторного входу все синхронізується знову.",
            confirmText = "Вийти",
            onConfirm = {
                signOutDialog = false
                viewModel.signOut()
            },
            onDismiss = { signOutDialog = false }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun MessageCard(text: String, onClose: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
            TextButton(onClick = onClose) { Text("Закрити") }
        }
    }
}

@Composable
private fun AuthCard(form: AccountForm, viewModel: AccountViewModel) {
    val register = form.mode == AuthMode.REGISTER
    SectionCard(if (register) "Реєстрація" else "Вхід") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !register,
                onClick = { viewModel.setMode(AuthMode.SIGN_IN) },
                label = { Text("Вхід") }
            )
            FilterChip(
                selected = register,
                onClick = { viewModel.setMode(AuthMode.REGISTER) },
                label = { Text("Реєстрація") }
            )
        }
        OutlinedTextField(
            value = form.username,
            onValueChange = viewModel::onUsername,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ім'я користувача") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )
        if (register) {
            OutlinedTextField(
                value = form.nickname,
                onValueChange = viewModel::onNickname,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Нікнейм у рейтингу") },
                singleLine = true,
                supportingText = { Text("Якщо залишити порожнім, візьмемо ім'я з профілю") }
            )
        }
        OutlinedTextField(
            value = form.password,
            onValueChange = viewModel::onPassword,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { if (register) Text("Щонайменше 8 символів") }
        )
        form.error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = viewModel::submit, enabled = !form.busy, modifier = Modifier.fillMaxWidth()) {
            if (form.busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (register) "Створити обліковий запис" else "Увійти")
        }
        Text(
            "Обліковий запис потрібен, щоб синхронізувати обране й історію між пристроями та потрапляти до рейтингу. Без нього застосунок працює повністю автономно.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileCard(
    session: SessionState,
    onChangePassword: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit
) {
    SectionCard("Ви увійшли") {
        Column {
            Text(session.nickname ?: session.username.orEmpty(), style = MaterialTheme.typography.titleLarge)
            Text(
                "@${session.username.orEmpty()}" + if (session.role == "admin") " · адміністратор" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onChangePassword, modifier = Modifier.fillMaxWidth()) { Text("Змінити пароль") }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Вийти") }
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text("Видалити обліковий запис", color = Danger)
        }
    }
}

@Composable
private fun SyncCard(ui: AccountUiState, session: SessionState, viewModel: AccountViewModel) {
    val running = ui.syncStatus is SyncStatus.Running
    SectionCard("Синхронізація") {
        Text(syncSummary(ui, session), style = MaterialTheme.typography.bodyMedium)
        if (ui.pending > 0) {
            Text(
                "Очікують відправлення: ${ui.pending}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!ui.online) {
            Text(
                "Немає мережі. Зміни збережуться на пристрої й відправляться, щойно з'явиться з'єднання.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = viewModel::syncNow, enabled = !running, modifier = Modifier.fillMaxWidth()) {
            if (running) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (session.signedIn) "Синхронізувати зараз" else "Оновити каталог")
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Автоматична синхронізація")
                Text(
                    if (session.signedIn) "Після змін і раз на кілька годин за наявності мережі" else "Доступна після входу",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = session.autoSync && session.signedIn,
                onCheckedChange = viewModel::setAutoSync,
                enabled = session.signedIn
            )
        }
    }
}

private fun syncSummary(ui: AccountUiState, session: SessionState): String {
    val status = ui.syncStatus
    return when {
        status is SyncStatus.Running -> "Синхронізація триває…"
        status is SyncStatus.Failed -> "Остання спроба невдала: ${status.message}"
        status is SyncStatus.Success -> {
            val report = status.report
            val sent = report.pushed + report.stale
            buildString {
                append("Синхронізовано ${formatDateTime(status.at)}. ")
                append("Оновлено пісень: ${report.catalogUpdated}")
                if (session.signedIn) append(", відправлено змін: $sent, отримано: ${report.pulled}")
                append('.')
                if (report.pullDeferred) append(" Отримання змін відкладено, поки не відправлені власні.")
                if (report.rejected > 0) append(" Сервер відхилив змін: ${report.rejected}.")
            }
        }
        session.lastSyncError != null -> "Остання спроба невдала: ${session.lastSyncError}"
        session.lastSyncAt > 0 -> "Остання синхронізація: ${formatDateTime(session.lastSyncAt)}."
        else -> "Синхронізацію ще не виконували."
    }
}

@Composable
private fun ServerCard(form: AccountForm, session: SessionState, viewModel: AccountViewModel) {
    SectionCard("Сервер") {
        OutlinedTextField(
            value = form.serverUrlDraft ?: session.serverUrl,
            onValueChange = viewModel::onServerUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Адреса сервера") },
            singleLine = true,
            isError = form.serverUrlError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            supportingText = { Text(form.serverUrlError ?: "Для емулятора: http://10.0.2.2:8080") }
        )
        if (form.serverUrlDraft != null && form.serverUrlDraft != session.serverUrl) {
            Text(
                "Зміна адреси завершить поточний сеанс і скине позначки синхронізації.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = viewModel::saveServerUrl, modifier = Modifier.fillMaxWidth()) { Text("Зберегти адресу") }
        }
    }
}

@Composable
private fun LegalCard(serverUrl: String) {
    val context = LocalContext.current
    fun open(path: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl + path))
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            return
        }
    }
    SectionCard("Документи") {
        OutlinedButton(onClick = { open("/privacy") }, modifier = Modifier.fillMaxWidth()) { Text("Політика конфіденційності") }
        OutlinedButton(onClick = { open("/terms") }, modifier = Modifier.fillMaxWidth()) { Text("Умови використання") }
        Text(
            "Сервер зберігає ім'я користувача, нікнейм, хеш пароля, обране та виконання. Пароль у відкритому вигляді не зберігається.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PasswordDialog(onSubmit: (String, String, (String?) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Зміна пароля") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it.take(128) },
                    label = { Text("Поточний пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = new,
                    onValueChange = { new = it.take(128) },
                    label = { Text("Новий пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && current.isNotEmpty() && new.isNotEmpty(),
                onClick = {
                    busy = true
                    onSubmit(current, new) { problem ->
                        busy = false
                        if (problem == null) onDismiss() else error = problem
                    }
                }
            ) { Text("Змінити") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Скасувати") } }
    )
}

@Composable
private fun DeleteDialog(onSubmit: (String, (String?) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Видалити обліковий запис?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Обране, виконання та записи голосу на сервері буде видалено без можливості відновлення. Дані на пристрої залишаться.")
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = { Text("Пароль для підтвердження") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && password.isNotEmpty(),
                onClick = {
                    busy = true
                    onSubmit(password) { problem ->
                        busy = false
                        if (problem == null) onDismiss() else error = problem
                    }
                }
            ) { Text("Видалити", color = Danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Скасувати") } }
    )
}
