package com.segment.analytics.edgefn.kotlin

import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.substrata.kotlin.JSValue
import com.segment.analytics.substrata.kotlin.j2v8.J2V8Engine
import com.segment.analytics.substrata.kotlin.j2v8.toJSObject
import com.segment.analytics.substrata.kotlin.jsValueToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
EdgeFn is the wrapper class that will end up calling into
the JS for a given EdgeFn.
 */
internal class EdgeFn(
    private val jsPlugin: JSValue,
    override val type: Plugin.Type,
    private val engine: J2V8Engine) : EventPlugin {

    override lateinit var analytics: Analytics

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)
        jsPlugin.call("update", listOf(
            JSValue.JSString(settings.toMap()),
            JSValue.JSString(type.toString())
        ))
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        val dict = engine.toJSObject(event)

        val modified = jsPlugin.call("execute", listOf(dict))

        if (modified is JSValue.JSNull) {
            return null
        }

        val json = jsValueToString(modified)
        return when(event) {
            is IdentifyEvent -> Json.decodeFromString<IdentifyEvent>(json)
            is TrackEvent -> Json.decodeFromString<TrackEvent>(json)
            is ScreenEvent -> Json.decodeFromString<ScreenEvent>(json)
            is AliasEvent -> Json.decodeFromString<AliasEvent>(json)
            is GroupEvent -> Json.decodeFromString<GroupEvent>(json)
            else -> event
        }
    }


    private fun JSValue.call(function: String, params: List<JSValue>): JSValue {
        val objectName = jsValueToString(this)
        return engine.call("$objectName.$function", params)
    }

    // to conform with swift's implementation, convert to string instead of map
    private fun Settings.toMap() = Json.encodeToString(this)
        // (Json.encodeToJsonElement(this) as JsonObject).toMap()
}