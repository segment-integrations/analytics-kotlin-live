package com.segment.analytics.edgefn.app

import android.app.Application
import com.segment.analytics.edgefn.kotlin.EdgeFunctions
import com.segment.analytics.kotlin.android.Analytics
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.platform.plugins.logger.logFlush

class MainApplication : Application() {
    companion object {
        lateinit var analytics: Analytics
    }

    override fun onCreate() {
        super.onCreate()

        analytics = Analytics(
            "HO63Z36e0Ufa8AAgbjDomDuKxFuUICqI",
            applicationContext
        ) {
            this.collectDeviceId = true
            this.trackApplicationLifecycleEvents = true
            this.trackDeepLinks = true
            this.flushAt = 1
            this.flushInterval = 0
        }
        val backup = resources.openRawResource(R.raw.default_edgefn)
        analytics.add(EdgeFunctions(backup, true))
        analytics.track("howdy doody")
    }
}