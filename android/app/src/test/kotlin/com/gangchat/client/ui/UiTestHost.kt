package com.gangchat.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.RootNavigator
import com.gangchat.client.widgets.WindowMediaQuery

/**
 * The app root as the tests need it (Dart's `MaterialApp(theme: uiTheme(),
 * home: Scaffold(body: ...))`): theme, window media query, root navigator,
 * and the scaffold's background.
 */
fun ComposeContentTestRule.setUiContent(
    background: Color = UiColors.background,
    notifications: Boolean = false,
    content: @Composable (BuildContext) -> Unit,
) {
    setContent {
        UiTheme {
            WindowMediaQuery {
                val app: @Composable () -> Unit = {
                    RootNavigator { context ->
                        Box(Modifier.fillMaxSize().background(background)) { content(context) }
                    }
                }
                if (notifications) AppNotificationHost { app() } else app()
            }
        }
    }
}

/** Matches a node carrying [message] as its tooltip (Flutter's `find.byTooltip`). */
fun hasTooltip(message: String): SemanticsMatcher = SemanticsMatcher.expectValue(TooltipSemanticsKey, message)

/**
 * The node carrying [message] as its tooltip, in the unmerged tree: the
 * tooltip property otherwise merges into the nearest merging ancestor (a text
 * field, say) and the lookup would hit that instead.
 */
fun ComposeContentTestRule.onTooltip(message: String): SemanticsNodeInteraction =
    onNode(hasTooltip(message), useUnmergedTree = true)

/** All nodes carrying [message] as their tooltip, in the unmerged tree. */
fun ComposeContentTestRule.onAllTooltips(message: String) = onAllNodes(hasTooltip(message), useUnmergedTree = true)

/** The layout a `Text` (or text field) node was laid out with. */
fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    val node = fetchSemanticsNode()
    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    return results.first()
}

/** The text colour a `Text` node was laid out with. */
fun SemanticsNodeInteraction.textColor(): Color = textLayout().layoutInput.style.color

/**
 * The colour painted at the centre of an icon node (where the glyphs the
 * tests sample, like the close cross, are solid).
 */
fun SemanticsNodeInteraction.iconColor(): Color {
    val pixels = captureToImage().toPixelMap()
    return pixels[pixels.width / 2, pixels.height / 2]
}

/** The text a node shows. */
fun SemanticsNodeInteraction.textValue(): String =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: ""
