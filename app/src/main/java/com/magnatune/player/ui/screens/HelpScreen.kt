package com.magnatune.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magnatune.player.ui.Routes
import com.magnatune.player.ui.theme.MagAccent

/**
 * The Help page, shown in the content area like any other section. Opened by tapping the
 * mascot in the sidebar or Settings -> About -> "App Help". Mirrors the iOS/web help.
 * Bolded section names are rendered as tappable links that jump to that section.
 */
@Composable
fun HelpScreen(onNavigate: (String) -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
    ) {
        helpSections.forEach { section ->
            Text(
                section.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
            )
            section.blocks.forEach { block ->
                when (block) {
                    is HText -> Text(
                        helpAnnotated(block.s, onNavigate), fontSize = 16.sp, lineHeight = 24.sp,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                    is HBullet -> Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•   ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(helpAnnotated(block.s, onNavigate), fontSize = 16.sp, lineHeight = 24.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Bolded words that name a navigable section -> its route. */
private val helpRoutes: Map<String, String> = mapOf(
    "Popular" to Routes.POPULAR, "Artists" to Routes.ARTISTS, "Albums" to Routes.ALBUMS,
    "Genres" to Routes.GENRES, "Tags" to Routes.TAGS, "Featured" to Routes.FEATURED,
    "Search" to Routes.SEARCH, "Favorites" to Routes.FAVORITES, "Playlists" to Routes.PLAYLISTS,
    "Settings" to Routes.SETTINGS,
)

/**
 * Tiny markdown: a **term** that names a section becomes a tappable accent-colored link;
 * every other **term** is plain bold.
 */
private fun helpAnnotated(s: String, onNavigate: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        val start = s.indexOf("**", i)
        if (start < 0) { append(s.substring(i)); break }
        append(s.substring(i, start))
        val end = s.indexOf("**", start + 2)
        if (end < 0) { append(s.substring(start)); break }
        val term = s.substring(start + 2, end)
        val route = helpRoutes[term]
        if (route != null) {
            withLink(
                LinkAnnotation.Clickable(
                    route,
                    TextLinkStyles(SpanStyle(color = MagAccent, fontWeight = FontWeight.Bold)),
                ) { onNavigate(route) },
            ) { append(term) }
        } else {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(term) }
        }
        i = end + 2
    }
}

private sealed interface HBlock
private data class HText(val s: String) : HBlock
private data class HBullet(val s: String) : HBlock
private data class HSection(val title: String, val blocks: List<HBlock>)

private val helpSections: List<HSection> = listOf(
    HSection("Welcome to Magnatune", listOf(
        HText("Magnatune is an independent record label founded in 2003 on a simple idea: be fair to musicians and fair to listeners. Our motto is “we are not evil.”"),
        HText("Every album is hand-picked — we accept about 3% of submissions — and you can listen to everything in full before you decide to buy. When you do buy, half the money goes straight to the artist. All the music is Creative Commons licensed, with no copy protection (DRM)."),
        HText("This app streams Magnatune's entire catalog: hundreds of artists across classical, jazz, electronica, rock, ambient, world and more."),
    )),
    HSection("Finding your way around", listOf(
        HText("On a tablet the sections live in the sidebar on the left. On a phone they're in the bar at the bottom."),
        HBullet("**Popular** — the most-loved albums, grouped by genre."),
        HBullet("**Artists**, **Albums** — browse the whole catalog."),
        HBullet("**Genres** — pick a style, then drill into its artists and albums."),
        HBullet("**Tags** — albums grouped by mood and theme (“Tagged as…”)."),
        HBullet("**Featured** — playlists hand-curated by Magnatune."),
        HBullet("**Search** — find any artist, album or song by name."),
        HBullet("**Favorites**, **Playlists**, **Settings** — your own music and options."),
    )),
    HSection("Browsing music", listOf(
        HText("Tap any artist, album or genre to open it. Album pages list every track; artist pages list their albums."),
        HBullet("At the bottom of an album or artist page you'll find **“You might also like”** — related music chosen for you."),
        HBullet("Genre and tag chips on a detail page are tappable, taking you to everything in that genre or tag."),
    )),
    HSection("Playing music", listOf(
        HText("Tap a song to play it. The **Play** button on an album or artist plays the whole thing; turn on **Shuffle** to mix up the order."),
        HText("The mini-player sits at the bottom. Tap it to open the full **Now Playing** screen — there you can tap the art, song or album to jump to that album, or the artist name to open the artist."),
        HBullet("**Play / Pause**, **Previous**, **Next** — the usual transport controls."),
        HBullet("**Shuffle** plays the queue in random order. **Repeat** loops the queue forever once it reaches the end."),
        HBullet("Drag the progress bar to skip to any point in a track."),
        HBullet("**Volume** — the slider in the player."),
        HBullet("**Crossfade** smoothly fades each track into the next. Turn it on or off in **Settings**."),
        HBullet("**Cast & AirPlay** — when a Chromecast or AirPlay device is on your network, a cast/AirPlay button appears in the player so you can send the music to it."),
        HBullet("Playback shows in your notification shade and on the lock screen, and keeps going in the background."),
    )),
    HSection("Favorites & dislikes", listOf(
        HText("Use the heart and broken-heart icons that appear next to every song, album and artist."),
        HBullet("**Heart** — add to your **Favorites**. Everything you favorite lives under **Favorites**."),
        HBullet("**Broken heart** — mark something you'd rather not see. With **Hide things I dislike** on (**Settings**), disliked music disappears everywhere; turn it off to bring it back."),
    )),
    HSection("Your playlists", listOf(
        HText("Build your own playlists under **Playlists**."),
        HBullet("Tap the **add-to-playlist** button next to any heart to add a song, a whole album, or an artist's entire catalog — or to create a new playlist on the spot."),
        HBullet("Inside a playlist you can remove tracks or delete the playlist."),
    )),
    HSection("Membership & sound quality", listOf(
        HText("You can stream everything for free. Free streams include a short spoken announcement at the end of each track."),
        HText("A Magnatune membership removes the announcement, unlocks higher-quality audio, and lets you download music. Sign in under **Settings** → Membership."),
        HBullet("Members can choose **Normal** or **Lossless** audio quality in **Settings**."),
    )),
    HSection("Offline listening", listOf(
        HText("Members can keep music on the device to play without a connection."),
        HBullet("**Auto-download favorites** (**Settings**) saves every song, album and artist you favorite, automatically."),
        HBullet("Tracks you play are also cached automatically so they don't re-download. See and clear storage under **Settings** → Storage."),
        HBullet("The download buttons on albums and songs (members only) save music in your chosen format."),
    )),
    HSection("Listening across your devices", listOf(
        HText("If you run Magnatune on more than one device on the same Wi-Fi, they can see each other."),
        HBullet("When you're not playing here, the player can show what's playing on another device — and its buttons control that device."),
    )),
    HSection("Settings", listOf(
        HText("Everything above is configurable under **Settings**: membership, playback, audio quality, downloads and your library. The catalog updates itself automatically in the background."),
        HText("Tap **Why not evil** or **Founder's rant** in **Settings** to learn more about what Magnatune stands for."),
        HText("Enjoy the music — and thanks for supporting independent artists."),
    )),
)
