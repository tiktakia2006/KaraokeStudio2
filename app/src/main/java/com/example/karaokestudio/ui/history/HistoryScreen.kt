package com.example.karaokestudio.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.components.ConfirmDialog
import com.example.karaokestudio.ui.components.EmptyView
import com.example.karaokestudio.ui.components.PerformanceRow
import com.example.karaokestudio.ui.components.UiStateContent

@Composable
fun HistoryScreen(
    onOpenResult: (Long) -> Unit,
    onOpenCatalog: () -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Text(
                "Історія співу",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            )
            UiStateContent(state, onRetry = viewModel::retry) { data ->
                if (data.total == 0) {
                    EmptyView(
                        icon = Icons.Rounded.History,
                        title = "Історія порожня",
                        subtitle = "Після першого виконання тут з'являться дата, тривалість і результат.",
                        actionText = "Обрати пісню",
                        onAction = onOpenCatalog
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(HistoryFilter.entries) { option ->
                                FilterChip(
                                    selected = option == data.filter,
                                    onClick = { viewModel.selectFilter(option) },
                                    label = { Text(option.label) }
                                )
                            }
                        }
                        if (data.items.isEmpty()) {
                            EmptyView(
                                icon = Icons.Rounded.History,
                                title = "За цим фільтром нічого немає",
                                subtitle = "Оберіть інший фільтр або покажіть усі виконання.",
                                actionText = "Показати всі",
                                onAction = { viewModel.selectFilter(HistoryFilter.ALL) }
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(data.items, key = { it.performance.id }) { details ->
                                    PerformanceRow(
                                        details = details,
                                        onClick = { onOpenResult(details.performance.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                        trailing = {
                                            IconButton(onClick = { pendingDelete = details.performance.id }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Видалити виконання")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { id ->
        ConfirmDialog(
            title = "Видалити виконання?",
            text = "Запис голосу, якщо він є, також буде видалено з пристрою.",
            confirmText = "Видалити",
            onConfirm = {
                viewModel.delete(id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}
