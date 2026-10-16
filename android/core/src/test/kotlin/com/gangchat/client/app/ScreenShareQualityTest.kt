package com.gangchat.client.app

import com.gangchat.client.live.ScreenShareQuality
import com.gangchat.client.live.ScreenShareQualityCheckResult
import com.gangchat.client.live.ScreenShareResolution
import com.gangchat.client.live.androidScreenShareFrameRateCap
import com.gangchat.client.live.defaultScreenShareMaxHeight
import com.gangchat.client.live.normalizedScreenShareFrameRate
import com.gangchat.client.live.normalizedScreenShareMaxHeight
import com.gangchat.client.live.screenShareCappedOutputResolution
import com.gangchat.client.live.screenShareFrameRateLabel
import com.gangchat.client.live.screenShareFrameRateOptions
import com.gangchat.client.live.screenShareHeightOptions
import com.gangchat.client.live.screenShareQualityCheckResult
import com.gangchat.client.live.screenShareQualityForHeight
import com.gangchat.client.live.screenShareResolutionForHeight
import com.gangchat.client.live.screenShareResolutionLabel
import com.gangchat.client.live.screenShareScaleDownBy
import com.gangchat.client.live.screenShareSourceScaleForSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Dart: test/unit/app/screen_share_quality_test.dart (tests lib/src/live/).
// Dart `group` names prefix each case. JVM method names cannot contain
// `.`, `/` or `:`, so those characters are written as the look-alikes
// `․` (U+2024), `∕` (U+2215) and `∶` (U+2236).
class ScreenShareQualityTest {
    // group('normalizedScreenShareMaxHeight')

    @Test
    fun `normalizedScreenShareMaxHeight passes through supported options`() {
        for (option in screenShareHeightOptions) {
            assertEquals(option, normalizedScreenShareMaxHeight(option))
        }
    }

    @Test
    fun `normalizedScreenShareMaxHeight falls back to default for null∕unsupported values`() {
        assertEquals(defaultScreenShareMaxHeight, normalizedScreenShareMaxHeight(null))
        assertEquals(defaultScreenShareMaxHeight, normalizedScreenShareMaxHeight(0))
        assertEquals(defaultScreenShareMaxHeight, normalizedScreenShareMaxHeight(900))
        assertEquals(defaultScreenShareMaxHeight, normalizedScreenShareMaxHeight(2160))
    }

    @Test
    fun `normalizedScreenShareMaxHeight default is 720`() {
        assertEquals(720, defaultScreenShareMaxHeight)
    }

    // group('screenShareResolutionForHeight')

    @Test
    fun `screenShareResolutionForHeight returns 16∶9 dimensions with an even width`() {
        assertEquals(ScreenShareResolution(854, 480), screenShareResolutionForHeight(480))
        assertEquals(ScreenShareResolution(1280, 720), screenShareResolutionForHeight(720))
        assertEquals(ScreenShareResolution(1920, 1080), screenShareResolutionForHeight(1080))
    }

    @Test
    fun `screenShareResolutionForHeight width is always even`() {
        for (option in screenShareHeightOptions) {
            assertTrue(screenShareResolutionForHeight(option).width % 2 == 0)
        }
    }

    @Test
    fun `screenShareResolutionForHeight coerces unsupported heights to the default resolution`() {
        assertEquals(ScreenShareResolution(1280, 720), screenShareResolutionForHeight(999))
    }

    // group('normalizedScreenShareFrameRate')

    @Test
    fun `normalizedScreenShareFrameRate passes through supported desktop options`() {
        for (option in screenShareFrameRateOptions) {
            assertEquals(option, normalizedScreenShareFrameRate(option))
        }
    }

    @Test
    fun `normalizedScreenShareFrameRate falls back to the platform default and applies its cap`() {
        assertEquals(30, normalizedScreenShareFrameRate(null))
        assertEquals(30, normalizedScreenShareFrameRate(24))
        assertEquals(
            30,
            normalizedScreenShareFrameRate(
                60,
                maxFrameRate = androidScreenShareFrameRateCap,
            ),
        )
        assertEquals(
            30,
            normalizedScreenShareFrameRate(
                null,
                maxFrameRate = androidScreenShareFrameRateCap,
            ),
        )
    }

    // group('screenShareQualityForHeight')

    @Test
    fun `screenShareQualityForHeight combines resolution and frame rate into a bitrate profile`() {
        assertEquals(
            ScreenShareQuality(
                maxHeight = 480,
                maxFrameRate = 15,
                maxBitrate = 1000 * 1000,
            ),
            screenShareQualityForHeight(480, frameRate = 15),
        )
        assertEquals(
            ScreenShareQuality(
                maxHeight = 720,
                maxFrameRate = 30,
                maxBitrate = 3000 * 1000,
            ),
            screenShareQualityForHeight(720, frameRate = 30),
        )
        assertEquals(
            ScreenShareQuality(
                maxHeight = 1080,
                maxFrameRate = 60,
                maxBitrate = 8000 * 1000,
            ),
            screenShareQualityForHeight(1080, frameRate = 60),
        )
    }

    @Test
    fun `screenShareQualityForHeight caps frame rate and bitrate together for Android`() {
        assertEquals(
            ScreenShareQuality(
                maxHeight = 1080,
                maxFrameRate = 30,
                maxBitrate = 4000 * 1000,
            ),
            screenShareQualityForHeight(
                1080,
                frameRate = 60,
                maxFrameRate = androidScreenShareFrameRateCap,
            ),
        )
        assertEquals(
            30,
            screenShareQualityForHeight(
                720,
                frameRate = 60,
                maxFrameRate = androidScreenShareFrameRateCap,
            ).maxFrameRate,
        )
    }

    @Test
    fun `screenShareQualityForHeight coerces unsupported heights to the default profile`() {
        assertEquals(
            screenShareQualityForHeight(defaultScreenShareMaxHeight, frameRate = 30),
            screenShareQualityForHeight(999, frameRate = 30),
        )
    }

    @Test
    fun `screenShareQualityForHeight keeps a stable bits-per-frame budget across FPS options`() {
        assertEquals(1500 * 1000, screenShareQualityForHeight(720, frameRate = 15).maxBitrate)
        assertEquals(6000 * 1000, screenShareQualityForHeight(720, frameRate = 60).maxBitrate)
    }

    // group('screenShareScaleDownBy')

    @Test
    fun `screenShareScaleDownBy downscales a taller source to the target`() {
        // 4K display capped at 720p.
        assertEquals(3.0, screenShareScaleDownBy(sourceHeight = 2160, targetHeight = 720), 1e-9)
        // 1440p capped at 480p.
        assertEquals(3.0, screenShareScaleDownBy(sourceHeight = 1440, targetHeight = 480), 1e-9)
    }

    @Test
    fun `screenShareScaleDownBy never scales up when the source already fits`() {
        assertEquals(1.0, screenShareScaleDownBy(sourceHeight = 720, targetHeight = 1080), 0.0)
        assertEquals(1.0, screenShareScaleDownBy(sourceHeight = 1080, targetHeight = 1080), 0.0)
    }

    @Test
    fun `screenShareScaleDownBy returns 1․0 for unknown∕non-positive source heights`() {
        assertEquals(1.0, screenShareScaleDownBy(sourceHeight = 0, targetHeight = 720), 0.0)
        assertEquals(1.0, screenShareScaleDownBy(sourceHeight = -5, targetHeight = 720), 0.0)
    }

    @Test
    fun `screenShareScaleDownBy coerces an unsupported target to the default before scaling`() {
        // target 999 -> 720; a 1080p source is capped at 720p.
        assertEquals(1.5, screenShareScaleDownBy(sourceHeight = 1080, targetHeight = 999), 0.0)
    }

    // group('screenShareSourceScaleForSample')

    @Test
    fun `screenShareSourceScaleForSample uses an RID∕SSRC matched scale without guessing`() {
        assertEquals(
            2.0,
            screenShareSourceScaleForSample(
                encodedHeight = 540,
                targetHeight = 1080,
                encodingScales = listOf(1.0, 2.0),
                matchedScale = 2.0,
            ),
            0.0,
        )
    }

    @Test
    fun `screenShareSourceScaleForSample reconstructs a low simulcast sample when native stats omit ids`() {
        assertEquals(
            2.0,
            screenShareSourceScaleForSample(
                encodedHeight = 540,
                targetHeight = 1080,
                encodingScales = listOf(1.0, 2.0),
            ),
            0.0,
        )
        assertEquals(
            1.0,
            screenShareSourceScaleForSample(
                encodedHeight = 1080,
                targetHeight = 720,
                encodingScales = listOf(1.0, 2.0),
            ),
            0.0,
        )
    }

    @Test
    fun `screenShareSourceScaleForSample uses a known source to identify the closest active layer`() {
        assertEquals(
            4.5,
            screenShareSourceScaleForSample(
                encodedHeight = 240,
                targetHeight = 480,
                encodingScales = listOf(2.25, 4.5),
                knownSourceHeight = 1080,
            ),
            0.0,
        )
    }

    // group('screenShareCappedOutputResolution')

    @Test
    fun `screenShareCappedOutputResolution keeps source aspect ratio while capping height`() {
        assertEquals(
            ScreenShareResolution(1720, 720),
            screenShareCappedOutputResolution(
                sourceWidth = 3440,
                sourceHeight = 1440,
                targetHeight = 720,
            ),
        )
    }

    @Test
    fun `screenShareCappedOutputResolution does not upscale a smaller source`() {
        assertEquals(
            ScreenShareResolution(960, 540),
            screenShareCappedOutputResolution(
                sourceWidth = 960,
                sourceHeight = 540,
                targetHeight = 1080,
            ),
        )
    }

    @Test
    fun `screenShareCappedOutputResolution uses the selected preset before source dimensions are known`() {
        assertEquals(
            ScreenShareResolution(1280, 720),
            screenShareCappedOutputResolution(
                sourceWidth = null,
                sourceHeight = null,
                targetHeight = 720,
            ),
        )
    }

    // group('screenShareQualityCheckResult')

    @Test
    fun `screenShareQualityCheckResult defers verification when parameters are ready but no frames flow`() {
        assertEquals(
            ScreenShareQualityCheckResult.awaitingOutboundFrames,
            screenShareQualityCheckResult(
                parametersReady = true,
                hasOutboundFrames = false,
                resolutionVerified = false,
                frameRateCapVerified = true,
            ),
        )
    }

    @Test
    fun `screenShareQualityCheckResult does not hide a rejected parameter update behind missing stats`() {
        assertEquals(
            ScreenShareQualityCheckResult.retry,
            screenShareQualityCheckResult(
                parametersReady = false,
                hasOutboundFrames = false,
                resolutionVerified = false,
                frameRateCapVerified = true,
            ),
        )
    }

    @Test
    fun `screenShareQualityCheckResult requires parameters, resolution and frame rate to match`() {
        assertEquals(
            ScreenShareQualityCheckResult.verified,
            screenShareQualityCheckResult(
                parametersReady = true,
                hasOutboundFrames = true,
                resolutionVerified = true,
                frameRateCapVerified = true,
            ),
        )
        assertEquals(
            ScreenShareQualityCheckResult.retry,
            screenShareQualityCheckResult(
                parametersReady = true,
                hasOutboundFrames = true,
                resolutionVerified = false,
                frameRateCapVerified = true,
            ),
        )
    }

    // group('screen-share picker labels')

    @Test
    fun `screen-share picker labels formats supported resolutions and frame rates`() {
        assertEquals("480p", screenShareResolutionLabel(480))
        assertEquals("720p", screenShareResolutionLabel(720))
        assertEquals("1080p", screenShareResolutionLabel(1080))
        assertEquals("15 FPS", screenShareFrameRateLabel(15))
        assertEquals("30 FPS", screenShareFrameRateLabel(30))
        assertEquals("60 FPS", screenShareFrameRateLabel(60))
    }

    @Test
    fun `screen-share picker labels coerces unsupported values to default labels`() {
        assertEquals("720p", screenShareResolutionLabel(999))
        assertEquals("30 FPS", screenShareFrameRateLabel(24))
    }
}
