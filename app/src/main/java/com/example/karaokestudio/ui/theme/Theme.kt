package com.example.karaokestudio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KaraokeColorScheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = VioletContainer,
    onPrimaryContainer = TextHigh,
    secondary = Sky,
    onSecondary = Color(0xFF00212F),
    tertiary = Pink,
    onTertiary = Color(0xFF3A0020),
    background = Background,
    onBackground = TextHigh,
    surface = SurfaceBase,
    onSurface = TextHigh,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextMid,
    surfaceContainer = SurfaceBase,
    surfaceContainerHigh = SurfaceCard,
    surfaceContainerHighest = SurfaceHigh,
    outline = OutlineColor,
    outlineVariant = OutlineColor,
    error = Danger,
    onError = Color.White
)

@Composable
fun KaraokeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KaraokeColorScheme,
        typography = KaraokeTypography,
        shapes = KaraokeShapes,
        content = content
    )
}
