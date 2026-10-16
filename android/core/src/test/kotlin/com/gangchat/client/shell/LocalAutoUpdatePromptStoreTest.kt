package com.gangchat.client.shell

import com.gangchat.client.plugins.SharedPreferences
import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LocalAutoUpdatePromptStoreTest {
    @get:Rule
    val gang = GangTestRule()

    @Before
    fun setUp() {
        SharedPreferences.setMockInitialValues(emptyMap())
    }

    @Test
    fun `auto update prompt defaults to enabled`() = gang.runTest {
        val store = LocalAutoUpdatePromptStore()

        assertTrue(store.read())
    }

    @Test
    fun `auto update prompt persists as a local app-wide preference`() = gang.runTest {
        val store = LocalAutoUpdatePromptStore()

        store.write(true)
        assertTrue(store.read())

        store.write(false)
        assertFalse(store.read())
    }

    @Test
    fun `ignored update version persists and can be cleared`() = gang.runTest {
        val store = LocalAutoUpdatePromptStore()

        assertNull(store.readIgnoredVersion())

        store.writeIgnoredVersion(" 0.5.1 ")
        assertEquals("0.5.1", store.readIgnoredVersion())

        store.writeIgnoredVersion(null)
        assertNull(store.readIgnoredVersion())
    }
}
