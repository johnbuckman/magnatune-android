package com.magnatune.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.theme.MagSecondary

// Favorites (Phase 4), Playlists (Phase 4), Settings (Phase 7) are filled in later.

@Composable
fun FavoritesScreen(vm: MagnatuneViewModel, nav: NavController, onPlay: OnPlay) = Placeholder("Favorites")

@Composable
fun PlaylistsScreen(vm: MagnatuneViewModel, nav: NavController, onPlay: OnPlay) = Placeholder("Playlists")

@Composable
fun SettingsScreen(vm: MagnatuneViewModel) = Placeholder("Settings")

@Composable
private fun Placeholder(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title — coming soon", color = MagSecondary)
    }
}
