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

    /** Whether the device currently has a usable network. */
    val isOnline = MutableStateFlow(true)

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
