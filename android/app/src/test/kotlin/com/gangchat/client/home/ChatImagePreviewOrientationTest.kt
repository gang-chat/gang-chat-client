package com.gangchat.client.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.MediaCacheController
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.shell.AndroidDisplayRotationReader
import com.gangchat.client.shell.AppOrientationController
import com.gangchat.client.platform.DeviceOrientation
import com.gangchat.client.ui.AppNotificationHost
import com.gangchat.client.ui.TooltipSemanticsKey
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTheme
import com.gangchat.client.ui.installTestImageLoader
import com.gangchat.client.ui.onTooltip
import com.gangchat.client.ui.solidPng
import com.gangchat.client.ui.testMediaCacheController
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.LocalMediaQuery
import com.gangchat.client.widgets.RootNavigator
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.WindowMediaQuery
import com.gangchat.client.widgets.rememberBuildContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dart's `chat_image_preview_orientation_test.dart`. Flutter resizes the test
 * view to simulate a rotation; here the host lays the app out at [viewSize]
 * (with a matching `MediaQuery`) inside a window large enough for both
 * orientations. System back goes through the activity's back dispatcher
 * (Dart's `handlePopRoute`). Windows and macOS cases are desktop-only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h800dp-mdpi")
@OptIn(ExperimentalTestApi::class)
class ChatImagePreviewOrientationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var viewSize by mutableStateOf(DpSize(400.dp, 800.dp))

    private fun host(
        orientationController: AppOrientationController,
        backdropCapture: ImagePreviewBackdropCapture? = null,
        imageUrl: String = "invalid-preview-url",
        mediaCache: MediaCacheController? = null,
        displayRotationReader: AndroidDisplayRotationReader? = null,
    ) {
        compose.setContent {
            UiTheme {
                WindowMediaQuery {
                    val media = LocalMediaQuery.current.copy(size = viewSize, padding = EdgeInsets.zero, viewPadding = EdgeInsets.zero)
                    CompositionLocalProvider(LocalMediaQuery provides media) {
                        Box(Modifier.fillMaxSize().wrapContentSize(Alignment.TopStart).size(viewSize)) {
                            AppNotificationHost {
                                RootNavigator { context ->
                                    Box(Modifier.fillMaxSize().background(UiColors.background)) {
                                        Text(
                                            "open",
                                            modifier =
                                                Modifier.clickable {
                                                    unawaited {
                                                        showChatImagePreview(
                                                            context,
                                                            imageUrl = imageUrl,
                                                            suggestedName = "preview.png",
                                                            actions =
                                                                ChatImagePreviewActions(
                                                                    onDownload = { _, _ -> },
                                                                    onSaveAs = { _, _ -> },
                                                                    onCopyToClipboard = { _ -> },
                                                                    mediaCache = mediaCache ?: testMediaCacheController(),
                                                                ),
                                                            orientationController = orientationController,
                                                            backdropCapture = backdropCapture,
                                                            displayRotationReader = displayRotationReader,
                                                        )
                                                    }
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tapOpen() {
        compose.onNodeWithText("open").performClick()
    }

    private fun systemBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    }

    private fun buttonIconCount(): Int = compose.onAllNodes(SemanticsMatcher.keyIsDefined(TooltipSemanticsKey), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun closeButtonCount(): Int = compose.onAllNodes(SemanticsMatcher.expectValue(TooltipSemanticsKey, "关闭"), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun sizeOf(tag: String): IntSize = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().size

    // mdpi: one pixel per dp.
    private fun dpSize(width: Int, height: Int) = IntSize(width, height)

    /** Whether the page under the preview is still painted (a non-opaque route). */
    private fun pageBelowVisible(): Boolean =
        try {
            compose.onNodeWithText("open").assertIsDisplayed()
            true
        } catch (_: AssertionError) {
            false
        }

    /**
     * The preview image's on-screen extent over its unscaled (contain-fit)
     * extent. The viewer clips the zoomed image along its short axis, so the
     * long (unclipped) side measures the scale.
     */
    private fun previewImageScale(unscaledExtentDp: Float): Float {
        val bounds = compose.onNodeWithTag(chatImagePreviewImageTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return maxOf(bounds.width, bounds.height) / unscaledExtentDp
    }

    private fun zoomPreview() {
        compose.onNodeWithTag(chatImagePreviewViewerTag).performMouseInput {
            moveTo(center)
            scroll(-1f)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun portraitBackdrop(): ImagePreviewBackdropSnapshot {
        val image = ImageBitmap(200, 400)
        Canvas(image).drawRect(0f, 0f, 200f, 400f, Paint().apply { color = Color.Blue })
        return ImagePreviewBackdropSnapshot(image = image, logicalSize = DpSize(400.dp, 800.dp))
    }

    @Test
    fun `Android image preview allows landscape and restores portrait on close`() {
        val requests = mutableListOf<List<DeviceOrientation>>()
        var displayQuarterTurns = 0
        val controller =
            AppOrientationController(
                logicalViewSize = { DpSize(400.dp, 800.dp) },
                setPreferredOrientations = { orientations -> requests.add(orientations.toList()) },
            )
        host(controller, backdropCapture = { portraitBackdrop() }, displayRotationReader = { displayQuarterTurns })
        tapOpen()
        compose.waitForIdle()
        compose.pump(200)

        assertEquals(listOf(AppOrientationController.fullScreenMedia), requests)
        assertFalse(pageBelowVisible())
        assertEquals(dpSize(400, 800), sizeOf(chatImagePreviewFrozenBackdropTag))
        compose.onNodeWithTag(chatImagePreviewFrozenBackdropRotationTag(0), useUnmergedTree = true).assertExists()
        assertEquals(4, buttonIconCount())

        displayQuarterTurns = 1
        viewSize = DpSize(800.dp, 400.dp)
        compose.waitForIdle()
        compose.pump(160)
        compose.onNodeWithTag(chatImagePreviewFrozenBackdropTag, useUnmergedTree = true).assertExists()
        // The portrait snapshot keeps its size; the rotation lays it out sideways.
        assertEquals(dpSize(400, 800), sizeOf(chatImagePreviewFrozenBackdropTag))
        compose.onNodeWithTag(chatImagePreviewFrozenBackdropRotationTag(3), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(chatImagePreviewFrozenBackdropRotationTag(0), useUnmergedTree = true).assertDoesNotExist()
        assertEquals(dpSize(800, 400), sizeOf(chatImagePreviewFrozenBackdropFrameTag))
        assertEquals(4, buttonIconCount())

        systemBack()
        compose.waitForIdle()

        assertEquals(listOf(AppOrientationController.fullScreenMedia, AppOrientationController.portraitOnly), requests)
        // The preview waits for the portrait viewport before closing.
        assertEquals(1, closeButtonCount())

        displayQuarterTurns = 0
        viewSize = DpSize(400.dp, 800.dp)
        compose.waitForIdle()
        compose.pump(300)
        assertEquals(0, closeButtonCount())

        assertEquals(
            listOf(AppOrientationController.fullScreenMedia, AppOrientationController.portraitOnly, AppOrientationController.portraitOnly),
            requests,
        )
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `Android tablet uses the wide preview path without phone orientation locks`() {
        viewSize = DpSize(1280.dp, 800.dp)
        var orientationCalls = 0
        var captures = 0
        var rotationReads = 0
        val controller =
            AppOrientationController(
                logicalViewSize = { DpSize(1280.dp, 800.dp) },
                setPreferredOrientations = { orientationCalls += 1 },
            )
        host(
            controller,
            backdropCapture = {
                captures += 1
                portraitBackdrop()
            },
            displayRotationReader = {
                rotationReads += 1
                0
            },
        )
        tapOpen()
        compose.waitForIdle()
        compose.pump(200)

        assertTrue(pageBelowVisible())
        compose.onNodeWithTag(chatImagePreviewFrozenBackdropTag, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, captures)
        assertEquals(0, rotationReads)
        assertEquals(0, orientationCalls)

        compose.onTooltip("关闭").performClick()
        compose.waitForIdle()
        compose.pump(300)
        assertEquals(0, closeButtonCount())
    }

    @Test
    fun `Android uses full area, resets image, and reveals timed controls`() {
        installTestImageLoader()
        val url = "https://example.invalid/preview.png"
        val mediaCache = testMediaCacheController(mapOf(url to solidPng(64, 64, android.graphics.Color.RED)))
        val controller =
            AppOrientationController(
                logicalViewSize = { DpSize(400.dp, 800.dp) },
                setPreferredOrientations = { _ -> },
            )
        host(controller, backdropCapture = { portraitBackdrop() }, imageUrl = url, mediaCache = mediaCache)
        tapOpen()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TestTag, chatImagePreviewTransformTag), true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // A square image fits as 400 x 400 dp in either orientation.
        val unscaledWidth = 400f
        assertEquals(4, buttonIconCount())
        assertEquals(dpSize(400, 800), sizeOf(chatImagePreviewViewerTag))
        assertEquals(1f, previewImageScale(unscaledWidth), 0.01f)
        zoomPreview()
        assertTrue(previewImageScale(unscaledWidth) > 1f)

        viewSize = DpSize(800.dp, 400.dp)
        compose.waitForIdle()

        assertEquals(4, buttonIconCount())
        assertEquals(dpSize(800, 400), sizeOf(chatImagePreviewViewerTag))
        assertEquals(1f, previewImageScale(unscaledWidth), 0.01f)

        compose.pump(3100)
        assertEquals(0, buttonIconCount())
        compose.onNodeWithTag(chatImagePreviewViewerTag).performClick()
        compose.waitForIdle()
        assertEquals(4, buttonIconCount())

        zoomPreview()
        assertTrue(previewImageScale(unscaledWidth) > 1f)

        viewSize = DpSize(400.dp, 800.dp)
        compose.waitForIdle()

        assertEquals(4, buttonIconCount())
        assertEquals(dpSize(400, 800), sizeOf(chatImagePreviewViewerTag))
        assertEquals(1f, previewImageScale(unscaledWidth), 0.01f)
        compose.onTooltip("关闭").performClick()
        compose.waitForIdle()
        compose.pump(300)
        assertEquals(0, closeButtonCount())
    }

    @Test
    fun `Android system back restores portrait after image preview`() {
        val requests = mutableListOf<List<DeviceOrientation>>()
        val controller =
            AppOrientationController(
                logicalViewSize = { DpSize(400.dp, 800.dp) },
                setPreferredOrientations = { orientations -> requests.add(orientations.toList()) },
            )
        host(controller, backdropCapture = { portraitBackdrop() })
        tapOpen()
        compose.waitForIdle()
        compose.pump(300)

        systemBack()
        compose.waitForIdle()
        compose.pump(300)
        assertEquals(0, closeButtonCount())

        assertEquals(
            listOf(AppOrientationController.fullScreenMedia, AppOrientationController.portraitOnly, AppOrientationController.portraitOnly),
            requests,
        )
    }

    @Test
    @Config(qualifiers = "w800dp-h600dp-xxhdpi")
    fun `preview action scope can capture the current portrait UI`() {
        var previewContext: BuildContext? = null
        compose.setContent {
            UiTheme {
                ChatImagePreviewActionsScope(actions = ChatImagePreviewActions.disabled()) {
                    previewContext = rememberBuildContext()
                    Box(Modifier.fillMaxSize().background(Color.Blue))
                }
            }
        }
        compose.waitForIdle()
        // Robolectric draws only when something captures the window; Flutter
        // tests always paint the frame the boundary records.
        compose.onRoot().captureToImage()

        var snapshot: ImagePreviewBackdropSnapshot? = null
        var done = false
        compose.runOnUiThread {
            unawaited {
                snapshot = ChatImagePreviewActionsScope.captureBackdrop(previewContext!!)
                done = true
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { done }

        assertNotNull(snapshot)
        assertEquals(DpSize(800.dp, 600.dp), snapshot?.logicalSize)
        snapshot?.dispose()
    }
}
