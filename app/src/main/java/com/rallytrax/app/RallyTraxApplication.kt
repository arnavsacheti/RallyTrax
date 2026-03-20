package com.rallytrax.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class RallyTraxApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure osmdroid (user-agent is required for tile downloads)
        Configuration.getInstance().apply {
            userAgentValue = packageName
        }
    }
}
