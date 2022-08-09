package com.segment.analytics.edgefn.app

import android.app.Application
import com.segment.analytics.edgefn.app.filters.DestinationFilters
import com.segment.analytics.edgefn.app.filters.WebhookPlugin
import com.segment.analytics.edgefn.kotlin.EdgeFunctions
import com.segment.analytics.kotlin.android.Analytics
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.manuallyEnableDestination
import com.segment.analytics.substrata.kotlin.asJSValue
import com.segment.analytics.substrata.kotlin.j2v8.J2V8Engine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

        val backup = resources.openRawResource(R.raw.default_edgefn)
        val edgeFunctions = EdgeFunctions(backup, true)
        analytics.add(edgeFunctions)
        edgeFunctions.dataBridge["mcvid"] = buildJsonObject{
            put("key1", "val1")
            put("key2", true)
            put("key3", 10)
            put("key4", buildJsonObject { put("truthy", null as Boolean?) })
        }.asJSValue()
    }

}