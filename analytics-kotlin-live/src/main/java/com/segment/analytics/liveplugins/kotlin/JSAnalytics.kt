package com.segment.analytics.liveplugins.kotlin

import android.content.Context
import com.eclipsesource.v8.V8Object
import com.segment.analytics.kotlin.android.Analytics
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.getInt
import com.segment.analytics.kotlin.core.utilities.getString
import com.segment.analytics.substrata.kotlin.JSValue
import com.segment.analytics.substrata.kotlin.j2v8.J2V8Engine
import com.segment.analytics.substrata.kotlin.j2v8.fromV8Object
import com.segment.analytics.substrata.kotlin.j2v8.toV8Object

class JSAnalytics {

    internal var analytics: Analytics
    internal var engine: J2V8Engine
    internal val mainAnalytics: Boolean

    val anonymousId: String
        get() = analytics.anonymousId()

    val userId: String?
        get() = analytics.userId()

    val traits: V8Object?
        get() {
            var res: V8Object? = null
            analytics.traits()?.let {
                res = engine.underlying.toV8Object(it)
            }
            return res
        }

    private val context: Any?
        get() = analytics.configuration.application

    // This is the constructor used by the native to create the injected instance
    constructor(analytics: Analytics, engine: J2V8Engine) {
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

    fun track(event: String, properties: V8Object) {
        analytics.track(event, fromV8Object(properties)!!)
    }

    fun identify(userId: String) {
        analytics.identify(userId)
    }

    fun identify(userId: String, traits: V8Object) {
        analytics.identify(userId, fromV8Object(traits)!!)
    }

    fun screen(title: String, category: String) {
        analytics.screen(title, category)
    }

    fun screen(title: String, category: String, properties: V8Object) {
        analytics.screen(title, fromV8Object(properties)!!, category)
    }

    fun group(groupId: String) {
        analytics.group(groupId)
    }

    fun group(groupId: String, traits: V8Object) {
        analytics.group(groupId, fromV8Object(traits)!!)
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

    fun add(plugin: V8Object): Boolean {
        if (!mainAnalytics) return false // Only allow adding plugins to injected analytics

        val jsPlugin = JSValue.JSObjectReference(plugin)
        val jsPluginData = jsPlugin.content ?: return false

        val type: Plugin.Type = jsPluginData.getInt("type")?.let {
            pluginTypeFromInt(it)
        } ?: return false
        val destination: String? = jsPluginData.getString("destination")

        var result = false
        val livePlugin = LivePlugin(jsPlugin, type, engine)
        if (destination != null) {
            val found = analytics.find(destination)
            found?.let {
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