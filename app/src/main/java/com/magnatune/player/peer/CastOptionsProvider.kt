package com.magnatune.player.peer

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

/**
 * Google Cast configuration. Uses the default media receiver, which can play the Magnatune AAC
 * HTTP streams directly. Referenced from the manifest OPTIONS_PROVIDER meta-data.
 *
 * NOTE: Cast only functions on devices with Google Play Services; on non-GMS devices the framework
 * stays dormant (PlaybackService guards its CastContext init). Not hardware-verified.
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        // DEFAULT_MEDIA_RECEIVER_APPLICATION_ID = "CC1AD845"
        return CastOptions.Builder()
            .setReceiverApplicationId("CC1AD845")
            .setCastMediaOptions(CastMediaOptions.Builder().build())
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): MutableList<SessionProvider>? = null
}
