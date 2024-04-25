package com.segment.analytics.liveplugins.kotlin

import android.content.Context
import android.content.SharedPreferences
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.log
import com.segment.analytics.kotlin.core.utilities.LenientJson
import com.segment.analytics.substrata.kotlin.JSScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

interface LivePluginsDependent {
    fun prepare(engine: JSScope)
    fun readyToStart()
}

@Serializable
data class LivePluginsSettings(
    val version: Int = -1,
    val downloadURL: String = ""
)

class LivePlugins(
    private val fallbackFile: InputStream? = null,
    private val forceFallbackFile: Boolean = false
) : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Utility

    companion object {
        const val LIVE_PLUGINS_FILE_NAME = "livePlugins.js"
        const val SHARED_PREFS_KEY = "LivePlugins"
        var loaded = false
    }
    override lateinit var analytics: Analytics

    private lateinit var sharedPreferences: SharedPreferences

    val engine = JSScope {
        it.printStackTrace()
    }

    private lateinit var livePluginFile: File

    private val dependents = CopyOnWriteArrayList<LivePluginsDependent>()

    // Call this function when app is destroyed, to prevent memory leaks
    fun release() {
        engine.release()
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        LivePluginsHolder.plugin = WeakReference(this)

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
        livePluginFile = File(storageDirectory, LIVE_PLUGINS_FILE_NAME)

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

    fun addDependent(plugin: LivePluginsDependent) {
        dependents.add(plugin)
    }

    private fun configureEngine() = engine.sync {
        val jsAnalytics = JSAnalytics(analytics, engine)
        export(jsAnalytics, "Analytics","analytics")

        evaluate(EmbeddedJS.ENUM_SETUP_SCRIPT)
        evaluate(EmbeddedJS.LIVE_PLUGINS_BASE_SETUP_SCRIPT)
    }

    private fun loadLivePlugin(file: File) {
        if (fallbackFile != null && (forceFallbackFile || !file.exists())) {
            // Forced to use fallback file
            fallbackFile.copyTo(FileOutputStream(file))
        }

        dependents.forEach { d -> d.prepare(engine) }
        engine.launch (global = true) {
            loadBundle(file.inputStream()) { error ->
                if (error != null) {
                    analytics.log(error.message ?: "")
                } else {
                    dependents.forEach { d -> d.readyToStart() }
                }
            }
        }
    }

    private fun setLivePluginData(data: LivePluginsSettings) {
        currentData().let { currData ->
            val newVersion = data.version
            val currVersion = currData.version

            if (newVersion > currVersion) {
                updateLivePluginsConfig(data)
            }
        } ?: updateLivePluginsConfig(data)
    }

    private fun currentData(): LivePluginsSettings {
        var currentData = LivePluginsSettings() // Default to an "empty" settings with version -1
        val dataString = sharedPreferences.getString(SHARED_PREFS_KEY, null)
        if (dataString != null) {
            currentData = Json.decodeFromString<LivePluginsSettings>(dataString)
        }
        return currentData
    }

    private fun updateLivePluginsConfig(data: LivePluginsSettings) {
        val urlString = data.downloadURL

        sharedPreferences.edit().putString(SHARED_PREFS_KEY, Json.encodeToString(data)).apply()

        with(analytics) {
            analyticsScope.launch(fileIODispatcher as CoroutineContext) {
                if (urlString.isNotEmpty()) {
                    download(urlString, livePluginFile)
                    log("New LivePlugins installed.  Will be used on next app launch.")
                } else {
                    disableBundleURL(livePluginFile)
                }
            }
        }
    }
}