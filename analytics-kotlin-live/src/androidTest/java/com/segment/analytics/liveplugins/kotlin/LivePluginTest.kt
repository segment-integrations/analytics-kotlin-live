package com.segment.analytics.liveplugins.kotlin

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.segment.analytics.kotlin.android.AndroidStorageProvider
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.liveplugins.kotlin.utils.testAnalytics
import com.segment.analytics.substrata.kotlin.JSScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LivePluginTest {

    private lateinit var engine: JSScope
    private lateinit var jsAnalytics: JSAnalytics
    private var exceptionThrown: Throwable? = null
    private val logger = Logger()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        exceptionThrown = null

        engine = JSScope{ exception ->
            exceptionThrown = exception
        }

        // Create a mock Analytics instance for testing using MockK
        val mockAnalytics = createMockAnalytics()
        jsAnalytics = JSAnalytics(mockAnalytics, engine)

        // Setup the engine similar to LivePlugins.configureEngine
        engine.sync {

            // Export JSAnalytics to the engine
            export(jsAnalytics, "Analytics", "analytics")
            export(logger, "Logger", "logger")

            // Evaluate the embedded JS scripts
            evaluate(EmbeddedJS.ENUM_SETUP_SCRIPT)
            evaluate(EmbeddedJS.LIVE_PLUGINS_BASE_SETUP_SCRIPT)
        }
    }

    private fun createMockAnalytics(): Analytics {
        val analytics  = testAnalytics(
            Configuration(
                writeKey = "123",
                application = InstrumentationRegistry.getInstrumentation().targetContext,
                storageProvider = AndroidStorageProvider
            ),
            testScope, testDispatcher
        )

        return analytics
    }

    @After
    fun tearDown() {
        if (exceptionThrown == null && ::engine.isInitialized) {
            engine.release()
        }
    }

    @Test
    fun testLivePlugin() {
        engine.sync {
            evaluate("""
                class AnonymizeIPs extends LivePlugin {
                    update(settings, type) { 
                        logger.updateCalled++
                    }
                    
                    execute(event) {
                        logger.executeCalled++
                        return super.execute(event)
                    }
                }
                let plugin = new AnonymizeIPs();
                analytics.add(plugin);
                
                
                // Test a complex interaction with multiple method calls
                analytics.identify("complex-user", {
                    name: "Complex User",
                    age: 30
                });
                
                analytics.track("Complex Event", {
                    category: "test",
                    value: 100
                });
                
                analytics.screen("Complex Screen", "Category", {
                    loaded_time: 2.5
                });
                
                analytics.group("complex-group", {
                    plan: "premium"
                });
                
                analytics.alias("complex-alias");
            """)
        }

        assertNull("No exception should be thrown", exceptionThrown)
        assertEquals(1, logger.updateCalled)
        assertEquals(5, logger.executeCalled)
    }

    class Logger {
        var updateCalled = 0
        var executeCalled = 0
    }
}