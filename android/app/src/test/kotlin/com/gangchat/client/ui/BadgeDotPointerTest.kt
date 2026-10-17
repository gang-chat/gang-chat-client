package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Regression test: a badge dot never takes the taps meant for the control that carries it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class BadgeDotPointerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tapping a badged segment label selects the segment`() {
        var selected by mutableStateOf("members")
        compose.setUiContent { _ ->
            Box(Modifier.width(360.dp)) {
                SegmentedControl(
                    expanded = true,
                    value = selected,
                    onChanged = { selected = it },
                    segments =
                        listOf(
                            Segment(value = "members", label = "房间成员"),
                            Segment(value = "new", label = "新成员", showBadge = true),
                            Segment(value = "blocked", label = "黑名单"),
                        ),
                )
            }
        }

        compose.onNodeWithText("新成员").performClick()
        compose.waitForIdle()

        assertEquals("new", selected)
    }
}
