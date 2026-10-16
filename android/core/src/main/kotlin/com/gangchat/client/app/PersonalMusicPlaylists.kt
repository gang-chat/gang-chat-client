package com.gangchat.client.app

import com.gangchat.client.protocol.MusicBoxSearchResult
import com.gangchat.client.protocol.PersonalMusicPlaylist
import com.gangchat.client.protocol.PersonalMusicPlaylistApi
import com.gangchat.client.protocol.PersonalMusicPlaylistBatchAddApi
import com.gangchat.client.protocol.PersonalMusicPlaylistBatchAddResult
import com.gangchat.client.protocol.PersonalMusicPlaylistItem
import com.gangchat.client.protocol.PersonalMusicPlaylistItemsPage
import com.gangchat.client.protocol.PersonalMusicPlaylistMergeApi
import com.gangchat.client.protocol.PersonalMusicPlaylistMergeResult
import com.gangchat.client.protocol.PersonalMusicPlaylistPage
import com.gangchat.client.protocol.RoomMusicPlaylistApi
import com.gangchat.client.protocol.RoomMusicPlaylistBatchAddApi
import com.gangchat.client.protocol.RoomMusicPlaylistCloneApi
import com.gangchat.client.protocol.RoomMusicPlaylistImportApi
import com.gangchat.client.protocol.RoomMusicPlaylistMergeApi

const val personalMusicPlaylistPageSize: Int = 50
const val personalPlaylistCountAll: String = ""
const val personalPlaylistCountEmpty: String = "empty"
const val personalPlaylistCount1To10: String = "1-10"
const val personalPlaylistCount11To50: String = "11-50"
const val personalPlaylistCount51To100: String = "51-100"
const val personalPlaylistCountOver100: String = "101+"

/**
 * Dart's `Future<List<MusicBoxSearchResult>> Function({required String
 * keyword, required String source})`; call it as `search(keyword, source)`.
 */
typealias MusicPlaylistTrackSearch =
    suspend (keyword: String, source: String) -> List<MusicBoxSearchResult>

class PersonalPlaylistFilterDraft(
    val keyword: String,
    val countFilter: String,
)

class PersonalPlaylistCreateDraft(
    val name: String,
    val importPlaylistId: String? = null,
)

class PersonalPlaylistItemFilterDraft(
    val keyword: String,
    val source: String,
)

class MusicPlaylistManagementCapabilities(
    val canCreatePlaylists: Boolean = true,
    val canRenamePlaylists: Boolean = true,
    val canDeletePlaylists: Boolean = true,
    val canReorderPlaylists: Boolean = true,
    val canAddItems: Boolean = true,
    val canDeleteItems: Boolean = true,
    val canReorderItems: Boolean = true,
) {
    val canManagePlaylists: Boolean
        get() = canRenamePlaylists || canDeletePlaylists || canReorderPlaylists

    val canManageItems: Boolean get() = canDeleteItems || canReorderItems

    companion object {
        /** Dart `const MusicPlaylistManagementCapabilities.readOnly()`. */
        fun readOnly(): MusicPlaylistManagementCapabilities =
            MusicPlaylistManagementCapabilities(
                canCreatePlaylists = false,
                canRenamePlaylists = false,
                canDeletePlaylists = false,
                canReorderPlaylists = false,
                canAddItems = false,
                canDeleteItems = false,
                canReorderItems = false,
            )
    }
}

private interface _MusicPlaylistsBackend {
    val identity: Any

    val capabilities: MusicPlaylistManagementCapabilities

    suspend fun loadPlaylists(): PersonalMusicPlaylistPage

    val canImportPersonalPlaylist: Boolean

    suspend fun loadImportPlaylists(): PersonalMusicPlaylistPage?

    val canCloneRoomPlaylistsToPersonal: Boolean

    suspend fun cloneRoomPlaylistToPersonal(playlistId: String): PersonalMusicPlaylist

    val canMergePlaylists: Boolean

    suspend fun mergePlaylists(
        name: String,
        playlistIds: List<String>,
    ): PersonalMusicPlaylistMergeResult

    val canBatchAddItems: Boolean

    suspend fun batchAddItems(
        sourcePlaylistId: String,
        targetPlaylistId: String,
        itemIds: List<String>,
    ): PersonalMusicPlaylistBatchAddResult

    suspend fun createPlaylist(
        name: String,
        importPlaylistId: String? = null,
    ): PersonalMusicPlaylist

    suspend fun renamePlaylist(
        playlistId: String,
        name: String,
    ): PersonalMusicPlaylist

    suspend fun deletePlaylist(playlistId: String)

    suspend fun pinPlaylists(playlistIds: List<String>)

    suspend fun movePlaylist(
        playlistId: String,
        direction: String,
    )

    suspend fun loadItems(
        playlistId: String,
        page: Int,
        pageSize: Int,
        keyword: String? = null,
        source: String? = null,
    ): PersonalMusicPlaylistItemsPage

    suspend fun searchTracks(
        keyword: String,
        source: String,
    ): List<MusicBoxSearchResult>

    suspend fun addTrack(
        playlistId: String,
        track: MusicBoxSearchResult,
    ): PersonalMusicPlaylistItem

    suspend fun deleteItems(
        playlistId: String,
        itemIds: List<String>,
    )

    suspend fun moveItem(
        playlistId: String,
        itemId: String,
        direction: String,
    )

    suspend fun reorderItems(
        playlistId: String,
        itemIds: List<String>,
    )
}

class PersonalMusicPlaylistsController private constructor(
    private val _backend: _MusicPlaylistsBackend?,
    private val _onChanged: (() -> Unit)?,
) {
    constructor(
        api: PersonalMusicPlaylistApi?,
        onChanged: (() -> Unit)? = null,
    ) : this(
        if (api == null) null else _PersonalMusicPlaylistsBackend(api),
        onChanged,
    )

    private fun _notifyChanged() {
        _onChanged?.invoke()
    }

    val api: Any? get() = _backend?.identity

    val capabilities: MusicPlaylistManagementCapabilities
        get() = _backend?.capabilities ?: MusicPlaylistManagementCapabilities.readOnly()

    val available: Boolean get() = _backend != null

    val roomScoped: Boolean get() = _backend is _RoomMusicPlaylistsBackend

    val sourceRoomId: String?
        get() {
            val backend = _backend
            return if (backend is _RoomMusicPlaylistsBackend) backend.roomId else null
        }

    val canImportPersonalPlaylist: Boolean
        get() = _backend?.canImportPersonalPlaylist ?: false

    val canCloneRoomPlaylistsToPersonal: Boolean
        get() = _backend?.canCloneRoomPlaylistsToPersonal ?: false

    val canMergePlaylists: Boolean get() = _backend?.canMergePlaylists ?: false

    val canBatchAddItems: Boolean get() = _backend?.canBatchAddItems ?: false

    suspend fun loadPlaylists(): PersonalMusicPlaylistPage? {
        val client = _backend ?: return null
        return client.loadPlaylists()
    }

    suspend fun loadImportPlaylists(): PersonalMusicPlaylistPage? {
        return _backend?.loadImportPlaylists()
    }

    /**
     * Loads the current account's writable playlists even while this
     * controller is managing a room-scoped playlist collection.
     */
    suspend fun loadPersonalPlaylistTargets(): PersonalMusicPlaylistPage? {
        val client = _backend
        if (client is _PersonalMusicPlaylistsBackend) {
            return client.api.listPersonalMusicPlaylists(
                page = 1,
                pageSize = personalMusicPlaylistPageSize,
            )
        }
        if (client is _RoomMusicPlaylistsBackend) {
            val api = client.personalApi ?: return null
            return api.listPersonalMusicPlaylists(
                page = 1,
                pageSize = personalMusicPlaylistPageSize,
            )
        }
        return null
    }

    suspend fun cloneRoomPlaylistToPersonal(playlistId: String): PersonalMusicPlaylist? {
        val normalized = playlistId.trim()
        val client = _backend
        if (normalized.isEmpty() || client == null) return null
        val result =
            client.cloneRoomPlaylistToPersonal(
                playlistId = normalized,
            )
        _notifyChanged()
        return result
    }

    suspend fun mergePlaylists(
        name: String,
        playlistIds: List<String>,
    ): PersonalMusicPlaylistMergeResult? {
        val normalizedName = normalizedPersonalPlaylistName(name)
        val normalizedIDs = uniquePersonalPlaylistIds(playlistIds)
        if (normalizedName == null ||
            normalizedIDs.size < 2 ||
            normalizedIDs.size != playlistIds.size
        ) {
            return null
        }
        val client = _backend ?: return null
        val result =
            client.mergePlaylists(
                name = normalizedName,
                playlistIds = normalizedIDs,
            )
        _notifyChanged()
        return result
    }

    suspend fun batchAddItems(
        sourcePlaylistId: String,
        targetPlaylistId: String,
        itemIds: Iterable<String>,
    ): PersonalMusicPlaylistBatchAddResult? {
        val sourceID = sourcePlaylistId.trim()
        val targetID = targetPlaylistId.trim()
        val requestedItemIDs = itemIds.toList()
        val normalizedItemIDs = uniquePersonalPlaylistItemIds(requestedItemIDs)
        if (sourceID.isEmpty() ||
            targetID.isEmpty() ||
            sourceID == targetID ||
            normalizedItemIDs.isEmpty() ||
            normalizedItemIDs.size != requestedItemIDs.size ||
            normalizedItemIDs.size > 500
        ) {
            return null
        }
        val client = _backend ?: return null
        val result =
            client.batchAddItems(
                sourcePlaylistId = sourceID,
                targetPlaylistId = targetID,
                itemIds = normalizedItemIDs,
            )
        _notifyChanged()
        return result
    }

    suspend fun createPlaylist(
        name: String,
        importPlaylistId: String? = null,
    ): PersonalMusicPlaylist? {
        val normalized = normalizedPersonalPlaylistName(name) ?: return null
        val client = _backend ?: return null
        val result =
            client.createPlaylist(
                name = normalized,
                importPlaylistId = importPlaylistId?.trim(),
            )
        _notifyChanged()
        return result
    }

    suspend fun renamePlaylist(
        playlistId: String,
        name: String,
    ): PersonalMusicPlaylist? {
        val normalized = normalizedPersonalPlaylistName(name) ?: return null
        val client = _backend ?: return null
        val result =
            client.renamePlaylist(
                playlistId = playlistId,
                name = normalized,
            )
        _notifyChanged()
        return result
    }

    suspend fun deletePlaylist(playlistId: String) {
        val client = _backend ?: return
        client.deletePlaylist(playlistId)
        _notifyChanged()
    }

    suspend fun pinPlaylists(playlistIds: List<String>) {
        val ids = uniquePersonalPlaylistIds(playlistIds)
        val client = _backend
        if (ids.isEmpty() || client == null) return
        client.pinPlaylists(playlistIds = ids)
        _notifyChanged()
    }

    suspend fun movePlaylist(
        playlistId: String,
        delta: Int,
    ) {
        if (delta != -1 && delta != 1) {
            throw IllegalArgumentException("Invalid argument (delta): must be -1 or 1: $delta")
        }
        val client = _backend ?: return
        client.movePlaylist(
            playlistId = playlistId,
            direction = if (delta < 0) "up" else "down",
        )
        _notifyChanged()
    }

    suspend fun loadItems(
        playlistId: String,
        page: Int = 1,
        keyword: String = "",
        source: String = "",
    ): PersonalMusicPlaylistItemsPage? {
        val client = _backend ?: return null
        return client.loadItems(
            playlistId = playlistId,
            page = page,
            pageSize = personalMusicPlaylistPageSize,
            keyword = keyword,
            source = source,
        )
    }

    suspend fun searchTracks(
        keyword: String,
        source: String,
    ): List<MusicBoxSearchResult>? {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isEmpty()) {
            return emptyList()
        }
        val client = _backend ?: return null
        return client.searchTracks(keyword = normalizedKeyword, source = source)
    }

    suspend fun addTrack(
        playlistId: String,
        track: MusicBoxSearchResult,
    ): PersonalMusicPlaylistItem? {
        val client = _backend ?: return null
        val result = client.addTrack(playlistId = playlistId, track = track)
        _notifyChanged()
        return result
    }

    /**
     * Adds a track to the current account's playlist without changing the
     * scope currently managed by this controller.
     */
    suspend fun addTrackToPersonalPlaylist(
        playlistId: String,
        track: MusicBoxSearchResult,
    ): PersonalMusicPlaylistItem? {
        val api =
            when (val client = _backend) {
                is _PersonalMusicPlaylistsBackend -> client.api
                is _RoomMusicPlaylistsBackend -> client.personalApi
                else -> null
            } ?: return null
        val result =
            api.addPersonalMusicPlaylistItem(
                playlistId = playlistId,
                track = track,
            )
        _notifyChanged()
        return result
    }

    suspend fun deleteItems(
        playlistId: String,
        itemIds: List<String>,
    ) {
        val ids = uniquePersonalPlaylistItemIds(itemIds)
        val client = _backend
        if (ids.isEmpty() || client == null) return
        client.deleteItems(playlistId = playlistId, itemIds = ids)
        _notifyChanged()
    }

    suspend fun moveItem(
        playlistId: String,
        itemId: String,
        delta: Int,
    ) {
        if (delta != -1 && delta != 1) {
            throw IllegalArgumentException("Invalid argument (delta): must be -1 or 1: $delta")
        }
        val client = _backend ?: return
        client.moveItem(
            playlistId = playlistId,
            itemId = itemId,
            direction = if (delta < 0) "up" else "down",
        )
        _notifyChanged()
    }

    suspend fun pinItems(
        playlistId: String,
        selectedItemIds: Iterable<String>,
    ) {
        val client = _backend ?: return
        val selectedIds = uniquePersonalPlaylistItemIds(selectedItemIds)
        if (selectedIds.isEmpty()) return

        val items = mutableListOf<PersonalMusicPlaylistItem>()
        var page = 1
        while (true) {
            val result =
                client.loadItems(
                    playlistId = playlistId,
                    page = page,
                    pageSize = personalMusicPlaylistPageSize,
                )
            items.addAll(result.items)
            if (!result.hasMore) break
            if (result.items.isEmpty() || items.size >= result.total) {
                throw IllegalStateException("歌单分页数据不完整，请刷新后重试")
            }
            page += 1
        }

        val order =
            personalPlaylistItemOrderWithSelectionPinnedToFront(
                items = items,
                selectedItemIds = selectedIds,
            ) ?: return
        client.reorderItems(playlistId = playlistId, itemIds = order)
        _notifyChanged()
    }

    companion object {
        /** Dart's `PersonalMusicPlaylistsController.room(...)`. */
        fun room(
            roomApi: RoomMusicPlaylistApi?,
            personalApi: PersonalMusicPlaylistApi? = null,
            roomId: String,
            canManage: Boolean,
            searchTracks: MusicPlaylistTrackSearch,
            onChanged: (() -> Unit)? = null,
        ): PersonalMusicPlaylistsController {
            return PersonalMusicPlaylistsController(
                if (roomApi == null) {
                    null
                } else {
                    _RoomMusicPlaylistsBackend(
                        roomApi = roomApi,
                        personalApi = personalApi,
                        roomId = roomId,
                        canManage = canManage,
                        searchTracksCallback = searchTracks,
                    )
                },
                onChanged,
            )
        }
    }
}

private class _PersonalMusicPlaylistsBackend(val api: PersonalMusicPlaylistApi) : _MusicPlaylistsBackend {
    override val identity: Any get() = api

    override val capabilities: MusicPlaylistManagementCapabilities
        get() = MusicPlaylistManagementCapabilities()

    override val canImportPersonalPlaylist: Boolean get() = false

    override val canCloneRoomPlaylistsToPersonal: Boolean get() = false

    override val canMergePlaylists: Boolean get() = api is PersonalMusicPlaylistMergeApi

    override val canBatchAddItems: Boolean get() = api is PersonalMusicPlaylistBatchAddApi

    override suspend fun cloneRoomPlaylistToPersonal(playlistId: String): PersonalMusicPlaylist {
        throw IllegalStateException("个人歌单不能作为房间歌单的克隆来源")
    }

    override suspend fun mergePlaylists(
        name: String,
        playlistIds: List<String>,
    ): PersonalMusicPlaylistMergeResult {
        val mergeApi = api
        if (mergeApi !is PersonalMusicPlaylistMergeApi) {
            throw IllegalStateException("当前服务端不支持合并个人歌单")
        }
        return mergeApi.mergePersonalMusicPlaylists(name = name, playlistIds = playlistIds)
    }

    override suspend fun batchAddItems(
        sourcePlaylistId: String,
        targetPlaylistId: String,
        itemIds: List<String>,
    ): PersonalMusicPlaylistBatchAddResult {
        val batchApi = api
        if (batchApi !is PersonalMusicPlaylistBatchAddApi) {
            throw IllegalStateException("当前服务端不支持批量添加个人歌单歌曲")
        }
        return batchApi.batchAddPersonalMusicPlaylistItems(
            sourcePlaylistId = sourcePlaylistId,
            targetPlaylistId = targetPlaylistId,
            itemIds = itemIds,
        )
    }

    override suspend fun loadImportPlaylists(): PersonalMusicPlaylistPage? {
        return null
    }

    override suspend fun loadPlaylists(): PersonalMusicPlaylistPage {
        return api.listPersonalMusicPlaylists()
    }

    override suspend fun createPlaylist(
        name: String,
        importPlaylistId: String?,
    ): PersonalMusicPlaylist {
        if (importPlaylistId != null && importPlaylistId.isNotEmpty()) {
            throw IllegalStateException("个人歌单不能从另一个个人歌单导入创建")
        }
        return api.createPersonalMusicPlaylist(name = name)
    }

    override suspend fun renamePlaylist(
        playlistId: String,
        name: String,
    ): PersonalMusicPlaylist {
        return api.renamePersonalMusicPlaylist(playlistId = playlistId, name = name)
    }

    override suspend fun deletePlaylist(playlistId: String) {
        api.deletePersonalMusicPlaylist(playlistId)
    }

    override suspend fun pinPlaylists(playlistIds: List<String>) {
        api.pinPersonalMusicPlaylists(playlistIds = playlistIds)
    }

    override suspend fun movePlaylist(
        playlistId: String,
        direction: String,
    ) {
        api.movePersonalMusicPlaylist(
            playlistId = playlistId,
            direction = direction,
        )
    }

    override suspend fun loadItems(
        playlistId: String,
        page: Int,
        pageSize: Int,
        keyword: String?,
        source: String?,
    ): PersonalMusicPlaylistItemsPage {
        return api.getPersonalMusicPlaylist(
            playlistId = playlistId,
            page = page,
            pageSize = pageSize,
            keyword = keyword,
            source = source,
        )
    }

    override suspend fun searchTracks(
        keyword: String,
        source: String,
    ): List<MusicBoxSearchResult> {
        return api.searchPersonalMusicPlaylistTracks(
            keyword = keyword,
            source = source,
        )
    }

    override suspend fun addTrack(
        playlistId: String,
        track: MusicBoxSearchResult,
    ): PersonalMusicPlaylistItem {
        return api.addPersonalMusicPlaylistItem(
            playlistId = playlistId,
            track = track,
        )
    }

    override suspend fun deleteItems(
        playlistId: String,
        itemIds: List<String>,
    ) {
        api.deletePersonalMusicPlaylistItems(
            playlistId = playlistId,
            itemIds = itemIds,
        )
    }

    override suspend fun moveItem(
        playlistId: String,
        itemId: String,
        direction: String,
    ) {
        api.movePersonalMusicPlaylistItem(
            playlistId = playlistId,
            itemId = itemId,
            direction = direction,
        )
    }

    override suspend fun reorderItems(
        playlistId: String,
        itemIds: List<String>,
    ) {
        api.reorderPersonalMusicPlaylistItems(
            playlistId = playlistId,
            itemIds = itemIds,
        )
    }
}

/** Dart's record identity `(roomApi, personalApi, roomId, canManage)`. */
private data class _RoomMusicPlaylistsIdentity(
    val roomApi: RoomMusicPlaylistApi,
    val personalApi: PersonalMusicPlaylistApi?,
    val roomId: String,
    val canManage: Boolean,
)

private class _RoomMusicPlaylistsBackend(
    val roomApi: RoomMusicPlaylistApi,
    val personalApi: PersonalMusicPlaylistApi?,
    val roomId: String,
    val canManage: Boolean,
    val searchTracksCallback: MusicPlaylistTrackSearch,
) : _MusicPlaylistsBackend {
    private fun _requireManagePermission() {
        if (!canManage) {
            throw IllegalStateException("当前账号没有管理房间歌单的权限")
        }
    }

    override val identity: Any
        get() = _RoomMusicPlaylistsIdentity(roomApi, personalApi, roomId, canManage)

    override val capabilities: MusicPlaylistManagementCapabilities
        get() =
            if (canManage) {
                MusicPlaylistManagementCapabilities()
            } else {
                MusicPlaylistManagementCapabilities.readOnly()
            }

    override val canImportPersonalPlaylist: Boolean
        get() = canManage && personalApi != null && roomApi is RoomMusicPlaylistImportApi

    override val canCloneRoomPlaylistsToPersonal: Boolean
        get() = canManage && roomApi is RoomMusicPlaylistCloneApi

    override val canMergePlaylists: Boolean
        get() = canManage && roomApi is RoomMusicPlaylistMergeApi

    override val canBatchAddItems: Boolean
        get() = canManage && roomApi is RoomMusicPlaylistBatchAddApi

    override suspend fun cloneRoomPlaylistToPersonal(playlistId: String): PersonalMusicPlaylist {
        _requireManagePermission()
        val cloneApi = roomApi
        if (cloneApi !is RoomMusicPlaylistCloneApi) {
            throw IllegalStateException("当前服务端不支持克隆房间歌单")
        }
        return cloneApi.cloneRoomMusicPlaylistToPersonal(
            roomId = roomId,
            playlistId = playlistId,
        )
    }

    override suspend fun mergePlaylists(
        name: String,
        playlistIds: List<String>,
    ): PersonalMusicPlaylistMergeResult {
        _requireManagePermission()
        val mergeApi = roomApi
        if (mergeApi !is RoomMusicPlaylistMergeApi) {
            throw IllegalStateException("当前服务端不支持合并房间歌单")
        }
        return mergeApi.mergeRoomMusicPlaylists(
            roomId = roomId,
            name = name,
            playlistIds = playlistIds,
        )
    }

    override suspend fun batchAddItems(
        sourcePlaylistId: String,
        targetPlaylistId: String,
        itemIds: List<String>,
    ): PersonalMusicPlaylistBatchAddResult {
        _requireManagePermission()
        val batchApi = roomApi
        if (batchApi !is RoomMusicPlaylistBatchAddApi) {
            throw IllegalStateException("当前服务端不支持批量添加房间歌单歌曲")
        }
        return batchApi.batchAddRoomMusicPlaylistItems(
            roomId = roomId,
            sourcePlaylistId = sourcePlaylistId,
            targetPlaylistId = targetPlaylistId,
            itemIds = itemIds,
        )
    }

    override suspend fun loadImportPlaylists(): PersonalMusicPlaylistPage? {
        _requireManagePermission()
        val api = personalApi ?: return null
        return api.listPersonalMusicPlaylists(
            page = 1,
            pageSize = personalMusicPlaylistPageSize,
        )
    }

    override suspend fun loadPlaylists(): PersonalMusicPlaylistPage {
        return roomApi.listRoomMusicPlaylists(roomId = roomId)
    }

    override suspend fun createPlaylist(
        name: String,
        importPlaylistId: String?,
    ): PersonalMusicPlaylist {
        _requireManagePermission()
        val normalizedImportID = importPlaylistId?.trim() ?: ""
        if (normalizedImportID.isNotEmpty()) {
            val importApi = roomApi
            if (importApi !is RoomMusicPlaylistImportApi) {
                throw IllegalStateException("当前服务端不支持导入我的歌单")
            }
            return importApi.createRoomMusicPlaylistFromPersonal(
                roomId = roomId,
                name = name,
                importPlaylistId = normalizedImportID,
            )
        }
        return roomApi.createRoomMusicPlaylist(roomId = roomId, name = name)
    }

    override suspend fun renamePlaylist(
        playlistId: String,
        name: String,
    ): PersonalMusicPlaylist {
        _requireManagePermission()
        return roomApi.renameRoomMusicPlaylist(
            roomId = roomId,
            playlistId = playlistId,
            name = name,
        )
    }

    override suspend fun deletePlaylist(playlistId: String) {
        _requireManagePermission()
        roomApi.deleteRoomMusicPlaylist(
            roomId = roomId,
            playlistId = playlistId,
        )
    }

    override suspend fun pinPlaylists(playlistIds: List<String>) {
        _requireManagePermission()
        roomApi.pinRoomMusicPlaylists(
            roomId = roomId,
            playlistIds = playlistIds,
        )
    }

    override suspend fun movePlaylist(
        playlistId: String,
        direction: String,
    ) {
        _requireManagePermission()
        roomApi.moveRoomMusicPlaylist(
            roomId = roomId,
            playlistId = playlistId,
            direction = direction,
        )
    }

    override suspend fun loadItems(
        playlistId: String,
        page: Int,
        pageSize: Int,
        keyword: String?,
        source: String?,
    ): PersonalMusicPlaylistItemsPage {
        return roomApi.getRoomMusicPlaylist(
            roomId = roomId,
            playlistId = playlistId,
            page = page,
            pageSize = pageSize,
            keyword = keyword,
            source = source,
        )
    }

    override suspend fun searchTracks(
        keyword: String,
        source: String,
    ): List<MusicBoxSearchResult> {
        return searchTracksCallback(keyword, source)
    }

    override suspend fun addTrack(
        playlistId: String,
        track: MusicBoxSearchResult,
    ): PersonalMusicPlaylistItem {
        _requireManagePermission()
        return roomApi.addRoomMusicPlaylistItem(
            roomId = roomId,
            playlistId = playlistId,
            track = track,
        )
    }

    override suspend fun deleteItems(
        playlistId: String,
        itemIds: List<String>,
    ) {
        _requireManagePermission()
        roomApi.deleteRoomMusicPlaylistItems(
            roomId = roomId,
            playlistId = playlistId,
            itemIds = itemIds,
        )
    }

    override suspend fun moveItem(
        playlistId: String,
        itemId: String,
        direction: String,
    ) {
        _requireManagePermission()
        roomApi.moveRoomMusicPlaylistItem(
            roomId = roomId,
            playlistId = playlistId,
            itemId = itemId,
            direction = direction,
        )
    }

    override suspend fun reorderItems(
        playlistId: String,
        itemIds: List<String>,
    ) {
        _requireManagePermission()
        roomApi.reorderRoomMusicPlaylistItems(
            roomId = roomId,
            playlistId = playlistId,
            itemIds = itemIds,
        )
    }
}

fun normalizedPersonalPlaylistName(value: String): String? {
    val normalized = value.trim()
    if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length) > 64) return null
    return normalized
}

fun uniquePersonalPlaylistItemIds(values: Iterable<String>): List<String> {
    return _uniqueNonEmptyStrings(values)
}

fun uniquePersonalPlaylistIds(values: Iterable<String>): List<String> {
    return _uniqueNonEmptyStrings(values)
}

private fun _uniqueNonEmptyStrings(values: Iterable<String>): List<String> {
    val result = mutableListOf<String>()
    val seen = HashSet<String>()
    for (value in values) {
        val normalized = value.trim()
        if (normalized.isEmpty() || !seen.add(normalized)) continue
        result.add(normalized)
    }
    return result
}

fun toggledPersonalPlaylistSelection(
    selected: Set<String>,
    itemId: String,
): Set<String> {
    val next = LinkedHashSet(selected)
    if (!next.add(itemId)) next.remove(itemId)
    return next
}

fun personalPlaylistFilterActive(
    keyword: String,
    source: String,
): Boolean {
    return keyword.trim().isNotEmpty() || source.trim().isNotEmpty()
}

fun personalPlaylistListFilterActive(
    keyword: String,
    countFilter: String,
): Boolean {
    return keyword.trim().isNotEmpty() || countFilter.trim().isNotEmpty()
}

fun filteredPersonalMusicPlaylists(
    playlists: Iterable<PersonalMusicPlaylist>,
    keyword: String,
    countFilter: String,
): List<PersonalMusicPlaylist> {
    val needle = keyword.trim().lowercase()
    return playlists.filter { playlist ->
        (needle.isEmpty() || playlist.name.lowercase().contains(needle)) &&
            personalPlaylistMatchesCountFilter(playlist.itemCount, countFilter)
    }
}

fun personalPlaylistMatchesCountFilter(count: Int, filter: String): Boolean {
    return when (filter) {
        personalPlaylistCountAll -> true
        personalPlaylistCountEmpty -> count == 0
        personalPlaylistCount1To10 -> count >= 1 && count <= 10
        personalPlaylistCount11To50 -> count >= 11 && count <= 50
        personalPlaylistCount51To100 -> count >= 51 && count <= 100
        personalPlaylistCountOver100 -> count >= 101
        else -> true
    }
}

fun toggledPersonalPlaylistListSelection(
    selected: List<String>,
    playlistId: String,
): List<String> {
    val next = selected.toMutableList()
    val index = next.indexOf(playlistId)
    if (index >= 0) {
        next.removeAt(index)
    } else {
        next.add(playlistId)
    }
    return next
}

fun personalPlaylistAllVisibleSelected(
    selectedPlaylistIds: List<String>,
    visiblePlaylists: List<PersonalMusicPlaylist>,
): Boolean {
    return visiblePlaylists.isNotEmpty() &&
        visiblePlaylists.all { playlist -> selectedPlaylistIds.contains(playlist.id) }
}

fun toggledVisiblePersonalPlaylistSelection(
    selectedPlaylistIds: List<String>,
    visiblePlaylists: List<PersonalMusicPlaylist>,
): List<String> {
    if (visiblePlaylists.isEmpty()) return selectedPlaylistIds.toList()
    val visibleIds = visiblePlaylists.map { playlist -> playlist.id }.toSet()
    if (personalPlaylistAllVisibleSelected(
            selectedPlaylistIds = selectedPlaylistIds,
            visiblePlaylists = visiblePlaylists,
        )
    ) {
        return selectedPlaylistIds.filter { id -> !visibleIds.contains(id) }
    }
    val next = selectedPlaylistIds.toMutableList()
    for (playlist in visiblePlaylists) {
        if (!next.contains(playlist.id)) next.add(playlist.id)
    }
    return next
}

fun personalPlaylistSelectionNumbers(selectedPlaylistIds: List<String>): Map<String, Int> {
    val numbers = LinkedHashMap<String, Int>()
    for ((index, playlistId) in selectedPlaylistIds.withIndex()) {
        numbers[playlistId] = index + 1
    }
    return numbers
}

fun personalPlaylistOrderWithSelectionPinnedToFront(
    playlists: List<PersonalMusicPlaylist>,
    selectedPlaylistIds: List<String>,
): List<String>? {
    val currentOrder = playlists.map { playlist -> playlist.id }
    val currentSet = currentOrder.toSet()
    val selectedOrder =
        uniquePersonalPlaylistIds(selectedPlaylistIds).filter { playlistId ->
            currentSet.contains(playlistId)
        }
    if (selectedOrder.isEmpty()) return null
    val selectedSet = selectedOrder.toSet()
    val nextOrder =
        selectedOrder + currentOrder.filter { playlistId -> !selectedSet.contains(playlistId) }
    for (index in currentOrder.indices) {
        if (currentOrder[index] != nextOrder[index]) return nextOrder
    }
    return null
}

fun personalPlaylistItemOrderWithSelectionPinnedToFront(
    items: Iterable<PersonalMusicPlaylistItem>,
    selectedItemIds: Iterable<String>,
): List<String>? {
    val currentOrder = items.map { item -> item.id }
    val currentSet = currentOrder.toSet()
    val selectedOrder =
        uniquePersonalPlaylistItemIds(selectedItemIds).filter { itemId ->
            currentSet.contains(itemId)
        }
    if (selectedOrder.isEmpty()) return null
    val selectedSet = selectedOrder.toSet()
    val nextOrder =
        selectedOrder + currentOrder.filter { itemId -> !selectedSet.contains(itemId) }
    for (index in currentOrder.indices) {
        if (currentOrder[index] != nextOrder[index]) return nextOrder
    }
    return null
}

fun personalPlaylistArtistsLabel(artists: List<String>): String {
    val normalized =
        artists
            .map { artist -> artist.trim() }
            .filter { artist -> artist.isNotEmpty() }
    return if (normalized.isEmpty()) "未知歌手" else normalized.joinToString("、")
}

fun personalPlaylistSourceLabel(source: String): String {
    return when (source) {
        "netease" -> "网易云"
        "bilibili" -> "哔哩哔哩"
        "tencent" -> "QQ音乐"
        else -> if (source.trim().isEmpty()) "未知来源" else source
    }
}
