package com.gangchat.client.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MediaCacheController
import com.gangchat.client.app.userAvatarLabel
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.AppNotificationHost
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.MediaCacheScope
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTheme
import com.gangchat.client.ui.testMediaCacheController
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.LocalMediaQuery
import com.gangchat.client.widgets.RootNavigator
import com.gangchat.client.widgets.WindowMediaQuery

/** Test tag of [AvatarHoverCardForTest]'s message avatar (Dart finds it as `find.byType(Avatar).first`). */
const val messageAvatarTag = "message-avatar"

val avatarHoverCardTestCurrentUser =
    CurrentUser(
        id = "avatar-hover-card-test-user",
        uid = "",
        username = "viewer",
        displayName = "Viewer",
        bio = "",
        gender = "secret",
        email = null,
        emailPublic = false,
        phoneNumber = null,
        phoneNumberPublic = false,
        avatarUrl = null,
        defaultAvatarKey = "green-2",
        isSuperuser = false,
        createdAt = null,
    )

/**
 * Dart's `AvatarHoverCardForTest` (`home/chat_profile_card.dart`, ported with
 * the chat pane): a 32 dp message avatar with the user's hover card.
 */
@Composable
fun AvatarHoverCardForTest(
    user: UserSummary,
    currentUser: CurrentUser = avatarHoverCardTestCurrentUser,
    onResolveProfile: UserProfileResolver? = null,
    onResolveRoomProfile: RoomProfileResolver? = null,
    onEnterCommonRoom: ((PublicRoom) -> Unit)? = null,
    profileActionBuilder: UserProfileActionBuilder? = null,
    inLive: Boolean = false,
    showRoomRole: Boolean = true,
) {
    UserHoverCard(
        user = user,
        currentUser = currentUser,
        onResolveProfile = onResolveProfile,
        onResolveRoomProfile = onResolveRoomProfile,
        onEnterCommonRoom = onEnterCommonRoom,
        profileActionBuilder = profileActionBuilder,
        inLive = inLive,
        showRoomRole = showRoomRole,
    ) {
        Avatar(label = userAvatarLabel(user), size = 32.dp, showBorder = false, modifier = Modifier.testTag(messageAvatarTag))
    }
}

/**
 * The app root for home widget tests (Dart's `MaterialApp(home: Scaffold(body:
 * ...))`): theme, window media query (with [viewPadding] overriding the
 * system insets when given), [cache] above the navigator, notices, and the
 * page background.
 */
fun ComposeContentTestRule.setHomeContent(
    cache: MediaCacheController = testMediaCacheController(),
    viewPadding: EdgeInsets? = null,
    content: @Composable (BuildContext) -> Unit,
) {
    setContent {
        UiTheme {
            WindowMediaQuery {
                val media = LocalMediaQuery.current
                val data = if (viewPadding == null) media else media.copy(viewPadding = viewPadding, padding = viewPadding)
                CompositionLocalProvider(LocalMediaQuery provides data) {
                    MediaCacheScope(cache) {
                        AppNotificationHost {
                            RootNavigator { context ->
                                Box(Modifier.fillMaxSize().background(UiColors.background).testTag("page")) { content(context) }
                            }
                        }
                    }
                }
            }
        }
    }
    waitForIdle()
}

/** Advances the clock by [millis] and lets the resulting frames run (Dart's `pump(duration)`). */
fun ComposeContentTestRule.pump(millis: Long) {
    mainClock.advanceTimeBy(millis)
    repeat(3) { mainClock.advanceTimeByFrame() }
    waitForIdle()
}

/** The colour at ([x], [y]) dp inside [node]. */
fun ComposeContentTestRule.pixelIn(node: SemanticsNodeInteraction, x: Float, y: Float): Color {
    val pixels = onRoot().captureToImage().toPixelMap()
    val bounds = node.fetchSemanticsNode().boundsInRoot
    return pixels[(bounds.left + x * density.density).toInt(), (bounds.top + y * density.density).toInt()]
}
