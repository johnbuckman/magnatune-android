package com.magnatune.player

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.RootScreen
import com.magnatune.player.ui.components.MiniPlayer
import com.magnatune.player.ui.theme.MagnatuneTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MagnatuneViewModel by viewModels {
        MagnatuneViewModel.Factory((application as MagnatuneApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Immersive full-screen: hide the status + soft-navigation bars so they don't obscure the
        // player; a swipe from the edge reveals them transiently.
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }

        val container = (application as MagnatuneApp).container
        container.playback.connect()
        // Verify membership + refresh the catalog in the background.
        lifecycleScope.launch {
            container.credentials.refreshMembership()
            if (container.catalogSync.refreshIfNeeded()) container.reopenCatalog()
            container.downloads.refreshStorage()
            container.downloads.syncAutoDownloads()
        }
        vm.deduplicateFavorites()

        setContent {
            MagnatuneTheme {
                RootScreen(
                    vm = vm,
                    onPlay = { tracks, startAt -> container.playback.play(tracks, startAt) },
                    miniPlayer = { navController -> MiniPlayer(vm, navController) },
                )
            }
        }
    }

    /** Re-assert immersive mode when the window regains focus (system bars can reappear). */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                .hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
}
