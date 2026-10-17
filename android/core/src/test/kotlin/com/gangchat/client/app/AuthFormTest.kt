package com.gangchat.client.app

import com.gangchat.client.auth.AuthException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of test/unit/app/auth_form_test.dart. */
class AuthFormTest {
    @Test
    fun `authRequestFromForm validates required login credentials`() {
        val result =
            authRequestFromForm(
                registering = false,
                login = " ",
                password = "",
            )

        assertNull(result.request)
        assertEquals("请输入账号和密码后继续", result.error)
    }

    @Test
    fun `authRequestFromForm localizes validation errors`() {
        val english =
            authRequestFromForm(
                registering = false,
                login = " ",
                password = "",
                language = "en",
            )
        val traditional =
            authRequestFromForm(
                registering = true,
                login = "a@example.test",
                password = "secret",
                language = "zh-Hant",
            )

        assertEquals("Enter your account and password to continue", english.error)
        assertEquals("使用者名稱不能為空", traditional.error)
    }

    @Test
    fun `authRequestFromForm builds trimmed login request`() {
        val result =
            authRequestFromForm(
                registering = false,
                login = " logan ",
                password = "secret",
            )

        assertNull(result.error)
        assertFalse(result.request!!.registering)
        assertEquals("logan", result.request?.login)
        assertEquals("secret", result.request?.password)
    }

    @Test
    fun `authRequestFromForm validates register fields`() {
        assertEquals(
            "登录用户名需为 3-32 位，只能包含英文字母、数字、下划线或连字符",
            authRequestFromForm(
                registering = true,
                username = "logan.test",
                login = "a@example.test",
                password = "secret",
                confirmPassword = "secret",
            ).error,
        )
        assertEquals(
            "请输入有效的邮箱地址",
            authRequestFromForm(
                registering = true,
                username = "logan",
                login = "invalid-email",
                password = "secret",
                confirmPassword = "secret",
            ).error,
        )
        assertEquals(
            "登录用户名不能为空",
            authRequestFromForm(
                registering = true,
                login = "a@example.test",
                password = "secret",
            ).error,
        )
        assertEquals(
            "两次输入的密码不一致",
            authRequestFromForm(
                registering = true,
                username = "logan",
                login = "a@example.test",
                password = "secret",
                confirmPassword = "different",
            ).error,
        )
    }

    @Test
    fun `registerEmailValidationError validates normalized email format`() {
        assertEquals("邮箱不能为空", registerEmailValidationError(" "))
        assertEquals("请输入有效的邮箱地址", registerEmailValidationError("logan.example.test"))
        assertNull(registerEmailValidationError(" logan@example.test "))
    }

    @Test
    fun `registerEmailValidationError treats Unicode spaces like Dart`() {
        // Dart's `\s` (JavaScript semantics) covers the ideographic and
        // no-break spaces that Java's `\s` does not.
        assertEquals("请输入有效的邮箱地址", registerEmailValidationError("lo　gan@example.test"))
        assertEquals("请输入有效的邮箱地址", registerEmailValidationError("logan@exa mple.test"))
        assertEquals("请输入有效的邮箱地址", registerEmailValidationError("a".repeat(250) + "@b.cd"))
    }

    @Test
    fun `authRequestFromForm builds register request`() {
        val result =
            authRequestFromForm(
                registering = true,
                username = " logan ",
                login = " a@example.test ",
                password = "secret",
                confirmPassword = "secret",
                emailVerificationToken = "verification-token",
            )

        assertNull(result.error)
        assertTrue(result.isValid)
        assertTrue(result.request!!.registering)
        assertEquals("logan", result.request?.username)
        assertEquals("a@example.test", result.request?.login)
        assertEquals("verification-token", result.request?.emailVerificationToken)
    }

    @Test
    fun `authRequestFromForm requires email verification before register`() {
        val result =
            authRequestFromForm(
                registering = true,
                username = "logan",
                login = "a@example.test",
                password = "secret",
                confirmPassword = "secret",
            )

        assertNull(result.request)
        assertEquals("请先验证邮箱", result.error)
    }

    @Test
    fun `auth submit state covers started invalid and generic failure`() {
        val started = authSubmitStarted()

        assertTrue(started.busy)
        assertNull(started.error)

        val invalid = authSubmitInvalid("Missing password")

        assertFalse(invalid.busy)
        assertEquals("Missing password", invalid.error)

        val failed = authSubmitFailed(IllegalStateException("offline"))

        assertFalse(failed.busy)
        assertEquals("无法连接服务器", failed.error)
    }

    @Test
    fun `auth submit failure gives a concise TLS recovery message`() {
        val failed =
            authSubmitFailed(
                SSLHandshakeException("Handshake error in client"),
            )

        assertEquals("无法建立安全连接，请检查网络、代理或系统时间后重试", failed.error)
    }

    @Test
    fun `auth submit failure localizes auth exception codes`() {
        val failed =
            authSubmitFailed(
                AuthException(
                    "Invalid credentials",
                    statusCode = 401,
                    code = "invalid_credentials",
                ),
            )
        val english =
            authSubmitFailed(
                AuthException(
                    "invalid credentials",
                    statusCode = 401,
                    code = "unauthorized",
                ),
                language = "en",
            )
        val traditional =
            authSubmitFailed(
                AuthException(
                    "too many failed login attempts",
                    statusCode = 429,
                    code = "rate_limited",
                ),
                language = "zh-Hant",
            )

        assertFalse(failed.busy)
        assertEquals("账号或密码不正确", failed.error)
        assertEquals("Incorrect account or password", english.error)
        assertEquals("登入嘗試次數過多，請稍後再試", traditional.error)
    }

    @Test
    fun `request failures keep the status code and connection prefixes lose their colon`() {
        val failure = AuthException("boom", statusCode = 502, code = "request_failed")

        assertEquals("请求失败 (502)", authSubmitFailureMessage(failure))
        assertEquals("Request failed (502)", authSubmitFailureMessage(failure, language = "en"))
        assertEquals(
            "Unable to connect to the server",
            authSubmitFailureMessage(IllegalStateException("offline"), language = "en"),
        )
        assertEquals(
            "無法連線伺服器",
            authSubmitFailureMessage(IllegalStateException("offline"), language = "zh-Hant"),
        )
    }
}
