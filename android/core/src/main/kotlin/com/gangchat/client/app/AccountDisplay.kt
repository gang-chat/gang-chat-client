package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.UserSession

enum class SessionListBodyState { loading, empty, results }

class AccountDeletionConfirmationSpec(
    val title: String,
    val body: String,
    val expectedText: String,
    val inputHint: String,
    val confirmLabel: String,
)

fun normalizeGender(value: String): String {
    return when (value) {
        "male", "female", "secret" -> value
        else -> "secret"
    }
}

fun formatDateTime(value: DateTime?): String {
    if (value == null) return "未知"
    val local = value.toLocal()
    val year = local.year.toString().padStart(4, '0')
    val month = local.month.toString().padStart(2, '0')
    val day = local.day.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$year-$month-$day $hour:$minute"
}

fun canEditUsername(user: CurrentUser, now: DateTime? = null): Boolean {
    val canAt = user.canChangeUsernameAt
    return canAt == null || !canAt.isAfter(now ?: DateTime.now())
}

fun usernameHelperText(user: CurrentUser, now: DateTime? = null): String {
    if (canEditUsername(user, now = now)) {
        return "用于登录和账号识别；同一账号一天只能修改一次。"
    }
    return "下次可修改时间：${formatDateTime(user.canChangeUsernameAt)}"
}

fun avatarUploadFilename(originalName: String, now: DateTime? = null): String {
    val cleaned =
        basename(originalName)
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .replace(Regex("-+"), "-")
    val stem = Regex("\\.[A-Za-z0-9]+$").replaceFirst(cleaned, "")
    val safeStem = if (stem.isEmpty()) "avatar" else stem
    val timestamp = (now ?: DateTime.now()).millisecondsSinceEpoch
    return "$safeStem-$timestamp.png"
}

fun accountAvatarPreviewPath(
    clearUploadedAvatar: Boolean,
    pendingAvatarUrl: String? = null,
    currentAvatarUrl: String? = null,
): String? {
    if (clearUploadedAvatar) return null
    return pendingAvatarUrl ?: currentAvatarUrl
}

fun shouldClearUploadedAvatarForPreset(currentAvatarUrl: String?): Boolean {
    return currentAvatarUrl != null
}

fun accountPresetAvatarNotice(clearUploadedAvatar: Boolean): String? {
    return if (clearUploadedAvatar) "保存用户资料后将使用预设头像" else null
}

fun accountNoBindingChangesNotice(): String {
    return "没有账号绑定变更"
}

fun accountBindingsSavedNotice(): String {
    return "账号绑定已保存"
}

fun preferencesNoChangesNotice(): String {
    return "没有偏好设置变更"
}

fun preferencesSavedNotice(): String {
    return "偏好设置已保存"
}

fun profileNoChangesNotice(): String {
    return "没有用户资料变更"
}

fun profileSavedNotice(): String {
    return "用户资料已保存"
}

@Suppress("UNUSED_PARAMETER")
fun avatarPickerOpenFailureMessage(error: Any): String {
    return "无法打开文件选择器"
}

@Suppress("UNUSED_PARAMETER")
fun avatarReadFailureMessage(error: Any): String {
    return "无法读取图片"
}

fun avatarEmptyFileMessage(): String {
    return "图片文件为空"
}

fun avatarUploadedPendingProfileNotice(): String {
    return "头像已上传，保存用户资料后生效"
}

fun avatarUpdatedNotice(): String {
    return "头像已更新"
}

fun passwordUpdatedNotice(): String {
    return "密码已更新"
}

fun canDeleteAccount(user: CurrentUser): Boolean {
    return !user.isSuperuser
}

fun canStartAccountDeletion(
    hasApi: Boolean,
    user: CurrentUser?,
    deletingAccount: Boolean,
): Boolean {
    return hasApi && user != null && canDeleteAccount(user) && !deletingAccount
}

fun accountDeletionDescription(user: CurrentUser): String {
    if (!canDeleteAccount(user)) return "超级用户账号不能被注销。"
    return "注销后账号不能继续登录，当前会话会失效，服务端将删除和该账号有关的信息。"
}

fun accountDeletionConfirmationSpec(user: CurrentUser): AccountDeletionConfirmationSpec {
    return AccountDeletionConfirmationSpec(
        title = "确认注销账号",
        body = accountDeletionDescription(user),
        expectedText = user.username,
        inputHint = "输入 ${user.username} 确认",
        confirmLabel = "确认注销",
    )
}

fun canStartPasswordChange(
    hasApi: Boolean,
    changingPassword: Boolean,
): Boolean {
    return hasApi && !changingPassword
}

fun sessionStateText(session: UserSession, now: DateTime? = null): String {
    if (session.isCurrent) return "当前会话"
    if (session.revokedAt != null) return "已失效"
    if (!session.expiresAt.isAfter(now ?: DateTime.now())) return "已过期"
    return "有效"
}

private val _gangChatUserAgentPattern =
    Regex(
        "^GangChat Client \\((Android|Fuchsia|iOS|Linux|macOS|Windows)\\)$",
        RegexOption.IGNORE_CASE,
    )

fun sessionDeviceLabel(session: UserSession): String {
    val userAgent = session.userAgent?.trim()
    if (userAgent == null || userAgent.isEmpty()) return "未知系统"

    val gangChatMatch = _gangChatUserAgentPattern.find(userAgent)
    if (gangChatMatch != null) {
        return _normalizeSystemName(gangChatMatch.groupValues[1])
    }

    val normalized = userAgent.lowercase()
    if (normalized.contains("android")) return "Android"
    if (normalized.contains("windows")) return "Windows"
    if (normalized.contains("iphone") ||
        normalized.contains("ipad") ||
        normalized.contains("ios")
    ) {
        return "iOS"
    }
    if (normalized.contains("macintosh") ||
        normalized.contains("mac os x") ||
        normalized.contains("macos")
    ) {
        return "macOS"
    }
    if (normalized.contains("linux")) return "Linux"
    if (normalized.contains("fuchsia")) return "Fuchsia"
    return "未知系统"
}

private fun _normalizeSystemName(value: String): String {
    return when (value.lowercase()) {
        "android" -> "Android"
        "fuchsia" -> "Fuchsia"
        "ios" -> "iOS"
        "linux" -> "Linux"
        "macos" -> "macOS"
        "windows" -> "Windows"
        else -> "未知系统"
    }
}

fun sessionIpAddressLabel(session: UserSession): String {
    val ipAddress = session.ipAddress?.trim()
    if (ipAddress == null || ipAddress.isEmpty()) return "未知 IP"
    return ipAddress
}

fun sessionLocationLabel(session: UserSession): String {
    val location = session.location.trim()
    if (location.isEmpty()) return "未知地点"
    return location
}

fun sessionDetailText(session: UserSession): String {
    return "位置：${sessionLocationLabel(session)} · IP：${sessionIpAddressLabel(session)} · 最近活动：${formatDateTime(session.lastUsedAt)}"
}

fun sessionListBodyState(
    loading: Boolean,
    sessions: Iterable<UserSession>,
): SessionListBodyState {
    if (sessions.any()) return SessionListBodyState.results
    if (loading) return SessionListBodyState.loading
    return SessionListBodyState.empty
}

/**
 * Dart's `RegExp(r'\s+')`: Dart regexps use JavaScript's `\s`, which also
 * matches Unicode spaces such as the ideographic space (U+3000) that Java's
 * default `\s` does not.
 */
private val _whitespaceRuns =
    Regex("[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")

fun initials(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return "?"
    val parts = trimmed.split(_whitespaceRuns).filter { part -> part.isNotEmpty() }
    val initials =
        parts
            .take(2)
            .joinToString("") { part -> String(Character.toChars(part.codePointAt(0))) }
    return initials.uppercase()
}
