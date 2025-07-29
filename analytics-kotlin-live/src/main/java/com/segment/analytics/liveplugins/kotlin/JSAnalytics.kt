package com.segment.analytics.liveplugins.kotlin

import android.content.Context
import com.segment.analytics.kotlin.android.Analytics
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.putInContext
import com.segment.analytics.kotlin.core.utilities.updateJsonObject
import com.segment.analytics.substrata.kotlin.JSObject
import com.segment.analytics.substrata.kotlin.JSScope
import com.segment.analytics.substrata.kotlin.JsonElementConverter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.lang.ref.WeakReference

object LivePluginsHolder {
    var plugin: WeakReference<LivePlugins>? = null
}

class JSAnalytics {

    internal lateinit var analytics: Analytics
    internal lateinit var engine: JSScope
    internal var mainAnalytics: Boolean = false

    val anonymousId: String
        get() = analytics.anonymousId()

    val userId: String?
        get() = analytics.userId()

    val traits: JSObject?
        get() = analytics.traits()?.let {
            engine.await {
                JsonElementConverter.write(it, context) as JSObject
            }
        }

    val context: Any?
        get() = analytics.configuration.application

    // JSEngine requires an empty constructor to be able to export this class
    constructor() {}

    // This is the constructor used by the native to create the injected instance
    constructor(analytics: Analytics, engine: JSScope) {
        this.analytics = analytics
        this.engine = engine
        mainAnalytics = true
    }

    // This is the constructor used when JS creates a new one
    constructor(writeKey: String) {
        val application = LivePluginsHolder.plugin?.get()?.analytics?.configuration?.application
        val engine = LivePluginsHolder.plugin?.get()?.engine
        require(application != null) {
            "Application Context Not Found!"
        }
        require(engine != null) {
            "JS Engine is not initialized!"
        }
        require(application is Context) {
            "Incompatible Android Context!"
        }
        this.analytics = Analytics(writeKey, application)
        this.engine = engine
        mainAnalytics = false
    }

    fun track(event: String) {
        analytics.track(event) {
            it?.insertEventOrigin()
        }
    }

    fun track(event: String, properties: JSObject) {
        analytics.track(event, JsonElementConverter.read(properties)) {
            it?.insertContext()
        }
    }

    fun track(event: String, properties: JSObject, enrichments: JSObject) {
        val jsonElement = JsonElementConverter.read(enrichments)
        analytics.track(event, JsonElementConverter.read(properties)) {
            it?.insertContext(jsonElement)
        }
    }

    fun identify(userId: String) {
        analytics.identify(userId) {
            it?.insertContext()
        }
    }

    fun identify(userId: String, traits: JSObject) {
        analytics.identify(userId, JsonElementConverter.read(traits)) {
            it?.insertContext()
        }
    }

    fun identify(userId: String, traits: JSObject, enrichments: JSObject) {
        val jsonElement = JsonElementConverter.read(enrichments)
        analytics.identify(userId, JsonElementConverter.read(traits)) {
            it?.insertContext(jsonElement)
        }
    }

    fun screen(title: String, category: String) {
        analytics.screen(title, category) {
            it?.insertContext()
        }
    }

    fun screen(title: String, category: String, properties: JSObject) {
        analytics.screen(title, JsonElementConverter.read(properties), category) {
            it?.insertContext()
        }
    }

    fun screen(title: String, category: String, properties: JSObject, enrichments: JSObject) {
        val jsonElement = JsonElementConverter.read(enrichments)
        analytics.screen(title, JsonElementConverter.read(properties), category) {
            it?.insertContext(jsonElement)
        }
    }

    fun group(groupId: String) {
        analytics.group(groupId) {
            it?.insertContext()
        }
    }

    fun group(groupId: String, traits: JSObject) {
        analytics.group(groupId, JsonElementConverter.read(traits)) {
            it?.insertContext()
        }
    }

    fun group(groupId: String, traits: JSObject, enrichments: JSObject) {
        val jsonElement = JsonElementConverter.read(enrichments)
        analytics.group(groupId, JsonElementConverter.read(traits)) {
            it?.insertContext(jsonElement)
        }
    }

    fun alias(newId: String) {
        analytics.alias(newId) {
            it?.insertContext()
        }
    }

    fun flush() {
        analytics.flush()
    }

    fun reset() {
        analytics.reset()
    }

    fun add(plugin: JSObject): Boolean {
        if (!mainAnalytics) return false // Only allow adding plugins to injected analytics
        val type: Plugin.Type = pluginTypeFromInt(plugin.getInt("type")) ?: return false

        // persist plugin in the global scope to avoid being garbage collected
        engine.sync {
            this[plugin.ref.toString()] = plugin
        }

        var result = false
        val livePlugin = LivePlugin(plugin, type, engine)
        val destination = plugin["destination"]
        if (destination is String) {
            analytics.find(destination)?.let {
                it.add(livePlugin)
                result = true
            }
        } else {
            // Add it to the main timeline
            analytics.add(livePlugin)
            result = true
        }
        return result
    }

    private fun BaseEvent.insertEventOrigin() : BaseEvent {
        return putInContext("__eventOrigin", buildJsonObject {
            put("type", "js")
            put("version", "")
        })
    }

    private fun BaseEvent.insertContext(enrichments: JsonElement? = null) : BaseEvent {
        val modified = insertEventOrigin()
        if (enrichments is JsonObject) {
            modified.context = updateJsonObject(modified.context) {
                it.putAll(enrichments)
            }
        }
        return modified
    }
}