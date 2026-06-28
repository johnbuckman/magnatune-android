package com.magnatune.player

import android.app.Application

/** Application entry point. Holds process-wide singletons (wired up in later phases). */
class MagnatuneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MagnatuneApp
            private set
    }
}
