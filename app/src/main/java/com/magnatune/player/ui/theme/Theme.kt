package com.magnatune.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The single drop-shadow used by both the sidebar card and the audio-player card, so they match. */
fun Modifier.magCardShadow(): Modifier = this.shadow(
    elevation = 2.dp,
    shape = RoundedCornerShape(12.dp),
    clip = false,
    ambientColor = Color.Black,
    spotColor = Color.Black,
)

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
