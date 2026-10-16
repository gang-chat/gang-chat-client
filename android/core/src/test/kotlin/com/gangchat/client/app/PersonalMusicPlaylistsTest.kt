package com.gangchat.client.app

import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.protocol.PersonalMusicPlaylistApi
import com.gangchat.client.protocol.PersonalMusicPlaylistBatchAddApi
import com.gangchat.client.protocol.PersonalMusicPlaylistBatchAddResult
import com.gangchat.client.protocol.PersonalMusicPlaylistItem
import com.gangchat.client.protocol.PersonalMusicPlaylistItemsPage
import com.gangchat.client.protocol.PersonalMusicPlaylistMergeApi
import com.gangchat.client.protocol.PersonalMusicPlaylistMergeResult
import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PersonalMusicPlaylistsTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `playlist names are trimmed and limited to 64 characters`() {
        assertEquals("夜晚", normalizedPersonalPlaylistName("  夜晚  "))
        assertNull(normalizedPersonalPlaylistName("   "))
        assertNull(normalizedPersonalPlaylistName("歌".repeat(65)))
    }

    @Test
    fun `playlist item ids are normalized without changing order`() {
        assertEquals(
            listOf("item_2", "item_1"),
            uniquePersonalPlaylistItemIds(listOf(" item_2 ", "", "item_1", "item_2")),
        )
    }

    @Test
    fun `selection toggle is immutable`() {
        val original = setOf("item_1")
        val added = toggledPersonalPlaylistSelection(original, "item_2")
        val removed = toggledPersonalPlaylistSelection(added, "item_1")

        assertEquals(setOf("item_1"), original)
        assertEquals(setOf("item_1", "item_2"), added)
        assertEquals(setOf("item_2"), removed)
    }

    @Test
    fun `playlist filters and labels share one presentation rule`() {
        assertTrue(personalPlaylistFilterActive(keyword = "晴天", source = ""))
        assertTrue(
            personalPlaylistFilterActive(keyword = " ", source = "netease"),
        )
        assertFalse(personalPlaylistFilterActive(keyword = " ", source = ""))
        assertEquals("周杰伦", personalPlaylistArtistsLabel(listOf(" 周杰伦 ", "")))
        assertEquals("未知歌手", personalPlaylistArtistsLabel(emptyList()))
        assertEquals("网易云", personalPlaylistSourceLabel("netease"))
    }

    @Test
    fun `playlist list filters names and song-count ranges`() {
        val playlists =
            listOf(
                PersonalMusicPlaylist(
                    id = "empty",
                    name = "空歌单",
                    description = "",
                    revision = 1,
                    itemCount = 0,
                    createdAt = null,
                    updatedAt = null,
                ),
                PersonalMusicPlaylist(
                    id = "short",
                    name = "夜晚精选",
                    description = "",
                    revision = 1,
                    itemCount = 10,
                    createdAt = null,
                    updatedAt = null,
                ),
                PersonalMusicPlaylist(
                    id = "long",
                    name = "夜晚长单",
                    description = "",
                    revision = 1,
                    itemCount = 101,
                    createdAt = null,
                    updatedAt = null,
                ),
            )

        assertEquals(
            listOf("short"),
            filteredPersonalMusicPlaylists(
                playlists,
                keyword = " 夜晚 ",
                countFilter = personalPlaylistCount1To10,
            ).map { playlist -> playlist.id },
        )
        assertEquals(
            listOf("long"),
            filteredPersonalMusicPlaylists(
                playlists,
                keyword = "",
                countFilter = personalPlaylistCountOver100,
            ).map { playlist -> playlist.id },
        )
        assertFalse(
            personalPlaylistListFilterActive(
                keyword = " ",
                countFilter = personalPlaylistCountAll,
            ),
        )
    }

    @Test
    fun `visible playlist selection preserves hidden selections and order`() {
        val visible =
            listOf(
                PersonalMusicPlaylist(
                    id = "visible_1",
                    name = "一",
                    description = "",
                    revision = 1,
                    itemCount = 1,
                    createdAt = null,
                    updatedAt = null,
                ),
                PersonalMusicPlaylist(
                    id = "visible_2",
                    name = "二",
                    description = "",
                    revision = 1,
                    itemCount = 2,
                    createdAt = null,
                    updatedAt = null,
                ),
            )
        val original = listOf("hidden")
        val selected =
            toggledVisiblePersonalPlaylistSelection(
                selectedPlaylistIds = original,
                visiblePlaylists = visible,
            )
        val cleared =
            toggledVisiblePersonalPlaylistSelection(
                selectedPlaylistIds = selected,
                visiblePlaylists = visible,
            )

        assertEquals(listOf("hidden"), original)
        assertEquals(listOf("hidden", "visible_1", "visible_2"), selected)
        assertEquals(
            mapOf(
                "hidden" to 1,
                "visible_1" to 2,
                "visible_2" to 3,
            ),
            personalPlaylistSelectionNumbers(selected),
        )
        assertEquals(listOf("hidden"), cleared)
    }

    @Test
    fun `batch pin follows selection order and preserves unselected order`() {
        val playlists =
            listOf(
                PersonalMusicPlaylist(
                    id = "first",
                    name = "一",
                    description = "",
                    revision = 1,
                    itemCount = 0,
                    createdAt = null,
                    updatedAt = null,
                ),
                PersonalMusicPlaylist(
                    id = "second",
                    name = "二",
                    description = "",
                    revision = 1,
                    itemCount = 0,
                    createdAt = null,
                    updatedAt = null,
                ),
                PersonalMusicPlaylist(
                    id = "third",
                    name = "三",
                    description = "",
                    revision = 1,
                    itemCount = 0,
                    createdAt = null,
                    updatedAt = null,
                ),
            )

        assertEquals(
            listOf("third", "second", "first"),
            personalPlaylistOrderWithSelectionPinnedToFront(
                playlists = playlists,
                selectedPlaylistIds = listOf("third", "second"),
            ),
        )
        assertNull(
            personalPlaylistOrderWithSelectionPinnedToFront(
                playlists = playlists,
                selectedPlaylistIds = listOf("first", "second"),
            ),
        )
        assertEquals(
            listOf("third", "first", "second"),
            personalPlaylistOrderWithSelectionPinnedToFront(
                playlists = playlists,
                selectedPlaylistIds = listOf("missing", " third ", "third"),
            ),
        )
    }

    @Test
    fun `batch item pin follows selection order`() {
        val items =
            listOf(
                PersonalMusicPlaylistItem(
                    id = "first",
                    playlistId = "playlist",
                    trackId = "track_1",
                    source = "netease",
                    title = "一",
                    artists = emptyList(),
                    durationMs = 0,
                    sortOrder = 10,
                    createdAt = null,
                ),
                PersonalMusicPlaylistItem(
                    id = "second",
                    playlistId = "playlist",
                    trackId = "track_2",
                    source = "netease",
                    title = "二",
                    artists = emptyList(),
                    durationMs = 0,
                    sortOrder = 20,
                    createdAt = null,
                ),
                PersonalMusicPlaylistItem(
                    id = "third",
                    playlistId = "playlist",
                    trackId = "track_3",
                    source = "netease",
                    title = "三",
                    artists = emptyList(),
                    durationMs = 0,
                    sortOrder = 30,
                    createdAt = null,
                ),
            )

        assertEquals(
            listOf("third", "second", "first"),
            personalPlaylistItemOrderWithSelectionPinnedToFront(
                items = items,
                selectedItemIds = listOf("third", "second"),
            ),
        )
        assertNull(
            personalPlaylistItemOrderWithSelectionPinnedToFront(
                items = items,
                selectedItemIds = listOf("first", "second"),
            ),
        )
    }

    @Test
    fun `item pin loads every page before submitting the complete order`() = gang.runTest {
        val api = _PagingPlaylistApi()
        var changes = 0
        val controller =
            PersonalMusicPlaylistsController(
                api,
                onChanged = { changes += 1 },
            )

        controller.pinItems(
            playlistId = "playlist",
            selectedItemIds = listOf("third", "second"),
        )

        assertEquals(listOf(1, 2), api.requestedPages)
        assertEquals(listOf("third", "second", "first"), api.reorderedItemIds)
        assertEquals(1, changes)
    }

    @Test
    fun `playlist merge validates input and preserves selection order`() = gang.runTest {
        val api = _MergePlaylistApi()
        var changes = 0
        val controller =
            PersonalMusicPlaylistsController(
                api,
                onChanged = { changes += 1 },
            )

        assertTrue(controller.canMergePlaylists)
        assertNotNull(
            controller.mergePlaylists(
                name = "  合并结果  ",
                playlistIds = listOf(" second ", "first"),
            ),
        )
        assertEquals("合并结果", api.mergeName)
        assertEquals(listOf("second", "first"), api.mergePlaylistIds)
        assertEquals(1, changes)

        api.mergeName = null
        assertNull(
            controller.mergePlaylists(
                name = "无效",
                playlistIds = listOf("first", "first"),
            ),
        )
        assertNull(api.mergeName)
        assertEquals(1, changes)
        assertFalse(
            PersonalMusicPlaylistsController(
                _PagingPlaylistApi(),
            ).canMergePlaylists,
        )
    }

    @Test
    fun `batch add validates scope and preserves selected item order`() = gang.runTest {
        val api = _BatchAddPlaylistApi()
        val controller = PersonalMusicPlaylistsController(api)

        assertTrue(controller.canBatchAddItems)
        assertNotNull(
            controller.batchAddItems(
                sourcePlaylistId = " source ",
                targetPlaylistId = " target ",
                itemIds = listOf(" second ", "first"),
            ),
        )
        assertEquals("source", api.sourcePlaylistId)
        assertEquals("target", api.targetPlaylistId)
        assertEquals(listOf("second", "first"), api.itemIds)

        api.sourcePlaylistId = null
        assertNull(
            controller.batchAddItems(
                sourcePlaylistId = "same",
                targetPlaylistId = "same",
                itemIds = listOf("first"),
            ),
        )
        assertNull(
            controller.batchAddItems(
                sourcePlaylistId = "source",
                targetPlaylistId = "target",
                itemIds = listOf("first", "first"),
            ),
        )
        assertNull(api.sourcePlaylistId)
        assertFalse(
            PersonalMusicPlaylistsController(_PagingPlaylistApi()).canBatchAddItems,
        )
    }

    private class _MergePlaylistApi : _PagingPlaylistApi(), PersonalMusicPlaylistMergeApi {
        var mergeName: String? = null
        var mergePlaylistIds: List<String>? = null

        override suspend fun mergePersonalMusicPlaylists(
            name: String,
            playlistIds: List<String>,
        ): PersonalMusicPlaylistMergeResult {
            mergeName = name
            mergePlaylistIds = playlistIds.toList()
            val playlist =
                PersonalMusicPlaylist(
                    id = "merged",
                    name = "合并结果",
                    description = "",
                    revision = 1,
                    itemCount = 2,
                    createdAt = null,
                    updatedAt = null,
                )
            return PersonalMusicPlaylistMergeResult(
                playlist = playlist,
                sourceItemCount = 2,
                uniqueItemCount = 2,
                duplicateCount = 0,
                itemCount = 2,
                omittedCount = 0,
                deletedPlaylistCount = 2,
                retainedPlaylistCount = 0,
                consumedSourceItemCount = 2,
                truncated = false,
            )
        }
    }

    private class _BatchAddPlaylistApi : _PagingPlaylistApi(), PersonalMusicPlaylistBatchAddApi {
        var sourcePlaylistId: String? = null
        var targetPlaylistId: String? = null
        var itemIds: List<String>? = null

        override suspend fun batchAddPersonalMusicPlaylistItems(
            sourcePlaylistId: String,
            targetPlaylistId: String,
            itemIds: List<String>,
        ): PersonalMusicPlaylistBatchAddResult {
            this.sourcePlaylistId = sourcePlaylistId
            this.targetPlaylistId = targetPlaylistId
            this.itemIds = itemIds.toList()
            val playlist =
                PersonalMusicPlaylist(
                    id = "target",
                    name = "目标歌单",
                    description = "",
                    revision = 2,
                    itemCount = 3,
                    createdAt = null,
                    updatedAt = null,
                )
            return PersonalMusicPlaylistBatchAddResult(
                playlist = playlist,
                selectedItemCount = 2,
                uniqueItemCount = 2,
                duplicateCount = 0,
                alreadyPresentCount = 0,
                addedItemCount = 2,
                omittedCount = 0,
                truncated = false,
            )
        }
    }

    private open class _PagingPlaylistApi : PersonalMusicPlaylistApi by _unimplemented() {
        val requestedPages: MutableList<Int> = mutableListOf()
        var reorderedItemIds: List<String>? = null

        override suspend fun getPersonalMusicPlaylist(
            playlistId: String,
            page: Int,
            pageSize: Int,
            keyword: String?,
            source: String?,
        ): PersonalMusicPlaylistItemsPage {
            requestedPages.add(page)
            val pageItems =
                if (page == 1) {
                    items.take(2)
                } else {
                    items.drop(2)
                }
            return PersonalMusicPlaylistItemsPage(
                playlist = playlist,
                items = pageItems,
                page = page,
                pageSize = 2,
                total = items.size,
                hasMore = page == 1,
            )
        }

        override suspend fun reorderPersonalMusicPlaylistItems(
            playlistId: String,
            itemIds: List<String>,
        ) {
            reorderedItemIds = itemIds.toList()
        }

        companion object {
            val playlist =
                PersonalMusicPlaylist(
                    id = "playlist",
                    name = "测试歌单",
                    description = "",
                    revision = 1,
                    itemCount = 3,
                    createdAt = null,
                    updatedAt = null,
                )

            val items =
                listOf(
                    PersonalMusicPlaylistItem(
                        id = "first",
                        playlistId = "playlist",
                        trackId = "track_1",
                        source = "netease",
                        title = "一",
                        artists = emptyList(),
                        durationMs = 0,
                        sortOrder = 10,
                        createdAt = null,
                    ),
                    PersonalMusicPlaylistItem(
                        id = "second",
                        playlistId = "playlist",
                        trackId = "track_2",
                        source = "netease",
                        title = "二",
                        artists = emptyList(),
                        durationMs = 0,
                        sortOrder = 20,
                        createdAt = null,
                    ),
                    PersonalMusicPlaylistItem(
                        id = "third",
                        playlistId = "playlist",
                        trackId = "track_3",
                        source = "netease",
                        title = "三",
                        artists = emptyList(),
                        durationMs = 0,
                        sortOrder = 30,
                        createdAt = null,
                    ),
                )
        }
    }
}

/**
 * Dart's `noSuchMethod` fallback for fakes that implement only part of an
 * interface: every method not overridden throws.
 */
private inline fun <reified T : Any> _unimplemented(): T {
    val name = T::class.java.simpleName
    return java.lang.reflect.Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.getOrNull(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "Unimplemented $name"
            else -> throw NotImplementedError("$name.${method.name} is not implemented by this fake")
        }
    } as T
}
