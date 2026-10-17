package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.IconSemanticsKey
import com.gangchat.client.widgets.LocalMediaQuery
import com.gangchat.client.widgets.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Dart's `tester.view.devicePixelRatio = 1; physicalSize = 600×400`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w600dp-h400dp-mdpi")
class ResponsiveDialogActionBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val cancelKey = "cancel-action"
    private val downloadKey = "download-action"

    private fun actions() =
        listOf(
            ResponsiveDialogAction(buttonKey = cancelKey, label = "取消", onPressed = {}),
            ResponsiveDialogAction(
                buttonKey = downloadKey,
                label = "下载新版本",
                icon = Icons.Outlined.Download,
                tone = ButtonTone.primary,
                onPressed = {},
            ),
        )

    private fun pumpBar(width: Int, actions: List<ResponsiveDialogAction> = actions()) {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                Box(Modifier.width(width.dp)) { ResponsiveDialogActionBar(expanded = true, actions = actions) }
            }
        }
    }

    private fun rect(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    private fun DpRect.centerY() = (top + bottom) / 2

    private fun hasIcon(icon: ImageVector) =
        compose.onAllNodes(SemanticsMatcher.expectValue(IconSemanticsKey, icon), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun exceedsMaxLines(text: String): Boolean =
        compose.onNodeWithText(text, useUnmergedTree = true).textLayout().hasVisualOverflow

    @Test
    fun `Android removes icons and keeps complete labels in one row when text fits`() {
        pumpBar(180)

        val cancelRect = rect(cancelKey)
        val downloadRect = rect(downloadKey)
        assertEquals(cancelRect.centerY(), downloadRect.centerY())
        assertFalse(hasIcon(Icons.Outlined.Download))
        assertFalse(exceedsMaxLines("下载新版本"))
        assertEquals(180f, (cancelRect.width + downloadRect.width + 10.dp).value, 0.01f)
    }

    @Test
    fun `Android stacks only after complete labels no longer fit`() {
        pumpBar(140)

        val cancelRect = rect(cancelKey)
        val downloadRect = rect(downloadKey)
        assertTrue(downloadRect.top > cancelRect.bottom)
        assertEquals(140f, cancelRect.width.value, 0.01f)
        assertEquals(140f, downloadRect.width.value, 0.01f)
        assertFalse(exceedsMaxLines("下载新版本"))
    }

    @Test
    fun `Android reset and confirm actions remove icons before stacking`() {
        pumpBar(
            220,
            listOf(
                ResponsiveDialogAction(label = "重置", icon = Icons.Filled.RestartAlt, onPressed = {}),
                ResponsiveDialogAction(label = "取消", onPressed = {}),
                ResponsiveDialogAction(label = "确认", icon = Icons.Filled.Check, tone = ButtonTone.primary, onPressed = {}),
            ),
        )

        fun centerY(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().centerY()
        assertEquals(centerY("重置"), centerY("取消"))
        assertEquals(centerY("取消"), centerY("确认"))
        assertFalse(hasIcon(Icons.Filled.RestartAlt))
        assertFalse(hasIcon(Icons.Filled.Check))
    }

    @Test
    @Config(qualifiers = "w600dp-h500dp-mdpi")
    fun `DialogFrame adapts standard actions on Android`() {
        compose.setUiContent { _ ->
            DialogFrame(title = "下载新版本", maxWidth = 300.dp, adaptiveActions = actions(), child = { Text("确认下载？") })
        }

        assertEquals(rect(cancelKey).centerY(), rect(downloadKey).centerY())
    }

    @Test
    @Config(qualifiers = "w420dp-h700dp-mdpi")
    fun `DialogFrame yields fixed content height to the Android IME`() {
        compose.setUiContent { _ ->
            val media = LocalMediaQuery.current
            CompositionLocalProvider(LocalMediaQuery provides media.copy(viewInsets = EdgeInsets(bottom = 300.dp))) {
                DialogFrame(
                    title = "搜索选择",
                    maxWidth = 360.dp,
                    adaptiveActions = actions(),
                    child = {
                        Box(Modifier.testTag("keyboard-safe-dialog-content").height(520.dp)) {
                            Column {
                                Input()
                                Box(Modifier.weight(1f))
                            }
                        }
                    },
                )
            }
        }

        val contentRect = rect("keyboard-safe-dialog-content")
        assertTrue(contentRect.height < 520.dp)
    }
}
