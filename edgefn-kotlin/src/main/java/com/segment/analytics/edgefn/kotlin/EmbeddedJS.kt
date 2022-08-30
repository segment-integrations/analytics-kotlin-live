package com.segment.analytics.edgefn.kotlin

import com.segment.analytics.kotlin.core.platform.Plugin

// TODO do we want utility??
object EmbeddedJS {
    val ENUM_SETUP_SCRIPT = """
    const EdgeFnType = {
        before: ${Plugin.Type.Before.toInt()},
        enrichment: ${Plugin.Type.Enrichment.toInt()},
        after: ${Plugin.Type.After.toInt()},
//        utility: ${Plugin.Type.Utility.toInt()}
    };
    """.trimIndent()

    val EDGE_FN_BASE_SETUP_SCRIPT = """
    class EdgeFn {
        constructor(type, destination) {
            console.log("js: EdgeFn.constructor() called");
            this.type = type;
            this.destination = destination;
        }
        update(settings, type) { }
        execute(event) {
            console.log("js: EdgeFn.execute() called");
            var result = event;
            switch(event.type) {
                case "identify":
                    result = this.identify(event);
                case "track":
                    result = this.track(event);
                case "group":
                    result = this.group(event);
                case "alias":
                    result = this.alias(event);
                case "screen":
                    result = this.screen(event);
            }
            return result;
        }
        identify(event) { return event; }
        track(event) { return event; }
        group(event) { return event; }
        alias(event) { return event; }
        screen(event) { return event; }
        reset() { }
        flush() { }
    }
    """.trimIndent()
}

fun Plugin.Type.toInt() = when(this) {
    Plugin.Type.Before -> 0
    Plugin.Type.Enrichment -> 1
    Plugin.Type.After -> 2
//    Plugin.Type.Utility -> 3
    else -> -1
}

fun pluginTypeFromInt(value: Int) = when (value) {
    0 -> Plugin.Type.Before
    1 -> Plugin.Type.Enrichment
    2 -> Plugin.Type.After
//    3 -> Plugin.Type.Utility
    else -> null
}