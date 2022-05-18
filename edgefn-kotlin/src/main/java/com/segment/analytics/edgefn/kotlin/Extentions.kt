package com.segment.analytics.edgefn.kotlin

import com.segment.analytics.kotlin.core.platform.Plugin

fun Plugin.Type.toInt() = when(this) {
    Plugin.Type.Before -> 0
    Plugin.Type.Enrichment -> 1
    Plugin.Type.After -> 2
    Plugin.Type.Utility -> 3
    else -> -1
}

fun pluginTypeFromInt(value: Int) = when (value) {
    0 -> Plugin.Type.Before
    1 -> Plugin.Type.Enrichment
    2 -> Plugin.Type.After
    3 -> Plugin.Type.Utility
    else -> null
}