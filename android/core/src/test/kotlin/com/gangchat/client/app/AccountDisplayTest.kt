package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.UserSession
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDisplayTest {
    @Test
    fun `normalizeGender keeps known values and falls back to secret`() {
        assertEquals("male", normalizeGender("male"))
        assertEquals("female", normalizeGender("female"))
        assertEquals("secret", normalizeGender("secret"))
        assertEquals("secret", normalizeGender("unknown"))
    }

    @Test
    fun `formatDateTime uses a compact local timestamp and handles null`() {
        assertEquals("未知", formatDateTime(null))
        assertEquals("2026-06-04 09:05", formatDateTime(DateTime(2026, 6, 4, 9, 5)))
    }

    @Test
    fun `username edit helper respects can-change time`() {
        val now = DateTime(2026, 6, 4, 12)
        val editable = _user(canChangeUsernameAt = now)
        val locked =
            _user(
                canChangeUsernameAt = now.add(2.hours),
            )

        assertTrue(canEditUsername(editable, now = now))
        assertTrue(usernameHelperText(editable, now = now).contains("一天只能修改一次"))
        assertFalse(canEditUsername(locked, now = now))
        assertTrue(usernameHelperText(locked, now = now).contains("下次可修改时间"))
    }

    @Test
    fun `avatarUploadFilename sanitizes basename and appends timestamp`() {
        val now = DateTime.fromMillisecondsSinceEpoch(1234567890)

        assertEquals(
            "My-Avatar-1234567890.png",
            avatarUploadFilename("/tmp/My Avatar.jpg", now = now),
        )
        assertEquals("avatar-1234567890.png", avatarUploadFilename(" .jpg ", now = now))
    }

    @Test
    fun `account avatar preview helpers handle pending and preset states`() {
        assertEquals(
            "/pending.png",
            accountAvatarPreviewPath(
                clearUploadedAvatar = false,
                pendingAvatarUrl = "/pending.png",
                currentAvatarUrl = "/current.png",
            ),
        )
        assertEquals(
            "/current.png",
            accountAvatarPreviewPath(
                clearUploadedAvatar = false,
                pendingAvatarUrl = null,
                currentAvatarUrl = "/current.png",
            ),
        )
        assertNull(
            accountAvatarPreviewPath(
                clearUploadedAvatar = true,
                pendingAvatarUrl = "/pending.png",
                currentAvatarUrl = "/current.png",
            ),
        )
        assertTrue(shouldClearUploadedAvatarForPreset("/current.png"))
        assertFalse(shouldClearUploadedAvatarForPreset(null))
        assertEquals(
            "保存用户资料后将使用预设头像",
            accountPresetAvatarNotice(clearUploadedAvatar = true),
        )
        assertNull(accountPresetAvatarNotice(clearUploadedAvatar = false))
    }

    @Test
    fun `account profile and avatar notices stay outside UI`() {
        assertEquals("没有账号绑定变更", accountNoBindingChangesNotice())
        assertEquals("账号绑定已保存", accountBindingsSavedNotice())
        assertEquals("没有偏好设置变更", preferencesNoChangesNotice())
        assertEquals("偏好设置已保存", preferencesSavedNotice())
        assertEquals("没有用户资料变更", profileNoChangesNotice())
        assertEquals("用户资料已保存", profileSavedNotice())
        assertEquals("无法打开文件选择器", avatarPickerOpenFailureMessage("denied"))
        assertEquals("无法读取图片", avatarReadFailureMessage("bad file"))
        assertEquals("图片文件为空", avatarEmptyFileMessage())
        assertEquals("头像已上传，保存用户资料后生效", avatarUploadedPendingProfileNotice())
        assertEquals("头像已更新", avatarUpdatedNotice())
        assertEquals("密码已更新", passwordUpdatedNotice())
    }

    @Test
    fun `account security action helpers gate dangerous actions`() {
        val user = _user()
        val superuser = _user(isSuperuser = true)

        assertTrue(canDeleteAccount(user))
        assertFalse(canDeleteAccount(superuser))
        assertTrue(
            canStartAccountDeletion(hasApi = true, user = user, deletingAccount = false),
        )
        assertFalse(
            canStartAccountDeletion(
                hasApi = false,
                user = user,
                deletingAccount = false,
            ),
        )
        assertFalse(
            canStartAccountDeletion(
                hasApi = true,
                user = superuser,
                deletingAccount = false,
            ),
        )
        assertEquals("超级用户账号不能被注销。", accountDeletionDescription(superuser))
        assertTrue(accountDeletionDescription(user).contains("注销后账号不能继续登录"))

        val spec = accountDeletionConfirmationSpec(user)
        assertEquals("确认注销账号", spec.title)
        assertEquals(accountDeletionDescription(user), spec.body)
        assertEquals("logan", spec.expectedText)
        assertEquals("输入 logan 确认", spec.inputHint)
        assertEquals("确认注销", spec.confirmLabel)
    }

    @Test
    fun `password change guard follows api and busy state`() {
        assertTrue(
            canStartPasswordChange(hasApi = true, changingPassword = false),
        )
        assertFalse(
            canStartPasswordChange(hasApi = false, changingPassword = false),
        )
        assertFalse(
            canStartPasswordChange(hasApi = true, changingPassword = true),
        )
    }

    @Test
    fun `sessionStateText prioritizes current revoked expired and active state`() {
        val now = DateTime(2026, 6, 4, 12)

        assertEquals("当前会话", sessionStateText(_session(isCurrent = true), now = now))
        assertEquals(
            "已失效",
            sessionStateText(
                _session(revokedAt = now.subtract(1.days)),
                now = now,
            ),
        )
        assertEquals(
            "已过期",
            sessionStateText(
                _session(expiresAt = now.subtract(1.minutes)),
                now = now,
            ),
        )
        assertEquals(
            "有效",
            sessionStateText(
                _session(expiresAt = now.add(1.minutes)),
                now = now,
            ),
        )
    }

    @Test
    fun `session display helpers provide fallback system and detail text`() {
        val session = _session(userAgent = "  ", ipAddress = "")

        assertEquals("未知系统", sessionDeviceLabel(session))
        assertEquals("未知 IP", sessionIpAddressLabel(session))
        assertEquals("Local", sessionLocationLabel(session))
        assertEquals(
            "位置：Local · IP：未知 IP · 最近活动：2026-06-04 00:00",
            sessionDetailText(session),
        )
    }

    @Test
    fun `session device label extracts systems from Gang Chat clients`() {
        assertEquals(
            "Android",
            sessionDeviceLabel(_session(userAgent = "GangChat Client (Android)")),
        )
        assertEquals(
            "Windows",
            sessionDeviceLabel(_session(userAgent = "GangChat Client (Windows)")),
        )
    }

    @Test
    fun `session device label recognizes common legacy user agents`() {
        assertEquals(
            "Android",
            sessionDeviceLabel(
                _session(
                    userAgent = "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36",
                ),
            ),
        )
        assertEquals(
            "Windows",
            sessionDeviceLabel(
                _session(userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"),
            ),
        )
        assertEquals("未知系统", sessionDeviceLabel(_session(userAgent = "Dart/3.9")))
    }

    @Test
    fun `sessionListBodyState separates loading empty and result states`() {
        assertEquals(
            SessionListBodyState.loading,
            sessionListBodyState(loading = true, sessions = emptyList()),
        )
        assertEquals(
            SessionListBodyState.empty,
            sessionListBodyState(loading = false, sessions = emptyList()),
        )
        assertEquals(
            SessionListBodyState.results,
            sessionListBodyState(loading = true, sessions = listOf(_session())),
        )
    }

    @Test
    fun `initials uses first characters from up to two words`() {
        assertEquals("?", initials(""))
        assertEquals("L", initials("Logan"))
        assertEquals("LZ", initials("Logan Zhang"))
    }

    private fun _user(canChangeUsernameAt: DateTime? = null, isSuperuser: Boolean = false): CurrentUser {
        return CurrentUser(
            id = "user_1",
            uid = "1001",
            username = "logan",
            displayName = "Logan",
            bio = "",
            gender = "secret",
            email = null,
            emailPublic = false,
            phoneNumber = null,
            phoneNumberPublic = false,
            avatarUrl = null,
            defaultAvatarKey = "blue-3",
            isSuperuser = isSuperuser,
            createdAt = DateTime(2026, 6, 4),
            canChangeUsernameAt = canChangeUsernameAt,
        )
    }

    private fun _session(
        isCurrent: Boolean = false,
        userAgent: String? = "Test Browser",
        ipAddress: String? = "127.0.0.1",
        expiresAt: DateTime? = null,
        revokedAt: DateTime? = null,
    ): UserSession {
        return UserSession(
            id = "session_1",
            userAgent = userAgent,
            ipAddress = ipAddress,
            location = "Local",
            createdAt = DateTime(2026, 6, 1),
            lastUsedAt = DateTime(2026, 6, 4),
            expiresAt = expiresAt ?: DateTime(2026, 6, 5),
            revokedAt = revokedAt,
            isCurrent = isCurrent,
        )
    }
}
