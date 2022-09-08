package com.segment.analytics.edgefn.kotlin

import android.content.Context
import android.content.SharedPreferences
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.LogFilterKind
import com.segment.analytics.kotlin.core.platform.plugins.logger.log
import com.segment.analytics.kotlin.core.utilities.LenientJson
import com.segment.analytics.substrata.kotlin.JavascriptDataBridge
import com.segment.analytics.substrata.kotlin.j2v8.J2V8Engine
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@Serializable
data class EdgeFunctionsSettings(
    val version: Int = -1,
    val downloadUrl: String = ""
)

class EdgeFunctions: Plugin {
    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var analytics: Analytics

    companion object {
        private var added = false
    }

    var plugin = EdgeFunctionsRunner()

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        if (added) return
        added = true
        analytics.add(plugin) // Ensure this is added only once to the timeline
    }

    fun setBackupFile(inputStream: InputStream) {
        plugin.fallbackFile = inputStream
    }

    // Call this function when app is destroyed, to prevent memory leaks
    fun release() {
        plugin.release()
    }
}

class EdgeFunctionsRunner internal constructor() : Plugin {
    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var analytics: Analytics

    var forceFallbackFile: Boolean = false
    var fallbackFile: InputStream? = null

    private lateinit var sharedPreferences: SharedPreferences

    val engine = J2V8Engine()
    val dataBridge: JavascriptDataBridge = engine.bridge

    private lateinit var edgeFnFile: File

    companion object {
        const val SHARED_PREFS_KEY = "EdgeFunctions"
        const val EDGE_FUNCTION_FILE_NAME = "edgeFunctions.js"
    }

    // Call this function when app is destroyed, to prevent memory leaks
    fun release() {
        engine.release()
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)

        require(analytics.configuration.application is Context) {
            "Incompatible Android Context!"
        }
        val context = analytics.configuration.application as Context
        sharedPreferences = context.getSharedPreferences(
            "analytics-edgefn-${analytics.configuration.writeKey}",
            Context.MODE_PRIVATE
        )
        val storageDirectory = context.getDir("segment-data", Context.MODE_PRIVATE)
        edgeFnFile = File(storageDirectory, EDGE_FUNCTION_FILE_NAME)

        configureEngine()
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        println("Updating edge functions")
        if (type != Plugin.UpdateType.Initial) {
            return
        }

        if (settings.edgeFunction != emptyJsonObject) {
            val edgeFnData = LenientJson.decodeFromJsonElement(
                EdgeFunctionsSettings.serializer(),
                settings.edgeFunction
            )
            setEdgeFnData(edgeFnData)
        }
        loadEdgeFn(edgeFnFile)
    }

    private fun configureEngine() {
        engine.errorHandler = {
            it.printStackTrace()
        }

        engine.expose(JSAnalytics::class, "Analytics")

        val jsAnalytics = JSAnalytics(analytics, engine)
        engine.expose("analytics", jsAnalytics)

        engine.execute(EmbeddedJS.ENUM_SETUP_SCRIPT)
        engine.execute(EmbeddedJS.EDGE_FN_BASE_SETUP_SCRIPT)
    }

    private fun loadEdgeFn(file: File) {
        fallbackFile?.let {
            if (forceFallbackFile || !file.exists()) {
                // Forced to use fallback file
                it.copyTo(FileOutputStream(file))
            }
        }

        engine.loadBundle(file.inputStream()) { error ->
            error?.let {
                analytics.log(error.message ?: "", kind = LogFilterKind.ERROR)
            }
        }
    }

    private fun setEdgeFnData(data: EdgeFunctionsSettings) {
        currentData()?.let { currData ->
            val newVersion = data.version
            val currVersion = currData.version

            if (newVersion > currVersion) {
                updateEdgeFunctionsConfig(data)
            }
        } ?: updateEdgeFunctionsConfig(data)
    }

    private fun currentData() =
        sharedPreferences.getString(SHARED_PREFS_KEY, null)?.let {
            Json.decodeFromString<EdgeFunctionsSettings>(it)
        }

    private fun updateEdgeFunctionsConfig(data: EdgeFunctionsSettings) {
        val urlString = data.downloadUrl

        sharedPreferences.edit().putString(SHARED_PREFS_KEY, Json.encodeToString(data)).apply()

        with(analytics) {
            analyticsScope.launch(fileIODispatcher) {
                if (urlString.isNotEmpty()) {
                    download(urlString, edgeFnFile)
                    log("New EdgeFunction installed.  Will be used on next app launch.")
                } else {
                    disableBundleURL(edgeFnFile)
                }
            }
        }
    }
}