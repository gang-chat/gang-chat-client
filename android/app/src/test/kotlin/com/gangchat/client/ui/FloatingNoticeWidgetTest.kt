package com.gangchat.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.IconSemanticsKey
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.decoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class FloatingNoticeWidgetTest {
    @get:Rule
    val compose = createComposeRule()

    /** Dart's `_productionHost` (with the notification host) or `_overlayFallbackHost`. */
    private fun pumpHost(withHost: Boolean = true, child: @Composable (BuildContext) -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setUiContent(notifications = withHost) { context -> child(context) }
        pump(0)
    }

    /** Dart's `tester.pump(duration)`: advance the clock, then run the frames that follow. */
    private fun pump(millis: Long) {
        // Flush snapshot writes made outside composition (taps) first.
        compose.waitForIdle()
        if (millis > 0) compose.mainClock.advanceTimeBy(millis)
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
    }

    @Composable
    private fun TextButton(label: String, modifier: Modifier = Modifier, onPressed: () -> Unit) {
        Box(modifier.clickable(onClick = onPressed).padding(8.dp)) { Text(label) }
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        pump(0)
    }

    private val closeIcon = SemanticsMatcher.expectValue(IconSemanticsKey, Icons.Filled.Close)

    private fun closeIcons() = compose.onAllNodes(closeIcon, useUnmergedTree = true)

    private fun count(text: String) = compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

    private fun DpRect.centerY() = (top + bottom) / 2

    @Test
    fun `floating notices work in MaterialApp builder without Overlay`() {
        pumpHost { context ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextButton("Show notice") { showFloatingNotice(context, "builder notice", duration = null) }
            }
        }

        tap("Show notice")
        pump(220)

        compose.onNodeWithText("builder notice").assertExists()
        assertEquals(1, closeIcons().fetchSemanticsNodes().size)
    }

    @Test
    fun `floating notices keep newest three and close immediately`() {
        pumpHost { context ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FlowRow {
                    for (label in listOf("one", "two", "three", "four")) {
                        TextButton("Show $label") { showFloatingNotice(context, "notice $label", duration = null) }
                    }
                }
            }
        }

        for (label in listOf("one", "two", "three", "four")) tap("Show $label")
        pump(220)

        compose.onNodeWithText("notice one").assertDoesNotExist()
        compose.onNodeWithText("notice two").assertExists()
        compose.onNodeWithText("notice three").assertExists()
        compose.onNodeWithText("notice four").assertExists()

        val fourTop = compose.onNodeWithText("notice four").getBoundsInRoot().top
        val threeTop = compose.onNodeWithText("notice three").getBoundsInRoot().top
        val twoTop = compose.onNodeWithText("notice two").getBoundsInRoot().top
        assertTrue(fourTop < threeTop)
        assertTrue(threeTop < twoTop)

        closeIcons().onFirst().performClick()
        pump(0)

        compose.onNodeWithText("notice four").assertDoesNotExist()
        compose.onNodeWithText("notice three").assertExists()
        compose.onNodeWithText("notice two").assertExists()
    }

    @Test
    fun `same notice message can be shown as a new event`() {
        pumpHost { context ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextButton("Repeat notice") { showFloatingNotice(context, "repeat notice", duration = null) }
            }
        }

        tap("Repeat notice")
        tap("Repeat notice")
        pump(220)

        assertEquals(2, count("repeat notice"))
    }

    @Test
    fun `auto notices fade out after visible duration`() {
        pumpHost { context ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextButton("Show auto") { showFloatingNotice(context, "auto notice", duration = 100.milliseconds) }
            }
        }

        tap("Show auto")
        pump(220)
        compose.onNodeWithText("auto notice").assertExists()

        pump(100)
        compose.onNodeWithText("auto notice").assertExists()

        pump(220)
        compose.onNodeWithText("auto notice").assertDoesNotExist()
    }

    @Test
    fun `persistent notices do not auto dismiss`() {
        pumpHost { context ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextButton("Show persistent") {
                    showFloatingNotice(context, "persistent notice", tone = FloatingNoticeTone.error, duration = null)
                }
            }
        }

        tap("Show persistent")
        pump(5000)
        compose.onNodeWithText("persistent notice").assertExists()

        closeIcons().onFirst().performClick()
        pump(0)

        compose.onNodeWithText("persistent notice").assertDoesNotExist()
    }

    @Test
    fun `notice emitter only emits fresh notices`() {
        var message by mutableStateOf<String?>(null)
        var eventKey by mutableIntStateOf(0)
        pumpHost { _ ->
            val current = message
            FloatingNoticeEmitter(
                notices = if (current != null) listOf(FloatingNotice(message = current, duration = null, eventKey = eventKey)) else emptyList(),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FlowRow {
                        TextButton("Emit same") {
                            message = "same notice"
                            eventKey++
                        }
                        TextButton("Emit next") {
                            message = "next notice"
                            eventKey++
                        }
                    }
                }
            }
        }

        tap("Emit same")
        pump(220)
        assertEquals(1, count("same notice"))

        tap("Emit same")
        pump(220)
        assertEquals(2, count("same notice"))

        tap("Emit next")
        pump(220)
        assertEquals(2, count("same notice"))
        assertEquals(1, count("next notice"))
    }

    @Test
    fun `standalone overlay fallback still works without host`() {
        pumpHost(withHost = false) { context ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextButton("Show notice") { showFloatingNotice(context, "builder notice", duration = null) }
            }
        }

        tap("Show notice")
        pump(220)
        compose.onNodeWithText("builder notice").assertExists()

        closeIcons().onFirst().performClick()
        pump(0)

        compose.onNodeWithText("builder notice").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w800dp-h600dp-mdpi")
    fun `floating notice does not relayout or block page controls`() {
        var pageTaps by mutableIntStateOf(0)
        pumpHost { context ->
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .offset(260.dp, 7.dp)
                        .testTag("top-search-box")
                        .size(280.dp, 30.dp)
                        .decoration(UiColors.surface, RoundedCornerShape(UiRadii.md), UiColors.border),
                )
                TextButton("page taps: $pageTaps", Modifier.offset(16.dp, 18.dp).testTag("page-control")) { pageTaps++ }
                TextButton("Show floating", Modifier.align(Alignment.BottomCenter)) {
                    showFloatingNotice(context, "small notice", duration = null)
                }
            }
        }
        val before = compose.onNodeWithTag("page-control").getBoundsInRoot()
        val searchRect = compose.onNodeWithTag("top-search-box").getBoundsInRoot()

        tap("Show floating")
        pump(220)

        assertEquals(before, compose.onNodeWithTag("page-control").getBoundsInRoot())
        assertTrue(compose.onNodeWithText("small notice").getBoundsInRoot().top > searchRect.bottom)
        compose.onNodeWithText("page taps: 0").assertExists()

        compose.onNodeWithTag("page-control").performClick()
        pump(0)

        compose.onNodeWithText("page taps: 1").assertExists()
        compose.onNodeWithText("small notice").assertExists()
    }

    @Test
    fun `floating notice matches app text and close button styling`() {
        pumpHost { context ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextButton("Show style") { showFloatingNotice(context, "style notice", duration = null) }
            }
        }

        tap("Show style")
        pump(220)

        val message = compose.onNodeWithText("style notice").textLayout().layoutInput.style
        assertEquals(UiTypography.label.fontSize, message.fontSize)
        assertEquals(UiTypography.label.lineHeight, message.lineHeight)
        assertEquals(FontWeight.W500, message.fontWeight)
        assertEquals(UiColors.text, message.color)
        assertEquals(UiFonts.client, message.fontFamily)
        assertEquals(TextDecoration.None, message.textDecoration)

        // As in Flutter, the 15 dp icon is forced to its 26 dp button's tight
        // size and paints its glyph centred; only the colour is observable.
        val icon = closeIcons().onFirst().getBoundsInRoot()
        assertEquals(UiColors.textMuted, closeIcons().onFirst().iconColor())
        val button = compose.onNodeWithTag(floatingNoticeCloseButtonTag, useUnmergedTree = true).getBoundsInRoot()
        assertEquals(26f, button.width.value, 0.01f)
        assertEquals(26f, button.height.value, 0.01f)
        assertEquals(button, icon)

        val messageCenter = compose.onNodeWithText("style notice").getBoundsInRoot().centerY()
        assertEquals(icon.centerY().value, messageCenter.value, 2f)
    }
}
