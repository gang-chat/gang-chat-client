package com.gangchat.client.auth

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.Uri
import com.gangchat.client.foundation.delayed
import com.gangchat.client.foundation.http.HttpClient
import com.gangchat.client.foundation.http.HttpResponse
import com.gangchat.client.foundation.http.delete
import com.gangchat.client.foundation.http.get
import com.gangchat.client.foundation.http.post
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.UserSession
import com.gangchat.client.protocol.decodeJsonBody
import com.gangchat.client.protocol.dartIntTryParse
import com.gangchat.client.protocol.encodeJsonBody
import com.gangchat.client.protocol.jsonAcceptHeader
import com.gangchat.client.protocol.jsonUtf8ContentType
import com.gangchat.client.protocol.localizedServerErrorMessage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Open like the Dart class, which tests subclass to fake single requests. */
open class AuthClient(
    val baseUrl: String,
    val userAgent: String? = null,
    httpClient: HttpClient? = null,
    environmentProxyClientFactory: AuthHttpClientFactory? = null,
    val handshakeRetryDelays: List<Duration> =
        listOf(
            250.milliseconds,
            750.milliseconds,
        ),
) {
    private val _httpClient: HttpClient = httpClient ?: HttpClient()
    private val _environmentProxyClientFactory: AuthHttpClientFactory =
        environmentProxyClientFactory ?: ::createEnvironmentProxyAuthHttpClient
    private var _environmentProxyClient: HttpClient? = null

    open suspend fun register(
        username: String,
        email: String,
        password: String,
        emailVerificationToken: String,
    ): AuthSession =
        _postAuth(
            "/auth/register",
            linkedMapOf(
                "username" to username,
                "email" to email,
                "password" to password,
                "email_verification_token" to emailVerificationToken,
            ),
        )

    open suspend fun login(login: String, password: String): AuthSession =
        _postAuth("/auth/login", linkedMapOf("login" to login, "password" to password))

    open suspend fun isUsernameAvailable(username: String): Boolean {
        val uri =
            _uri(
                "/auth/username-availability",
            ).replace(queryParameters = mapOf("username" to username.trim()))
        val response =
            _sendWithHandshakeRetry { client ->
                client.get(uri, headers = _headers())
            }
        _throwIfFailed(response)
        val body = _jsonMap(decodeJsonBody(response)!!)
        return body["available"] as Boolean? ?: false
    }

    open suspend fun isEmailAvailable(email: String): Boolean {
        val uri =
            _uri(
                "/auth/email-availability",
            ).replace(queryParameters = mapOf("email" to email.trim()))
        val response =
            _sendWithHandshakeRetry { client ->
                client.get(uri, headers = _headers())
            }
        _throwIfFailed(response)
        val body = _jsonMap(decodeJsonBody(response)!!)
        return body["available"] as Boolean? ?: false
    }

    open suspend fun inspectEmailVerification(
        email: String,
        accessToken: String? = null,
    ): EmailVerificationInspection {
        val response =
            _postAuthWithHandshakeRetry(
                if (accessToken == null) {
                    "/auth/email-verification/inspect"
                } else {
                    "/users/me/email-verification/inspect"
                },
                mapOf("email" to email.trim()),
                accessToken = accessToken,
            )
        _throwIfFailed(response)
        return EmailVerificationInspection.fromJson(_jsonMap(decodeJsonBody(response)!!))
    }

    open suspend fun startEmailVerification(
        email: String,
        accessToken: String? = null,
    ): EmailVerificationChallenge {
        val response =
            _postAuthWithHandshakeRetry(
                if (accessToken == null) {
                    "/auth/email-verification/start"
                } else {
                    "/users/me/email-verification/start"
                },
                mapOf("email" to email.trim()),
                accessToken = accessToken,
            )
        _throwIfFailed(response)
        return EmailVerificationChallenge.fromJson(_jsonMap(decodeJsonBody(response)!!))
    }

    open suspend fun resendEmailVerificationCode(challengeId: String): EmailVerificationChallenge {
        val response =
            _postAuthWithHandshakeRetry(
                "/auth/email-verification/resend",
                mapOf("challenge_id" to challengeId),
            )
        _throwIfFailed(response)
        return EmailVerificationChallenge.fromJson(_jsonMap(decodeJsonBody(response)!!))
    }

    open suspend fun verifyEmailVerificationCode(
        challengeId: String,
        code: String,
    ): String {
        val response =
            _postAuthWithHandshakeRetry(
                "/auth/email-verification/verify",
                linkedMapOf("challenge_id" to challengeId, "code" to code.trim()),
            )
        _throwIfFailed(response)
        val body = _jsonMap(decodeJsonBody(response)!!)
        return body["verification_token"] as String
    }

    open suspend fun refresh(refreshToken: String): AuthSession =
        _postAuth("/auth/refresh", mapOf("refresh_token" to refreshToken))

    open suspend fun logout(accessToken: String? = null, refreshToken: String? = null) {
        val response =
            _httpClient.post(
                _uri("/auth/logout"),
                headers = _headers(accessToken = accessToken),
                body = encodeJsonBody(mapOf("refresh_token" to refreshToken)),
            )
        _throwIfFailed(response)
    }

    open suspend fun me(accessToken: String): CurrentUser {
        val response =
            _httpClient.get(
                _uri("/me"),
                headers = _headers(accessToken = accessToken),
            )
        _throwIfFailed(response)
        return CurrentUser.fromJson(_jsonMap(decodeJsonBody(response)!!))
    }

    open suspend fun listSessions(accessToken: String): List<UserSession> {
        val response =
            _httpClient.get(
                _uri("/auth/sessions"),
                headers = _headers(accessToken = accessToken),
            )
        _throwIfFailed(response)
        val decoded = decodeJsonBody(response) as List<*>
        return decoded.map { UserSession.fromJson(_jsonMap(it)) }
    }

    open suspend fun revokeSession(
        accessToken: String,
        sessionId: String,
    ) {
        val response =
            _httpClient.delete(
                _uri("/auth/sessions/$sessionId"),
                headers = _headers(accessToken = accessToken),
            )
        _throwIfFailed(response)
    }

    open suspend fun changePassword(
        accessToken: String,
        currentPassword: String,
        newPassword: String,
        revokeOtherSessions: Boolean = true,
    ) {
        val response =
            _httpClient.post(
                _uri("/auth/password"),
                headers = _headers(accessToken = accessToken),
                body =
                    encodeJsonBody(
                        linkedMapOf(
                            "current_password" to currentPassword,
                            "new_password" to newPassword,
                            "revoke_other_sessions" to revokeOtherSessions,
                        ),
                    ),
            )
        _throwIfFailed(response)
    }

    open suspend fun startPasswordReset(login: String): PasswordResetChallenge {
        val response =
            _postAuthWithHandshakeRetry(
                "/auth/password-reset/start",
                mapOf("login" to login.trim()),
            )
        _throwIfFailed(response)
        return PasswordResetChallenge.fromJson(_jsonMap(decodeJsonBody(response)!!))
    }

    open suspend fun inspectPasswordReset(login: String): PasswordResetInspection {
        val response =
            _postAuthWithHandshakeRetry(
                "/auth/password-reset/inspect",
                mapOf("login" to login.trim()),
            )
        _throwIfFailed(response)
        return PasswordResetInspection.fromJson(_jsonMap(decodeJsonBody(response)!!))
    }

    open suspend fun resendPasswordResetCode(challengeId: String): PasswordResetChallenge {
        val response =
            _postAuthWithHandshakeRetry(
                "/auth/password-reset/resend",
                mapOf("challenge_id" to challengeId),
            )
        _throwIfFailed(response)
        return PasswordResetChallenge.fromJson(_jsonMap(decodeJsonBody(response)!!))
    }

    open suspend fun verifyPasswordResetCode(
        challengeId: String,
        code: String,
    ): String {
        val response =
            _postAuthWithHandshakeRetry(
                "/auth/password-reset/verify",
                linkedMapOf("challenge_id" to challengeId, "code" to code.trim()),
            )
        _throwIfFailed(response)
        val body = _jsonMap(decodeJsonBody(response)!!)
        return body["reset_token"] as String
    }

    open suspend fun completePasswordReset(
        resetToken: String,
        newPassword: String,
    ) {
        val response =
            _postAuthWithHandshakeRetry(
                "/auth/password-reset/complete",
                linkedMapOf("reset_token" to resetToken, "new_password" to newPassword),
            )
        _throwIfFailed(response)
    }

    open suspend fun claimPasswordResetForSession(
        accessToken: String,
        resetToken: String,
    ) {
        val response =
            _httpClient.post(
                _uri("/auth/password-reset/claim"),
                headers = _headers(accessToken = accessToken),
                body = encodeJsonBody(mapOf("reset_token" to resetToken)),
            )
        _throwIfFailed(response)
    }

    private suspend fun _postAuth(path: String, body: Map<String, Any?>): AuthSession {
        val response = _postAuthWithHandshakeRetry(path, body)
        _throwIfFailed(response)
        return AuthSession.fromJson(_jsonMap(decodeJsonBody(response)!!))
    }

    private suspend fun _postAuthWithHandshakeRetry(
        path: String,
        body: Map<String, Any?>,
        accessToken: String? = null,
    ): HttpResponse =
        _sendWithHandshakeRetry { client ->
            client.post(
                _uri(path),
                headers = _headers(accessToken = accessToken),
                body = encodeJsonBody(body),
            )
        }

    private suspend fun _sendWithHandshakeRetry(
        send: suspend (client: HttpClient) -> HttpResponse,
    ): HttpResponse {
        var client = _httpClient
        var attempt = 0
        while (true) {
            try {
                return send(client)
            } catch (error: Exception) {
                if (!isTlsHandshakeFailure(error) ||
                    attempt >= handshakeRetryDelays.size
                ) {
                    throw error
                }

                delayed(handshakeRetryDelays[attempt])
                if (attempt == handshakeRetryDelays.size - 1) {
                    client = _environmentProxyClient
                        ?: _environmentProxyClientFactory().also { _environmentProxyClient = it }
                }
            }
            attempt += 1
        }
    }

    private fun _throwIfFailed(response: HttpResponse) {
        if (response.statusCode >= 200 && response.statusCode < 300) return
        throw AuthException.fromResponse(response)
    }

    private fun _uri(path: String): Uri {
        val normalizedBase =
            if (baseUrl.endsWith("/")) baseUrl.substring(0, baseUrl.length - 1) else baseUrl
        return Uri.parse("$normalizedBase$path")
    }

    private fun _headers(accessToken: String? = null): Map<String, String> {
        val normalizedUserAgent = userAgent?.trim()
        return buildMap {
            put("accept", jsonAcceptHeader)
            put("content-type", jsonUtf8ContentType)
            if (normalizedUserAgent != null && normalizedUserAgent.isNotEmpty()) {
                put("user-agent", normalizedUserAgent)
            }
            if (accessToken != null) put("authorization", "Bearer $accessToken")
        }
    }

    open fun close() {
        _httpClient.close()
        val environmentProxyClient = _environmentProxyClient
        if (environmentProxyClient != null &&
            environmentProxyClient !== _httpClient
        ) {
            environmentProxyClient.close()
        }
    }
}

class PasswordResetChallenge(
    val id: String,
    val maskedEmail: String,
    val retryAfterSeconds: Int,
) {
    companion object {
        fun fromJson(json: JsonMap): PasswordResetChallenge =
            PasswordResetChallenge(
                id = json["challenge_id"] as String,
                maskedEmail = json["masked_email"] as String,
                retryAfterSeconds = _retryAfterSeconds(json["retry_after"]),
            )
    }
}

class EmailVerificationChallenge(
    val id: String,
    val retryAfterSeconds: Int,
) {
    companion object {
        fun fromJson(json: JsonMap): EmailVerificationChallenge =
            EmailVerificationChallenge(
                id = json["challenge_id"] as String,
                retryAfterSeconds = _retryAfterSeconds(json["retry_after"]),
            )
    }
}

class EmailVerificationInspection(
    val canSend: Boolean,
    val retryAfterSeconds: Int,
    val challengeId: String? = null,
) {
    val reusableChallenge: EmailVerificationChallenge?
        get() {
            val id = challengeId
            if (id == null || id.isEmpty()) return null
            return EmailVerificationChallenge(
                id = id,
                retryAfterSeconds = retryAfterSeconds,
            )
        }

    companion object {
        fun fromJson(json: JsonMap): EmailVerificationInspection =
            EmailVerificationInspection(
                canSend = json["can_send"] as Boolean? ?: false,
                challengeId = json["challenge_id"] as String?,
                retryAfterSeconds = _retryAfterSeconds(json["retry_after"]),
            )
    }
}

class PasswordResetInspection(
    val canSend: Boolean,
    val maskedEmail: String,
    val retryAfterSeconds: Int,
    val challengeId: String? = null,
) {
    val reusableChallenge: PasswordResetChallenge?
        get() {
            val id = challengeId
            if (id == null || id.isEmpty()) return null
            return PasswordResetChallenge(
                id = id,
                maskedEmail = maskedEmail,
                retryAfterSeconds = retryAfterSeconds,
            )
        }

    companion object {
        fun fromJson(json: JsonMap): PasswordResetInspection =
            PasswordResetInspection(
                canSend = json["can_send"] as Boolean? ?: false,
                challengeId = json["challenge_id"] as String?,
                maskedEmail = json["masked_email"] as String,
                retryAfterSeconds = _retryAfterSeconds(json["retry_after"]),
            )
    }
}

class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: DateTime,
    val user: CurrentUser,
) {
    fun isAccessTokenExpiringSoon(threshold: Duration = 60.seconds): Boolean =
        DateTime.now().add(threshold).isAfter(accessTokenExpiresAt)

    companion object {
        fun fromJson(json: JsonMap): AuthSession =
            AuthSession(
                accessToken = json["access_token"] as String,
                refreshToken = json["refresh_token"] as String,
                accessTokenExpiresAt = _parseAccessTokenExpiry(json),
                user = CurrentUser.fromJson(_jsonMap(json["user"])),
            )

        /**
         * Resolves the access-token expiry from whichever field the server sent.
         * Prefers the absolute `access_token_expires_at`; otherwise derives it from
         * a relative `expires_in` (accepting int, double, or numeric string). Falls
         * back to a conservative default when neither is present or parseable, so a
         * malformed/partial token response can't crash the login flow.
         */
        private fun _parseAccessTokenExpiry(json: JsonMap): DateTime {
            val fallback = 15.minutes
            val expiresAtJson = json["access_token_expires_at"] as String?
            if (expiresAtJson != null) {
                val parsed = DateTime.tryParse(expiresAtJson)
                if (parsed != null) return parsed
            }
            val expiresIn = json["expires_in"]
            var seconds: Long? = null
            if (expiresIn is Long || expiresIn is Int) {
                seconds = (expiresIn as Number).toLong()
            } else if (expiresIn is Double) {
                seconds = expiresIn.toLong()
            } else if (expiresIn is String) {
                seconds = dartIntTryParse(expiresIn)
            }
            return DateTime.now().add(
                if (seconds != null && seconds > 0) seconds.seconds else fallback,
            )
        }
    }
}

class AuthException(
    override val message: String,
    val statusCode: Int,
    val code: String,
) : Exception(message) {
    val isUnauthorized: Boolean get() = statusCode == 401

    override fun toString(): String = message

    companion object {
        fun fromResponse(response: HttpResponse): AuthException {
            try {
                val decoded = _jsonMap(decodeJsonBody(response))
                @Suppress("UNCHECKED_CAST")
                val error = decoded["error"] as Map<String, Any?>?
                val message = error?.get("message") as String?
                val code = error?.get("code") as String?
                if (message != null && message.isNotEmpty()) {
                    return AuthException(
                        localizedServerErrorMessage(
                            code = code ?: "request_failed",
                            statusCode = response.statusCode,
                            message = message,
                        ),
                        statusCode = response.statusCode,
                        code = code ?: "request_failed",
                    )
                }
            } catch (_: Exception) {
                // Fall through to the status-based message.
            }
            return AuthException(
                "请求失败 (${response.statusCode})",
                statusCode = response.statusCode,
                code = "request_failed",
            )
        }
    }
}

/** The `retry_after` switch shared by the challenge/inspection models. */
private fun _retryAfterSeconds(retryAfter: Any?): Int =
    when (retryAfter) {
        is Long -> retryAfter.toInt()
        is Int -> retryAfter
        is Number -> retryAfter.toDouble().toInt()
        is String -> dartIntTryParse(retryAfter)?.toInt() ?: 0
        else -> 0
    }

@Suppress("UNCHECKED_CAST")
private fun _jsonMap(value: Any?): JsonMap = value as Map<String, Any?>
