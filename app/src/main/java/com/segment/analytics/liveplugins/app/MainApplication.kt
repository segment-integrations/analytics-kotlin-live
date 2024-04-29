package com.segment.analytics.liveplugins.app

import android.app.Application
import com.segment.analytics.kotlin.android.Analytics
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.liveplugins.app.filters.WebhookPlugin
import com.segment.analytics.liveplugins.kotlin.LivePlugins
import java.util.concurrent.Executors

class MainApplication : Application() {
    companion object {
        lateinit var analytics: Analytics
    }

    override fun onCreate() {
        super.onCreate()
        analytics = Analytics(
            "93EMLzmXzP6EJ3cJOhdaAgEVNnZjwRqA",
            applicationContext
        ) {
            this.collectDeviceId = true
            this.trackApplicationLifecycleEvents = true
            this.trackDeepLinks = true
            this.flushAt = 1
            this.flushInterval = 0
        }

        analytics.add(WebhookPlugin("https://webhook.site/5fefa55b-b5cf-4bd5-abe6-9234a003baa8", Executors.newSingleThreadExecutor()))

        val backup = resources.openRawResource(R.raw.default_liveplugins)
        val livePlugins = LivePlugins(backup, true)
        analytics.add(livePlugins)
    }

}