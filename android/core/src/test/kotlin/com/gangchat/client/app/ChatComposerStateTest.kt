package com.gangchat.client.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatComposerStateTest {
    @Test
    fun `chat composer panel toggle opens and closes the same panel`() {
        val opened =
            chatComposerPanelToggled(
                currentPanel = null,
                panel = ChatComposerPanel.tools,
            )

        assertEquals(ChatComposerPanel.tools, opened.openPanel)
        assertFalse(opened.shouldLoadStickers)

        val closed =
            chatComposerPanelToggled(
                currentPanel = ChatComposerPanel.tools,
                panel = ChatComposerPanel.tools,
            )

        assertNull(closed.openPanel)
        assertFalse(closed.shouldLoadStickers)
    }

    @Test
    fun `chat composer sticker panel open requests sticker loading`() {
        val opened =
            chatComposerPanelToggled(
                currentPanel = null,
                panel = ChatComposerPanel.stickers,
            )

        assertEquals(ChatComposerPanel.stickers, opened.openPanel)
        assertTrue(opened.shouldLoadStickers)

        val switched =
            chatComposerPanelToggled(
                currentPanel = ChatComposerPanel.tools,
                panel = ChatComposerPanel.stickers,
            )

        assertEquals(ChatComposerPanel.stickers, switched.openPanel)
        assertTrue(switched.shouldLoadStickers)
    }

    @Test
    fun `chat composer close and room reset clear open panel`() {
        assertNull(chatComposerPanelClosed(currentPanel = null))

        val closed =
            chatComposerPanelClosed(
                currentPanel = ChatComposerPanel.stickers,
            )

        assertNull(closed?.openPanel)
        assertEquals(false, closed?.shouldLoadStickers)

        val reset = chatComposerPanelResetForRoomChange()

        assertNull(reset.openPanel)
        assertFalse(reset.shouldLoadStickers)
    }

    @Test
    fun `chat composer input height patch ignores sub-threshold changes`() {
        assertNull(
            chatComposerInputHeightMeasured(currentHeight = 76.0, measuredHeight = 76.25),
        )

        val patch =
            chatComposerInputHeightMeasured(
                currentHeight = 76.0,
                measuredHeight = 84.0,
            )

        assertEquals(84.0, patch?.inputHeight)
    }
}
