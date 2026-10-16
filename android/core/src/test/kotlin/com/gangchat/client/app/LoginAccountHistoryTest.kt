package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginAccountHistoryTest {
    @Test
    fun `rememberLoginAccount records recent login without password by default`() {
        val records =
            rememberLoginAccount(
                records = emptyList(),
                login = "  Kai@example.com  ",
                password = "secret",
                rememberPassword = false,
                now = DateTime.utc(2026, 1, 2),
            )

        assertEquals(1, records.size)
        assertEquals("Kai@example.com", records.single().login)
        assertNull(records.single().password)
        assertFalse(records.single().remembersPassword)
        assertEquals(1, records.single().useCount)
    }

    @Test
    fun `rememberLoginAccount stores password only when enabled`() {
        val records =
            rememberLoginAccount(
                records = emptyList(),
                login = "kai",
                password = "secret",
                rememberPassword = true,
                avatarUrl = "/avatars/kai.png",
                defaultAvatarKey = "green-2",
                now = DateTime.utc(2026, 1, 2),
            )

        assertEquals("secret", records.single().password)
        assertTrue(records.single().remembersPassword)
        assertEquals("/avatars/kai.png", records.single().avatarUrl)
        assertEquals("green-2", records.single().defaultAvatarKey)
        assertEquals(1, records.single().useCount)
    }

    @Test
    fun `rememberLoginAccount moves existing account to top and clears password`() {
        val records =
            rememberLoginAccount(
                records =
                    listOf(
                        LoginAccountRecord(
                            login = "kai",
                            password = "old-secret",
                            useCount = 2,
                            updatedAt = DateTime.utc(2026, 1, 1),
                        ),
                        LoginAccountRecord(
                            login = "morgan",
                            updatedAt = DateTime.utc(2026, 1, 1),
                        ),
                    ),
                login = "KAI",
                password = "new-secret",
                rememberPassword = false,
                now = DateTime.utc(2026, 1, 3),
            )

        assertEquals(listOf("KAI", "morgan"), records.map { record -> record.login })
        assertNull(records.first().password)
        assertEquals(3, records.first().useCount)
    }

    @Test
    fun `normalizeLoginAccountHistory sorts by use count then latest login`() {
        val records =
            normalizeLoginAccountHistory(
                listOf(
                    LoginAccountRecord(
                        login = "kai",
                        useCount = 2,
                        updatedAt = DateTime.utc(2026, 1, 1),
                    ),
                    LoginAccountRecord(
                        login = "morgan",
                        useCount = 2,
                        updatedAt = DateTime.utc(2026, 1, 3),
                    ),
                    LoginAccountRecord(
                        login = "riley",
                        useCount = 3,
                        updatedAt = DateTime.utc(2026, 1, 2),
                    ),
                ),
            )

        assertEquals(listOf("riley", "morgan", "kai"), records.map { record -> record.login })
    }

    @Test
    fun `lastLoginAccountRecord returns the most recent login`() {
        val latest =
            lastLoginAccountRecord(
                listOf(
                    LoginAccountRecord(
                        login = "kai",
                        useCount = 5,
                        updatedAt = DateTime.utc(2026, 1, 1),
                    ),
                    LoginAccountRecord(
                        login = "morgan",
                        useCount = 1,
                        updatedAt = DateTime.utc(2026, 1, 4),
                    ),
                ),
            )

        assertEquals("morgan", latest?.login)
    }

    @Test
    fun `avatar metadata refreshes matching username and email aliases`() {
        val updatedAt = DateTime.utc(2026, 1, 4)
        val records =
            updateLoginAccountAvatarMetadata(
                records =
                    listOf(
                        LoginAccountRecord(
                            login = "Kai@example.com",
                            password = "secret",
                            useCount = 3,
                            updatedAt = updatedAt,
                        ),
                        LoginAccountRecord(
                            login = "morgan",
                            defaultAvatarKey = "green-2",
                            updatedAt = DateTime.utc(2026, 1, 3),
                        ),
                    ),
                accountAliases = listOf("kai", "kai@example.com"),
                avatarUrl = "/assets/avatar-kai/custom.png",
                defaultAvatarKey = "purple-2",
            )

        val kai = findLoginAccountRecord(records, "kai@example.com")!!
        assertEquals("/assets/avatar-kai/custom.png", kai.avatarUrl)
        assertEquals("purple-2", kai.defaultAvatarKey)
        assertEquals("secret", kai.password)
        assertEquals(3, kai.useCount)
        assertEquals(updatedAt, kai.updatedAt)
        assertEquals(
            "green-2",
            findLoginAccountRecord(records, "morgan")!!.defaultAvatarKey,
        )
    }

    @Test
    fun `avatar metadata refresh clears a removed custom avatar`() {
        val records =
            updateLoginAccountAvatarMetadata(
                records =
                    listOf(
                        LoginAccountRecord(
                            login = "kai",
                            avatarUrl = "/assets/avatar-kai/old.png",
                            defaultAvatarKey = "green-2",
                        ),
                    ),
                accountAliases = listOf("KAI"),
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
            )

        assertNull(records.single().avatarUrl)
        assertEquals("blue-3", records.single().defaultAvatarKey)
    }

    @Test
    fun `deleteLoginAccountRecord removes records case-insensitively`() {
        val records =
            deleteLoginAccountRecord(
                records =
                    listOf(
                        LoginAccountRecord(login = "kai"),
                        LoginAccountRecord(login = "morgan"),
                    ),
                login = "KAI",
            )

        assertEquals(listOf("morgan"), records.map { record -> record.login })
    }
}
