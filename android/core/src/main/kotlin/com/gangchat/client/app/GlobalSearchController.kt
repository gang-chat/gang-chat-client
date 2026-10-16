package com.gangchat.client.app

import com.gangchat.client.foundation.completedFuture
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.GlobalSearchCounts
import com.gangchat.client.protocol.GlobalSearchCursors
import com.gangchat.client.protocol.GlobalSearchResults
import com.gangchat.client.protocol.UserSearchPage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

open class GlobalSearchController(val api: GangApi) {
    open suspend fun search(
        query: String,
        limit: Int = 8,
        categories: Iterable<GlobalSearchCategory>? = null,
        myRoomsCursor: String? = null,
        publicRoomsCursor: String? = null,
        userSettingsCursor: String? = null,
        messagesCursor: String? = null,
        filesCursor: String? = null,
    ): GlobalSearchResults =
        coroutineScope {
            val selected = categories?.toList()
            val includeUserSettings =
                selected?.contains(GlobalSearchCategory.userSettings) ?: false
            val coreCategories =
                selected?.filter { category -> category != GlobalSearchCategory.userSettings }
            // Both requests start before either is awaited, like the Dart futures.
            val coreFuture =
                if (coreCategories != null && coreCategories.isEmpty()) {
                    completedFuture(
                        GlobalSearchResults(
                            myRooms = emptyList(),
                            publicRooms = emptyList(),
                            messages = emptyList(),
                            files = emptyList(),
                        ),
                    )
                } else {
                    async(start = CoroutineStart.UNDISPATCHED) {
                        api.search(
                            query = query,
                            limit = limit,
                            categories = coreCategories?.map { category -> globalSearchCategoryKey(category) },
                            myRoomsCursor = myRoomsCursor,
                            publicRoomsCursor = publicRoomsCursor,
                            messagesCursor = messagesCursor,
                            filesCursor = filesCursor,
                        )
                    }
                }
            val userFuture =
                if (includeUserSettings) {
                    async(start = CoroutineStart.UNDISPATCHED) {
                        api.searchUsersPage(
                            query = query,
                            limit = limit,
                            cursor = userSettingsCursor,
                            includeSuspended = true,
                        )
                    }
                } else {
                    completedFuture(UserSearchPage(users = emptyList()))
                }
            val core = coreFuture.await()
            val users = userFuture.await()
            GlobalSearchResults(
                myRooms = core.myRooms,
                publicRooms = core.publicRooms,
                userSettings = users.users,
                messages = core.messages,
                files = core.files,
                nextCursors =
                    GlobalSearchCursors(
                        myRooms = core.nextCursors.myRooms,
                        publicRooms = core.nextCursors.publicRooms,
                        userSettings = users.nextCursor,
                        messages = core.nextCursors.messages,
                        files = core.nextCursors.files,
                    ),
                totalCounts =
                    GlobalSearchCounts(
                        myRooms = core.totalCounts.myRooms,
                        publicRooms = core.totalCounts.publicRooms,
                        userSettings = users.totalCount,
                        messages = core.totalCounts.messages,
                        files = core.totalCounts.files,
                    ),
            )
        }
}
