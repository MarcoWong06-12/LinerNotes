package com.linernotes.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    secondary = SecondaryAccent,
    background = VaultBlack,
    surface = VaultSurface,
    surfaceVariant = VaultSurfaceVariant,
    onPrimary = VaultBlack,
    onSecondary = VaultBlack,
    onBackground = OnSurfaceWhite,
    onSurface = OnSurfaceWhite,
    onSurfaceVariant = OnSurfaceMuted
)

@Composable
fun LinerNotesTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
