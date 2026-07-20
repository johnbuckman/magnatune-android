package com.magnatune.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic app palette. The concrete values are theme-dependent (light / dark) and supplied through
 * [LocalMagColors] by [MagnatuneTheme]. UI code keeps referring to `MagBg`, `MagCard`, … which now
 * resolve to the current theme's value — so the whole app follows the system light/dark setting
 * (matching the web app's dark-mode pass). Read only inside a @Composable scope.
 */
data class MagColors(
    val bg: Color,
    val card: Color,
    val accent: Color,
    val hairline: Color,
    val secondary: Color,
    val onBg: Color,
)

// Catalyst light palette (mirrors the iOS app).
val LightMagColors = MagColors(
    bg = Color(0xFFFFFFFF),
    card = Color(0xFFF2F2F7),
    accent = Color(0xFF007AFF),
    hairline = Color(0xFFD9D9D9),
    secondary = Color(0xFF6C6C70),
    onBg = Color(0xFF1C1C1E),
)

// Dark palette (mirrors the web app's dark mode — iOS system-dark tones).
val DarkMagColors = MagColors(
    bg = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    accent = Color(0xFF0A84FF),
    hairline = Color(0xFF38383A),
    secondary = Color(0xFF98989F),
    onBg = Color(0xFFF2F2F7),
)

val LocalMagColors = staticCompositionLocalOf { LightMagColors }

// Theme-aware accessors — same names the UI already uses, now resolving to the active theme.
val MagBg: Color @Composable @ReadOnlyComposable get() = LocalMagColors.current.bg
val MagCard: Color @Composable @ReadOnlyComposable get() = LocalMagColors.current.card
val MagAccent: Color @Composable @ReadOnlyComposable get() = LocalMagColors.current.accent
val MagHairline: Color @Composable @ReadOnlyComposable get() = LocalMagColors.current.hairline
val MagSecondary: Color @Composable @ReadOnlyComposable get() = LocalMagColors.current.secondary
val MagOnBg: Color @Composable @ReadOnlyComposable get() = LocalMagColors.current.onBg
