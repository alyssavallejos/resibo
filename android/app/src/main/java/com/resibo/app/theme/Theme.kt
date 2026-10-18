package com.resibo.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ResiboPrimary,
    onPrimary = Color.White,
    primaryContainer = ResiboPrimaryContainer,
    onPrimaryContainer = ResiboOnPrimaryContainer,
    background = ResiboBackground,
    surface = ResiboSurface,
    surfaceVariant = ResiboSurface,
    surfaceTint = Color.Transparent,
    surfaceContainer = ResiboSurface,
    surfaceContainerLow = ResiboSurface,
    surfaceContainerHigh = ResiboSurface,
    surfaceContainerHighest = ResiboSurface,
    surfaceContainerLowest = ResiboSurface,
    onBackground = ResiboOnSurface,
    onSurface = ResiboOnSurface,
    onSurfaceVariant = ResiboOnSurfaceVariant,
    outline = ResiboOutline
)

@Composable
fun ResiboTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
