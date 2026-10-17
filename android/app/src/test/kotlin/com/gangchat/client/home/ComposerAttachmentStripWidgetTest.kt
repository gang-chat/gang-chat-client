package com.gangchat.client.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.ComposerAttachmentStatus
import com.gangchat.client.app.ComposerAttachmentView
import com.gangchat.client.ui.ShadowNoOpMagnifier
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.widgets.IconSemanticsKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Port of test/widget/home/composer_attachment_strip_widget_test.dart. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class ComposerAttachmentStripWidgetTest {
    @get:Rule
    val compose = createComposeRule()

    private fun hasIcon(icon: ImageVector) = SemanticsMatcher.expectValue(IconSemanticsKey, icon)

    private fun host(attachments: List<ComposerAttachmentView>, onRemove: (String) -> Unit = {}, onRetry: ((String) -> Unit)? = null) {
        compose.setChatContent(width = 360.dp) { _ -> ComposerAttachmentStripForTest(attachments = attachments, onRemove = onRemove, onRetry = onRetry) }
    }

    @Test
    fun `strip lists each staged file with its name`() {
        host(
            listOf(
                ComposerAttachmentView(id = "1", filename = "report.pdf", status = ComposerAttachmentStatus.uploaded, sizeBytes = 2048, mimeType = "application/pdf"),
                ComposerAttachmentView(id = "2", filename = "photo.png", status = ComposerAttachmentStatus.uploaded, mimeType = "image/png"),
            ),
        )

        compose.onNodeWithText("report.pdf").assertExists()
        compose.onNodeWithText("photo.png").assertExists()
        compose.onTooltip("report.pdf").assertExists()
        compose.onTooltip("photo.png").assertExists()
        // Size shows only when the picker reported one.
        compose.onNodeWithText("2.0 KB").assertExists()
        compose.onNode(hasIcon(Icons.Outlined.PictureAsPdf), useUnmergedTree = true).assertExists()
        compose.onNode(hasIcon(Icons.Outlined.Image), useUnmergedTree = true).assertExists()
        assertEquals(2, compose.onAllNodes(hasIcon(Icons.Filled.Close), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `the remove button reports the chip id`() {
        var removed: String? = null
        host(listOf(ComposerAttachmentView(id = "abc", filename = "a.txt", status = ComposerAttachmentStatus.uploaded)), onRemove = { removed = it })

        compose.onNode(hasIcon(Icons.Filled.Close), useUnmergedTree = true).performClick()
        assertEquals("abc", removed)
    }

    @Test
    fun `an uploading chip shows progress and no type glyph`() {
        host(listOf(ComposerAttachmentView(id = "1", filename = "clip.mp4", status = ComposerAttachmentStatus.uploading, progress = 0.42, mimeType = "video/mp4")))

        compose.onNodeWithText("上传中 42%").assertExists()
        // Flutter's CircularProgressIndicator (determinate at 42%).
        assertEquals(1, compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.42f, 0f..1f)), useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNode(hasIcon(Icons.Outlined.Movie), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a failed chip exposes a tappable retry`() {
        var retried: String? = null
        host(listOf(ComposerAttachmentView(id = "x", filename = "doc.pdf", status = ComposerAttachmentStatus.failed)), onRetry = { retried = it })

        assertEquals(1, compose.onAllNodesWithText("上传失败，点击重试").fetchSemanticsNodes().size)
        compose.onNode(hasIcon(Icons.Filled.Refresh), useUnmergedTree = true).performClick()
        assertEquals("x", retried)
    }
}
