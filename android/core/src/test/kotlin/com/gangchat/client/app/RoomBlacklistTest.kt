package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.RoomBlacklistEntry
import com.gangchat.client.protocol.RoomMember
import com.gangchat.client.protocol.UserCommonRoom
import com.gangchat.client.protocol.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomBlacklistTest {
    @Test
    fun `roomBlacklistCandidates combines search results and blocked users`() {
        val blocked =
            _blacklistEntry(
                _user("blocked", displayName = "Alpha Gone"),
            )
        val candidates =
            roomBlacklistCandidates(
                searchResults =
                    listOf(
                        _user("external", displayName = "Alpha External"),
                        _user("member", displayName = "Alpha Member"),
                    ),
                blacklist = listOf(blocked),
                members = listOf(_member("member")),
                query = "alpha",
            )

        assertEquals(
            listOf("external", "member", "blocked"),
            candidates.map { item -> item.user.id },
        )
        assertFalse(candidates[0].member)
        assertFalse(candidates[0].blocked)
        assertTrue(candidates[1].member)
        assertFalse(candidates[1].blockActionEnabled)
        assertTrue(candidates[2].blocked)
        assertTrue(candidates[2].canUnblock)
    }

    @Test
    fun `roomBlacklistCandidates ignores room names in user search context`() {
        val candidates =
            roomBlacklistCandidates(
                searchResults =
                    listOf(
                        _user(
                            "room_context",
                            roomDisplayName = "Alpha Room Alias",
                            commonRooms =
                                listOf(
                                    UserCommonRoom(
                                        id = "room_1",
                                        rid = "R10001",
                                        name = "Alpha Common Room",
                                        roomDisplayName = "Alpha Shared Name",
                                    ),
                                ),
                        ),
                        _user("display_match", displayName = "Alpha Person"),
                    ),
                blacklist = emptyList(),
                members = listOf(_member("room_member", roomDisplayName = "Alpha Current Room")),
                query = "alpha",
            )

        assertEquals(listOf("display_match"), candidates.map { item -> item.user.id })
    }

    @Test
    fun `roomBlacklistCandidates marks protected and busy states`() {
        val candidates =
            roomBlacklistCandidates(
                searchResults = listOf(_user("super", isSuperuser = true), _user("busy")),
                blacklist = emptyList(),
                members = emptyList(),
                query = "user",
                busyUserIds = listOf("busy"),
            )

        assertTrue(candidates[0].superuser)
        assertFalse(candidates[0].blockActionEnabled)
        assertTrue(candidates[1].busy)
        assertFalse(candidates[1].canBlock)
    }

    @Test
    fun `roomBlacklistSearchBodyState describes loading prompt empty and results`() {
        val candidate =
            RoomBlacklistCandidate(
                user = _user("user_1"),
                member = false,
                blocked = false,
                superuser = false,
                busy = false,
            )

        assertEquals(
            RoomBlacklistSearchBodyState.loading,
            roomBlacklistSearchBodyState(
                searching = true,
                query = "",
                candidates = emptyList(),
            ),
        )
        assertEquals(
            RoomBlacklistSearchBodyState.prompt,
            roomBlacklistSearchBodyState(
                searching = false,
                query = "   ",
                candidates = listOf(candidate),
            ),
        )
        assertEquals(
            RoomBlacklistSearchBodyState.empty,
            roomBlacklistSearchBodyState(
                searching = false,
                query = "logan",
                candidates = emptyList(),
            ),
        )
        assertEquals(
            RoomBlacklistSearchBodyState.results,
            roomBlacklistSearchBodyState(
                searching = false,
                query = "logan",
                candidates = listOf(candidate),
            ),
        )
    }

    @Test
    fun `roomBlacklistActionLabel describes candidate actions`() {
        assertEquals(
            "在房间内",
            roomBlacklistActionLabel(member = true, superuser = false, blocked = false),
        )
        assertEquals(
            "超级用户",
            roomBlacklistActionLabel(member = false, superuser = true, blocked = false),
        )
        assertEquals(
            "取消拉黑",
            roomBlacklistActionLabel(member = false, superuser = false, blocked = true),
        )
        assertEquals(
            "拉黑",
            roomBlacklistActionLabel(member = false, superuser = false, blocked = false),
        )
    }

    @Test
    fun `upsert and remove room blacklist entries keep one row per user`() {
        val oldEntry = _blacklistEntry(_user("blocked", displayName = "Old"))
        val newEntry = _blacklistEntry(_user("blocked", displayName = "New"))
        val other = _blacklistEntry(_user("other"))

        val updated = upsertRoomBlacklistEntry(listOf(oldEntry, other), newEntry)

        assertEquals(listOf("New", "User other"), updated.map { item -> item.user.displayName })
        assertEquals(listOf(other), removeRoomBlacklistEntry(updated, "blocked"))
    }

    private fun _blacklistEntry(user: UserSummary): RoomBlacklistEntry {
        return RoomBlacklistEntry(user = user, createdAt = DateTime.utc(2026, 6, 23))
    }

    private fun _member(id: String, roomDisplayName: String? = null): RoomMember {
        return RoomMember(
            user = _user(id),
            role = "member",
            joinedAt = DateTime.utc(2026, 6, 5),
            roomDisplayName = roomDisplayName,
        )
    }

    private fun _user(
        id: String,
        displayName: String? = null,
        roomDisplayName: String? = null,
        commonRooms: List<UserCommonRoom> = emptyList(),
        isSuperuser: Boolean = false,
    ): UserSummary {
        return UserSummary(
            id = id,
            username = "user_$id",
            displayName = displayName ?: "User $id",
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
            roomDisplayName = roomDisplayName,
            commonRooms = commonRooms,
            isSuperuser = isSuperuser,
        )
    }
}
