package com.magnatune.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MagnatuneColors = lightColorScheme(
    primary = MagAccent,
    onPrimary = MagBg,
    background = MagBg,
    onBackground = MagOnBg,
    surface = MagBg,
    onSurface = MagOnBg,
    surfaceVariant = MagCard,
    onSurfaceVariant = MagSecondary,
    secondary = MagSecondary,
    outline = MagHairline,
)

/** The app uses the iOS Catalyst light look on all platforms (no dark variant yet). */
@Composable
fun MagnatuneTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MagnatuneColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
