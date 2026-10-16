package com.gangchat.client.app

import com.gangchat.client.plugins.SharedPreferences
import com.gangchat.client.shell.LocalCloseBehaviorStore
import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CloseBehaviorTest {
    @get:Rule
    val gang = GangTestRule()

    @Before
    fun setUp() {
        SharedPreferences.setMockInitialValues(emptyMap())
    }

    @Test
    fun `close behavior defaults to asking every time`() = gang.runTest {
        val store = LocalCloseBehaviorStore()

        assertEquals(CloseBehavior.askEveryTime, store.read())
    }

    @Test
    fun `close behavior persists as a local app-wide preference`() = gang.runTest {
        val store = LocalCloseBehaviorStore()

        store.write(CloseBehavior.minimizeToTray)
        assertEquals(CloseBehavior.minimizeToTray, store.read())

        store.write(CloseBehavior.exitProgram)
        assertEquals(CloseBehavior.exitProgram, store.read())
    }

    @Test
    fun `unknown stored close behavior falls back to asking every time`() {
        assertEquals(
            defaultCloseBehavior,
            closeBehaviorFromStorageValue("older-client-value"),
        )
    }
}
