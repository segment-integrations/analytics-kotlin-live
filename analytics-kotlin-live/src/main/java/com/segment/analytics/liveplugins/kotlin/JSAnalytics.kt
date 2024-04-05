package com.segment.analytics.liveplugins.kotlin

import android.content.Context
import com.segment.analytics.kotlin.android.Analytics
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.substrata.kotlin.JSObject
import com.segment.analytics.substrata.kotlin.JSScope
import com.segment.analytics.substrata.kotlin.JsonElementConverter

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

    private val context: Any?
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
    constructor(writeKey: String, baseAnalytics: JSAnalytics) {
        require(baseAnalytics.context is Context) {
            "Incompatible Android Context!"
        }
        this.analytics = Analytics(writeKey, baseAnalytics.context as Context)
        this.engine = baseAnalytics.engine
        mainAnalytics = false
    }

    fun track(event: String) {
        analytics.track(event)
    }

    fun track(event: String, properties: JSObject) {
        analytics.track(event, JsonElementConverter.read(properties))
    }

    fun identify(userId: String) {
        analytics.identify(userId)
    }

    fun identify(userId: String, traits: JSObject) {
        analytics.identify(userId, JsonElementConverter.read(traits))
    }

    fun screen(title: String, category: String) {
        analytics.screen(title, category)
    }

    fun screen(title: String, category: String, properties: JSObject) {
        analytics.screen(title, JsonElementConverter.read(properties), category)
    }

    fun group(groupId: String) {
        analytics.group(groupId)
    }

    fun group(groupId: String, traits: JSObject) {
        analytics.group(groupId, JsonElementConverter.read(traits))
    }

    fun alias(newId: String) {
        analytics.alias(newId)
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
        var result = false
        val livePlugin = LivePlugin(plugin, type, engine)
        if (plugin.contains("destination")) {
            val destination = plugin.getString("destination")
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
}