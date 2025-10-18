package com.segment.analytics.liveplugins.kotlin

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.segment.analytics.kotlin.android.AndroidStorageProvider
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.emptyJsonObject
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.liveplugins.kotlin.utils.TestCoroutineConfiguration
import com.segment.analytics.liveplugins.kotlin.utils.testAnalytics
import com.segment.analytics.substrata.kotlin.JSExceptionHandler
import com.segment.analytics.substrata.kotlin.JSScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LivePluginsTest {

    private lateinit var livePlugins: LivePlugins
    private lateinit var analytics: Analytics
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private var exceptionThrown: Throwable? = null
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val capturedDependents = mutableListOf<LivePluginsDependent>()

    @Before
    fun setUp() {
        exceptionThrown = null
        capturedDependents.clear()
        
        context = InstrumentationRegistry.getInstrumentation().targetContext
        analytics = testAnalytics(
            Configuration(
                writeKey = "test-write-key",
                application = context,
                storageProvider = AndroidStorageProvider
            ),
            testScope, testDispatcher
        )

        livePlugins = LivePlugins(exceptionHandler = { exception ->
            exceptionThrown = exception
        })
        
        // Clear the loaded state for each test
        LivePlugins.loaded = false
    }

    @After
    fun tearDown() {
        if (exceptionThrown == null && ::livePlugins.isInitialized) {
            livePlugins.release()
        }
        LivePlugins.loaded = false
    }

    @Test
    fun testLivePluginsCreation() {
        assertNotNull("LivePlugins should be created", livePlugins)
        assertEquals("Plugin type should be Utility", Plugin.Type.Utility, livePlugins.type)
        assertNotNull("Engine should be initialized", livePlugins.engine)
        assertFalse("Loaded should be false initially", LivePlugins.loaded)
    }

    @Test
    fun testLivePluginsWithFallbackFile() {
        val fallbackContent = "console.log('fallback script');"
        val fallbackStream = ByteArrayInputStream(fallbackContent.toByteArray())
        
        val livePluginsWithFallback = LivePlugins(
            fallbackFile = fallbackStream,
            forceFallbackFile = true
        )
        
        assertNotNull("LivePlugins with fallback should be created", livePluginsWithFallback)
        livePluginsWithFallback.release()
    }

    @Test
    fun testLivePluginsWithLocalJS() {
        val localJSContent = "console.log('local js script');"
        val localJSStream = ByteArrayInputStream(localJSContent.toByteArray())
        
        val livePluginsWithLocalJS = LivePlugins(
            localJS = listOf(localJSStream)
        )
        
        assertNotNull("LivePlugins with local JS should be created", livePluginsWithLocalJS)
        livePluginsWithLocalJS.release()
    }

    @Test
    fun testSetupWithValidAnalytics() {
        livePlugins.setup(analytics)
        
        assertEquals("Analytics should be assigned", analytics, livePlugins.analytics)
        assertNotNull("LivePluginsHolder should contain weak reference", LivePluginsHolder.plugin)
        assertEquals("LivePluginsHolder should reference our instance", livePlugins, LivePluginsHolder.plugin?.get())
        
        assertNull("No exception should be thrown during setup", exceptionThrown)
    }

    @Test
    fun testSetupSkipsWhenExistingLivePluginsFound() {
        val existingLivePlugins = LivePlugins()
        analytics.add(existingLivePlugins)
        
        livePlugins.setup(analytics)
        
        // Should not crash and should skip setup
        assertNull("No exception should be thrown", exceptionThrown)
        
        existingLivePlugins.release()
    }

    @Test
    fun testEngineConfiguration() {
        livePlugins.setup(analytics)
        
        // Verify that the engine is configured with JSAnalytics
        livePlugins.engine.sync {
            val result = evaluate("typeof analytics")
            assertEquals("analytics should be available in JS", "object", result)
        }
        
        // Verify LivePluginType enum is available
        livePlugins.engine.sync {
            val beforeType = evaluate("LivePluginType.before")
            val enrichmentType = evaluate("LivePluginType.enrichment")
            val afterType = evaluate("LivePluginType.after")
            
            assertEquals("Before type should be 0", 0, beforeType)
            assertEquals("Enrichment type should be 1", 1, enrichmentType)
            assertEquals("After type should be 2", 2, afterType)
        }
        
        // Verify LivePlugin class is available
        livePlugins.engine.sync {
            val result = evaluate("typeof LivePlugin")
            assertEquals("LivePlugin class should be available", "function", result)
        }
        
        assertNull("No exception should be thrown during engine configuration", exceptionThrown)
    }

    @Test
    fun testUpdateWithoutSettings() {
        livePlugins.setup(analytics)
        
        val emptySettings = Settings(emptyJsonObject)
        livePlugins.update(emptySettings, Plugin.UpdateType.Initial)
        
        assertTrue("Loaded should be true after update", LivePlugins.loaded)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testUpdateWithEdgeFunctionSettings() {
        livePlugins.setup(analytics)
        
        val edgeFunctionSettings = buildJsonObject {
            put("version", 2)
            put("downloadURL", "https://example.com/plugins.js")
        }
        
        val settings = Settings(integrations = emptyJsonObject, edgeFunction = edgeFunctionSettings)
        livePlugins.update(settings, Plugin.UpdateType.Initial)
        
        assertTrue("Loaded should be true after update", LivePlugins.loaded)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testUpdateRemovesDuplicateInstance() {
        val existingLivePlugins = LivePlugins()
        analytics.add(existingLivePlugins)
        
        livePlugins.setup(analytics)
        livePlugins.update(Settings(emptyJsonObject), Plugin.UpdateType.Initial)
        
        // The duplicate instance should be removed
        assertFalse("Analytics should not contain our instance", analytics.findAll(LivePlugins::class).contains(livePlugins))
        
        existingLivePlugins.release()
    }

    @Test
    fun testUpdateSkipsWhenAlreadyLoaded() {
        livePlugins.setup(analytics)
        
        // First update
        livePlugins.update(Settings(emptyJsonObject), Plugin.UpdateType.Initial)
        assertTrue("Should be loaded after first update", LivePlugins.loaded)
        
        // Second update should be skipped
        val settingsWithEdgeFunction = Settings(
            integrations = emptyJsonObject,
            edgeFunction = buildJsonObject {
                put("version", 5)
                put("downloadURL", "https://example.com/new-plugins.js")
            }
        )
        
        livePlugins.update(settingsWithEdgeFunction, Plugin.UpdateType.Refresh)
        
        // Should still be loaded but not process the update again
        assertTrue("Should still be loaded", LivePlugins.loaded)
        assertNull("No exception should be thrown", exceptionThrown)
    }

    @Test
    fun testAddDependentWhenNotLoaded() {
        livePlugins.setup(analytics)
        
        val mockDependent = object : LivePluginsDependent {
            override fun prepare(engine: JSScope) {
                capturedDependents.add(this)
            }
            
            override fun readyToStart() {
                // Not called when not loaded
            }
        }
        
        livePlugins.addDependent(mockDependent)
        
        // Dependent should be added but not notified yet
        assertEquals("Should have 1 dependent", 1, livePlugins.dependents.size)
        assertEquals("Should not call prepare yet", 0, capturedDependents.size)
    }

    @Test
    fun testAddDependentWhenLoaded() {
        livePlugins.setup(analytics)
        livePlugins.update(Settings(emptyJsonObject), Plugin.UpdateType.Initial)
        
        var prepareCallCount = 0
        var readyToStartCallCount = 0
        
        val mockDependent = object : LivePluginsDependent {
            override fun prepare(engine: JSScope) {
                prepareCallCount++
            }
            
            override fun readyToStart() {
                readyToStartCallCount++
            }
        }
        
        livePlugins.addDependent(mockDependent)
        
        // Dependent should be added and notified immediately
        assertEquals("Should have 1 dependent", 1, livePlugins.dependents.size)
        assertEquals("Should call prepare once", 1, prepareCallCount)
        assertEquals("Should call readyToStart once", 1, readyToStartCallCount)
    }

    @Test
    fun testShouldUpdateLivePluginWithNoCache() {
        livePlugins.setup(analytics)
        
        val newSettings = LivePluginsSettings(version = 1, downloadURL = "https://example.com/plugins.js")
        
        // Use reflection to test private method
        val method = LivePlugins::class.java.getDeclaredMethod("shouldUpdateLivePlugin", LivePluginsSettings::class.java)
        method.isAccessible = true
        val result = method.invoke(livePlugins, newSettings) as Boolean
        
        assertTrue("Should update when no cache exists", result)
    }

    @Test
    fun testShouldUpdateLivePluginWithOlderCache() {
        livePlugins.setup(analytics)
        
        // Simulate cached settings with older version
        val context = analytics.configuration.application as Context
        val sharedPrefs = context.getSharedPreferences(
            "analytics-liveplugins-test-write-key",
            Context.MODE_PRIVATE
        )
        sharedPrefs.edit().putString(
            LivePlugins.SHARED_PREFS_KEY,
            """{"version":1,"downloadURL":"https://old.com/plugins.js"}"""
        ).apply()
        
        val newSettings = LivePluginsSettings(version = 2, downloadURL = "https://new.com/plugins.js")
        
        // Use reflection to test private method
        val method = LivePlugins::class.java.getDeclaredMethod("shouldUpdateLivePlugin", LivePluginsSettings::class.java)
        method.isAccessible = true
        val result = method.invoke(livePlugins, newSettings) as Boolean
        
        assertTrue("Should update when new version is higher", result)
        
        // Clean up
        sharedPrefs.edit().clear().apply()
    }

    @Test
    fun testShouldNotUpdateLivePluginWithSameOrNewerCache() {
        livePlugins.setup(analytics)
        
        // Simulate cached settings with same version
        val context = analytics.configuration.application as Context
        val sharedPrefs = context.getSharedPreferences(
            "analytics-liveplugins-test-write-key",
            Context.MODE_PRIVATE
        )
        sharedPrefs.edit().putString(
            LivePlugins.SHARED_PREFS_KEY,
            """{"version":2,"downloadURL":"https://current.com/plugins.js"}"""
        ).apply()
        
        val newSettings = LivePluginsSettings(version = 2, downloadURL = "https://same.com/plugins.js")
        
        // Use reflection to test private method
        val method = LivePlugins::class.java.getDeclaredMethod("shouldUpdateLivePlugin", LivePluginsSettings::class.java)
        method.isAccessible = true
        val result = method.invoke(livePlugins, newSettings) as Boolean
        
        assertFalse("Should not update when version is same or older", result)
        
        // Clean up
        sharedPrefs.edit().clear().apply()
    }

    @Test
    fun testLivePluginsSettingsDataClass() {
        val defaultSettings = LivePluginsSettings()
        assertEquals("Default version should be -1", -1, defaultSettings.version)
        assertEquals("Default download URL should be empty", "", defaultSettings.downloadURL)
        
        val customSettings = LivePluginsSettings(version = 5, downloadURL = "https://example.com/plugins.js")
        assertEquals("Custom version should be 5", 5, customSettings.version)
        assertEquals("Custom download URL should match", "https://example.com/plugins.js", customSettings.downloadURL)
    }

    @Test
    fun testCompanionObjectConstants() {
        assertEquals("File name constant should match", "livePlugins.js", LivePlugins.LIVE_PLUGINS_FILE_NAME)
        assertEquals("Shared prefs key should match", "LivePlugins", LivePlugins.SHARED_PREFS_KEY)
    }

    @Test
    fun testRelease() {
        livePlugins.setup(analytics)
        
        // Engine should be functional before release
        livePlugins.engine.sync {
            val result = evaluate("1 + 1")
            assertEquals("Engine should work before release", 2, result)
        }
        
        livePlugins.release()
        
        // After release, engine operations should not work (or should be handled gracefully)
        assertNull("No exception should be thrown during release", exceptionThrown)
    }

    @Test
    fun testFallbackFileUsage() {
        val fallbackContent = "console.log('fallback executed');"
        val fallbackStream = ByteArrayInputStream(fallbackContent.toByteArray())
        
        val livePluginsWithFallback = LivePlugins(
            fallbackFile = fallbackStream,
            forceFallbackFile = true
        )
        
        livePluginsWithFallback.setup(analytics)
        livePluginsWithFallback.update(Settings(emptyJsonObject), Plugin.UpdateType.Initial)
        
        // Verify fallback file was used by checking if file exists
        val context = analytics.configuration.application as Context
        val storageDirectory = context.getDir("segment-data", Context.MODE_PRIVATE)
        val livePluginFile = File(storageDirectory, LivePlugins.LIVE_PLUGINS_FILE_NAME)
        
        assertTrue("LivePlugin file should exist after fallback", livePluginFile.exists())
        
        livePluginsWithFallback.release()
        
        // Clean up
        livePluginFile.delete()
    }

    @Test
    fun testLocalJSExecution() {
        val localJSContent = "console.log('local JS executed');"
        val localJSStream = ByteArrayInputStream(localJSContent.toByteArray())
        
        val livePluginsWithLocalJS = LivePlugins(
            localJS = listOf(localJSStream)
        )
        
        livePluginsWithLocalJS.setup(analytics)
        livePluginsWithLocalJS.update(Settings(emptyJsonObject), Plugin.UpdateType.Initial)
        
        assertTrue("Should be loaded successfully", LivePlugins.loaded)
        
        livePluginsWithLocalJS.release()
    }

    @Test
    fun testMultipleDependents() {
        livePlugins.setup(analytics)
        
        var dependent1PrepareCount = 0
        var dependent1ReadyCount = 0
        var dependent2PrepareCount = 0
        var dependent2ReadyCount = 0
        
        val dependent1 = object : LivePluginsDependent {
            override fun prepare(engine: JSScope) { dependent1PrepareCount++ }
            override fun readyToStart() { dependent1ReadyCount++ }
        }
        
        val dependent2 = object : LivePluginsDependent {
            override fun prepare(engine: JSScope) { dependent2PrepareCount++ }
            override fun readyToStart() { dependent2ReadyCount++ }
        }
        
        livePlugins.addDependent(dependent1)
        livePlugins.addDependent(dependent2)
        
        assertEquals("Should have 2 dependents", 2, livePlugins.dependents.size)
        
        // Update to trigger loading
        livePlugins.update(Settings(emptyJsonObject), Plugin.UpdateType.Initial)
        
        assertEquals("Dependent 1 prepare should be called once", 1, dependent1PrepareCount)
        assertEquals("Dependent 1 ready should be called once", 1, dependent1ReadyCount)
        assertEquals("Dependent 2 prepare should be called once", 1, dependent2PrepareCount)
        assertEquals("Dependent 2 ready should be called once", 1, dependent2ReadyCount)
    }
}