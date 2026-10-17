package com.gangchat.client.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.VoiceRecorderState
import com.gangchat.client.app.voiceRecordingStarted
import com.gangchat.client.app.voiceRecordingStopped
import com.gangchat.client.app.voiceRecordingTicked
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.dpBounds
import com.gangchat.client.widgets.IconSemanticsKey
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Port of test/widget/home/voice_panel_widget_test.dart. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class VoicePanelWidgetTest {
    @get:Rule
    val compose = createComposeRule()

    private fun hasIcon(icon: ImageVector) = SemanticsMatcher.expectValue(IconSemanticsKey, icon)

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun host(state: VoiceRecorderState, onStart: () -> Unit = {}, onSend: () -> Unit = {}, onCancel: () -> Unit = {}) {
        compose.setChatContent(width = 360.dp) { _ -> VoicePanelForTest(state = state, onStart = onStart, onSend = onSend, onCancel = onCancel) }
    }

    @Test
    fun `idle voice panel shows a plain mic trigger, no raised surface`() {
        var started = 0
        host(VoiceRecorderState(), onStart = { started++ })

        compose.onNodeWithText("点击录音").assertExists()
        // Plain IconButton, not a PressableSurface circle: the only button is
        // the Material icon button with its 48 dp tap target.
        val buttons = compose.onAllNodes(isButton).fetchSemanticsNodes()
        assertEquals(1, buttons.size)
        val bounds = compose.onNode(isButton).dpBounds(compose.density.density)
        assertEquals(48f, (bounds.right - bounds.left).value, 0.01f)
        assertEquals(48f, (bounds.bottom - bounds.top).value, 0.01f)

        compose.onNode(hasIcon(Icons.Filled.MicNone), useUnmergedTree = true).performClick()
        assertEquals(1, started)
    }

    @Test
    fun `recording panel shows a live timer and send-cancel`() {
        var sent = 0
        var cancelled = 0
        host(voiceRecordingTicked(voiceRecordingStarted(), 7.seconds), onSend = { sent++ }, onCancel = { cancelled++ })

        compose.onNodeWithText("7\"").assertExists()
        compose.onNode(hasIcon(Icons.AutoMirrored.Rounded.Send), useUnmergedTree = true).assertExists()

        compose.onNode(hasIcon(Icons.AutoMirrored.Rounded.Send), useUnmergedTree = true).performClick()
        compose.onNode(hasIcon(Icons.Filled.DeleteOutline), useUnmergedTree = true).performClick()
        assertEquals(1, sent)
        assertEquals(1, cancelled)
    }

    @Test
    fun `review panel keeps the clip duration and a send action`() {
        val review = voiceRecordingStopped(state = voiceRecordingStarted(), path = "/tmp/voice.m4a", elapsed = 12.seconds)
        host(review)

        compose.onNodeWithText("12\"").assertExists()
        compose.onNode(hasIcon(Icons.AutoMirrored.Rounded.Send), useUnmergedTree = true).assertExists()
        compose.onNode(hasIcon(Icons.Filled.DeleteOutline), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("点击发送或取消").assertExists()
    }
}
