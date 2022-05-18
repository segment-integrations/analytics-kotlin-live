package com.segment.analytics.edgefn.kotlin

import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Object
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.substrata.kotlin.JSValue
import com.segment.analytics.substrata.kotlin.j2v8.*
import com.segment.analytics.substrata.kotlin.jsValueToString
import com.segment.analytics.substrata.kotlin.wrapAsJSValue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json.Default.encodeToJsonElement

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

        if (jsPlugin is JSValue.JSObject) {
            val list = listOf(Json.encodeToJsonElement(settings), JsonPrimitive(type.toString()))
            engine.syncRunEngine {
                jsPlugin.content.executeVoidFunction("update", engine.toJSArray(list).content)
            }

        }
    }

    override fun alias(payload: AliasEvent): BaseEvent? {
        return execute(payload)
    }

    override fun group(payload: GroupEvent): BaseEvent? {
        return execute(payload)
    }

    override fun identify(payload: IdentifyEvent): BaseEvent? {
        return execute(payload)
    }

    override fun screen(payload: ScreenEvent): BaseEvent? {
        return execute(payload)
    }

    override fun track(payload: TrackEvent): BaseEvent? {
        return execute(payload)
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        val list = listOf(event.context, event.integrations)
        val modified = engine.syncRunEngine {
            val arr = V8Array(it)
            arr.push(event.toV8Object(it))
            return@syncRunEngine (jsPlugin as JSValue.JSObject).content.executeObjectFunction("execute", arr)
        }

        if (modified !is JSValue.JSObject) {
            return null
        }

        return when(event) {
            is IdentifyEvent -> convert<IdentifyEvent>(modified)
            is TrackEvent -> convert<TrackEvent>(modified)
            is ScreenEvent -> convert<ScreenEvent>(modified)
            is AliasEvent -> convert<AliasEvent>(modified)
            is GroupEvent -> convert<GroupEvent>(modified)
            else -> event
        }
    }

    // to conform with swift's implementation, convert to string instead of map
    private fun Settings.toMap() = Json.encodeToString(this)
        // (Json.encodeToJsonElement(this) as JsonObject).toMap()

    private fun <T: BaseEvent> convert(event: JSValue.JSObject): BaseEvent? {
        val ret = engine.syncRun {
            toSegmentEvent<T>(event)!!
        }

        return ret
    }
}