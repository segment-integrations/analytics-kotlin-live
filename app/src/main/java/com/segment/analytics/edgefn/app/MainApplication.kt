package com.segment.analytics.edgefn.app

import android.app.Application
import com.segment.analytics.edgefn.kotlin.EdgeFunctions
import com.segment.analytics.kotlin.android.Analytics
import com.segment.analytics.kotlin.core.Analytics

class MainApplication : Application() {
    companion object {
        lateinit var analytics: Analytics
    }

    override fun onCreate() {
        super.onCreate()

        analytics = Analytics(
            "tteOFND0bb5ugJfALOJWpF0wu1tcxYgr",
            applicationContext
        ) {
            this.collectDeviceId = true
            this.trackApplicationLifecycleEvents = true
            this.trackDeepLinks = true
            this.flushAt = 1
            this.flushInterval = 0
        }
        val backup = resources.openRawResource(R.raw.default_edgefn)
        analytics.add(EdgeFunctions(backup))
    }
}