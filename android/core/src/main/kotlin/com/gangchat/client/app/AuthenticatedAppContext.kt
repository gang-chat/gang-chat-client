package com.gangchat.client.app

import com.gangchat.client.auth.AuthSession
import com.gangchat.client.protocol.AccessTokenProvider
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.protocol.GangApiClient
import com.gangchat.client.protocol.StickerPackStore

typealias LogoutCallback = suspend () -> Unit
typealias ExitSessionCallback = suspend () -> Unit
typealias CurrentUserUpdatedCallback = (user: CurrentUser) -> Unit

class AuthenticatedAppContext(
    val session: AuthSession,
    val apiBaseUrl: String,
    val accessTokenProvider: AccessTokenProvider,
    val logout: LogoutCallback,
    val exitSessionForAppExit: ExitSessionCallback,
    val serverClock: ServerClock,
    val emailVerificationController: EmailVerificationController? = null,
    val passwordResetController: PasswordResetController? = null,
    val onCurrentUserUpdated: CurrentUserUpdatedCallback? = null,
    val api: GangApi? = null,
    // Dart's `const StickerPackStore()`: its caches are static, so separate
    // instances behave like the shared constant.
    val stickerPackStore: StickerPackStore = StickerPackStore(),
) {
    val currentUser: CurrentUser get() = session.user

    fun createApiClient(): GangApi {
        val injectedApi = api
        if (injectedApi != null) return injectedApi
        return GangApiClient(
            baseUrl = apiBaseUrl,
            accessTokenProvider = accessTokenProvider,
            onServerTime = { value -> serverClock.updateFromHeader(value) },
            onRequestLatency = { value -> serverClock.updateRequestRoundTrip(value) },
        )
    }

    fun hasSameApiSource(other: AuthenticatedAppContext): Boolean {
        return apiBaseUrl == other.apiBaseUrl &&
            api == other.api &&
            serverClock == other.serverClock
    }
}
