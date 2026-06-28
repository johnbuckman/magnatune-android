package com.magnatune.player.data

import android.content.Context
import androidx.room.Room
import com.magnatune.player.db.CatalogStore
import com.magnatune.player.db.UserDatabase
import com.magnatune.player.db.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
