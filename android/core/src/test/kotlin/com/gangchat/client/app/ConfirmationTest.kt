package com.gangchat.client.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationTest {
    @Test
    fun `matchesConfirmationText trims input but keeps exact target matching`() {
        assertTrue(matchesConfirmationText(" room-name ", "room-name"))
        assertFalse(matchesConfirmationText("Room-Name", "room-name"))
        assertFalse(matchesConfirmationText("room-name-extra", "room-name"))
    }
}
