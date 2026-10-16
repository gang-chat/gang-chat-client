package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.JsonMap
import com.gangchat.client.foundation.asJsonMap
import com.gangchat.client.foundation.base64UrlDecode
import com.gangchat.client.foundation.base64UrlEncode
import com.gangchat.client.foundation.jsonDecode
import com.gangchat.client.foundation.jsonEncode
import com.gangchat.client.foundation.utf8Decode
import com.gangchat.client.foundation.utf8Encode
import com.gangchat.client.protocol.Message
import com.gangchat.client.protocol.MessageAttachment
import com.gangchat.client.protocol.MessageQuote
import com.gangchat.client.protocol.SharedMusicPlaylist
import com.gangchat.client.protocol.SharedMusicTrack
import com.gangchat.client.protocol.Sticker
import com.gangchat.client.protocol.UserSummary

enum class MessageContentKind { sticker, voice, files, playlist, musicTrack, text }

const val kSystemMessageType: String = "system"
const val kSystemEventRoomMemberJoined: String = "room_member_joined"
const val kSystemEventRoomMemberLeft: String = "room_member_left"
const val kSystemEventRoomMemberRemoved: String = "room_member_removed"
const val kSystemEventLiveJoined: String = "live_joined"
const val kSystemEventLiveLeft: String = "live_left"
const val kSystemEventRoomRoleChanged: String = "room_role_changed"
const val kSystemEventRoomNameChanged: String = "room_name_changed"
const val kSystemEventRoomDescriptionChanged: String = "room_description_changed"
const val kSystemEventRoomVisibilityChanged: String = "room_visibility_changed"
const val kSystemEventRoomJoinPolicyChanged: String = "room_join_policy_changed"

class SystemMessageEvent(
    val event: String,
    val message: Message,
    val user: UserSummary? = null,
    val actor: UserSummary? = null,
    val target: UserSummary? = null,
    val fromRole: String? = null,
    val toRole: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
) {
    val subject: UserSummary get() = target ?: user ?: message.sender
    val isRoleChange: Boolean get() = event == kSystemEventRoomRoleChanged
}

class MessageTextEdit(val text: String, val cursorOffset: Int)

class StickerMessageDraft(
    val body: String,
    val type: String,
    val attachments: List<MessageAttachment>,
)

fun saveMessageDraft(
    drafts: Map<String, String>,
    roomId: String?,
    text: String,
): Map<String, String> {
    if (roomId == null) return drafts
    return LinkedHashMap(drafts).apply { put(roomId, text) }
}

fun removeMessageDraft(
    drafts: Map<String, String>,
    roomId: String?,
): Map<String, String> {
    if (roomId == null || !drafts.containsKey(roomId)) return drafts
    val result = LinkedHashMap<String, String>()
    for ((key, value) in drafts) {
        if (key != roomId) result[key] = value
    }
    return result
}

fun messageDraftForRoom(
    drafts: Map<String, String>,
    roomId: String,
): String {
    return drafts[roomId] ?: ""
}

fun formatMessageTime(value: DateTime): String {
    val local = value.toLocal()
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}

fun formatChatTimestamp(value: DateTime, now: DateTime? = null): String {
    val local = value.toLocal()
    val localNow = (now ?: DateTime.now()).toLocal()
    val today = DateTime(localNow.year, localNow.month, localNow.day)
    val date = DateTime(local.year, local.month, local.day)
    val dayDelta = today.difference(date).inWholeDays
    val time = formatMessageTime(local)

    if (dayDelta == 0L) return time
    if (dayDelta == 1L) return "昨天 $time"
    if (dayDelta == 2L) return "前天 $time"
    if (dayDelta >= 3 && dayDelta < 7) {
        return "${_weekdayLabel(local.weekday)} $time"
    }
    if (local.year == localNow.year) {
        return "${local.month}月${local.day}日 $time"
    }
    return "${local.year}年${local.month}月${local.day}日 $time"
}

fun formatDetailedChatTimestamp(value: DateTime): String {
    val local = value.toLocal()
    val time = formatMessageTime(local)
    return "${local.year}年${local.month}月${local.day}日 " +
        "${_weekdayLabel(local.weekday)} $time"
}

fun shouldShowChatTimestamp(
    current: DateTime,
    previous: DateTime? = null,
    now: DateTime? = null,
): Boolean {
    if (previous == null) return true
    val referenceNow = now ?: DateTime.now()
    return formatChatTimestamp(current, now = referenceNow) !=
        formatChatTimestamp(previous, now = referenceNow)
}

private fun _weekdayLabel(weekday: Int): String {
    return when (weekday) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        7 -> "星期日"
        else -> "星期日"
    }
}

fun messageContentKind(message: Message): MessageContentKind {
    if (message.stickerAttachment != null) return MessageContentKind.sticker
    if (voiceMessageAttachment(message) != null) {
        return MessageContentKind.voice
    }
    if (message.fileAttachments.isNotEmpty()) return MessageContentKind.files
    if (message.playlistAttachment != null) return MessageContentKind.playlist
    if (message.musicTrackAttachment != null) {
        return MessageContentKind.musicTrack
    }
    return MessageContentKind.text
}

fun systemMessageEvent(message: Message): SystemMessageEvent? {
    if (message.type != kSystemMessageType) return null
    var attachment: MessageAttachment? = null
    for (candidate in message.attachments) {
        if (candidate.type == kSystemMessageType) {
            attachment = candidate
            break
        }
    }
    val event = attachment?.event?.trim()
    if (event == null || event.isEmpty()) return null
    val user = attachment?.user
    val target = attachment?.target ?: user
    return SystemMessageEvent(
        event = event,
        message = message,
        user = user,
        actor = attachment?.actor,
        target = target,
        fromRole = attachment?.fromRole,
        toRole = attachment?.toRole,
        oldValue = attachment?.oldValue,
        newValue = attachment?.newValue,
    )
}

fun systemMessageRoleLabel(role: String?): String {
    return when (role) {
        "owner", "creator" -> "创建者"
        "admin" -> "管理员"
        "member" -> "成员"
        else -> "成员"
    }
}

fun systemMessageRoleVerb(event: SystemMessageEvent): String {
    val fromRank = _roleRank(event.fromRole)
    val toRank = _roleRank(event.toRole)
    if (toRank > fromRank) return "晋升为"
    return "降职为"
}

fun systemMessageRoleChangeOmitsActor(event: SystemMessageEvent): Boolean {
    return event.isRoleChange &&
        event.fromRole == "owner" &&
        event.toRole == "admin"
}

fun messageCopyText(message: Message): String {
    if (message.isRemoved) return removedMessageCopyText(message)
    val event = systemMessageEvent(message)
    if (event != null) return systemMessageCopyText(event)
    return message.body.trimEnd()
}

/**
 * Text written to the system clipboard for a complete chat component.
 *
 * The normal [messageCopyText] deliberately stays compact because it is also
 * used by history/search previews. Structured music messages use a dedicated
 * clipboard representation so right-click copy never loses fields that are
 * only visible inside their interactive card.
 */
fun messageClipboardText(message: Message): String {
    if (message.isRemoved) return removedMessageCopyText(message)
    val playlist = message.playlistAttachment?.playlist
    if (playlist != null) return _musicPlaylistClipboardText(playlist)
    val track = message.musicTrackAttachment?.track
    if (track != null) return _musicTrackClipboardText(track)
    return messageCopyText(message)
}

/**
 * Clipboard text for a music component that remains readable in other apps
 * while carrying an application-only immutable snapshot for Gang Chat.
 *
 * Unicode tag characters are intentionally non-rendering. Unlike an
 * in-memory "last copied message" cache, this envelope survives focus
 * changes and cross-platform system-clipboard round trips. The server still
 * validates [Message.id] when the component is sent, so the snapshot cannot
 * grant access to a message the user may not reference.
 */
fun messageComponentClipboardText(message: Message): String {
    val visible = messageClipboardText(message)
    if (!_isClipboardMusicComponent(message)) return visible
    val payload =
        jsonEncode(
            linkedMapOf(
                "version" to 1,
                "message" to
                    linkedMapOf(
                        "id" to message.id,
                        "room_id" to message.roomId,
                        "client_message_id" to message.clientMessageId,
                        "type" to message.type,
                        "body" to message.body,
                        "created_at" to message.createdAt.toUtc().toIso8601String(),
                        "sender" to _clipboardUserSummaryJson(message.sender),
                        "attachments" to message.attachments.map { attachment -> attachment.toJson() },
                    ),
            ),
        )
    val encoded = base64UrlEncode(utf8Encode(payload)).replace("=", "")
    val builder = StringBuilder(visible)
    builder.appendCodePoint(_clipboardEnvelopeStart)
    for (codeUnit in encoded) builder.appendCodePoint(_clipboardTagBase + codeUnit.code)
    builder.appendCodePoint(_clipboardEnvelopeEnd)
    return builder.toString()
}

/** Restores a copied music component directly from clipboard text. */
fun messageComponentFromClipboardText(clipboardText: String?): Message? {
    if (clipboardText == null || clipboardText.isEmpty()) return null
    val encoded = _clipboardEnvelopePayload(clipboardText)
    if (encoded == null || encoded.isEmpty() || encoded.length > 512 * 1024) {
        return null
    }
    try {
        val padded = encoded.padEnd((encoded.length + 3) / 4 * 4, '=')
        val decoded = jsonDecode(utf8Decode(base64UrlDecode(padded)))
        if (decoded !is Map<*, *> || !_isJsonOne(decoded["version"])) return null
        val messageJson = decoded["message"]
        if (messageJson !is Map<*, *>) return null
        val message = Message.fromJson(LinkedHashMap(messageJson.asJsonMap()))
        if (message.id.trim().isEmpty() || !_isClipboardMusicComponent(message)) {
            return null
        }
        if (_normalizedClipboardText(_withoutClipboardEnvelope(clipboardText)) !=
            _normalizedClipboardText(messageClipboardText(message))
        ) {
            return null
        }
        return message
    } catch (_: Exception) {
        return null
    }
}

/**
 * Whether the current plain-text clipboard still represents [message].
 *
 * Windows clipboard providers may expose line endings as CRLF even when the
 * app originally wrote LF. A terminal line break may also be appended by a
 * clipboard bridge. Normalizing only those transport differences preserves
 * stale-copy protection without degrading a structured component to text.
 */
fun messageClipboardTextMatches(message: Message, clipboardText: String?): Boolean {
    if (clipboardText == null) return false
    return _normalizedClipboardText(_withoutClipboardEnvelope(clipboardText)) ==
        _normalizedClipboardText(messageClipboardText(message))
}

private const val _clipboardEnvelopeStart: Int = 0xE0001
private const val _clipboardEnvelopeEnd: Int = 0xE007F
private const val _clipboardTagBase: Int = 0xE0000

/** Dart's `value == 1` on a decoded JSON value (an `int` or `double`). */
private fun _isJsonOne(value: Any?): Boolean =
    (value is Long && value == 1L) || (value is Double && value == 1.0)

private fun _isClipboardMusicComponent(message: Message): Boolean {
    return !message.isRemoved &&
        !message.pending &&
        (message.playlistAttachment != null || message.musicTrackAttachment != null)
}

private fun _clipboardUserSummaryJson(user: UserSummary): JsonMap {
    return linkedMapOf(
        "id" to user.id,
        "uid" to user.uid,
        "username" to user.username,
        "display_name" to user.displayName,
        "avatar_url" to user.avatarUrl,
        "default_avatar_key" to user.defaultAvatarKey,
        "room_display_name" to user.roomDisplayName,
        "room_role" to user.roomRole,
        "is_superuser" to user.isSuperuser,
        "is_online" to user.isOnline,
        "is_deleted" to user.isDeleted,
        "is_suspended" to user.isSuspended,
    )
}

/** Dart's `runes.indexOf(value, start)`. */
private fun IntArray._indexOf(value: Int, start: Int): Int {
    for (index in maxOf(start, 0) until size) {
        if (this[index] == value) return index
    }
    return -1
}

/** Dart's `String.fromCharCodes(runes)`. */
private fun _stringFromRunes(runes: Iterable<Int>): String {
    val builder = StringBuilder()
    for (rune in runes) builder.appendCodePoint(rune)
    return builder.toString()
}

private fun _clipboardEnvelopePayload(value: String): String? {
    val runes = value.codePoints().toArray()
    val start = runes.lastIndexOf(_clipboardEnvelopeStart)
    if (start < 0) return null
    val end = runes._indexOf(_clipboardEnvelopeEnd, start + 1)
    if (end <= start + 1) return null
    val codeUnits = mutableListOf<Int>()
    for (rune in runes.copyOfRange(start + 1, end)) {
        val codeUnit = rune - _clipboardTagBase
        if (codeUnit < 0x20 || codeUnit > 0x7E) return null
        codeUnits.add(codeUnit)
    }
    return _stringFromRunes(codeUnits)
}

private fun _withoutClipboardEnvelope(value: String): String {
    val runes = value.codePoints().toArray()
    val start = runes.lastIndexOf(_clipboardEnvelopeStart)
    if (start < 0) return value
    val end = runes._indexOf(_clipboardEnvelopeEnd, start + 1)
    if (end < 0) return value
    return _stringFromRunes(runes.take(start) + runes.drop(end + 1))
}

private fun _normalizedClipboardText(value: String): String {
    return value.replace("\r\n", "\n").replace("\r", "\n").trimEnd()
}

private fun _musicPlaylistClipboardText(playlist: SharedMusicPlaylist): String {
    val creator = _systemUserLabel(playlist.creator)
    val createdAt = playlist.createdAt?.toLocal()?.toIso8601String() ?: "未知"
    val lines =
        mutableListOf(
            "[歌单] ${playlist.name}",
            "歌曲数量：${playlist.itemCount}",
            "创建人：$creator",
            "创建日期：$createdAt",
        )
    val description = playlist.description.trim()
    if (description.isNotEmpty()) lines.add("简介：$description")
    if (playlist.items.isNotEmpty()) {
        lines.add("歌曲：")
        for (index in playlist.items.indices) {
            val item = playlist.items[index]
            val artists =
                item.artists
                    .map { value -> value.trim() }
                    .filter { value -> value.isNotEmpty() }
                    .joinToString(" / ")
            lines.add(
                "${index + 1}. ${item.title}" +
                    (if (artists.isEmpty()) "" else " - $artists") +
                    " [${item.source}:${item.trackId}]",
            )
        }
    }
    return lines.joinToString("\n")
}

private fun _musicTrackClipboardText(track: SharedMusicTrack): String {
    val artists =
        track.artists
            .map { value -> value.trim() }
            .filter { value -> value.isNotEmpty() }
            .joinToString(" / ")
    val seconds = if (track.durationMs <= 0) 0 else track.durationMs / 1000
    val duration =
        if (seconds <= 0) {
            "未知"
        } else {
            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        }
    return listOf(
        "[歌曲] ${track.title}",
        "歌手：${if (artists.isEmpty()) "未知" else artists}",
        "时长：$duration",
        "来源：${track.source}",
        "链接标识：${track.trackId}",
    ).joinToString("\n")
}

fun messageQuoteSnapshot(message: Message): MessageQuote {
    val roomName = message.sender.roomDisplayName?.trim()
    val displayName = message.sender.displayName.trim()
    val username = message.sender.username.trim()
    return MessageQuote(
        messageId = message.id,
        senderDisplayName =
            if (message.type == kSystemMessageType) {
                ""
            } else if (roomName?.isNotEmpty() == true) {
                roomName
            } else if (displayName.isNotEmpty()) {
                displayName
            } else if (username.isNotEmpty()) {
                username
            } else {
                "用户"
            },
        body = messageQuoteBodySnapshot(message),
        createdAt = message.createdAt,
        previewAttachment = messageQuotePreviewAttachment(message),
    )
}

fun messageQuotePreviewAttachment(message: Message): MessageAttachment? {
    val sticker = message.stickerAttachment
    if (sticker != null) return sticker
    for (attachment in message.fileAttachments) {
        if (isImageMimeType(attachment.asset?.mimeType)) return attachment
    }
    return null
}

fun messageQuoteBodySnapshot(message: Message): String {
    val text = messageCopyText(message).trim()
    if (messageContentKind(message) == MessageContentKind.text && text.isNotEmpty()) {
        return text
    }
    val voice = voiceMessageAttachment(message)
    if (voice != null) {
        val duration = formatVoiceBubbleDuration(voiceAttachmentDuration(voice))
        return if (duration.isEmpty()) "[语音]" else "[语音] $duration"
    }
    val sticker = message.stickerAttachment
    if (sticker != null) return "[表情] ${stickerAttachmentTitle(sticker)}"
    val files = message.fileAttachments.toList()
    if (files.isNotEmpty()) {
        val nonImage = files.filter { attachment -> !isImageMimeType(attachment.asset?.mimeType) }
        val preview = if (nonImage.isNotEmpty()) nonImage.first() else files.first()
        val label = if (nonImage.isEmpty()) "[图片]" else "[文件]"
        return "$label ${fileAttachmentTitle(preview)}"
    }
    return if (text.isEmpty()) "[消息]" else text
}

fun removedMessageCopyText(message: Message): String {
    if (message.isForceDeleted) {
        val actor = message.forceDeletedBy ?: return "消息已被删除"
        return "${_systemUserLabel(actor)} 删除了一条消息"
    }

    val actor = message.recalledBy ?: message.sender
    if (actor.id == message.sender.id) {
        return "${_systemUserLabel(actor)} 撤回了一条消息"
    }
    return "${_systemUserLabel(actor)} 撤回了一条来自 " +
        "${_systemUserLabel(message.sender)} 的消息"
}

fun systemMessageCopyText(event: SystemMessageEvent): String {
    val subject = event.subject
    when (event.event) {
        kSystemEventRoomMemberJoined -> return "${_systemUserLabel(subject)} 加入了房间"
        kSystemEventRoomMemberLeft -> return "${_systemUserLabel(subject)} 离开了房间"
        kSystemEventRoomMemberRemoved -> {
            val actor = event.actor ?: return "${_systemUserLabel(subject)} 被踢出了房间"
            return "${_systemUserLabel(subject)} 被 ${_systemUserLabel(actor)} " +
                "踢出了房间"
        }
        kSystemEventLiveJoined -> return "${_systemUserLabel(subject)} 进入了语音频道"
        kSystemEventLiveLeft -> return "${_systemUserLabel(subject)} 退出了语音频道"
        kSystemEventRoomRoleChanged -> {
            val roleLabel = systemMessageRoleLabel(event.toRole)
            val verb = systemMessageRoleVerb(event)
            val omitActor = systemMessageRoleChangeOmitsActor(event)
            val actor = event.actor
            val subjectPart = _systemUserLabel(subject)
            if (!omitActor && actor != null) {
                return "$subjectPart 被 ${_systemUserLabel(actor)} $verb $roleLabel"
            }
            return "$subjectPart $verb $roleLabel"
        }
        kSystemEventRoomNameChanged -> {
            val actor = event.actor ?: event.user
            val value = _systemChangedValueLabel(event.newValue)
            if (actor == null) return "房间名称 修改为 $value"
            return "房间名称 被 ${_systemUserLabel(actor)} 修改为 $value"
        }
        kSystemEventRoomDescriptionChanged -> {
            val actor = event.actor ?: event.user
            val value = _systemChangedValueLabel(event.newValue)
            if (actor == null) return "房间简介 修改为\n$value"
            return "房间简介 被 ${_systemUserLabel(actor)} 修改为\n$value"
        }
        kSystemEventRoomVisibilityChanged -> {
            val actor = event.actor ?: event.user
            val value = systemMessageVisibilityLabel(event.newValue)
            if (actor == null) return "房间可见性 修改为 $value"
            return "房间可见性 被 ${_systemUserLabel(actor)} 修改为 $value"
        }
        kSystemEventRoomJoinPolicyChanged -> {
            val actor = event.actor ?: event.user
            val value = systemMessageJoinPolicyLabel(event.newValue)
            if (actor == null) return "房间加入方式 修改为 $value"
            return "房间加入方式 被 ${_systemUserLabel(actor)} 修改为 $value"
        }
        else -> {
            val fallback = event.message.body.trimEnd()
            val subjectPart = _systemUserLabel(subject)
            if (fallback.isEmpty()) return subjectPart
            return "$subjectPart $fallback"
        }
    }
}

private fun _roleRank(role: String?): Int {
    return when (role) {
        "owner", "creator" -> 3
        "admin" -> 2
        "member" -> 1
        else -> 0
    }
}

private fun _systemUserLabel(user: UserSummary): String {
    val roomDisplayName = user.roomDisplayName?.trim()
    if (roomDisplayName != null && roomDisplayName.isNotEmpty()) {
        return roomDisplayName
    }
    val displayName = user.displayName.trim()
    if (displayName.isNotEmpty()) return displayName
    return user.username
}

private fun _systemChangedValueLabel(value: String?): String {
    val normalized = value ?: ""
    if (normalized.isEmpty()) return "（空）"
    return normalized
}

fun systemMessageVisibilityLabel(visibility: String?): String {
    return when ((visibility ?: "").trim().lowercase()) {
        "private" -> "私密"
        else -> "公开"
    }
}

fun systemMessageJoinPolicyLabel(joinPolicy: String?): String {
    return when ((joinPolicy ?: "").trim().lowercase()) {
        "open" -> "开放"
        "closed" -> "关闭"
        else -> "需审批"
    }
}

fun shouldShowFileAttachmentBody(
    body: String,
    attachments: List<MessageAttachment>,
): Boolean {
    val trimmedBody = body.trim()
    if (trimmedBody.isEmpty()) return false
    if (attachments.size != 1) return true
    return trimmedBody != fileAttachmentTitle(attachments[0])
}

fun stickerAttachmentTitle(attachment: MessageAttachment): String {
    val explicitName = attachment.name?.trim()
    if (explicitName != null && explicitName.isNotEmpty()) return explicitName
    return "表情"
}

/**
 * A safe filename to seed the sticker image preview's download/save-as. Uses
 * the asset's own filename when present, otherwise derives one from the
 * sticker name and the asset's mime type.
 */
fun stickerPreviewFilename(attachment: MessageAttachment): String {
    val assetFilename = attachment.asset?.filename?.trim()
    if (assetFilename != null && assetFilename.isNotEmpty()) return assetFilename
    val mimeType = attachment.asset?.mimeType ?: "image/png"
    val ext = imageExtensionForMimeType(mimeType)
    val rawName = stickerAttachmentTitle(attachment)
    val safeName =
        rawName
            .replace(_unsafeStickerFilenameChars, "_")
            .replace(_underscoreRuns, "_")
    val stem = if (safeName.isEmpty() || safeName == "_") "sticker" else safeName
    return "$stem.$ext"
}

/**
 * Dart's `RegExp(r'[\\/:*?"<>|\s]+')`. Dart regexps use JavaScript's `\s`,
 * which also matches Unicode spaces (U+3000, U+00A0, ...) that Java's default
 * `\s` does not.
 */
private val _unsafeStickerFilenameChars =
    Regex(
        "[\\\\/:*?\"<>|\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+",
    )

private val _underscoreRuns = Regex("_+")

fun messageDeliveryStatusText(message: Message): String? {
    if (message.failed) return "发送失败"
    if (message.pending) return "发送中"
    return null
}

fun outgoingTextMessageBody(value: String): String? {
    val body = value.trimEnd()
    return if (body.trim().isEmpty()) null else body
}

fun stickerMessageDraft(sticker: Sticker): StickerMessageDraft {
    return StickerMessageDraft(
        body = "[表情] ${sticker.name}",
        type = "sticker",
        attachments =
            listOf(
                MessageAttachment(
                    type = "sticker",
                    stickerId = sticker.id,
                    name = sticker.name,
                    asset = sticker.asset,
                ),
            ),
    )
}

fun insertMessageText(
    currentText: String,
    insertedText: String,
    selectionStart: Int?,
    selectionEnd: Int?,
): MessageTextEdit {
    val length = currentText.length
    val start = selectionStart?.coerceIn(0, length) ?: length
    val end = selectionEnd?.coerceIn(0, length) ?: length
    val replaceStart = if (start < end) start else end
    val replaceEnd = if (start < end) end else start
    val nextText = currentText.replaceRange(replaceStart, replaceEnd, insertedText)
    return MessageTextEdit(
        text = nextText,
        cursorOffset = replaceStart + insertedText.length,
    )
}
