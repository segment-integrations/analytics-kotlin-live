package com.segment.analytics.liveplugins.kotlin

import android.content.SharedPreferences
import androidx.core.content.edit
import com.segment.analytics.kotlin.core.utilities.getBoolean
import com.segment.analytics.kotlin.core.utilities.getDouble
import com.segment.analytics.kotlin.core.utilities.getInt
import com.segment.analytics.kotlin.core.utilities.getLong
import com.segment.analytics.kotlin.core.utilities.getString
import com.segment.analytics.substrata.kotlin.JSArray
import com.segment.analytics.substrata.kotlin.JSObject
import com.segment.analytics.substrata.kotlin.JSScope
import com.segment.analytics.substrata.kotlin.JsonElementConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class JSStorage {

    internal var sharedPreferences: SharedPreferences? = null

    private var engine: JSScope? = null

    // JSEngine requires an empty constructor to be able to export this class
    constructor() {}

    constructor(sharedPreferences: SharedPreferences, engine: JSScope) {
        this.sharedPreferences = sharedPreferences
        this.engine = engine
    }

    fun setValue(key: String, value: Boolean) {
        save(key, value, TYPE_BOOLEAN)
    }

    fun setValue(key: String, value: Double) {
        save(key, value, TYPE_DOUBLE)
    }

    fun setValue(key: String, value: Int) {
        save(key, value, TYPE_INT)
    }

    fun setValue(key: String, value: String) {
        save(key, value, TYPE_STRING)
    }

    fun setValue(key: String, value: Long) {
        save(key, value, TYPE_LONG)
    }

    fun setValue(key: String, value: JSObject) {
        save(
            key,
            JsonElementConverter.read(value),
            TYPE_OBJECT
        )
    }

    fun setValue(key: String, value: JSArray) {
        save(
            key,
            JsonElementConverter.read(value),
            TYPE_ARRAY
        )
    }

    fun getValue(key: String): Any? {
        return this.sharedPreferences?.getString(key, null)?.let {
             Json.decodeFromString<JsonObject>(it).unwrap()
        }
    }

    fun removeValue(key: String) {
        this.sharedPreferences?.edit(commit = true) { remove(key) }
    }

    private inline fun <reified T> save(key: String, value: T, type: String) {
        val jsonObject = buildJsonObject {
            put(PROP_TYPE, type)
            put(PROP_VALUE, Json.encodeToString(value))
        }

        this.sharedPreferences?.edit(commit = true) { putString(key, Json.encodeToString(jsonObject)) }
    }

    private fun JsonObject.unwrap(): Any? {
        return when(this.getString(PROP_TYPE)) {
            TYPE_BOOLEAN -> this.getBoolean(PROP_VALUE)
            TYPE_INT -> this.getInt(PROP_VALUE)
            TYPE_DOUBLE -> this.getDouble(PROP_VALUE)
            TYPE_STRING -> this.getString(PROP_VALUE)?.let { Json.decodeFromString<String>(it) }
            TYPE_LONG -> this.getLong(PROP_VALUE)?.toDouble()
            else -> {
                this.getString(PROP_VALUE)?.let {
                    val json = Json.decodeFromString<JsonElement>(it)
                    engine?.await(true) {
                        JsonElementConverter.write(json, context)
                    }
                }
            }
        }
    }

    companion object {
        const val PROP_TYPE = "type"
        const val PROP_VALUE = "value"
        const val TYPE_BOOLEAN = "boolean"
        const val TYPE_INT = "int"
        const val TYPE_DOUBLE = "double"
        const val TYPE_STRING = "string"
        const val TYPE_LONG = "long"
        const val TYPE_OBJECT = "object"
        const val TYPE_ARRAY = "array"
    }
}