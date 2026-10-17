package com.gangchat.client.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import com.gangchat.client.plugins.SharedPreferences
import com.gangchat.client.shell.ShellTestLoop
import com.gangchat.client.shell.rect
import com.gangchat.client.ui.avatarInitials
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.ui.onTooltip
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * The shared fixture of the home shell suites: an activity-hosted compose
 * rule (system back reaches the shell), the manual clock, the test event
 * loop, and Dart's finders and gestures for the shell.
 */
abstract class HomeShellTestBase {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val loop = ShellTestLoop()

    @Before
    fun setUpShell() {
        SharedPreferences.setMockInitialValues(emptyMap())
        loop.start()
        installTestImageLoader()
        compose.mainClock.autoAdvance = false
    }

    @After
    fun tearDownShell() = loop.finish()

    protected fun tag(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    protected fun tagExists(tag: String): Boolean = compose.exists(hasTestTag(tag))

    protected fun isSelected(node: SemanticsNodeInteraction): Boolean =
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true

    /** Dart's `ButtonIcon.selected`: the surface under the tagged button reports it. */
    protected fun buttonSelected(tag: String): Boolean =
        compose.exists(
            (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))) and
                SemanticsMatcher("selected") { it.config.getOrNull(SemanticsProperties.Selected) == true },
        )

    private val notEditable: SemanticsMatcher =
        SemanticsMatcher("not editable") { node -> node.config.getOrNull(SemanticsProperties.EditableText) == null }

    /** Dart's `tap(find.text(text))` on a label (not a text field showing it). */
    protected fun tapText(text: String) {
        compose.onNode(hasText(text) and notEditable).performClick()
        compose.settleShell()
    }

    protected fun tapTag(tag: String) {
        tag(tag).performClick()
        compose.settleShell()
    }

    protected fun tapTooltip(tooltip: String) {
        compose.onTooltip(tooltip).performClick()
        compose.settleShell()
    }

    /** Dart's secondary click or hold: a long press on [node]. */
    protected fun hold(node: SemanticsNodeInteraction) {
        node.performTouchInput { longClick(center) }
        compose.settleShell()
    }

    /** Dart's `tapAt(offset)` on the whole window. */
    protected fun tapAt(x: Dp, y: Dp) {
        compose.onAllNodes(SemanticsMatcher("root") { it.parent == null }).onFirst().performTouchInput { click(Offset(x.toPx(), y.toPx())) }
        compose.settleShell()
    }

    protected fun inTag(ancestorTag: String, matcher: SemanticsMatcher): SemanticsMatcher = matcher and hasAnyAncestor(hasTestTag(ancestorTag))

    protected fun dockHasText(text: String): Boolean = compose.exists(inTag(homeTitleLiveRoomTag, hasText(text)))

    protected fun dockHasIcon(icon: ImageVector): Boolean = compose.exists(inTag(homeTitleLiveRoomTag, hasIcon(icon)))

    protected fun dockHasAvatar(roomName: String): Boolean = dockHasText(avatarInitials(roomName))

    /** Whether the tagged node (or something inside it) shows [icon]. */
    protected fun tagHasIcon(tag: String, icon: ImageVector): Boolean =
        compose.exists(hasIcon(icon) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

    /** Dart's `_openLiveChannelFromHeader`. */
    protected fun openLiveChannelFromHeader() {
        tag(chatHeaderLiveButtonTag).assertExists()
        tapTag(chatHeaderLiveButtonTag)
    }

    /** Dart's `tap(find.widgetWithText(ui.Button, '加入'))` in the live pane. */
    protected fun joinLive() = tapTag("live-control:join")

    protected fun openAlphaAndJoinLive() {
        tapText("Alpha Room")
        openLiveChannelFromHeader()
        joinLive()
    }

    /** Dart's `enterText` into the title search plus the 320 ms debounce pump. */
    protected fun enterSearch(text: String) {
        compose.textFieldIn(homeTitleSearchTag).performTextReplacement(text)
        compose.pumpShell(320)
        compose.settleShell()
    }

    protected fun searchResultsOpen(): Boolean = tagExists(homeTitleSearchResultsTag)

    protected fun widthOf(tag: String): Dp = tag(tag).rect().let { it.right - it.left }

    /**
     * Dart's `ensureVisible` inside the room settings form: scrolls the lazy
     * form list to [matcher] with the clock running just for the scroll.
     */
    protected fun scrollFormTo(matcher: SemanticsMatcher) {
        val list = compose.onAllNodes(hasScrollToNodeAction() and hasAnyDescendant(hasTestTag(roomSettingsNameInputTag))).onFirst()
        scrollWithClock { list.performScrollToNode(matcher) }
    }

    /** Runs a scroll action with the clock advancing on its own (the action animates). */
    protected fun scrollWithClock(action: () -> Unit) {
        val manual = !compose.mainClock.autoAdvance
        compose.mainClock.autoAdvance = true
        try {
            action()
        } finally {
            compose.mainClock.autoAdvance = !manual
        }
        compose.settleShell()
    }

    /** Dart's `find.byType(LiveChannelPane)`: the pane's controls are on screen. */
    protected fun livePaneShown(): Boolean =
        compose.exists(SemanticsMatcher("live control") { node -> node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("live-control:") == true })

    /** Dart's `binding.handlePopRoute()`: the system back gesture. */
    protected fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.settleShell()
    }
}
