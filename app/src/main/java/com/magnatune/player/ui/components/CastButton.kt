package com.magnatune.player.ui.components

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Google Cast button (the standard [MediaRouteButton]). Renders only on devices with Google Play
 * Services + Cast; on non-GMS devices it draws nothing. The button auto-hides when no Cast device
 * is on the network, and the actual playback handoff is done by PlaybackService's CastPlayer.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val available = remember {
        runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }
    if (!available) return
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // MediaRouteButton requires an AppCompat-derived theme context.
            val themed = ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat_Light)
            MediaRouteButton(themed).apply {
                runCatching { CastButtonFactory.setUpMediaRouteButton(themed, this) }
            }
        },
    )
}
