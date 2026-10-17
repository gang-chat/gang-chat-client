package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Dart's `tester.view.devicePixelRatio = 1; physicalSize = 640×120`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w640dp-h120dp-mdpi")
@OptIn(ExperimentalTestApi::class)
class SegmentedControlTest {
    @get:Rule
    val compose = createComposeRule()

    private fun rect(tag: String, unmerged: Boolean = true): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = unmerged).getBoundsInRoot()

    private fun segment(index: Int): DpRect = rect(segmentedControlSegmentTag(index))

    private fun textRect(text: String): DpRect = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot()

    private fun DpRect.centerX() = (left + right) / 2

    private fun DpRect.centerY() = (top + bottom) / 2

    private fun scrollRange(): ScrollAxisRange =
        compose.onNodeWithTag(segmentedControlScrollViewTag).fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]

    @Test
    fun `segmented control badge follows content instead of segment edge`() {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(600.dp)) {
                    SegmentedControl(
                        expanded = true,
                        value = 1,
                        onChanged = {},
                        segments =
                            listOf(
                                Segment(value = 0, label = "Members"),
                                Segment(
                                    value = 1,
                                    label = "New Members",
                                    icon = Icons.Filled.PersonAddAlt1,
                                    showBadge = true,
                                    badgeKey = "new-members-tab-badge",
                                ),
                                Segment(value = 2, label = "Blacklist"),
                            ),
                    )
                }
            }
        }

        val badgeRect = rect("new-members-tab-badge")
        val labelRect = textRect("New Members")
        val segmentRect = segment(1)

        assertTrue(badgeRect.centerX() > labelRect.right - 2.dp)
        assertTrue(badgeRect.centerX() < labelRect.right + 12.dp)
        assertTrue(badgeRect.centerY() < labelRect.centerY())
        assertTrue(segmentRect.right - badgeRect.right > 6.dp)
    }

    @Test
    fun `segmented control fills the row with content-adaptive widths`() {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(480.dp)) {
                    SegmentedControl(
                        expanded = true,
                        value = 0,
                        onChanged = {},
                        modifier = Modifier.testTag("control"),
                        segments =
                            listOf(
                                Segment(value = 0, label = "A"),
                                Segment(value = 1, label = "Medium"),
                                Segment(value = 2, label = "Much Longer Label"),
                            ),
                    )
                }
            }
        }

        val segmentRects = (0..2).map { segment(it) }
        val controlRect = rect("control", unmerged = false)
        assertTrue(segmentRects[0].width < segmentRects[1].width)
        assertTrue(segmentRects[1].width < segmentRects[2].width)
        assertEquals(controlRect.left.value, segmentRects.first().left.value, 0.01f)
        assertEquals(controlRect.right.value, segmentRects.last().right.value, 0.01f)
        for ((index, label) in listOf("A", "Medium", "Much Longer Label").withIndex()) {
            assertTrue(segment(index).width > textRect(label).width)
        }
        compose.onTooltip("选择选项").assertDoesNotExist()
    }

    @Test
    fun `segmented control avoids fractional overflow at its minimum width`() {
        val segments =
            listOf(
                Segment(value = 0, label = "全部 16"),
                Segment(value = 1, label = "语音 0"),
                Segment(value = 2, label = "在线 2"),
                Segment(value = 3, label = "离线 14"),
            )
        compose.setUiContent { context ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(SegmentedControl.minimumWidthFor(context, segments))) {
                    SegmentedControl(expanded = true, value = 0, onChanged = {}, segments = segments)
                }
            }
        }

        compose.onNodeWithTag(segmentedControlScrollViewTag).assertDoesNotExist()
        for (segment in segments) compose.onNodeWithText(segment.label, useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "w260dp-h160dp-mdpi")
    fun `segmented control uses hidden horizontal overflow controls when needed`() {
        var selected by mutableStateOf("info")
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(220.dp)) {
                    SegmentedControl(
                        expanded = true,
                        value = selected,
                        onChanged = { selected = it },
                        segments =
                            listOf(
                                Segment(value = "info", label = "房间信息", icon = Icons.Outlined.Info),
                                Segment(value = "profile", label = "个性化设置", icon = Icons.Filled.Tune),
                                Segment(value = "messages", label = "消息记录", icon = Icons.Filled.History),
                                Segment(value = "stickers", label = "表情包管理", icon = Icons.Outlined.EmojiEmotions),
                            ),
                    )
                }
            }
        }

        compose.onNodeWithTag(segmentedControlScrollViewTag).assertExists()
        compose.onTooltip("选择选项").assertExists()
        compose.onTooltip("回到开头").assertDoesNotExist()
        for (label in listOf("房间信息", "个性化设置", "消息记录", "表情包管理")) {
            assertEquals(TextOverflow.Visible, compose.onNodeWithText(label, useUnmergedTree = true).textLayout().layoutInput.overflow)
        }

        compose.onNodeWithTag(segmentedControlScrollViewTag).performTouchInput {
            down(center)
            moveBy(Offset(-40.dp.toPx(), 0f))
            moveBy(Offset(-40.dp.toPx(), 0f))
            moveBy(Offset(-40.dp.toPx(), 0f))
            up()
        }
        compose.waitForIdle()

        compose.onTooltip("回到开头").assertExists()

        compose.onTooltip("选择选项").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()

        compose.onNodeWithText("当前").assertDoesNotExist()
        assertEquals(UiColors.accent, compose.onAllNodesWithText("房间信息", useUnmergedTree = true).onLast().textColor())
        compose.onAllNodesWithText("消息记录", useUnmergedTree = true).onLast().performClick()
        compose.waitForIdle()

        assertEquals("messages", selected)
        assertEquals(rect(segmentedControlScrollViewTag).centerX().value, segment(2).centerX().value, 1f)

        compose.onTooltip("选择选项").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        compose.onAllNodesWithText("表情包管理", useUnmergedTree = true).onLast().performClick()
        compose.waitForIdle()

        assertEquals("stickers", selected)
        val range = scrollRange()
        assertEquals(range.maxValue(), range.value(), 1f)

        compose.onTooltip("回到开头").performClick()
        compose.waitForIdle()

        compose.onTooltip("回到开头").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w260dp-h120dp-mdpi")
    fun `overflow control supports primary-button mouse dragging`() {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(220.dp)) {
                    SegmentedControl(
                        expanded = true,
                        value = 0,
                        onChanged = {},
                        segments =
                            listOf(
                                Segment(value = 0, label = "用户资料", icon = Icons.Filled.Badge),
                                Segment(value = 1, label = "偏好设置", icon = Icons.Filled.Tune),
                                Segment(value = 2, label = "隐私和安全", icon = Icons.Filled.Shield),
                                Segment(value = 3, label = "语音和视频", icon = Icons.Filled.GraphicEq),
                            ),
                    )
                }
            }
        }

        assertEquals(0f, scrollRange().value(), 0f)

        compose.onNodeWithTag(segmentedControlScrollViewTag).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(-50f, 0f))
            moveBy(Offset(-50f, 0f))
            release()
        }
        compose.waitForIdle()

        assertTrue(scrollRange().value() > 0f)
        compose.onTooltip("回到开头").assertExists()
    }

    @Test
    @Config(qualifiers = "w260dp-h120dp-mdpi")
    fun `overflow control centers the initial selection or reaches its edge`() {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(220.dp)) {
                    SegmentedControl(
                        expanded = true,
                        value = 3,
                        onChanged = {},
                        segments =
                            listOf(
                                Segment(value = 0, label = "房间信息", icon = Icons.Filled.Info),
                                Segment(value = 1, label = "个性设置", icon = Icons.Filled.Tune),
                                Segment(value = 2, label = "消息记录", icon = Icons.Filled.History),
                                Segment(value = 3, label = "表情管理", icon = Icons.Filled.EmojiEmotions),
                            ),
                    )
                }
            }
        }

        val range = scrollRange()
        assertEquals(range.maxValue(), range.value(), 1f)
        compose.onTooltip("回到开头").assertExists()
    }

    @Test
    fun `keyed segmented controls do not inherit previous thumb position`() {
        var generation by mutableIntStateOf(0)
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(300.dp)) {
                    key(if (generation == 0) "first-segmented-setting" else "second-segmented-setting") {
                        SegmentedControl(
                            expanded = true,
                            value = if (generation == 0) 0 else 2,
                            onChanged = {},
                            segments =
                                listOf(
                                    Segment(value = 0, label = "One"),
                                    Segment(value = 1, label = "Two"),
                                    Segment(value = 2, label = "Three"),
                                ),
                        )
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        generation = 1
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()

        val thumb = rect(segmentedControlThumbTag)
        val third = segment(2)
        val center = thumb.center()
        assertTrue(center.first >= third.left && center.first <= third.right)
        assertTrue(center.second >= third.top && center.second <= third.bottom)
    }

    private fun DpRect.center() = centerX() to centerY()
}
