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
import androidx.compose.ui.layout.layout
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
import com.magnatune.player.ui.theme.magCardShadow

private fun iconFor(tab: NavTab): String = when (tab) {
    NavTab.POPULAR -> com.magnatune.player.ui.components.Fa.star
    NavTab.ARTISTS -> com.magnatune.player.ui.components.Fa.user
    NavTab.ALBUMS -> com.magnatune.player.ui.components.Fa.compactDisc
    NavTab.SONGS -> com.magnatune.player.ui.components.Fa.music
    NavTab.GENRES -> com.magnatune.player.ui.components.Fa.guitar
    NavTab.TAGS -> com.magnatune.player.ui.components.Fa.tag
    NavTab.FEATURED -> com.magnatune.player.ui.components.Fa.rectangleList
    NavTab.SEARCH -> com.magnatune.player.ui.components.Fa.magnifyingGlass
    NavTab.FAVORITES -> com.magnatune.player.ui.components.Fa.heart
    NavTab.PLAYLISTS -> com.magnatune.player.ui.components.Fa.listUl
    NavTab.SETTINGS -> com.magnatune.player.ui.components.Fa.gear
}

@Composable
fun RootScreen(vm: MagnatuneViewModel, onPlay: OnPlay, miniPlayer: @Composable (NavController) -> Unit = {}) {
    val nav = rememberNavController()
    NavRestore(nav, vm.settings)
    Row(Modifier.fillMaxSize()) {
        NavSidebar(nav, Modifier.width(168.dp).fillMaxHeight().padding(start = 8.dp, top = 8.dp, bottom = 8.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).fillMaxHeight()) {
            ContentTopBar(nav)
            MainNav(vm, nav, onPlay, Modifier.weight(1f))
            miniPlayer(nav)
        }
    }
}

@Composable
private fun NavSidebar(nav: NavController, modifier: Modifier = Modifier) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    Surface(
        color = MagCard, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = modifier.magCardShadow(),
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
                // Grouped + ordered like the OSX/web sidebar: Browse, gap, Library, gap, Search.
                val navGroups = listOf(
                    listOf(NavTab.POPULAR, NavTab.ARTISTS, NavTab.ALBUMS, NavTab.GENRES, NavTab.TAGS, NavTab.FEATURED, NavTab.SONGS),
                    listOf(NavTab.FAVORITES, NavTab.PLAYLISTS),
                    listOf(NavTab.SEARCH),
                )
                navGroups.forEachIndexed { i, group ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    group.forEach { tab ->
                        NavRow(tab, current == tab.route) {
                            nav.navigate(tab.route) { popUpTo(Routes.POPULAR); launchSingleTop = true }
                        }
                    }
                }
            }
            // Mascot above Settings: wide (1000x392), centered, with its left/right edges clipped to
            // the column — matches iOS.
            // Mascot: filled to the specified HEIGHT (the box height); since the image is far wider
            // than the narrow sidebar, Crop scales it by height and truncates the left/right sides
            // (centered). The height is shown in full — never cropped top/bottom. `fullBleedWidth`
            // expands it past the Column's 8dp horizontal padding so it truncates right at the card
            // edges instead of a few px short.
            val mascotH = 74.dp
            Box(
                Modifier.fillMaxWidth().fullBleedWidth(8.dp).height(mascotH).offset(y = 0.dp).clipToBounds()
                    .clickable { nav.navigate(Routes.HELP) { popUpTo(Routes.POPULAR); launchSingleTop = true } },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.magnatune.player.R.drawable.magnatune_mascot),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
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
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        com.magnatune.player.ui.components.FaIcon(iconFor(tab), null,
            tint = if (selected) com.magnatune.player.ui.theme.MagAccent else com.magnatune.player.ui.theme.MagSecondary,
            size = 18.dp)
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
    val isTopLevel = route == null || route == Routes.HELP || NavTab.entries.any { it.route == route }
    if (isTopLevel) return
    // iOS-style nav back: accent chevron + "Back", no filled bar.
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable { nav.popBackStack() }
            .padding(start = 8.dp, end = 8.dp, top = 36.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.magnatune.player.ui.components.FaIcon(com.magnatune.player.ui.components.Fa.chevronLeft, "Back",
            tint = com.magnatune.player.ui.theme.MagAccent, size = 16.dp)
        Spacer(Modifier.width(4.dp))
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
            composable(Routes.SONGS) { com.magnatune.player.ui.screens.SongsScreen(vm, nav, onPlay) }
            composable(Routes.GENRES) { GenresScreen(vm, nav) }
            composable(Routes.TAGS) { TagsScreen(vm, nav) }
            composable(Routes.FEATURED) { FeaturedScreen(vm, nav) }
            composable(Routes.SEARCH) { SearchScreen(vm, nav, onPlay) }
            composable(Routes.FAVORITES) { FavoritesScreen(vm, nav, onPlay) }
            composable(Routes.PLAYLISTS) { PlaylistsScreen(vm, nav, onPlay) }
            composable(Routes.SETTINGS) {
                SettingsScreen(vm, onShowHelp = {
                    nav.navigate(Routes.HELP) { popUpTo(Routes.POPULAR); launchSingleTop = true }
                })
            }
            composable(Routes.HELP) {
                com.magnatune.player.ui.screens.HelpScreen(onNavigate = { route ->
                    nav.navigate(route) { popUpTo(Routes.POPULAR); launchSingleTop = true }
                })
            }
            longArg(Routes.ARTIST) { ArtistDetailScreen(vm, nav, it, onPlay) }
            composable(
                Routes.ALBUM,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("song") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val song = entry.arguments?.getLong("song") ?: -1L
                AlbumDetailScreen(vm, nav, id, onPlay, highlightSongId = song.takeIf { it > 0 })
            }
            longArg(Routes.GENRE) { GenreDetailScreen(vm, nav, it) }
            longArg(Routes.TAG) { TagDetailScreen(vm, nav, it) }
            longArg(Routes.CATALOG_PLAYLIST) { CatalogPlaylistDetailScreen(vm, nav, it, onPlay) }
            longArg(Routes.USER_PLAYLIST) { com.magnatune.player.ui.screens.UserPlaylistDetailScreen(vm, nav, it, onPlay) }
        }
    }
}

/** Expands a child by [eachSide] on the left and right (and re-centers it) so it bleeds past the
 *  parent's horizontal padding, while still reporting the original width to the layout. */
private fun Modifier.fullBleedWidth(eachSide: androidx.compose.ui.unit.Dp): Modifier =
    this.layout { measurable, constraints ->
        val extra = eachSide.roundToPx()
        val newMax = (constraints.maxWidth + extra * 2).coerceAtLeast(0)
        val placeable = measurable.measure(constraints.copy(minWidth = newMax, maxWidth = newMax))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
    }

/** composable() helper for a route with a single Long {id} arg. */
private fun NavGraphBuilder.longArg(route: String, content: @Composable (Long) -> Unit) {
    composable(route, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
        content(entry.arguments?.getLong("id") ?: 0L)
    }
}

/**
 * Remembers the page you were on across app relaunches: replays the saved nav back stack once on
 * launch (so you land on the exact same page, with Back working), and persists the stack on every
 * destination change.
 */
@Composable
private fun NavRestore(nav: NavHostController, settings: com.magnatune.player.data.Settings) {
    // Capture the saved stack during composition, before anything navigates.
    val savedStack = remember { settings.navBackStack }

    LaunchedEffect(Unit) {
        // Suspend until the NavHost has installed its graph (first emission = start destination),
        // then replay the saved stack bottom-up. No persistence happens during the replay, so the
        // saved deep stack on disk isn't clobbered by these intermediate navigations.
        nav.currentBackStackEntryFlow.first()
        savedStack.forEachIndexed { i, route ->
            if (i == 0 && route == Routes.POPULAR) return@forEachIndexed   // already the start destination
            runCatching {
                nav.navigate(route) {
                    // Top-level sections sit directly above Popular (mirrors the sidebar); details just push.
                    if (NavTab.entries.any { it.route == route }) { popUpTo(Routes.POPULAR); launchSingleTop = true }
                }
            }
        }
        // Persist the full back stack on every change. currentBackStack is a StateFlow that reflects
        // the SETTLED stack (unlike reading currentBackStack.value inside an OnDestinationChanged
        // listener, which races on pushes), so detail pushes are captured reliably. The first
        // emission re-saves the restored page (a harmless no-op write).
        nav.currentBackStack.collect { stack ->
            settings.navBackStack = stack.mapNotNull { it.concreteRoute() }
        }
    }
}

/** The concrete route for a back-stack entry: top-level routes as-is, detail routes with {id} filled. */
private fun NavBackStackEntry.concreteRoute(): String? {
    val pat = destination.route ?: return null
    if (!pat.contains("{id}")) return pat
    val id = arguments?.getLong("id") ?: return pat
    var r = pat.replace("{id}", id.toString())
    // Drop the optional ?song=… highlight arg when persisting, so a relaunch reopens the album
    // without re-flashing the song.
    if (r.contains("?")) r = r.substringBefore("?")
    return r
}
