package com.segment.analytics.edgefn.kotlin

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.getString
import com.segment.analytics.substrata.kotlin.j2v8.J2V8Engine
import com.segment.analytics.substrata.kotlin.wrapAsJSValue
import kotlinx.serialization.json.JsonObject
import java.io.InputStream

class EdgeFunctions: EventPlugin {

    companion object {
        const val USER_DEFAULTS_KEY = "EdgeFunction"
        const val VERSION_KEY = "version"
        const val DOWNLOAD_URL_KEY = "downloadURL"

        const val EDGE_FUNCTION_FILE_NAME = "edgeFunction.js"
    }

    override lateinit var analytics: Analytics

    override val type: Plugin.Type = Plugin.Type.Utility

    internal val engine = J2V8Engine()

    internal var loaded = false

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        if (type != Plugin.UpdateType.Initial || loaded) {
            return
        }

        loaded = true

        val edgeFnData = settings.edgeFunction
        setEdgeFnData(edgeFnData)

        loadEdgeFn()
    }

    fun loadEdgeFn(url: String) {
        engine.errorHandler = {
            println(it)
        }

        engine.expose(JSAnalytics::class, "Analytics")

        val jsAnalytics = JSAnalytics(analytics, engine)
        // TODO: expose jsAnalytics object
        engine.set("analytics", wrapAsJSValue(jsAnalytics))

        engine.execute(EmbeddedJS.ENUM_SETUP_SCRIPT)
        engine.execute(EmbeddedJS.EDGE_FN_BASE_SETUP_SCRIPT)

        // TODO: load bundle from disk
        val file: InputStream
        engine.loadBundle(file) { error ->
            error?.let {
                println(it)
            }
        }
    }

    fun setEdgeFnData(data: JsonObject) {

    }

    private fun update(data: JsonObject) {
        val urlString = data.getString(DOWNLOAD_URL_KEY) ?: return

        // TODO: update local settings



    }
}