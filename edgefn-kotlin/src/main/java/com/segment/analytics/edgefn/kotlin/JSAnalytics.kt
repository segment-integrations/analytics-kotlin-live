package com.segment.analytics.edgefn.kotlin

import android.content.Context
import com.segment.analytics.kotlin.android.Analytics
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.getString
import com.segment.analytics.substrata.kotlin.JSValue
import com.segment.analytics.substrata.kotlin.j2v8.J2V8Engine
import com.segment.analytics.substrata.kotlin.j2v8.toJSObject
import com.segment.analytics.substrata.kotlin.wrapAsJSValue
import java.lang.Exception

class JSAnalytics private constructor() {

    internal lateinit var analytics: Analytics

    internal var engine: J2V8Engine? = null

    val anonymousId: String
        get() = analytics.anonymousId()

    val userId: String?
        get() = analytics.userId()

    val traits: JSValue.JSObject?
        get() {
            engine?.run {
                analytics.traits()?.toMap()?.let {
                    return@run this.toJSObject(it)
                }
            }

            return null
        }

    val context: Any?
        get() = analytics.configuration.application

    constructor(analytics: Analytics, engine: J2V8Engine): this() {
        this.analytics = analytics
        this.engine = engine
    }

    constructor(writeKey: String, context: Any?): this() {
        require (context is Context) {
            "Incompatible Android Context!"
        }
        this.analytics = Analytics(writeKey, context)
    }

    fun track(event: String, properties: JSValue.JSObject) {
        properties.mapRepresentation?.let {
            analytics.track(event, it)
        }
    }

    fun identify(userId: String, traits: JSValue.JSObject) {
        traits.mapRepresentation?.let {
            analytics.identify(userId, it)
        }
    }

    fun screen(title: String, category: String, properties: JSValue.JSObject) {
        properties.mapRepresentation?.let {
            analytics.screen(title, it, category)
        }
    }

    fun group(groupId: String, traits: JSValue.JSObject) {
        traits.mapRepresentation?.let {
            analytics.group(groupId, it)
        }
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

    fun add(plugin: Any): Boolean {
        var result = false
        engine ?: return result

        val jsPlugin = wrapAsJSValue(plugin)
        if (jsPlugin !is JSValue.JSObject || jsPlugin.mapRepresentation == null) {
            return result
        }

        val type: Plugin.Type? = jsPlugin.mapRepresentation!!.getString("type")?.let {
            enumValueOf<Plugin.Type>(it)
        }
        val destination: String? = jsPlugin.mapRepresentation!!.getString("destination")
        type ?: return result

        val edgeFn = EdgeFn(jsPlugin, type, engine!!)
        destination?.let { dest ->
            // TODO: replace with analytics.find
            analytics.findAll(DestinationPlugin::class).find {
                it.key == dest
            }?.let {
                it.add(edgeFn)
                result = true
            }
//            analytics!!.find(dest)?.let {
//                it.add(edgeFn)
//                result = true
//            }
        } ?: let {
            analytics.add(edgeFn)
            result = true
        }

        return result
    }
}