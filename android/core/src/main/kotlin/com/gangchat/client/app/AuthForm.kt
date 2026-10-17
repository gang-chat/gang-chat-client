package com.gangchat.client.app

import com.gangchat.client.auth.AuthException
import com.gangchat.client.auth.isTlsHandshakeFailure

// PORT: Dart's `\s` follows JavaScript (Unicode spaces such as U+3000 and
// U+00A0 included); Java's `\s` is ASCII only, so the class is spelled out.
private val _registerEmailPattern =
    Regex(
        "^[^\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF@]+" +
            "@[^\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF@]+" +
            "\\.[^\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF@]+$",
    )

fun registerEmailValidationError(email: String): String? {
    val normalized = email.trim()
    if (normalized.isEmpty()) return "邮箱不能为空"
    if (normalized.length > 254 || !_registerEmailPattern.containsMatchIn(normalized)) {
        return "请输入有效的邮箱地址"
    }
    return null
}

class AuthFormResult private constructor(
    val request: AuthRequest? = null,
    val error: String? = null,
) {
    val isValid: Boolean get() = request != null

    companion object {
        /** Dart `const AuthFormResult.valid(request)`. */
        fun valid(request: AuthRequest): AuthFormResult = AuthFormResult(request = request)

        /** Dart `const AuthFormResult.invalid(error)`. */
        fun invalid(error: String): AuthFormResult = AuthFormResult(error = error)
    }
}

class AuthSubmitState(
    val busy: Boolean = false,
    val error: String? = null,
)

fun authRequestFromForm(
    registering: Boolean,
    login: String,
    password: String,
    username: String = "",
    confirmPassword: String = "",
    emailVerificationToken: String = "",
    language: String = defaultLanguagePreference,
): AuthFormResult {
    val copy = authFormCopy(language)
    val normalizedLogin = login.trim()
    if (normalizedLogin.isEmpty() || password.isEmpty()) {
        return AuthFormResult.invalid(copy.missingCredentials)
    }

    if (!registering) {
        return AuthFormResult.valid(
            AuthRequest.login(login = normalizedLogin, password = password),
        )
    }

    val normalizedUsername = username.trim()
    if (normalizedUsername.isEmpty()) {
        return AuthFormResult.invalid(copy.missingUsername)
    }
    val usernameError = loginUsernameValidationError(normalizedUsername)
    if (usernameError != null) {
        return AuthFormResult.invalid(usernameError)
    }
    val emailError = registerEmailValidationError(normalizedLogin)
    if (emailError != null) {
        return AuthFormResult.invalid(emailError)
    }
    if (password != confirmPassword) {
        return AuthFormResult.invalid(copy.passwordMismatch)
    }
    if (emailVerificationToken.trim().isEmpty()) {
        return AuthFormResult.invalid(copy.emailVerificationRequired)
    }

    return AuthFormResult.valid(
        AuthRequest.register(
            username = normalizedUsername,
            login = normalizedLogin,
            password = password,
            emailVerificationToken = emailVerificationToken,
        ),
    )
}

fun authSubmitStarted(): AuthSubmitState {
    return AuthSubmitState(busy = true)
}

fun authSubmitInvalid(error: String?): AuthSubmitState {
    return AuthSubmitState(error = error)
}

fun authSubmitFailed(
    failure: Any,
    language: String = defaultLanguagePreference,
): AuthSubmitState {
    return AuthSubmitState(
        error = authSubmitFailureMessage(failure, language = language),
    )
}

fun authSubmitFailureMessage(
    failure: Any,
    language: String = defaultLanguagePreference,
): String {
    val copy = authFormCopy(language)
    if (failure is AuthException) return copy.authException(failure)
    if (isTlsHandshakeFailure(failure)) return copy.secureConnectionFailed
    return copy.connectionFailed(failure)
}

fun authFormCopy(language: String): AuthFormCopy {
    return when (normalizeLanguagePreference(language)) {
        "zh-Hant" -> AuthFormCopy.zhHant
        "en" -> AuthFormCopy.en
        else -> AuthFormCopy.zhHans
    }
}

class AuthFormCopy private constructor(
    val missingCredentials: String,
    val missingUsername: String,
    val passwordMismatch: String,
    val emailVerificationRequired: String,
    val connectionFailedPrefix: String,
    val secureConnectionFailed: String,
    val invalidCredentials: String,
    val rateLimited: String,
    val conflict: String,
    val badRequest: String,
    val serverError: String,
    val requestFailedPrefix: String,
    val fallback: String,
) {
    fun connectionFailed(@Suppress("UNUSED_PARAMETER") failure: Any): String {
        return _trailingColon.replaceFirst(connectionFailedPrefix, "")
    }

    fun authException(failure: AuthException): String {
        return when (failure.code.trim()) {
            "unauthorized", "invalid_credentials" -> invalidCredentials
            "rate_limited" -> rateLimited
            "conflict" -> conflict
            "bad_request", "validation_failed" -> badRequest
            "email_verification_required" -> emailVerificationRequired
            "internal_error" -> serverError
            "request_failed" -> requestFailed(failure.statusCode)
            else -> fallback
        }
    }

    fun requestFailed(statusCode: Int): String {
        return "$requestFailedPrefix ($statusCode)"
    }

    companion object {
        private val _trailingColon = Regex("[:：]\\s*$")

        /** Dart `const AuthFormCopy.zhHans()`. */
        val zhHans: AuthFormCopy =
            AuthFormCopy(
                missingCredentials = "请输入账号和密码后继续",
                missingUsername = "登录用户名不能为空",
                passwordMismatch = "两次输入的密码不一致",
                emailVerificationRequired = "请先验证邮箱",
                connectionFailedPrefix = "无法连接服务器：",
                secureConnectionFailed = "无法建立安全连接，请检查网络、代理或系统时间后重试",
                invalidCredentials = "账号或密码不正确",
                rateLimited = "登录尝试次数过多，请稍后再试",
                conflict = "登录用户名或邮箱已被占用",
                badRequest = "请检查账号信息后再试",
                serverError = "服务器暂时无法完成请求，请稍后再试",
                requestFailedPrefix = "请求失败",
                fallback = "认证请求失败，请稍后再试",
            )

        /** Dart `const AuthFormCopy.zhHant()`. */
        val zhHant: AuthFormCopy =
            AuthFormCopy(
                missingCredentials = "請輸入帳號和密碼後繼續",
                missingUsername = "使用者名稱不能為空",
                passwordMismatch = "兩次輸入的密碼不一致",
                emailVerificationRequired = "請先驗證電子郵件",
                connectionFailedPrefix = "無法連線伺服器：",
                secureConnectionFailed = "無法建立安全連線，請檢查網路、代理或系統時間後重試",
                invalidCredentials = "帳號或密碼不正確",
                rateLimited = "登入嘗試次數過多，請稍後再試",
                conflict = "使用者名稱或電子郵件已被使用",
                badRequest = "請檢查帳號資訊後再試",
                serverError = "伺服器暫時無法完成請求，請稍後再試",
                requestFailedPrefix = "請求失敗",
                fallback = "認證請求失敗，請稍後再試",
            )

        /** Dart `const AuthFormCopy.en()`. */
        val en: AuthFormCopy =
            AuthFormCopy(
                missingCredentials = "Enter your account and password to continue",
                missingUsername = "Username is required",
                passwordMismatch = "The two passwords do not match",
                emailVerificationRequired = "Verify your email first",
                connectionFailedPrefix = "Unable to connect to the server: ",
                secureConnectionFailed =
                    "Could not establish a secure connection. Check your network, proxy, or system time and try again",
                invalidCredentials = "Incorrect account or password",
                rateLimited = "Too many login attempts, try again later",
                conflict = "That username or email is already taken",
                badRequest = "Check your account details and try again",
                serverError =
                    "The server could not complete the request, try again later",
                requestFailedPrefix = "Request failed",
                fallback = "Authentication failed, try again later",
            )
    }
}
