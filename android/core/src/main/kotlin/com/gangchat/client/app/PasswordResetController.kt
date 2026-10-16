package com.gangchat.client.app

import com.gangchat.client.auth.AuthClient
import com.gangchat.client.auth.PasswordResetChallenge
import com.gangchat.client.auth.PasswordResetInspection

typealias PasswordResetAuthClientFactory = (baseUrl: String) -> AuthClient
typealias PasswordResetAccessTokenProvider = suspend () -> String

class PasswordResetController(
    val apiBaseUrl: String,
    authClientFactory: PasswordResetAuthClientFactory? = null,
    val accessTokenProvider: PasswordResetAccessTokenProvider? = null,
) {
    private val _authClientFactory: PasswordResetAuthClientFactory =
        authClientFactory ?: { baseUrl -> AuthClient(baseUrl = baseUrl) }
    private var _sessionAuthorizedEmail: String? = null

    fun isCurrentSessionAuthorizedFor(email: String?): Boolean {
        val normalized = email?.trim()?.lowercase() ?: ""
        return normalized.isNotEmpty() && _sessionAuthorizedEmail == normalized
    }

    fun clearCurrentSessionAuthorization() {
        _sessionAuthorizedEmail = null
    }

    fun invalidateAuthorizationIfEmailChanged(email: String?) {
        val authorizedEmail = _sessionAuthorizedEmail ?: return
        if (authorizedEmail != (email?.trim()?.lowercase() ?: "")) {
            clearCurrentSessionAuthorization()
        }
    }

    suspend fun start(login: String): PasswordResetChallenge {
        return _withClient { client -> client.startPasswordReset(login) }
    }

    suspend fun inspect(login: String): PasswordResetInspection {
        return _withClient { client -> client.inspectPasswordReset(login) }
    }

    suspend fun resend(challengeId: String): PasswordResetChallenge {
        return _withClient { client -> client.resendPasswordResetCode(challengeId) }
    }

    suspend fun verify(challengeId: String, code: String): String {
        return _withClient { client ->
            client.verifyPasswordResetCode(challengeId = challengeId, code = code)
        }
    }

    suspend fun complete(
        resetToken: String,
        newPassword: String,
    ) {
        return _withClient { client ->
            client.completePasswordReset(
                resetToken = resetToken,
                newPassword = newPassword,
            )
        }
    }

    suspend fun claimForCurrentSession(
        resetToken: String,
        email: String,
    ) {
        val tokenProvider =
            accessTokenProvider ?: throw IllegalStateException("当前页面没有可用的登录会话")
        val accessToken = tokenProvider()
        _withClient { client ->
            client.claimPasswordResetForSession(
                accessToken = accessToken,
                resetToken = resetToken,
            )
        }
        _sessionAuthorizedEmail = email.trim().lowercase()
    }

    private suspend fun <T> _withClient(action: suspend (client: AuthClient) -> T): T {
        val client = _authClientFactory(apiBaseUrl)
        try {
            return action(client)
        } finally {
            client.close()
        }
    }
}
