@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.karaokestudio.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.karaokestudio.ui.AppViewModelProvider
import com.example.karaokestudio.ui.components.GradientButton
import com.example.karaokestudio.ui.components.StatCard
import com.example.karaokestudio.ui.components.UiStateContent
import com.example.karaokestudio.ui.theme.ActionEnd
import com.example.karaokestudio.ui.theme.ActionStart

@Composable
fun ProfileScreen(
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenRanking: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UiStateContent(state, onRetry = viewModel::retry) { data ->
        var name by remember(data.nickname) { mutableStateOf(data.nickname) }
        val error = viewModel.validateNickname(name)
        val changed = name.trim() != data.nickname

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(name)
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    ProfileViewModel.levelFor(data.summary.totalCount),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ім'я співака") },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "Від 2 до 24 символів") }
            )
            GradientButton(
                text = "Зберегти ім'я",
                onClick = { viewModel.saveNickname(name) },
                enabled = changed && error == null,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Виконань", data.summary.totalCount.toString(), Icons.Rounded.Mic, Modifier.weight(1f))
                StatCard("Найкраща оцінка", data.summary.bestScore?.toString() ?: "—", Icons.Rounded.Star, Modifier.weight(1f))
            }
            NavRow("Статистика", Icons.Rounded.BarChart, onOpenStats)
            NavRow("Рейтинг гравців", Icons.Rounded.EmojiEvents, onOpenRanking)
            NavRow("Обліковий запис і синхронізація", Icons.Rounded.CloudSync, onOpenAccount)
            NavRow("Налаштування", Icons.Rounded.Settings, onOpenSettings)
            AboutCard()
        }
    }
}

@Composable
private fun Avatar(name: String) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(listOf(ActionStart, ActionEnd))),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, style = MaterialTheme.typography.headlineMedium, color = Color.White)
    }
}

@Composable
private fun NavRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun AboutCard() {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Про застосунок", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Karaoke Studio — навчальний проєкт. Пісні з позначкою «Тест» створені для демонстрації: " +
                    "їхні тексти й музика не є справжніми піснями. Оцінка показує, наскільки активно ви співали " +
                    "разом із текстом (за рівнем мікрофона), але не перевіряє точність нот. " +
                    "Для чесного результату співайте в навушниках.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
