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
        // Verify membership + refresh the catalog in the background.
        lifecycleScope.launch {
            container.credentials.refreshMembership()
            if (container.catalogSync.refreshIfNeeded()) container.reopenCatalog()
        }

        setContent {
            MagnatuneTheme {
                // onPlay is a no-op until Phase 3 wires the Media3 controller.
                RootScreen(vm = vm, onPlay = { tracks, startAt ->
                    Log.d("Magnatune", "play ${tracks.size} tracks @ $startAt (player wired in Phase 3)")
                })
            }
        }
    }
}
