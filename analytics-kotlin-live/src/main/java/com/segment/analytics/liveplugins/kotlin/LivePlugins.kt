package com.segment.analytics.liveplugins.kotlin

import android.content.Context
import android.content.SharedPreferences
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.EventPlugin
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
data class LivePluginsSettings(
    val version: Int = -1,
    val downloadUrl: String = ""
)

class LivePlugins(
    private val fallbackFile: InputStream? = null,
    private val forceFallbackFile: Boolean = false
) : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Utility

    companion object {
        const val EDGE_FUNCTION_FILE_NAME = "livePlugins.js"
        const val SHARED_PREFS_KEY = "LivePlugins"
        var loaded = false
    }
    override lateinit var analytics: Analytics

    private lateinit var sharedPreferences: SharedPreferences

    val engine = J2V8Engine()
    val dataBridge: JavascriptDataBridge = engine.bridge

    private lateinit var livePluginFile: File

    // Call this function when app is destroyed, to prevent memory leaks
    fun release() {
        engine.release()
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)

        // if we've already got LivePlugins, we don't wanna do any setup
        if (analytics.find(LivePlugins::class) != null) {
            // we can't remove ourselves here because configure needs to be
            // called before update; so we can only remove ourselves in update.
            return
        }


        require(analytics.configuration.application is Context) {
            "Incompatible Android Context!"
        }
        val context = analytics.configuration.application as Context
        sharedPreferences = context.getSharedPreferences(
            "analytics-liveplugins-${analytics.configuration.writeKey}",
            Context.MODE_PRIVATE
        )
        val storageDirectory = context.getDir("segment-data", Context.MODE_PRIVATE)
        livePluginFile = File(storageDirectory, EDGE_FUNCTION_FILE_NAME)

        configureEngine()
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        // if we find an existing LivePlugins instance that is not ourselves...
        if (analytics.find(LivePlugins::class) != this) {
            // remove ourselves.  we can't do this in configure.
            analytics.remove(this)
            return
        }

        if (type != Plugin.UpdateType.Initial || loaded) {
            return
        }

        loaded = true

        if (settings.edgeFunction != emptyJsonObject) {
            val livePluginsData = LenientJson.decodeFromJsonElement(
                LivePluginsSettings.serializer(),
                settings.edgeFunction
            )
            setLivePluginData(livePluginsData)
        }
        loadLivePlugin(livePluginFile)
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

    private fun loadLivePlugin(file: File) {
        if (fallbackFile != null && (forceFallbackFile || !file.exists())) {
            // Forced to use fallback file
            fallbackFile.copyTo(FileOutputStream(file))
        }

        engine.loadBundle(file.inputStream()) { error ->
            error?.let {
                analytics.log(error.message ?: "", kind = LogFilterKind.ERROR)
            }
        }
    }

    private fun setLivePluginData(data: LivePluginsSettings) {
        currentData()?.let { currData ->
            val newVersion = data.version
            val currVersion = currData.version

            if (newVersion > currVersion) {
                updateLivePluginsConfig(data)
            }
        } ?: updateLivePluginsConfig(data)
    }

    private fun currentData() =
        sharedPreferences.getString(SHARED_PREFS_KEY, null)?.let {
            Json.decodeFromString<LivePluginsSettings>(it)
        }

    private fun updateLivePluginsConfig(data: LivePluginsSettings) {
        val urlString = data.downloadUrl

        sharedPreferences.edit().putString(SHARED_PREFS_KEY, Json.encodeToString(data)).apply()

        with(analytics) {
            analyticsScope.launch(fileIODispatcher) {
                if (urlString.isNotEmpty()) {
                    download(urlString, livePluginFile)
                    log("New EdgeFunction installed.  Will be used on next app launch.")
                } else {
                    disableBundleURL(livePluginFile)
                }
            }
        }
    }
}