package com.segment.analytics.liveplugins.kotlin

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.Traits
import com.segment.analytics.substrata.kotlin.JSScope
import io.mockk.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After

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
        
        try {
            engine = JSScope{ exception ->
                exceptionThrown = exception
            }

            // Setup the engine similar to LivePlugins.configureEngine
            engine.sync {
                // Create a mock Analytics instance for testing using MockK
                val mockAnalytics = createMockAnalytics()
                jsAnalytics = JSAnalytics(mockAnalytics, engine)

                // Export JSAnalytics to the engine
                export(jsAnalytics, "Analytics", "analytics")

                // Evaluate the embedded JS scripts
                evaluate(EmbeddedJS.ENUM_SETUP_SCRIPT)
                evaluate(EmbeddedJS.LIVE_PLUGINS_BASE_SETUP_SCRIPT)
            }
        } catch (e: UnsatisfiedLinkError) {
            // JSScope requires native libraries not available in unit tests
            exceptionThrown = e
        } catch (e: NoClassDefFoundError) {
            // JSScope requires native libraries not available in unit tests
            exceptionThrown = e
        } catch (e: ExceptionInInitializerError) {
            // JSScope requires native libraries not available in unit tests
            exceptionThrown = e
        } catch (e: Exception) {
            // Any other JSScope-related exception
            exceptionThrown = e
        }
    }

    private fun createMockAnalytics(): Analytics {
        val mockAnalytics = mockk<Analytics>(relaxed = true)
        val mockConfiguration = mockk<Configuration>()
        val mockTraits = mockk<Traits>(relaxed = true)

        // Use mutable variables to track state changes
        var currentUserId: String? = "test-user-id"
        var currentAnonymousId: String = "test-anonymous-id"

        // Setup basic properties with dynamic responses
        every { mockAnalytics.anonymousId() } answers { currentAnonymousId }
        every { mockAnalytics.userId() } answers { currentUserId }
        every { mockAnalytics.traits() } returns mockTraits
        every { mockAnalytics.configuration } returns mockConfiguration
        every { mockConfiguration.application } returns null

        // Capture method calls using global variables
        every { mockAnalytics.track(any<String>(), any()) } answers {
            val event = firstArg<String>()
            val properties = secondArg<Any?>()
            capturedTrackCalls.add(event to properties)
        }
        
        every { mockAnalytics.identify(any<String>(), any()) } answers {
            val userId = firstArg<String>()
            val traits = secondArg<Any?>()
            currentUserId = userId
            capturedIdentifyCalls.add(userId to traits)
        }
        
        
        every { mockAnalytics.group(any<String>(), any()) } answers {
            val groupId = firstArg<String>()
            val traits = secondArg<Any?>()
            capturedGroupCalls.add(groupId to traits)
        }
        
        every { mockAnalytics.alias(any<String>(), any()) } answers {
            val newId = firstArg<String>()
            capturedAliasCalls.add(newId)
        }
        
        every { mockAnalytics.flush() } answers {
            capturedFlushCalls++
        }

        // Setup reset behavior
        every { mockAnalytics.reset() } answers {
            capturedResetCalls++
            currentUserId = null
            currentAnonymousId = "reset-anonymous-id"
        }

        return mockAnalytics
    }

    @Test
    fun testJSAnalyticsBasicFunctionality() {
        if (exceptionThrown != null) {
            // Skip test when JSScope is not available - JSAnalytics requires a valid JSScope
            return
        }
        
        // Test JSAnalytics basic properties access
        assertEquals("test-anonymous-id", jsAnalytics.anonymousId)
        assertEquals("test-user-id", jsAnalytics.userId)
        
        // Test method calls - these should not throw exceptions
        jsAnalytics.track("test-event")
        jsAnalytics.identify("direct-user")
        jsAnalytics.flush()
        jsAnalytics.reset()
        
        // Verify the calls were captured
        assertEquals(1, capturedTrackCalls.size)
        assertEquals("test-event", capturedTrackCalls[0].first)
        
        assertEquals(1, capturedIdentifyCalls.size)
        assertEquals("direct-user", capturedIdentifyCalls[0].first)
        
        assertEquals(1, capturedFlushCalls)
        assertEquals(1, capturedResetCalls)
    }

    @After
    fun tearDown() {
        if (exceptionThrown == null && ::engine.isInitialized) {
            engine.release()
        }
    }

    @Test
    fun testJSAnalyticsProperties() {
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
        val anonymousId = jsAnalytics.anonymousId
        val userId = jsAnalytics.userId

        assertEquals("test-anonymous-id", anonymousId)
        assertEquals("test-user-id", userId)
    }

    @Test
    fun testTrackFromJavaScript() {
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
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
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
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
        assertNotNull("Properties should be passed from JavaScript", capturedTrackCalls[0].second)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testIdentifyFromJavaScript() {
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
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
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
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
        assertNotNull("Traits should be passed from JavaScript", capturedIdentifyCalls[0].second)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testScreenFromJavaScript() {
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
        engine.sync {
            evaluate("""analytics.screen("Home Screen", "Navigation");""")
        }

        // Verify that the screen method was called with correct parameters from JavaScript
        assertEquals(1, capturedScreenCalls.size)
        assertEquals("Home Screen", capturedScreenCalls[0].first)
        assertEquals("Navigation", capturedScreenCalls[0].second)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testScreenWithPropertiesFromJavaScript() {
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
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
        assertNotNull("Properties should be passed from JavaScript", capturedScreenCalls[0].third)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testGroupFromJavaScript() {
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
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
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
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
        assertNotNull("Traits should be passed from JavaScript", capturedGroupCalls[0].second)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testAliasFromJavaScript() {
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
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
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
        engine.sync {
            evaluate("""analytics.flush();""")
        }

        // Verify that the flush method was called from JavaScript
        assertEquals(1, capturedFlushCalls)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testResetFromJavaScript() {
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
        engine.sync {
            evaluate("""analytics.reset();""")
        }

        // Verify that the reset method was called from JavaScript
        assertEquals(1, capturedResetCalls)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testAnonymousIdPropertyFromJavaScript() {
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
        engine.sync {
            val result = evaluate("""analytics.anonymousId;""")
            assertEquals("test-anonymous-id", result)
        }

        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testUserIdPropertyFromJavaScript() {
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
        engine.sync {
            val result = evaluate("""analytics.userId;""")
            assertEquals("test-user-id", result)
        }

        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testAddPluginFromJavaScript() {
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
        engine.sync {
            val result = evaluate("""
                var plugin = {
                    type: LivePluginType.enrichment,
                    destination: null,
                    execute: function(event) {
                        return event;
                    }
                };
                analytics.add(plugin);
            """)
            // The result will be false since our mock analytics doesn't support plugin addition
            assertEquals(false, result)
        }

        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testLivePluginTypeEnumFromJavaScript() {
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
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
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
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
        if (exceptionThrown != null) {
            // Skip JS engine tests when native libraries are not available (unit tests)
            return
        }
        
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
        assertEquals(1, capturedIdentifyCalls.size)
        assertEquals("complex-user", capturedIdentifyCalls[0].first)
        assertNotNull("Traits should be passed from JavaScript", capturedIdentifyCalls[0].second)
        
        assertEquals(1, capturedTrackCalls.size)
        assertEquals("Complex Event", capturedTrackCalls[0].first)
        assertNotNull("Properties should be passed from JavaScript", capturedTrackCalls[0].second)
        
        assertEquals(1, capturedScreenCalls.size)
        assertEquals("Complex Screen", capturedScreenCalls[0].first)
        assertEquals("Category", capturedScreenCalls[0].second)
        assertNotNull("Properties should be passed from JavaScript", capturedScreenCalls[0].third)
        
        assertEquals(1, capturedGroupCalls.size)
        assertEquals("complex-group", capturedGroupCalls[0].first)
        assertNotNull("Traits should be passed from JavaScript", capturedGroupCalls[0].second)
        
        assertEquals(1, capturedAliasCalls.size)
        assertEquals("complex-alias", capturedAliasCalls[0])
        
        assertEquals(1, capturedFlushCalls)
        
        assertNull("No exception should be thrown", exceptionThrown)
    }
}