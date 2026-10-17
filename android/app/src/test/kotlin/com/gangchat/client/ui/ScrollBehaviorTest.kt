package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScrollBehaviorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Android keeps native vertical list width unchanged`() {
        compose.setUiContent { _ ->
            Box(Modifier.testTag("app").size(240.dp)) {
                // GangScrollBehavior adds no scrollbar or gutter on Android.
                LazyColumn(Modifier.testTag("scrollable-list").fillMaxSize()) {
                    items(20) { index -> Box(Modifier.size(80.dp)) { Text("$index") } }
                }
            }
        }

        val listRect = compose.onNodeWithTag("scrollable-list").getBoundsInRoot()
        val appRect = compose.onNodeWithTag("app").getBoundsInRoot()
        assertEquals(appRect.right, listRect.right)
        assertEquals(0.0, GangScrollBehavior.verticalScrollbarGutter - 8.0, 0.0)
    }
}
