package com.gangchat.client.app

import kotlin.math.abs

enum class ChatComposerPanel { stickers, voice, file, tools }

class ChatComposerPanelPatch(
    val openPanel: ChatComposerPanel?,
    val shouldLoadStickers: Boolean,
)

class ChatComposerInputHeightPatch(val inputHeight: Double)

fun chatComposerPanelToggled(
    currentPanel: ChatComposerPanel?,
    panel: ChatComposerPanel,
): ChatComposerPanelPatch {
    val opening = currentPanel != panel
    return ChatComposerPanelPatch(
        openPanel = if (opening) panel else null,
        shouldLoadStickers = opening && panel == ChatComposerPanel.stickers,
    )
}

fun chatComposerPanelClosed(currentPanel: ChatComposerPanel?): ChatComposerPanelPatch? {
    if (currentPanel == null) return null
    return _closedPanelPatch
}

fun chatComposerPanelResetForRoomChange(): ChatComposerPanelPatch {
    return _closedPanelPatch
}

fun chatComposerInputHeightMeasured(
    currentHeight: Double,
    measuredHeight: Double,
    threshold: Double = 0.5,
): ChatComposerInputHeightPatch? {
    if (abs(currentHeight - measuredHeight) < threshold) return null
    return ChatComposerInputHeightPatch(inputHeight = measuredHeight)
}

// One shared instance, like Dart's canonicalized `const` patch.
private val _closedPanelPatch =
    ChatComposerPanelPatch(
        openPanel = null,
        shouldLoadStickers = false,
    )
