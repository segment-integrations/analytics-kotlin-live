package com.segment.analytics.liveplugins.kotlin

import com.segment.analytics.kotlin.core.platform.Plugin
import org.junit.Test
import org.junit.Assert.*

class EmbeddedJSTest {

    @Test
    fun pluginTypeToInt_mapsCorrectValues() {
        assertEquals("Before type should map to 0", 0, Plugin.Type.Before.toInt())
        assertEquals("Enrichment type should map to 1", 1, Plugin.Type.Enrichment.toInt())
        assertEquals("After type should map to 2", 2, Plugin.Type.After.toInt())
    }

    @Test
    fun pluginTypeToInt_handlesUnknownType() {
        val unknownType = Plugin.Type.Destination
        assertEquals("Unknown type should map to -1", -1, unknownType.toInt())
    }

    @Test
    fun pluginTypeFromInt_mapsCorrectValues() {
        assertEquals("0 should map to Before type", Plugin.Type.Before, pluginTypeFromInt(0))
        assertEquals("1 should map to Enrichment type", Plugin.Type.Enrichment, pluginTypeFromInt(1))
        assertEquals("2 should map to After type", Plugin.Type.After, pluginTypeFromInt(2))
    }

    @Test
    fun pluginTypeFromInt_handlesInvalidValues() {
        assertNull("Invalid positive value should return null", pluginTypeFromInt(99))
        assertNull("Invalid negative value should return null", pluginTypeFromInt(-1))
        assertNull("Invalid large value should return null", pluginTypeFromInt(Integer.MAX_VALUE))
    }

    @Test
    fun pluginTypeFromInt_handlesEdgeCases() {
        assertNull("3 should return null (utility is commented out)", pluginTypeFromInt(3))
        assertNull("4 should return null", pluginTypeFromInt(4))
    }

    @Test
    fun pluginTypeConversion_isReversible() {
        val types = listOf(Plugin.Type.Before, Plugin.Type.Enrichment, Plugin.Type.After)
        
        for (type in types) {
            val intValue = type.toInt()
            val convertedBack = pluginTypeFromInt(intValue)
            assertEquals("Conversion should be reversible for $type", type, convertedBack)
        }
    }

}