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

        val container = (application as MagnatuneApp).container
        container.playback.connect()
        // Verify membership + refresh the catalog in the background.
        lifecycleScope.launch {
            container.credentials.refreshMembership()
            if (container.catalogSync.refreshIfNeeded()) container.reopenCatalog()
        }

        setContent {
            MagnatuneTheme {
                RootScreen(
                    vm = vm,
                    onPlay = { tracks, startAt -> container.playback.play(tracks, startAt) },
                    miniPlayer = { MiniPlayer(container.playback) },
                )
            }
        }
    }
}
