package com.segment.analytics.liveplugins.kotlin

import android.content.Context
import android.content.SharedPreferences
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.WaitingPlugin
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
import androidx.core.content.edit
import com.segment.analytics.substrata.kotlin.JSExceptionHandler
import kotlinx.serialization.json.decodeFromJsonElement

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
    private val forceFallbackFile: Boolean = false,
    exceptionHandler: JSExceptionHandler? = null,
    private val localJS: List<InputStream> = listOf()
) : EventPlugin, WaitingPlugin {
    override val type: Plugin.Type = Plugin.Type.Utility

    companion object {
        const val LIVE_PLUGINS_FILE_NAME = "livePlugins.js"
        const val SHARED_PREFS_KEY = "LivePlugins"
        var loaded = false
    }
    override lateinit var analytics: Analytics

    private lateinit var sharedPreferences: SharedPreferences

    val engine = JSScope(exceptionHandler = exceptionHandler)

    private lateinit var livePluginFile: File

    internal val dependents = CopyOnWriteArrayList<LivePluginsDependent>()

    // Call this function when app is destroyed, to prevent memory leaks
    fun release() {
        engine.release()
    }

    override fun setup(analytics: Analytics) {
        super<WaitingPlugin>.setup(analytics)

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
        analytics.find(LivePlugins::class)?.let {
            if (it != this@LivePlugins) {
                // remove ourselves.  we can't do this in configure.
                analytics.remove(this@LivePlugins)
                return
            }
        }

        if (loaded) {
            return
        }

        loaded = true

        updateLivePlugin(settings)
        loadLivePlugin(livePluginFile)
    }

    fun addDependent(plugin: LivePluginsDependent) {
        dependents.add(plugin)
        // this plugin already loaded, notify the dependents right away
        if (loaded) {
            plugin.prepare(engine)
            plugin.readyToStart()
        }
    }

    private fun configureEngine() = engine.sync {
        val jsAnalytics = JSAnalytics(analytics, engine)
        export(jsAnalytics, "Analytics","analytics")
        val jsStorage = JSStorage(sharedPreferences, engine)
        export(jsStorage, "Storage", "storage")

        evaluate(EmbeddedJS.ENUM_SETUP_SCRIPT)
        evaluate(EmbeddedJS.LIVE_PLUGINS_BASE_SETUP_SCRIPT)
    }

    private fun loadLivePlugin(file: File) {
        if (fallbackFile != null && (forceFallbackFile || !file.exists())) {
            // Forced to use fallback file
            fallbackFile.copyTo(FileOutputStream(file))
        }

        for (d in dependents) {
            d.prepare(engine)
        }
        engine.launch (global = true) {
            for (js in localJS) {
                loadBundle(js)
            }

            loadBundle(file.inputStream()) { error ->
                if (error != null) {
                    analytics.log(error.message ?: "")
                } else {
                    for (d in dependents) {
                        d.readyToStart()
                    }
                }

                resume()
            }
        }
    }

    private fun updateLivePlugin(settings: Settings) {
        if (settings.edgeFunction != emptyJsonObject) {
            LenientJson.decodeFromJsonElement<LivePluginsSettings>(
                settings.edgeFunction
            ).also {
                if (shouldUpdateLivePlugin(it)) {
                    performLivePluginUpdate(it)
                }
            }
        }
    }

    private fun shouldUpdateLivePlugin(livePluginSettings: LivePluginsSettings): Boolean {
        val cache = sharedPreferences.getString(SHARED_PREFS_KEY, null)
        if (cache != null) {
            val cachedLivePluginSettings = Json.decodeFromString<LivePluginsSettings>(cache)
            if (livePluginSettings.version > cachedLivePluginSettings.version) {
                return true
            }
            else {
                return false
            }
        }

        return true
    }

    private fun performLivePluginUpdate(data: LivePluginsSettings) {
        val urlString = data.downloadURL

        sharedPreferences.edit { putString(SHARED_PREFS_KEY, Json.encodeToString(data)) }

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