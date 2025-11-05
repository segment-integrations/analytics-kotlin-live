package com.segment.analytics.liveplugins.kotlin

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.segment.analytics.liveplugins.kotlin.utils.MemorySharedPreferences
import com.segment.analytics.substrata.kotlin.JSArray
import com.segment.analytics.substrata.kotlin.JSObject
import com.segment.analytics.substrata.kotlin.JSScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class JSStorageTest {

    private lateinit var engine: JSScope
    private lateinit var jsStorage: JSStorage
    private var exceptionThrown: Throwable? = null

    @Before
    fun setUp() {
        exceptionThrown = null

        engine = JSScope{ exception ->
            exceptionThrown = exception
        }
        jsStorage = JSStorage(MemorySharedPreferences(), engine)
        // Setup the engine similar to LivePlugins.configureEngine
        engine.sync {
            export(jsStorage, "Storage", "storage")
        }
    }

    @Test
    fun testJSStorageWithInt() {
        // set from js
        var value = engine.await {
            evaluate("""storage.setValue("int", 1)""")
            evaluate("""storage.getValue("int")""")
        }
        assertNull(exceptionThrown)
        assertEquals(1, value)
        assertEquals(1, jsStorage.getValue("int"))

        // set from native
        jsStorage.setValue("int", 2)
        value = engine.await {
            evaluate("""storage.getValue("int")""")
        }
        assertEquals(2, value)
        assertEquals(2, jsStorage.getValue("int"))
    }

    @Test
    fun testJSStorageWithBoolean() {
        // set from js
        var value = engine.await {
            evaluate("""storage.setValue("boolean", true)""")
            evaluate("""storage.getValue("boolean")""")
        }
        assertNull(exceptionThrown)
        assertEquals(true, value)
        assertEquals(true, jsStorage.getValue("boolean"))

        // set from native
        jsStorage.setValue("boolean", false)
        value = engine.await {
            evaluate("""storage.getValue("boolean")""")
        }
        assertEquals(false, value)
        assertEquals(false, jsStorage.getValue("boolean"))
    }

    @Test
    fun testJSStorageWithDouble() {
        // set from js
        var value = engine.await {
            evaluate("""storage.setValue("double", 3.14)""")
            evaluate("""storage.getValue("double")""")
        }
        assertNull(exceptionThrown)
        assertEquals(3.14, value)
        assertEquals(3.14, jsStorage.getValue("double"))

        // set from native
        jsStorage.setValue("double", 2.71)
        value = engine.await {
            evaluate("""storage.getValue("double")""")
        }
        assertEquals(2.71, value)
        assertEquals(2.71, jsStorage.getValue("double"))
    }

    @Test
    fun testJSStorageWithString() {
        // set from js
        var value = engine.await {
            evaluate("""storage.setValue("string", "hello")""")
            evaluate("""storage.getValue("string")""")
        }
        assertNull(exceptionThrown)
        assertEquals("hello", value)
        assertEquals("hello", jsStorage.getValue("string"))

        // set from native
        jsStorage.setValue("string", "world")
        value = engine.await {
            evaluate("""storage.getValue("string")""")
        }
        assertEquals("world", value)
        assertEquals("world", jsStorage.getValue("string"))
    }

    @Test
    fun testJSStorageWithLong() {
        // set from js
        var value = engine.await {
            evaluate("""storage.setValue("long", 1234567890123)""")
            evaluate("""storage.getValue("long")""")
        }
        assertNull(exceptionThrown)
        assertEquals(1234567890123L.toDouble(), value)
        assertEquals(1234567890123L.toDouble(), jsStorage.getValue("long"))

        // set from native
        jsStorage.setValue("long", 9876543210987L)
        value = engine.await {
            evaluate("""storage.getValue("long")""")
        }
        assertEquals(9876543210987L.toDouble(), value)
        assertEquals(9876543210987L.toDouble(), jsStorage.getValue("long"))
    }

    @Test
    fun testJSStorageWithJSObject() {
        // set from js
        var value = engine.await {
            evaluate("""storage.setValue("object", {name: "test", value: 42})""")
            evaluate("""storage.getValue("object")""")
        }
        assertNull(exceptionThrown)
        val jsObjectValue = value as JSObject
        assertEquals("test", jsObjectValue.getString("name"))
        assertEquals(42, jsObjectValue.getInt("value"))

        // set from native
        val nativeObject = engine.await {
            evaluate("""{name: "native", value: 100}""")
        } as JSObject
        jsStorage.setValue("object", nativeObject)
        value = engine.await {
            evaluate("""storage.getValue("object")""")
        }
        val retrievedObject = value as JSObject
        assertEquals("native", retrievedObject.getString("name"))
        assertEquals(100, retrievedObject.getInt("value"))
    }

    @Test
    fun testJSStorageWithJSArray() {
        // set from js
        var value = engine.await {
            evaluate("""storage.setValue("array", [1, "test", true])""")
            evaluate("""storage.getValue("array")""")
        }
        assertNull(exceptionThrown)
        val jsArrayValue = value as JSArray
        assertEquals(3, jsArrayValue.size)
        assertEquals(1, jsArrayValue.getInt(0))
        assertEquals("test", jsArrayValue.getString(1))
        assertEquals(true, jsArrayValue.getBoolean(2))

        // set from native
        val nativeArray = engine.await {
            evaluate("""[42, "native", false]""")
        } as JSArray
        jsStorage.setValue("array", nativeArray)
        value = engine.await {
            evaluate("""storage.getValue("array")""")
        }
        val retrievedArray = value as JSArray
        assertEquals(3, retrievedArray.size)
        assertEquals(42, retrievedArray.getInt(0))
        assertEquals("native", retrievedArray.getString(1))
        assertEquals(false, retrievedArray.getBoolean(2))
    }
}