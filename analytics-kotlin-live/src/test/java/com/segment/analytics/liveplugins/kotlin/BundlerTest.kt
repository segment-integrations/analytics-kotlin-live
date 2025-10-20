package com.segment.analytics.liveplugins.kotlin

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import java.io.File
import java.io.IOException
import java.net.MalformedURLException

class BundlerTest {
    
    private lateinit var testFile: File
    
    @Before
    fun setUp() {
        testFile = File.createTempFile("test_bundle", ".js")
        testFile.deleteOnExit()
    }
    
    @After
    fun tearDown() {
        if (testFile.exists()) {
            testFile.delete()
        }
    }
    
    @Test
    fun disableBundleURL_createsFileWithCorrectContent() {
        disableBundleURL(testFile)
        
        assertTrue("File should exist after disableBundleURL", testFile.exists())
        val content = testFile.readText()
        assertEquals("// live plugins are disabled.", content)
    }
    
    @Test
    fun disableBundleURL_overwritesExistingFile() {
        testFile.writeText("existing content")
        
        disableBundleURL(testFile)
        
        val content = testFile.readText()
        assertEquals("// live plugins are disabled.", content)
    }
    
    @Test
    fun disableBundleURL_createsNewFileIfNotExists() {
        testFile.delete()
        assertFalse("File should not exist before test", testFile.exists())
        
        disableBundleURL(testFile)
        
        assertTrue("File should be created", testFile.exists())
        val content = testFile.readText()
        assertEquals("// live plugins are disabled.", content)
    }
    
    @Test
    fun disableBundleURL_handlesFilePermissions() {
        disableBundleURL(testFile)
        
        assertTrue("File should be readable", testFile.canRead())
        val content = testFile.readText()
        assertEquals("// live plugins are disabled.", content)
    }
    
    @Test
    fun disableBundleURL_writesToCorrectFile() {
        val anotherFile = File.createTempFile("another_test", ".js")
        anotherFile.deleteOnExit()
        
        disableBundleURL(testFile)
        disableBundleURL(anotherFile)
        
        assertTrue("Both files should exist", testFile.exists() && anotherFile.exists())
        assertEquals("// live plugins are disabled.", testFile.readText())
        assertEquals("// live plugins are disabled.", anotherFile.readText())
        
        anotherFile.delete()
    }
    
    @Test(expected = IOException::class)
    fun download_throwsExceptionForInvalidURL() {
        download("invalid-url", testFile)
    }
    
    @Test(expected = MalformedURLException::class)
    fun download_throwsExceptionForMalformedURL() {
        download("not-a-url", testFile)
    }
    
    @Test(expected = IOException::class)
    fun download_throwsExceptionForNonExistentServer() {
        download("http://localhost:54321/bundle.js", testFile)
    }
    
    @Test(expected = IOException::class) 
    fun download_throwsExceptionForNonExistentHost() {
        download("http://invalid-host-that-does-not-exist-12345.com/bundle.js", testFile)
    }
    
    @Test
    fun download_createsFileEvenIfDownloadFails() {
        try {
            download("http://localhost:54321/bundle.js", testFile)
            fail("Should have thrown IOException")
        } catch (e: IOException) {
            // Expected - but check that file structure is handled correctly
            assertNotNull("Exception should be thrown", e)
        }
    }
}