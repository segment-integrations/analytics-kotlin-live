package com.segment.analytics.liveplugins.kotlin

import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.GroupEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.EncodeDefaultsJson
import com.segment.analytics.kotlin.core.utilities.toBaseEvent
import com.segment.analytics.substrata.kotlin.JSObject
import com.segment.analytics.substrata.kotlin.JSScope
import com.segment.analytics.substrata.kotlin.JsonElementConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * LivePlugin is the native Analytics Plugin representation of the jsPlugin specified
 * in the LivePlugins bundle.
 * LivePlugin is responsible for ensuring all data being passed is understandable by JS
 */
internal class LivePlugin(
    private val jsPlugin: JSObject,
    override val type: Plugin.Type,
    private val engine: JSScope
) : EventPlugin {

    override lateinit var analytics: Analytics

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)

        val settingsJson = Json.encodeToJsonElement(settings).jsonObject
        engine.sync {
            call(jsPlugin,
                "update",
                JsonElementConverter.write(settingsJson, context),
                type.toString())
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
        val payload = EncodeDefaultsJson.encodeToJsonElement(event)
        val ret = engine.await {
            val modified = call(
                jsPlugin,
                "execute",
                JsonElementConverter.write(payload, context)
            )

            return@await if (modified is JSObject) {
                JsonElementConverter.read(modified).jsonObject.toBaseEvent()
            } else {
                null
            }
        }
        return ret
    }
}