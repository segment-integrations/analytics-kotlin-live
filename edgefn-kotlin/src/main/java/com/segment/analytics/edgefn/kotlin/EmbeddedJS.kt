package com.segment.analytics.edgefn.kotlin

import com.segment.analytics.kotlin.core.platform.Plugin

object EmbeddedJS {
    val ENUM_SETUP_SCRIPT = """
    const EdgeFnType = {
        before: ${Plugin.Type.Before},
        enrichment: ${Plugin.Type.Enrichment},
        after: ${Plugin.Type.After},
        utility: ${Plugin.Type.Utility}
    };
    """

    const val EDGE_FN_BASE_SETUP_SCRIPT = """
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
    """
}