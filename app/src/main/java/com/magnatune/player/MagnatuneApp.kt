package com.magnatune.player

import android.app.Application
import com.magnatune.player.data.AppContainer

/** Application entry point. Holds process-wide singletons via [AppContainer]. */
class MagnatuneApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }

    companion object {
        lateinit var instance: MagnatuneApp
            private set
    }
}
