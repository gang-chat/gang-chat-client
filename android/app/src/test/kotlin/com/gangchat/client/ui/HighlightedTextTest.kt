package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class HighlightedTextTest {
    @get:Rule
    val compose = createComposeRule()

    private fun SemanticsNodeInteraction.annotated(): AnnotatedString =
        fetchSemanticsNode().config[SemanticsProperties.Text].single()

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }

    @Test
    fun `highlights all case-insensitive query matches`() {
        compose.setUiContent { _ -> HighlightedText(text = "Alpha beta alpha", query = "ALPHA", style = UiTypography.body) }

        val text = compose.onNodeWithText("Alpha beta alpha").annotated()
        val highlighted = text.spanStyles.filter { it.item.color == UiColors.accent }
        assertEquals(2, highlighted.size)
    }

    @Test
    fun `uses plain text when query does not match`() {
        compose.setUiContent { _ -> HighlightedText(text = "Alpha beta", query = "gamma", style = UiTypography.body) }

        val text = compose.onNodeWithText("Alpha beta").annotated()
        assertEquals("Alpha beta", text.text)
        assertTrue(text.spanStyles.isEmpty())
    }

    @Test
    fun `adaptive highlighted text wraps fully and shrinks only after its comfortable lines`() {
        val value = "iGAYice5king-long-room-member-name"
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(84.dp)) {
                    AdaptiveHighlightedText(text = value, query = "king", comfortableLines = 1, style = TextStyle(fontSize = 14.sp))
                }
            }
        }

        val node = compose.onNodeWithText(value)
        val input = node.layout().layoutInput
        assertEquals(Int.MAX_VALUE, input.maxLines)
        assertEquals(TextOverflow.Clip, input.overflow)
        assertTrue(input.softWrap)
        assertTrue(input.style.fontSize.value < 14f)
        assertTrue(node.getBoundsInRoot().height > 20.dp)
    }
}
