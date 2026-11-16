@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.karaokestudio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.karaokestudio.data.local.entity.Song
import com.example.karaokestudio.data.local.model.PerformanceDetails
import com.example.karaokestudio.ui.theme.ActionEnd
import com.example.karaokestudio.ui.theme.ActionStart
import com.example.karaokestudio.ui.theme.Amber
import com.example.karaokestudio.ui.theme.Mint
import com.example.karaokestudio.ui.theme.OutlineColor
import com.example.karaokestudio.ui.theme.Pink
import com.example.karaokestudio.ui.theme.Sky
import com.example.karaokestudio.ui.theme.TextMid
import com.example.karaokestudio.ui.theme.Violet

private val CoverGradients = listOf(
    listOf(Color(0xFF8B5CF6), Color(0xFF38BDF8)),
    listOf(Color(0xFF6D28D9), Color(0xFFF472B6)),
    listOf(Color(0xFF0EA5E9), Color(0xFF8B5CF6)),
    listOf(Color(0xFF4C1D95), Color(0xFF2563EB)),
    listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)),
    listOf(Color(0xFF14B8A6), Color(0xFF6366F1))
)

@Composable
fun CoverArt(coverSource: String?, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    if (coverSource != null && coverSource.startsWith("https://")) {
        val fallbackIndex = Math.floorMod(coverSource.hashCode(), CoverGradients.size)
        SubcomposeAsyncImage(
            model = coverSource,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(corner)),
            contentScale = ContentScale.Crop,
            loading = { GradientCover(fallbackIndex, Modifier.fillMaxSize(), corner) },
            error = { GradientCover(fallbackIndex, Modifier.fillMaxSize(), corner) }
        )
    } else {
        val index = coverSource?.removePrefix("gradient:")?.toIntOrNull() ?: 0
        GradientCover(index, modifier, corner)
    }
}

@Composable
private fun GradientCover(index: Int, modifier: Modifier, corner: Dp) {
    val colors = CoverGradients[index.mod(CoverGradients.size)]
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.fillMaxSize(0.5f)
        )
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(listOf(ActionStart, ActionEnd)), alpha = if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SongCard(
    song: Song,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(song.coverSource, Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(song.durationMs), style = MaterialTheme.typography.labelMedium, color = TextMid)
                    if (song.isDemo) {
                        Spacer(Modifier.width(8.dp))
                        DemoBadge()
                    }
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Прибрати з обраного" else "Додати до обраного",
                    tint = if (isFavorite) Pink else TextMid
                )
            }
        }
    }
}

@Composable
fun SongTile(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(140.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        CoverArt(song.coverSource, Modifier.size(140.dp), corner = 16.dp)
        Spacer(Modifier.size(8.dp))
        Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ScoreChip(score: Int?, modifier: Modifier = Modifier) {
    val color = when {
        score == null -> TextMid
        score >= 75 -> Mint
        score >= 50 -> Sky
        else -> Amber
    }
    Surface(modifier, shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f)) {
        Text(
            score?.toString() ?: "—",
            color = color,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PerformanceRow(
    details: PerformanceDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    val performance = details.performance
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(details.song.coverSource, Modifier.size(48.dp), corner = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    details.song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatDateTime(performance.performedAt)}, ${formatDuration(performance.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (details.recording != null || performance.isDemo) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (details.recording != null) {
                            Icon(Icons.Rounded.Mic, contentDescription = "Є запис", tint = Violet, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Запис голосу", style = MaterialTheme.typography.labelMedium, color = Violet)
                        }
                        if (performance.isDemo) {
                            if (details.recording != null) Spacer(Modifier.width(8.dp))
                            DemoBadge()
                        }
                    }
                }
            }
            ScoreChip(performance.score)
            trailing?.invoke()
        }
    }
}

@Composable
fun StarsRow(stars: Int, modifier: Modifier = Modifier, starSize: Dp = 28.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { i ->
            Icon(
                if (i < stars) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = null,
                tint = if (i < stars) Amber else OutlineColor,
                modifier = Modifier.size(starSize)
            )
        }
    }
}

@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, diameter: Dp = 180.dp) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(score) { progress.animateTo(score / 100f, tween(1200)) }
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = OutlineColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.linearGradient(listOf(Violet, Sky)),
                startAngle = 135f,
                sweepAngle = 270f * progress.value,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), style = MaterialTheme.typography.displayLarge)
            Text("із 100", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
