package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Dart `group` names prefix each case.
class MessageMentionsTest {
    // group('activeMessageMentionQuery')

    @Test
    fun `activeMessageMentionQuery finds the query before the cursor`() {
        val query =
            activeMessageMentionQuery(
                text = "hello @pan",
                cursorOffset = "hello @pan".length,
            )

        assertNotNull(query)
        assertEquals(6, query!!.start)
        assertEquals(10, query.end)
        assertEquals("pan", query.query)
    }

    @Test
    fun `activeMessageMentionQuery ignores email-like text`() {
        val query =
            activeMessageMentionQuery(
                text = "mail a@b.com",
                cursorOffset = "mail a@b.com".length,
            )

        assertNull(query)
    }

    @Test
    fun `activeMessageMentionQuery finds mention directly after existing text`() {
        val query =
            activeMessageMentionQuery(
                text = "hello@pan",
                cursorOffset = "hello@pan".length,
            )

        assertNotNull(query)
        assertEquals(5, query!!.start)
        assertEquals(9, query.end)
        assertEquals("pan", query.query)
    }

    @Test
    fun `activeMessageMentionQuery finds mention directly after chinese text`() {
        val query =
            activeMessageMentionQuery(
                text = "你好@潘",
                cursorOffset = "你好@潘".length,
            )

        assertNotNull(query)
        assertEquals(2, query!!.start)
        assertEquals("潘", query.query)
    }

    @Test
    fun `messageMentionRanges highlights mention tokens`() {
        val ranges = messageMentionRanges("hi @alpha and @beta plus text@gamma")

        assertEquals(
            listOf(listOf(3, 9), listOf(14, 19), listOf(29, 35)),
            ranges.map { range -> listOf(range.start, range.end) },
        )
    }

    @Test
    fun `messageMentionRanges ignores email addresses`() {
        val ranges = messageMentionRanges("mail a@b.com and hi@alpha")

        assertEquals(
            listOf(listOf(19, 25)),
            ranges.map { range -> listOf(range.start, range.end) },
        )
    }

    @Test
    fun `messageMentionRanges can use known labels containing spaces`() {
        val ranges =
            messageMentionRanges(
                "hi @Panel Member and @other",
                labels = listOf("Panel Member"),
            )

        assertEquals(
            listOf(listOf(3, 16), listOf(21, 27)),
            ranges.map { range -> listOf(range.start, range.end) },
        )
    }

    @Test
    fun `messageMentionOptions filters by room display name and role`() {
        val owner = _member("1", "owner", "Panel Owner", role = "owner")
        val admin = _member("2", "admin", "Panel Admin", role = "admin")
        val member = _member("3", "member", "Panel Member")

        val options =
            messageMentionOptions(
                members = listOf(member, admin, owner),
                query = "pan",
                ownerUserId = owner.user.id,
            )

        assertEquals(
            listOf("Panel Owner", "Panel Admin", "Panel Member"),
            options.map { option -> option.label },
        )
    }

    @Test
    fun `messageMentionOptions excludes the current user`() {
        val currentUser = _member("1", "me", "Panel Me")
        val otherUser = _member("2", "other", "Panel Other")

        val options =
            messageMentionOptions(
                members = listOf(currentUser, otherUser),
                query = "pan",
                ownerUserId = null,
                excludedUserId = currentUser.user.id,
            )

        assertEquals(listOf("Panel Other"), options.map { option -> option.label })
    }

    @Test
    fun `messageMentionOptions filters by UID`() {
        val member = _member("1", "member", "Panel Member", uid = "10000088")

        val options =
            messageMentionOptions(
                members = listOf(member),
                query = "0088",
                ownerUserId = null,
            )

        assertEquals(listOf("Panel Member"), options.map { option -> option.label })
    }

    @Test
    fun `messageMentionOptions places special mentions first`() {
        val member = _member("1", "member", "Panel Member")

        val options =
            messageMentionOptions(
                members = listOf(member),
                query = "",
                ownerUserId = null,
            )

        assertEquals(
            listOf("所有人", "管理员", "Panel Member"),
            options.map { option -> option.label }.take(3),
        )
        assertEquals(MessageMentionKind.everyone, options[0].kind)
        assertEquals(MessageMentionKind.admins, options[1].kind)
    }

    @Test
    fun `messageMentionDescriptors includes special and user mentions`() {
        val member = _member("1", "member", "Panel Member")

        val mentions =
            messageMentionDescriptors(
                text = "@所有人 @管理员 @Panel Member @Panel Member",
                members = listOf(member),
                confirmedLabels = listOf("所有人", "管理员", "Panel Member", "Panel Member"),
            )

        assertEquals(
            listOf(
                mapOf("type" to "all", "label" to "所有人"),
                mapOf("type" to "admins", "label" to "管理员"),
                mapOf("type" to "user", "user_id" to "1", "label" to "Panel Member"),
            ),
            mentions,
        )
    }

    @Test
    fun `messageMentionsUser matches all admins and direct users`() {
        val admin = _member("1", "admin", "Panel Admin", role = "admin").user
        val member = _member("2", "member", "Panel Member").user

        assertTrue(
            messageMentionsUser(text = "@管理员", user = admin, ownerUserId = null),
        )
        assertFalse(
            messageMentionsUser(text = "@管理员", user = member, ownerUserId = null),
        )
        assertTrue(
            messageMentionsUser(
                text = "",
                mentions =
                    listOf(
                        mapOf("type" to "user", "user_id" to "2"),
                    ),
                user = member,
                ownerUserId = null,
            ),
        )
        assertTrue(
            messageMentionsUser(text = "@所有人", user = member, ownerUserId = null),
        )
    }

    private fun _member(
        id: String,
        username: String,
        roomDisplayName: String,
        role: String = "member",
        uid: String? = null,
    ): RoomMember {
        val user =
            UserSummary(
                id = id,
                username = username,
                displayName = username,
                avatarUrl = null,
                defaultAvatarKey = "blue-3",
                uid = uid,
                roomDisplayName = roomDisplayName,
                roomRole = role,
            )
        return RoomMember(
            user = user,
            role = role,
            joinedAt = DateTime.fromMillisecondsSinceEpoch(0, isUtc = true),
            roomDisplayName = roomDisplayName,
        )
    }
}
