package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountStateTest {
    @Test
    fun `accountLoadStarted preserves user and clears error`() {
        val user = _user(id = "user_1")
        val patch = accountLoadStarted(user = user)

        assertSame(user, patch.user)
        assertTrue(patch.loading)
        assertNull(patch.accountError)
    }

    @Test
    fun `accountLoadSucceeded stores user and clears busy state`() {
        val user = _user(id = "user_2")
        val patch = accountLoadSucceeded(user = user)

        assertSame(user, patch.user)
        assertFalse(patch.loading)
        assertNull(patch.accountError)
    }

    @Test
    fun `accountLoadCancelled preserves existing feedback`() {
        val user = _user(id = "user_1")
        val patch =
            accountLoadCancelled(
                user = user,
                accountError = "previous error",
            )

        assertSame(user, patch.user)
        assertFalse(patch.loading)
        assertEquals("previous error", patch.accountError)
    }

    @Test
    fun `accountLoadFailed preserves user and reports error`() {
        val user = _user(id = "user_1")
        val patch = accountLoadFailed(user = user, failure = "load failed")

        assertSame(user, patch.user)
        assertFalse(patch.loading)
        assertEquals("操作失败，请稍后重试", patch.accountError)
    }
}

private fun _user(id: String): CurrentUser {
    return CurrentUser(
        id = id,
        uid = "1001",
        username = "logan",
        displayName = "Logan",
        bio = "",
        gender = "secret",
        email = "logan@example.test",
        emailPublic = false,
        phoneNumber = null,
        phoneNumberPublic = false,
        avatarUrl = null,
        defaultAvatarKey = "blue-3",
        isSuperuser = false,
        createdAt = DateTime(2026, 6, 4),
    )
}
