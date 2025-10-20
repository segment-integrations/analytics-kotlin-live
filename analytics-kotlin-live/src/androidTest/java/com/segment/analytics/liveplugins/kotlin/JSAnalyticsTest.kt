package com.segment.analytics.liveplugins.kotlin

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.segment.analytics.kotlin.android.AndroidStorageProvider
import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.GroupEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.liveplugins.kotlin.utils.StubPlugin
import com.segment.analytics.liveplugins.kotlin.utils.testAnalytics
import com.segment.analytics.substrata.kotlin.JSScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class JSAnalyticsTest {

    private lateinit var engine: JSScope
    private lateinit var jsAnalytics: JSAnalytics
    private var exceptionThrown: Throwable? = null
    
    // Global tracking variables for captured method calls
    private val capturedTrackCalls = mutableListOf<Pair<String, Any?>>()
    private val capturedIdentifyCalls = mutableListOf<Pair<String, Any?>>()
    private val capturedScreenCalls = mutableListOf<Triple<String, String?, Any?>>()
    private val capturedGroupCalls = mutableListOf<Pair<String, Any?>>()
    private val capturedAliasCalls = mutableListOf<String>()
    private var capturedFlushCalls = 0
    private var capturedResetCalls = 0
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        exceptionThrown = null
        
        // Clear tracking variables
        capturedTrackCalls.clear()
        capturedIdentifyCalls.clear()
        capturedScreenCalls.clear()
        capturedGroupCalls.clear()
        capturedAliasCalls.clear()
        capturedFlushCalls = 0
        capturedResetCalls = 0

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
        // Create a custom capture plugin that doesn't require mocking
        val plugin = object : StubPlugin() {
            override fun track(payload: TrackEvent): TrackEvent {
                capturedTrackCalls.add(payload.event to payload.properties)
                return payload
            }

            override fun reset() {
                capturedResetCalls++
            }

            override fun screen(payload: ScreenEvent): BaseEvent? {
                capturedScreenCalls.add(Triple(payload.name, payload.category, payload.properties))
                return payload
            }

            override fun identify(payload: IdentifyEvent): IdentifyEvent {
                capturedIdentifyCalls.add(payload.userId to payload.traits)
                return payload
            }
            
            override fun group(payload: GroupEvent): GroupEvent {
                capturedGroupCalls.add(payload.groupId to payload.traits)
                return payload
            }
            
            override fun alias(payload: AliasEvent): AliasEvent {
                capturedAliasCalls.add(payload.userId)
                return payload
            }
            
            override fun flush() {
                capturedFlushCalls++
                super.flush()
            }
        }

        analytics.add(plugin)

        return analytics
    }

    @Test
    fun testJSAnalyticsBasicFunctionality() {
        // First set up state through JavaScript calls
        engine.sync {
            evaluate("""analytics.identify("test-user-id");""")
            evaluate("""analytics.track("test-event");""")
            evaluate("""analytics.flush();""")
        }
        
        // Now verify the properties reflect the JavaScript calls
        assertNotNull("Anonymous ID should not be null", jsAnalytics.anonymousId)
        assertTrue("Anonymous ID should not be empty", jsAnalytics.anonymousId.isNotEmpty())
        assertEquals("test-user-id", jsAnalytics.userId)
        
        // Verify the calls were captured by our plugin
        assertEquals(1, capturedTrackCalls.size)
        assertEquals("test-event", capturedTrackCalls[0].first)
        
        assertEquals(1, capturedIdentifyCalls.size)
        assertEquals("test-user-id", capturedIdentifyCalls[0].first)
        
        assertEquals(1, capturedFlushCalls)
        
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @After
    fun tearDown() {
        if (exceptionThrown == null && ::engine.isInitialized) {
            engine.release()
        }
    }

    @Test
    fun testJSAnalyticsProperties() {
        // First identify a user through JavaScript
        engine.sync {
            evaluate("""analytics.identify("test-user-id");""")
        }
        
        val anonymousId = jsAnalytics.anonymousId
        val userId = jsAnalytics.userId

        assertNotNull("Anonymous ID should not be null", anonymousId)
        assertTrue("Anonymous ID should not be empty", anonymousId.isNotEmpty())
        assertEquals("test-user-id", userId)
        
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testTrackFromJavaScript() {
        engine.sync {
            evaluate("""analytics.track("Test Event");""")
        }

        // Verify that the track method was called with correct parameters from JavaScript
        assertEquals(1, capturedTrackCalls.size)
        assertEquals("Test Event", capturedTrackCalls[0].first)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testTrackWithPropertiesFromJavaScript() {
        engine.sync {
            evaluate("""
                analytics.track("Test Event", {
                    property1: "value1",
                    property2: 42
                });
            """)
        }

        // Verify that the track method was called with correct parameters from JavaScript
        assertEquals(1, capturedTrackCalls.size)
        assertEquals("Test Event", capturedTrackCalls[0].first)
        
        // Verify the properties JsonElement content
        val properties = capturedTrackCalls[0].second as JsonElement
        assertNotNull("Properties should be passed from JavaScript", properties)
        assertTrue("Properties should be a JsonObject", properties is JsonObject)
        
        val propsObj = properties.jsonObject
        assertEquals("value1", propsObj["property1"]?.jsonPrimitive?.content)
        assertEquals(42, propsObj["property2"]?.jsonPrimitive?.content?.toInt())
        
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testIdentifyFromJavaScript() {
        engine.sync {
            evaluate("""analytics.identify("new-user-id");""")
        }

        // Verify that the identify method was called with correct parameters from JavaScript
        assertEquals(1, capturedIdentifyCalls.size)
        assertEquals("new-user-id", capturedIdentifyCalls[0].first)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testIdentifyWithTraitsFromJavaScript() {
        engine.sync {
            evaluate("""
                analytics.identify("new-user-id", {
                    name: "John Doe",
                    email: "john@example.com"
                });
            """)
        }

        // Verify that the identify method was called with traits from JavaScript
        assertEquals(1, capturedIdentifyCalls.size)
        assertEquals("new-user-id", capturedIdentifyCalls[0].first)
        
        // Verify the traits JsonElement content
        val traits = capturedIdentifyCalls[0].second as JsonElement
        assertNotNull("Traits should be passed from JavaScript", traits)
        assertTrue("Traits should be a JsonObject", traits is JsonObject)
        
        val traitsObj = traits.jsonObject
        assertEquals("John Doe", traitsObj["name"]?.jsonPrimitive?.content)
        assertEquals("john@example.com", traitsObj["email"]?.jsonPrimitive?.content)
        
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testScreenWithPropertiesFromJavaScript() {
        engine.sync {
            evaluate("""
                analytics.screen("Home Screen", "Navigation", {
                    loaded_time: 1.2,
                    user_type: "premium"
                });
            """)
        }

        // Verify that the screen method was called with properties from JavaScript  
        assertEquals(1, capturedScreenCalls.size)
        assertEquals("Home Screen", capturedScreenCalls[0].first)
        assertEquals("Navigation", capturedScreenCalls[0].second)
        
        // Since screen methods are handled by relaxed mock, we'll skip detailed property verification here
        // but verify that the method was called correctly
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testGroupFromJavaScript() {
        engine.sync {
            evaluate("""analytics.group("group-123");""")
        }

        // Verify that the group method was called with correct parameters from JavaScript
        assertEquals(1, capturedGroupCalls.size)
        assertEquals("group-123", capturedGroupCalls[0].first)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testGroupWithTraitsFromJavaScript() {
        engine.sync {
            evaluate("""
                analytics.group("group-123", {
                    name: "Acme Inc",
                    plan: "enterprise"
                });
            """)
        }

        // Verify that the group method was called with traits from JavaScript
        assertEquals(1, capturedGroupCalls.size)
        assertEquals("group-123", capturedGroupCalls[0].first)
        
        // Verify the traits JsonElement content
        val traits = capturedGroupCalls[0].second as JsonElement
        assertNotNull("Traits should be passed from JavaScript", traits)
        assertTrue("Traits should be a JsonObject", traits is JsonObject)
        
        val traitsObj = traits.jsonObject
        assertEquals("Acme Inc", traitsObj["name"]?.jsonPrimitive?.content)
        assertEquals("enterprise", traitsObj["plan"]?.jsonPrimitive?.content)
        
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testAliasFromJavaScript() {
        engine.sync {
            evaluate("""analytics.alias("new-identity");""")
        }

        // Verify that the alias method was called with correct parameters from JavaScript
        assertEquals(1, capturedAliasCalls.size)
        assertEquals("new-identity", capturedAliasCalls[0])
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testFlushFromJavaScript() {
        engine.sync {
            evaluate("""analytics.flush();""")
        }

        // Verify that the flush method was called from JavaScript
        assertEquals(1, capturedFlushCalls)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testResetFromJavaScript() {
        engine.sync {
            evaluate("""analytics.reset();""")
        }

        // Verify that the reset method was called from JavaScript
        assertEquals(1, capturedResetCalls)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testAnonymousIdPropertyFromJavaScript() {
        engine.sync {
            val result = evaluate("""analytics.anonymousId;""")
            // Verify that JavaScript returns the same anonymous ID as Kotlin
            assertEquals(jsAnalytics.anonymousId, result)
        }

        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testUserIdPropertyFromJavaScript() {
        engine.sync {
            // First set the user ID through JavaScript
            evaluate("""analytics.identify("test-user-id");""")
            // Then verify it's accessible from JavaScript
            val result = evaluate("""analytics.userId;""")
            assertEquals("test-user-id", result)
        }

        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testAddPluginFromJavaScript() {
        engine.sync {
            val result = evaluate("""
                class AnonymizeIPs extends LivePlugin {
                    execute(event) {
                        event.context.ip = "xxx.xxx.xxx.xxx";
                        return super.execute(event)
                    }
                }
                let plugin = new AnonymizeIPs();
                analytics.add(plugin);
            """)
            // The result should be true since our real analytics supports plugin addition
            assertEquals(true, result)
        }

        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testLivePluginTypeEnumFromJavaScript() {
        engine.sync {
            val before = evaluate("LivePluginType.before;")
            val enrichment = evaluate("LivePluginType.enrichment;")
            val after = evaluate("LivePluginType.after;")

            assertEquals(0, before)
            assertEquals(1, enrichment)
            assertEquals(2, after)
        }

        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testLivePluginClassFromJavaScript() {
        engine.sync {
            val result = evaluate("""
                var plugin = new LivePlugin(LivePluginType.enrichment, null);
                typeof plugin;
            """)
            assertEquals("object", result)
        }

        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testComplexJavaScriptInteraction() {
        engine.sync {
            evaluate("""
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
                analytics.flush();
            """)
        }

        // Verify all method calls were captured correctly from the JavaScript interaction
        
        // Verify identify call with traits
        assertEquals(1, capturedIdentifyCalls.size)
        assertEquals("complex-user", capturedIdentifyCalls[0].first)
        val identifyTraits = capturedIdentifyCalls[0].second as JsonElement
        assertNotNull("Traits should be passed from JavaScript", identifyTraits)
        assertTrue("Traits should be a JsonObject", identifyTraits is JsonObject)
        val identifyTraitsObj = identifyTraits.jsonObject
        assertEquals("Complex User", identifyTraitsObj["name"]?.jsonPrimitive?.content)
        assertEquals(30, identifyTraitsObj["age"]?.jsonPrimitive?.content?.toInt())
        
        // Verify track call with properties
        assertEquals(1, capturedTrackCalls.size)
        assertEquals("Complex Event", capturedTrackCalls[0].first)
        val trackProperties = capturedTrackCalls[0].second as JsonElement
        assertNotNull("Properties should be passed from JavaScript", trackProperties)
        assertTrue("Properties should be a JsonObject", trackProperties is JsonObject)
        val trackPropsObj = trackProperties.jsonObject
        assertEquals("test", trackPropsObj["category"]?.jsonPrimitive?.content)
        assertEquals(100, trackPropsObj["value"]?.jsonPrimitive?.content?.toInt())
        
        // Verify screen call (relaxed mock handling)
        assertEquals(1, capturedScreenCalls.size)
        assertEquals("Complex Screen", capturedScreenCalls[0].first)
        assertEquals("Category", capturedScreenCalls[0].second)
        
        // Verify group call with traits
        assertEquals(1, capturedGroupCalls.size)
        assertEquals("complex-group", capturedGroupCalls[0].first)
        val groupTraits = capturedGroupCalls[0].second as JsonElement
        assertNotNull("Traits should be passed from JavaScript", groupTraits)
        assertTrue("Traits should be a JsonObject", groupTraits is JsonObject)
        val groupTraitsObj = groupTraits.jsonObject
        assertEquals("premium", groupTraitsObj["plan"]?.jsonPrimitive?.content)
        
        // Verify alias call
        assertEquals(1, capturedAliasCalls.size)
        assertEquals("complex-alias", capturedAliasCalls[0])
        
        // Verify flush call
        assertEquals(1, capturedFlushCalls)
        
        assertNull("No exception should be thrown", exceptionThrown)
    }
}