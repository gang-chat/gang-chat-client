package com.gangchat.client.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.showDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Regression test: a tap on a dialog's own content does not reach its dismissing barrier. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DialogFramePointerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tapping a dialog's text keeps it open while an outside tap dismisses it`() {
        compose.setUiContent { context ->
            Button(onPressed = { unawaited { showDialog<Unit>(context) { _ -> DialogFrame(title = "标题", child = { Text("正文") }) } } }) {
                Text("打开")
            }
        }
        compose.onNodeWithText("打开").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("正文").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("标题").assertExists()

        compose.onRoot().performTouchInput { click(Offset(5f, 5f)) }
        compose.waitForIdle()
        compose.onNodeWithText("标题").assertDoesNotExist()
    }
}
