package com.segment.analytics.substrata.kotlin.j2v8

import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.GroupEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.utilities.EncodeDefaultsJson
import com.segment.analytics.kotlin.core.utilities.LenientJson
import com.segment.analytics.kotlin.core.utilities.safeJsonObject
import com.segment.analytics.kotlin.core.utilities.safeJsonPrimitive
import com.segment.analytics.substrata.kotlin.JSValue
import com.segment.analytics.substrata.kotlin.wrapAsJSValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

fun BaseEvent.toJSObject(): JSValue.JSObject {
    val x = when (this) {
        is TrackEvent -> EncodeDefaultsJson.encodeToJsonElement(TrackEvent.serializer(), this)
        is IdentifyEvent -> EncodeDefaultsJson.encodeToJsonElement(IdentifyEvent.serializer(), this)
        is GroupEvent -> EncodeDefaultsJson.encodeToJsonElement(GroupEvent.serializer(), this)
        is AliasEvent -> EncodeDefaultsJson.encodeToJsonElement(AliasEvent.serializer(), this)
        is ScreenEvent -> EncodeDefaultsJson.encodeToJsonElement(ScreenEvent.serializer(), this)
    }
    return JSValue.JSObject(x.jsonObject)
}

fun JSValue.JSObject.toBaseEvent(): BaseEvent? {
    val event = this.content
    val type = event?.get("type")?.safeJsonPrimitive?.content

    return when (type) {
        "identify" -> LenientJson.decodeFromJsonElement(IdentifyEvent.serializer(), event)
        "track" -> LenientJson.decodeFromJsonElement(TrackEvent.serializer(), event)
        "screen" -> LenientJson.decodeFromJsonElement(ScreenEvent.serializer(), event)
        "group" -> LenientJson.decodeFromJsonElement(GroupEvent.serializer(), event)
        "alias" -> LenientJson.decodeFromJsonElement(AliasEvent.serializer(), event)
        else -> null
    }
}

//fun J2V8Engine.toJSObject(event: BaseEvent): JSValue.JSObject =
//    syncRunEngine { event.toV8Object(underlying) } as JSValue.JSObject
//
//fun J2V8Engine.toJSObject(map: Map<String, JsonElement>): JSValue.JSObject =
//    syncRunEngine { underlying.toV8Object(map) } as JSValue.JSObject

//fun J2V8Engine.toJSArray(list: List<JsonElement>): JSValue.JSArray =
//    syncRunEngine { underlying.toV8Array(list) } as JSValue.JSArray

//fun <T: BaseEvent> toSegmentEvent(jsObj: JSValue.JSObject): T? =
//    jsObj.content.toSegmentEvent()
//
//fun J2V8Engine.fromJSObject(jsObj: JSValue.JSObject): JsonObject? =
//    syncRun { fromV8Object(jsObj.content) }
//
//fun J2V8Engine.fromJSArray(jsObj: JSValue.JSArray): JsonArray? =
//    syncRun { fromV8Array(jsObj.content) }