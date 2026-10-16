package com.gangchat.client.shell

import com.gangchat.client.foundation.TargetPlatform
import com.gangchat.client.foundation.defaultTargetPlatform

fun clientSystemName(platform: TargetPlatform): String =
    when (platform) {
        TargetPlatform.android -> "Android"
        TargetPlatform.fuchsia -> "Fuchsia"
        TargetPlatform.iOS -> "iOS"
        TargetPlatform.linux -> "Linux"
        TargetPlatform.macOS -> "macOS"
        TargetPlatform.windows -> "Windows"
    }

fun gangChatClientUserAgent(platform: TargetPlatform): String = "GangChat Client (${clientSystemName(platform)})"

fun currentGangChatClientUserAgent(): String = gangChatClientUserAgent(defaultTargetPlatform)
