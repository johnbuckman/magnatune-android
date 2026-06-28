package com.magnatune.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.magnatune.player.ui.components.SectionHeader
import com.magnatune.player.ui.screens.AlbumDetailScreen
import com.magnatune.player.ui.screens.AlbumsScreen
import com.magnatune.player.ui.screens.ArtistDetailScreen
import com.magnatune.player.ui.screens.ArtistsScreen
import com.magnatune.player.ui.screens.CatalogPlaylistDetailScreen
import com.magnatune.player.ui.screens.FavoritesScreen
import com.magnatune.player.ui.screens.FeaturedScreen
import com.magnatune.player.ui.screens.GenreDetailScreen
import com.magnatune.player.ui.screens.GenresScreen
import com.magnatune.player.ui.screens.OnPlay
import com.magnatune.player.ui.screens.PlaylistsScreen
import com.magnatune.player.ui.screens.PopularScreen
import com.magnatune.player.ui.screens.SearchScreen
import com.magnatune.player.ui.screens.SettingsScreen
import com.magnatune.player.ui.screens.TagDetailScreen
import com.magnatune.player.ui.screens.TagsScreen
import com.magnatune.player.ui.theme.MagCard

private fun iconFor(tab: NavTab): ImageVector = when (tab) {
    NavTab.POPULAR -> Icons.Filled.Star
    NavTab.ARTISTS -> Icons.Filled.Person
    NavTab.ALBUMS -> Icons.Filled.Album
    NavTab.GENRES -> Icons.Filled.Category
    NavTab.TAGS -> Icons.Filled.Tag
    NavTab.FEATURED -> Icons.AutoMirrored.Filled.QueueMusic
    NavTab.SEARCH -> Icons.Filled.Search
    NavTab.FAVORITES -> Icons.Filled.Favorite
    NavTab.PLAYLISTS -> Icons.Filled.PlaylistPlay
    NavTab.SETTINGS -> Icons.Filled.Settings
}

@Composable
fun RootScreen(vm: MagnatuneViewModel, onPlay: OnPlay, miniPlayer: @Composable () -> Unit = {}) {
    val nav = rememberNavController()
    Row(Modifier.fillMaxSize()) {
        NavSidebar(nav, Modifier.width(168.dp).fillMaxHeight().padding(start = 8.dp, top = 8.dp, bottom = 8.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).fillMaxHeight()) {
            ContentTopBar(nav)
            MainNav(vm, nav, onPlay, Modifier.weight(1f))
            miniPlayer()
        }
    }
}

@Composable
private fun NavSidebar(nav: NavController, modifier: Modifier = Modifier) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    Surface(
        color = MagCard, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        shadowElevation = 2.dp, modifier = modifier,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxHeight().padding(8.dp)) {
            // Wordmark logo (top-left).
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.magnatune.player.R.drawable.magnatune_logo),
                contentDescription = "Magnatune",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                alignment = androidx.compose.ui.Alignment.CenterStart,
                modifier = Modifier.fillMaxWidth().height(42.dp).padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
            )
            androidx.compose.foundation.layout.Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp),
            ) {
                NavTab.entries.filter { it != NavTab.SETTINGS }.forEach { tab ->
                    NavRow(tab, current == tab.route) {
                        nav.navigate(tab.route) { popUpTo(Routes.POPULAR); launchSingleTop = true }
                    }
                }
            }
            // Mascot above Settings: wide (1000x392), centered, with its left/right edges clipped to
            // the column — matches iOS.
            Box(
                Modifier.fillMaxWidth().height(200.dp)
                    .offset(y = 20.dp)
                    .clipToBounds(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.magnatune.player.R.drawable.magnatune_mascot),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.height(240.dp).width(612.dp),
                )
            }
            NavRow(NavTab.SETTINGS, current == NavTab.SETTINGS.route) {
                nav.navigate(NavTab.SETTINGS.route) { popUpTo(Routes.POPULAR); launchSingleTop = true }
            }
        }
    }
}

@Composable
private fun NavRow(tab: NavTab, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(if (selected) com.magnatune.player.ui.theme.MagAccent.copy(alpha = 0.14f)
                       else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(iconFor(tab), null, tint = if (selected) com.magnatune.player.ui.theme.MagAccent
            else com.magnatune.player.ui.theme.MagSecondary, modifier = Modifier.size(20.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        Text(tab.title, style = MaterialTheme.typography.bodyMedium,
            color = if (selected) com.magnatune.player.ui.theme.MagAccent else MaterialTheme.colorScheme.onBackground)
    }
}

/** Thin top bar in the content column: shows a back arrow + title on detail (non-top-level) routes. */
@Composable
private fun ContentTopBar(nav: NavController) {
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isTopLevel = route == null || NavTab.entries.any { it.route == route }
    if (isTopLevel) return
    // iOS-style nav back: accent chevron + "Back", no filled bar.
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable { nav.popBackStack() }
            .padding(start = 8.dp, end = 8.dp, top = 36.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back",
            tint = com.magnatune.player.ui.theme.MagAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(2.dp))
        Text("Back", style = MaterialTheme.typography.bodyLarge, color = com.magnatune.player.ui.theme.MagAccent)
    }
}

@Composable
private fun MainNav(vm: MagnatuneViewModel, nav: NavHostController, onPlay: OnPlay, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        NavHost(nav, startDestination = Routes.POPULAR) {
            composable(Routes.POPULAR) { PopularScreen(vm, nav) }
            composable(Routes.ARTISTS) { ArtistsScreen(vm, nav) }
            composable(Routes.ALBUMS) { AlbumsScreen(vm, nav) }
            composable(Routes.GENRES) { GenresScreen(vm, nav) }
            composable(Routes.TAGS) { TagsScreen(vm, nav) }
            composable(Routes.FEATURED) { FeaturedScreen(vm, nav) }
            composable(Routes.SEARCH) { SearchScreen(vm, nav, onPlay) }
            composable(Routes.FAVORITES) { FavoritesScreen(vm, nav, onPlay) }
            composable(Routes.PLAYLISTS) { PlaylistsScreen(vm, nav, onPlay) }
            composable(Routes.SETTINGS) { SettingsScreen(vm) }
            longArg(Routes.ARTIST) { ArtistDetailScreen(vm, nav, it, onPlay) }
            longArg(Routes.ALBUM) { AlbumDetailScreen(vm, nav, it, onPlay) }
            longArg(Routes.GENRE) { GenreDetailScreen(vm, nav, it) }
            longArg(Routes.TAG) { TagDetailScreen(vm, nav, it) }
            longArg(Routes.CATALOG_PLAYLIST) { CatalogPlaylistDetailScreen(vm, nav, it, onPlay) }
            longArg(Routes.USER_PLAYLIST) { com.magnatune.player.ui.screens.UserPlaylistDetailScreen(vm, nav, it, onPlay) }
        }
    }
}

/** composable() helper for a route with a single Long {id} arg. */
private fun NavGraphBuilder.longArg(route: String, content: @Composable (Long) -> Unit) {
    composable(route, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
        content(entry.arguments?.getLong("id") ?: 0L)
    }
}
