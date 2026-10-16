package com.gangchat.client.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.testing.GangTestRule
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
class WidgetsSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    private class CounterWidget(val label: String)

    private class CounterState : ViewState<CounterWidget>() {
        var count = 0
        var disposed = false

        override fun dispose() {
            disposed = true
        }

        @Composable
        override fun build() {
            Text("${widget.label}: $count", Modifier.clickable { setState { count += 1 } })
        }
    }

    @Test
    fun viewStateRebuildsOnSetState() {
        compose.setContent {
            StatefulWidget(CounterWidget("taps")) { CounterState() }
        }
        compose.onNodeWithText("taps: 0").performClick()
        compose.onNodeWithText("taps: 1").assertExists()
    }

    @Test
    fun dialogReturnsPopResult() {
        var result: Boolean? = null
        compose.setContent {
            WindowMediaQuery {
                RootNavigator { context ->
                    Box(Modifier.fillMaxSize().background(Color(0xFF181C24))) {
                        Text(
                            "open",
                            Modifier.clickable {
                                unawaited {
                                    result =
                                        showDialog<Boolean>(context) { dialogContext ->
                                            Dialog {
                                                Column(
                                                    Modifier
                                                        .boxShadow(listOf(BoxShadow(Color(0x66000000), DpOffset(0.dp, 8.dp), 24.dp)), RoundedCornerShape(16.dp))
                                                        .background(Color(0xFF232733), RoundedCornerShape(16.dp))
                                                        .padding(20.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                ) {
                                                    Text("确认操作？", style = flutterTextStyle(color = Color.White, fontSize = 16.0, height = 1.4))
                                                    Box(Modifier.size(12.dp))
                                                    Text(
                                                        "确定",
                                                        Modifier.clickable { Navigator.of(dialogContext).pop(true) },
                                                        style = flutterTextStyle(color = Color(0xFF7AA2FF), fontSize = 14.0),
                                                    )
                                                }
                                            }
                                        }
                                }
                            },
                            style = flutterTextStyle(color = Color.White, fontSize = 14.0),
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("open").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("确认操作？").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/widgets_dialog.png")
        compose.onNodeWithText("确定").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("确认操作？").assertDoesNotExist()
        assertEquals(true, result)
    }
}
