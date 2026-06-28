package com.magnatune.player.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.room.Room
import com.magnatune.player.db.CatalogStore
import com.magnatune.player.db.UserDatabase
import com.magnatune.player.db.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * Process-wide singletons (a hand-rolled service locator — small app, no DI framework needed).
 * Built once in [com.magnatune.player.MagnatuneApp].
 */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val credentials = Credentials(context)
    val catalogSync = CatalogSync(context)

    private val userDb = Room.databaseBuilder(context, UserDatabase::class.java, "magnatune_user.db").build()
    val userStore = UserStore(userDb.dao(), appScope)

    val settings = Settings(context)

    val playback = com.magnatune.player.service.PlaybackController(
        context = context, scope = appScope, credentials = credentials, settings = settings,
        userStore = userStore, downloadPath = { userStore.downloadPath(it) },
    )

    val downloads = DownloadManager(context, credentials, settings, userStore) { catalog }

    /** LAN peer sync (NSD + TCP). Device name = manufacturer model. */
    val peer = com.magnatune.player.peer.PeerService(
        context = context,
        deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
        scope = appScope,
    ).also { p ->
        p.onControl = { cmd ->
            when (cmd) {
                "playPause" -> playback.togglePlayPause()
                "next" -> playback.next()
                "prev" -> playback.previous()
            }
        }
    }

    /** Whether the device currently has a usable network. */
    val isOnline = MutableStateFlow(true)

    /** The peer we'd surface for remote control: the most-recently-started peer that's playing
     *  while local playback is idle (mirrors iOS remoteFocus — local playback always wins). */
    val remoteFocus: kotlinx.coroutines.flow.StateFlow<com.magnatune.player.peer.PeerService.PeerInfo?> =
        combine(peer.peers, playback.currentTrack) { peers, local ->
            if (local != null) null
            else peers.filter { it.snapshot.state == "playing" }.maxByOrNull { it.snapshot.startedAt ?: 0L }
        }.stateIn(appScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    init {
        // Connectivity tracking.
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        isOnline.value = cm.activeNetwork != null
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isOnline.value = true }
            override fun onLost(network: Network) { isOnline.value = false }
        })
        // Auto-download favorites: re-sync when favorites, the toggle, membership, or connectivity change.
        combine(
            userStore.favoriteSongIds, userStore.favoriteAlbumIds, userStore.favoriteArtistIds,
            settings.autoDownloadFavorites, credentials.isMember,
        ) { _, _, _, _, _ -> Unit }
            .drop(1)
            .debounce(1500)
            .onEach { downloads.syncAutoDownloads() }
            .launchIn(appScope)

        // LAN peer sync: start/stop with the setting; broadcast playback state to peers.
        settings.peerSharingEnabled
            .onEach { if (it) peer.start() else peer.stop() }
            .launchIn(appScope)
        combine(playback.currentTrack, playback.isPlaying) { track, playing ->
            com.magnatune.player.peer.PeerService.Snapshot(
                state = if (track == null) "idle" else if (playing) "playing" else "paused",
                songId = track?.id,
                position = playback.positionMs.value / 1000.0,
                startedAt = if (playing) System.currentTimeMillis() else null,
            )
        }
            .onEach { peer.updateLocalState(it) }
            .launchIn(appScope)
    }

    @Volatile
    private var _catalog: CatalogStore? = null

    /** The catalog store, (re)opened on the current on-disk catalog file. Call [reopenCatalog]
     *  after a refresh installs a new file. */
    val catalog: CatalogStore
        get() = _catalog ?: synchronized(this) {
            _catalog ?: openCatalog().also { _catalog = it }
        }

    private fun openCatalog(): CatalogStore {
        catalogSync.ensureSeeded()
        return CatalogStore(catalogSync.catalogPath())
    }

    /** Swap in a freshly downloaded catalog (closes the old handle). */
    fun reopenCatalog() {
        synchronized(this) {
            _catalog?.close()
            _catalog = CatalogStore(catalogSync.catalogPath())
        }
    }
}
