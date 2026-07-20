package com.magnatune.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

private val LightScheme = lightColorScheme(
    primary = LightMagColors.accent,
    onPrimary = LightMagColors.bg,
    background = LightMagColors.bg,
    onBackground = LightMagColors.onBg,
    surface = LightMagColors.bg,
    onSurface = LightMagColors.onBg,
    surfaceVariant = LightMagColors.card,
    onSurfaceVariant = LightMagColors.secondary,
    secondary = LightMagColors.secondary,
    outline = LightMagColors.hairline,
)

private val DarkScheme = darkColorScheme(
    primary = DarkMagColors.accent,
    onPrimary = Color.White,
    background = DarkMagColors.bg,
    onBackground = DarkMagColors.onBg,
    surface = DarkMagColors.bg,
    onSurface = DarkMagColors.onBg,
    surfaceVariant = DarkMagColors.card,
    onSurfaceVariant = DarkMagColors.secondary,
    secondary = DarkMagColors.secondary,
    outline = DarkMagColors.hairline,
)

/** Follows the system light/dark setting. The semantic `Mag*` tokens (see Color.kt) and the
 *  Material color scheme are both swapped, so the whole UI re-themes. */
@Composable
fun MagnatuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val mag = if (darkTheme) DarkMagColors else LightMagColors
    CompositionLocalProvider(LocalMagColors provides mag) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
