package com.gangchat.client.app

import com.gangchat.client.auth.AuthClient
import com.gangchat.client.auth.AuthException
import com.gangchat.client.auth.AuthSession
import com.gangchat.client.auth.TokenStore
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.Timer
import com.gangchat.client.foundation.failedFuture
import com.gangchat.client.foundation.future
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Deferred

typealias AuthClientFactory = (baseUrl: String) -> AuthClient
typealias AuthSessionListener = () -> Unit

class AuthRequest private constructor(
    val registering: Boolean,
    val username: String?,
    val emailVerificationToken: String?,
    val login: String,
    val password: String,
) {
    companion object {
        /** Dart `const AuthRequest.login(...)`. */
        fun login(login: String, password: String): AuthRequest =
            AuthRequest(
                registering = false,
                username = null,
                emailVerificationToken = null,
                login = login,
                password = password,
            )

        /** Dart `const AuthRequest.register(...)`. */
        fun register(
            username: String?,
            login: String,
            password: String,
            emailVerificationToken: String?,
        ): AuthRequest =
            AuthRequest(
                registering = true,
                username = username,
                emailVerificationToken = emailVerificationToken,
                login = login,
                password = password,
            )
    }
}

enum class AuthRestoreStatus { missingRefreshToken, restored, refreshFailed }

class AuthRestoreResult private constructor(
    val status: AuthRestoreStatus,
    val error: Any? = null,
) {
    val hasSession: Boolean get() = status == AuthRestoreStatus.restored

    companion object {
        fun missingRefreshToken(): AuthRestoreResult = AuthRestoreResult(AuthRestoreStatus.missingRefreshToken)

        fun restored(): AuthRestoreResult = AuthRestoreResult(AuthRestoreStatus.restored)

        fun refreshFailed(error: Any): AuthRestoreResult =
            AuthRestoreResult(AuthRestoreStatus.refreshFailed, error = error)
    }
}

class AuthSessionController(
    tokenStore: TokenStore,
    val apiBaseUrl: String,
    clientUserAgent: String? = null,
    authClientFactory: AuthClientFactory? = null,
) {
    private val _tokenStore: TokenStore = tokenStore
    private val _authClientFactory: AuthClientFactory =
        authClientFactory
            ?: { baseUrl -> AuthClient(baseUrl = baseUrl, userAgent = clientUserAgent) }

    private var _session: AuthSession? = null
    private var _refreshTimer: Timer? = null
    private var _refreshInFlight: Deferred<String>? = null
    private val _listeners: MutableList<AuthSessionListener> = mutableListOf()

    val session: AuthSession? get() = _session

    fun addListener(listener: AuthSessionListener) {
        _listeners.add(listener)
    }

    fun removeListener(listener: AuthSessionListener) {
        _listeners.remove(listener)
    }

    suspend fun restoreSession(): AuthRestoreResult {
        val refreshToken: String?
        try {
            refreshToken = _tokenStore.readRefreshToken()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _clearSession()
            return AuthRestoreResult.refreshFailed(e)
        }
        if (refreshToken == null || refreshToken.isEmpty()) {
            _clearSession()
            return AuthRestoreResult.missingRefreshToken()
        }

        val client = _authClientFactory(apiBaseUrl)
        try {
            val session = client.refresh(refreshToken)
            _setSession(session)
            _scheduleTokenRefresh(session)
            _writeRefreshTokenBestEffort(session.refreshToken)
            return AuthRestoreResult.restored()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (_isTerminalRefreshFailure(e)) {
                _tokenStore.clearRefreshToken()
            }
            _clearSession()
            return AuthRestoreResult.refreshFailed(e)
        } finally {
            client.close()
        }
    }

    suspend fun authenticate(request: AuthRequest): AuthSession {
        val client = _authClientFactory(apiBaseUrl)
        try {
            if (request.registering) {
                return client.register(
                    username = request.username!!,
                    email = request.login,
                    password = request.password,
                    emailVerificationToken = request.emailVerificationToken!!,
                )
            }
            return client.login(
                login = request.login,
                password = request.password,
            )
        } finally {
            client.close()
        }
    }

    suspend fun isUsernameAvailable(username: String): Boolean {
        val client = _authClientFactory(apiBaseUrl)
        try {
            return client.isUsernameAvailable(username)
        } finally {
            client.close()
        }
    }

    suspend fun isEmailAvailable(email: String): Boolean {
        val client = _authClientFactory(apiBaseUrl)
        try {
            return client.isEmailAvailable(email)
        } finally {
            client.close()
        }
    }

    suspend fun acceptSession(session: AuthSession) {
        _tokenStore.writeRefreshToken(session.refreshToken)
        _tokenStore.writeApiBaseUrl(apiBaseUrl)
        _setSession(session)
        _scheduleTokenRefresh(session)
    }

    fun beginLogout(): AuthSession? {
        _refreshTimer?.cancel()
        _refreshTimer = null
        _refreshInFlight = null
        val session = _session
        _clearSession()
        return session
    }

    suspend fun finishLogout(session: AuthSession?) {
        _tokenStore.clearRefreshToken()
        if (session == null) return

        val client = _authClientFactory(apiBaseUrl)
        try {
            client.logout(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Local logout should win even if the server revoke request fails.
        } finally {
            client.close()
        }
    }

    suspend fun accessToken(forceRefresh: Boolean = false): String {
        val activeRefresh = _refreshInFlight
        if (activeRefresh != null) return activeRefresh.await()

        val session = _session ?: throw IllegalStateException("尚未登录")
        if (forceRefresh) return _refreshAccessToken().await()
        if (!session.isAccessTokenExpiringSoon()) {
            return session.accessToken
        }
        return _refreshAccessToken().await()
    }

    /**
     * Starts (or joins) the shared refresh. Like the Dart future, the returned
     * handle may fail; callers that do not await it (the refresh timers) drop
     * the error, which is Dart's `catchError((_) => '')`.
     */
    private fun _refreshAccessToken(): Deferred<String> {
        val activeRefresh = _refreshInFlight
        if (activeRefresh != null) return activeRefresh

        val session = _session ?: return failedFuture(IllegalStateException("尚未登录"))

        val refresh = future { _refreshSession(session.refreshToken) }
        _refreshInFlight = refresh
        refresh.invokeOnCompletion { _refreshInFlight = null }
        return refresh
    }

    private suspend fun _refreshSession(refreshToken: String): String {
        val client = _authClientFactory(apiBaseUrl)
        try {
            val session = client.refresh(refreshToken)
            _setSession(session)
            _scheduleTokenRefresh(session)
            _writeRefreshTokenBestEffort(session.refreshToken)
            return session.accessToken
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (_isTerminalRefreshFailure(e)) {
                _tokenStore.clearRefreshToken()
                _clearSession()
            } else {
                _scheduleRefreshRetry(refreshToken)
            }
            throw e
        } finally {
            client.close()
        }
    }

    private fun _scheduleTokenRefresh(session: AuthSession) {
        _refreshTimer?.cancel()
        val delay =
            session.accessTokenExpiresAt
                .subtract(60.seconds)
                .difference(DateTime.now())
        _refreshTimer =
            Timer(if (delay.isNegative()) Duration.ZERO else delay) {
                _refreshAccessToken()
            }
    }

    private fun _scheduleRefreshRetry(refreshToken: String) {
        val session = _session
        if (session == null || session.refreshToken != refreshToken) return
        _refreshTimer?.cancel()
        _refreshTimer =
            Timer(_refreshRetryDelay) {
                _refreshAccessToken()
            }
    }

    private fun _isTerminalRefreshFailure(error: Any): Boolean {
        return error is AuthException && error.isUnauthorized
    }

    private suspend fun _writeRefreshTokenBestEffort(refreshToken: String) {
        try {
            _tokenStore.writeRefreshToken(refreshToken)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Keep the in-memory rotated token. A later refresh can persist it again.
        }
    }

    private fun _setSession(session: AuthSession) {
        _session = session
        _notifyListeners()
    }

    private fun _clearSession() {
        if (_session == null) return
        _session = null
        _notifyListeners()
    }

    private fun _notifyListeners() {
        for (listener in _listeners.toList()) {
            listener()
        }
    }

    fun dispose() {
        _refreshTimer?.cancel()
        _listeners.clear()
    }

    private companion object {
        val _refreshRetryDelay: Duration = 30.seconds
    }
}
