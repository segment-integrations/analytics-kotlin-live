package com.segment.analytics.liveplugins.kotlin

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.segment.analytics.liveplugins.kotlin.utils.MemorySharedPreferences
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
}