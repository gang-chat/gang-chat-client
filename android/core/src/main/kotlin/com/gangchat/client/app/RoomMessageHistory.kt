package com.gangchat.client.app

import com.gangchat.client.foundation.DateTime
import com.gangchat.client.protocol.Message
import kotlin.time.Duration.Companion.days

enum class RoomMessageHistoryCategory {
    all,
    links,
    voice,
    stickers,
    images,
    files,
    music,
    system,
}

/** Dart's `extension RoomMessageHistoryCategoryValue` `apiValue`. */
val RoomMessageHistoryCategory.apiValue: String
    get() =
        when (this) {
            RoomMessageHistoryCategory.all -> "all"
            RoomMessageHistoryCategory.links -> "links"
            RoomMessageHistoryCategory.voice -> "voice"
            RoomMessageHistoryCategory.stickers -> "stickers"
            RoomMessageHistoryCategory.images -> "images"
            RoomMessageHistoryCategory.files -> "files"
            RoomMessageHistoryCategory.music -> "music"
            RoomMessageHistoryCategory.system -> "system"
        }

fun roomMessageHistoryCopyText(message: Message): String {
    val content = messageCopyText(message).trim()
    if (content.isNotEmpty()) return content
    for (attachment in message.attachments) {
        val name = attachment.name?.trim()
        if (name != null && name.isNotEmpty()) return name
    }
    return if (message.type == "sticker") "[表情]" else "[消息]"
}

fun roomMessageHistoryDayStart(value: DateTime): DateTime {
    val local = value.toLocal()
    return DateTime(local.year, local.month, local.day)
}

fun roomMessageHistoryDayEndExclusive(value: DateTime): DateTime {
    return roomMessageHistoryDayStart(value).add(1.days)
}
