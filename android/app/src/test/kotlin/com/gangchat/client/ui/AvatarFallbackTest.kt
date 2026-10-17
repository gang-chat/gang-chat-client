package com.gangchat.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AvatarFallbackTest {
    @get:Rule
    val compose = createComposeRule()

    /** The colour painted at ([x], [y]) dp inside the node tagged [tag]. */
    private fun pixelAt(tag: String, x: Float, y: Float): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        return pixels[(bounds.left + x * density).toInt(), (bounds.top + y * density).toInt()]
    }

    private fun SemanticsNodeInteraction.center() = fetchSemanticsNode().boundsInRoot.center

    private fun assertClose(expected: Float, actual: Float, tolerancePx: Float = 1f) {
        assertTrue("expected $expected but was $actual", abs(expected - actual) <= tolerancePx)
    }

    @Test
    fun `avatarFallbackColor maps known keys and falls back to blue`() {
        assertEquals(Color(0xFF4E68D0), avatarFallbackColor("blue-3"))
        assertEquals("blue-3", normalizeAvatarPresetKey("room-1"))
        assertEquals(Color(0xFF4E68D0), avatarFallbackColor("room-1"))
        assertEquals(Color(0xFF50525E), avatarFallbackColor("graphite-2"))
        assertEquals(Color(0xFFC9363F), avatarFallbackColor("red-2"))
        assertEquals(Color(0xFF4E68D0), avatarFallbackColor("missing"))
        assertEquals("蓝色", avatarPresetLabel("room-1"))
        assertEquals("紫色", avatarPresetLabel("purple-2"))
        assertEquals("蓝色", avatarPresetLabel("missing"))
        assertTrue(kAvatarPresetKeys.containsAll(listOf("red-2", "gold-2", "black-2")))
    }

    @Test
    fun `uploaded avatar keeps its background transparent`() {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Avatar(label = "Kai", imageUrl = "uploaded-avatar", defaultAvatarKey = "red-2", modifier = Modifier.testTag("avatar"))
            }
        }
        compose.waitForIdle()

        // Inside the circle, away from the centred loading/error glyph, the
        // page shows through instead of the red-2 preset.
        assertEquals(UiColors.background, pixelAt("avatar", 5f, 20f))
    }

    @Test
    fun `avatar picker exposes Chinese color names`() {
        compose.setUiContent { _ ->
            AvatarPicker(
                label = "头像",
                displayName = "凯",
                imageUrl = null,
                defaultAvatarKey = "red-2",
                usingPreset = true,
                uploading = false,
                enabled = true,
                presetKeys = listOf("red-2"),
                onUpload = {},
                onPresetSelected = {},
            )
        }

        compose.onTooltip("红色").assertExists()
        compose.onTooltip("red-2").assertDoesNotExist()
    }

    @Test
    fun `Android avatar picker reuses centered preset avatar text`() {
        compose.setUiContent { _ ->
            AvatarPicker(
                label = "头像",
                displayName = "Test",
                imageUrl = null,
                defaultAvatarKey = "blue-3",
                usingPreset = true,
                uploading = false,
                enabled = true,
                presetKeys = listOf("blue-3"),
                onUpload = {},
                onPresetSelected = {},
            )
        }

        val preview = compose.onNodeWithTag(avatarPickerPreviewTag)
        val text = compose.onNode(hasText("TE") and hasAnyAncestor(hasTestTag(avatarPickerPreviewTag)), useUnmergedTree = true)
        text.assertExists()
        assertClose(preview.center().x, text.center().x)
        assertClose(preview.center().y, text.center().y)
    }

    @Test
    fun `active avatar paints the status border above its content`() {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Avatar(
                    label = "Kai",
                    active = true,
                    activeBorderColor = UiColors.presenceVoice,
                    paintBorderOnForeground = true,
                    modifier = Modifier.testTag("avatar"),
                )
            }
        }

        // The 2 dp ring at the top of the 40 dp circle.
        assertEquals(UiColors.presenceVoice, pixelAt("avatar", 20f, 1f))
        assertEquals(UiColors.surface, pixelAt("avatar", 20f, 4f))
    }

    @Test
    fun `avatar keeps status border in the background by default`() {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Avatar(label = "Kai", active = true, modifier = Modifier.testTag("avatar"))
            }
        }

        // Behind the (inset) content: the ring keeps the accent, the inside
        // keeps the neutral fill.
        assertEquals(UiColors.accent, pixelAt("avatar", 20f, 1f))
        assertEquals(UiColors.surface, pixelAt("avatar", 20f, 4f))
    }

    @Test
    fun `Android preset avatar text stays geometrically centered at fixed scale`() {
        compose.setUiContent { _ ->
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.8f)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Avatar(label = "Test", defaultAvatarKey = "blue-3", size = 18.dp, modifier = Modifier.testTag("android-avatar-latin"))
                    Avatar(label = "12", defaultAvatarKey = "green-2", size = 34.dp, modifier = Modifier.testTag("android-avatar-digits"))
                    Avatar(label = "暗影", defaultAvatarKey = "purple-2", size = 40.dp, modifier = Modifier.testTag("android-avatar-chinese"))
                }
            }
        }

        for ((avatarKey, initials, size) in listOf(
            Triple("android-avatar-latin", "TE", 18f),
            Triple("android-avatar-digits", "12", 34f),
            Triple("android-avatar-chinese", "暗影", 40f),
        )) {
            val avatar = compose.onNodeWithTag(avatarKey)
            val text = compose.onNode(hasText(initials) and hasAnyAncestor(hasTestTag(avatarKey)), useUnmergedTree = true)
            val style = text.textLayout().layoutInput.style
            assertEquals(TextAlign.Center, style.textAlign)
            assertEquals(FontFamily.SansSerif, style.fontFamily)
            // TextScaler.noScaling: the rendered size ignores the 1.8 font scale.
            assertEquals(size * 0.36f, style.fontSize.value * 1.8f, 0.01f)
            assertClose(avatar.center().x, text.center().x)
            assertClose(avatar.center().y, text.center().y)
        }
    }

    @Test
    fun `avatar can suppress shared status and base borders`() {
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Avatar(label = "Kai", active = true, showBorder = false, modifier = Modifier.testTag("avatar"))
            }
        }

        assertEquals(UiColors.surface, pixelAt("avatar", 20f, 1f))
        // Without the active border the initials keep the neutral fallback color
        // instead of switching to the active accent.
        val label = compose.onNode(hasText("KA"), useUnmergedTree = true)
        assertEquals(Color.White, label.textColor())
    }

    @Test
    fun `avatar picker opens preview only for uploaded images`() {
        var previews = 0
        var usingPreset by mutableStateOf(false)
        compose.setUiContent { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(460.dp)) {
                    AvatarPicker(
                        label = "Avatar",
                        displayName = "Kai",
                        imageUrl = "uploaded-avatar",
                        defaultAvatarKey = "blue-3",
                        usingPreset = usingPreset,
                        uploading = false,
                        enabled = true,
                        onUpload = {},
                        onPresetSelected = {},
                        onImagePreview = { previews += 1 },
                    )
                }
            }
        }

        compose.onNodeWithTag(avatarPickerPreviewTag).performClick()
        compose.waitForIdle()
        assertEquals(1, previews)

        usingPreset = true
        compose.waitForIdle()
        compose.onNodeWithTag(avatarPickerPreviewTag).performClick()
        compose.waitForIdle()
        assertEquals(1, previews)
    }
}
