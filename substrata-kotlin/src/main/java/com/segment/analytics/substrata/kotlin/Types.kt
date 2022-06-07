package com.segment.analytics.substrata.kotlin

import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Function
import com.eclipsesource.v8.V8Object
import com.segment.analytics.substrata.kotlin.j2v8.fromV8Array
import com.segment.analytics.substrata.kotlin.j2v8.fromV8Object
import com.segment.analytics.substrata.kotlin.j2v8.memScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * We cant add extensions to existing types like in swift, so its up to the user to provide
 * serializer/deserializer for custom types. We will provide all primitive ones.
 */
interface JSValue {

    @JvmInline
    value class JSString(val content: String) : JSValue

    @JvmInline
    value class JSBool(val content: Boolean) : JSValue

    @JvmInline
    value class JSInt(val content: Int) : JSValue

    @JvmInline
    value class JSDouble(val content: Double) : JSValue

    class JSObject : JSValue {
        val content: JsonObject?

        constructor(obj: V8Object) {
            content = fromV8Object(obj)
        }

        constructor(obj: JsonObject) {
            content = obj
        }
    }

    class JSArray : JSValue {
        val content: JsonArray?

        constructor(array: V8Array) {
            content = fromV8Array(array)
        }

        constructor(array: JsonArray) {
            content = array
        }
    }

    class JSFunction(val fn: V8Function) : JSValue

    class JSObjectReference(val ref: V8Object) : JSValue {
        val content: JsonObject? = fromV8Object(ref)
    }

    object JSUndefined : JSValue // might not need this, might just be a compile time error
    object JSNull : JSValue
}

// Use this API fro wrapping values coming from J2V8
// Must be run on the J2V8 thread
internal fun wrapAsJSValue(obj: Any?): JSValue {
    return when (obj) {
        null -> return JSValue.JSNull
        is Boolean -> JSValue.JSBool(obj)
        is Int -> JSValue.JSInt(obj)
        is Double -> JSValue.JSDouble(obj)
        is String -> JSValue.JSString(obj)
        is V8Array -> JSValue.JSArray(obj)
        is V8Object -> JSValue.JSObject(obj)
        else -> JSValue.JSUndefined
    }
}
