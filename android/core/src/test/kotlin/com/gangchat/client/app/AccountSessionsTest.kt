package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSessionsTest {
    @Test
    fun `accountSessionsLoadStarted preserves sessions and clears error`() {
        val existing = listOf(_session(id = "session_1"))
        val patch = accountSessionsLoadStarted(sessions = existing)

        assertEquals(existing, patch.sessions)
        assertTrue(patch.loading)
        assertNull(patch.securityError)
    }

    @Test
    fun `accountSessionsLoadSucceeded stores sessions and clears busy state`() {
        val sessions = listOf(_session(id = "session_2"))
        val patch = accountSessionsLoadSucceeded(sessions = sessions)

        assertEquals(sessions, patch.sessions)
        assertFalse(patch.loading)
        assertNull(patch.securityError)
    }

    @Test
    fun `accountSessionsLoadCancelled preserves existing feedback`() {
        val existing = listOf(_session(id = "session_1"))
        val patch =
            accountSessionsLoadCancelled(
                sessions = existing,
                securityError = "previous error",
            )

        assertEquals(existing, patch.sessions)
        assertFalse(patch.loading)
        assertEquals("previous error", patch.securityError)
    }

    @Test
    fun `accountSessionsLoadFailed preserves sessions and reports error`() {
        val existing = listOf(_session(id = "session_1"))
        val patch =
            accountSessionsLoadFailed(
                sessions = existing,
                failure = "load failed",
            )

        assertEquals(existing, patch.sessions)
        assertFalse(patch.loading)
        assertEquals("操作失败，请稍后重试", patch.securityError)
    }
}

private fun _session(id: String): UserSession {
    return UserSession(
        id = id,
        userAgent = "Test Browser",
        ipAddress = "127.0.0.1",
        location = "Local",
        createdAt = DateTime(2026, 6, 1),
        lastUsedAt = DateTime(2026, 6, 4),
        expiresAt = DateTime(2026, 6, 5),
        revokedAt = null,
        isCurrent = false,
    )
}
