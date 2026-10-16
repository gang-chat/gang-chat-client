package com.gangchat.client.app

import com.gangchat.client.auth.AuthClient
import com.gangchat.client.auth.EmailVerificationChallenge
import com.gangchat.client.auth.EmailVerificationInspection

typealias EmailVerificationAuthClientFactory = (baseUrl: String) -> AuthClient
typealias EmailVerificationAccessTokenProvider = suspend () -> String

class EmailVerificationController(
    val apiBaseUrl: String,
    authClientFactory: EmailVerificationAuthClientFactory? = null,
    val accessTokenProvider: EmailVerificationAccessTokenProvider? = null,
) {
    private val _authClientFactory: EmailVerificationAuthClientFactory =
        authClientFactory ?: { baseUrl -> AuthClient(baseUrl = baseUrl) }

    suspend fun isEmailAvailable(email: String): Boolean {
        return _withClient { client -> client.isEmailAvailable(email) }
    }

    suspend fun inspect(email: String): EmailVerificationInspection {
        return _withClient { client -> client.inspectEmailVerification(email) }
    }

    suspend fun start(email: String): EmailVerificationChallenge {
        return _withClient { client -> client.startEmailVerification(email) }
    }

    suspend fun inspectOrStart(email: String): EmailVerificationChallenge {
        val inspection = inspect(email)
        val reusable = inspection.reusableChallenge
        if (reusable != null) return reusable
        // Start is idempotent during the server cooldown: it returns the active
        // challenge without sending another email. This also supports servers
        // whose inspection response omits the reusable challenge id.
        return start(email)
    }

    suspend fun inspectOrStartForCurrentUser(email: String): EmailVerificationChallenge {
        val provider = accessTokenProvider ?: return inspectOrStart(email)
        val accessToken = provider()
        val inspection =
            _withClient { client ->
                client.inspectEmailVerification(email, accessToken = accessToken)
            }
        val reusable = inspection.reusableChallenge
        if (reusable != null) return reusable
        return _withClient { client ->
            client.startEmailVerification(email, accessToken = accessToken)
        }
    }

    suspend fun resend(challengeId: String): EmailVerificationChallenge {
        return _withClient { client -> client.resendEmailVerificationCode(challengeId) }
    }

    suspend fun verify(challengeId: String, code: String): String {
        return _withClient { client ->
            client.verifyEmailVerificationCode(
                challengeId = challengeId,
                code = code,
            )
        }
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
