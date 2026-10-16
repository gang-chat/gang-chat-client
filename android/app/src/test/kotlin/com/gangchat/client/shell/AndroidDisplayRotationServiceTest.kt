package com.gangchat.client.shell

import android.view.Surface
import androidx.activity.ComponentActivity
import com.gangchat.client.platform.GangChatPlatform
import com.gangchat.client.testing.GangTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplay

/** Port of test/unit/shell/android_display_rotation_service_test.dart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDisplayRotationServiceTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `normalizes Android display rotation values`() {
        assertEquals(0, normalizeDisplayRotationQuarterTurns(null))
        assertEquals(0, normalizeDisplayRotationQuarterTurns(-1))
        assertEquals(0, normalizeDisplayRotationQuarterTurns(0))
        assertEquals(1, normalizeDisplayRotationQuarterTurns(1))
        assertEquals(2, normalizeDisplayRotationQuarterTurns(2))
        assertEquals(3, normalizeDisplayRotationQuarterTurns(3))
        assertEquals(0, normalizeDisplayRotationQuarterTurns(4))
    }

    @Test
    fun `counter rotation handles both Android landscape directions`() {
        assertEquals(0, counterDisplayRotationQuarterTurns(0))
        assertEquals(3, counterDisplayRotationQuarterTurns(1))
        assertEquals(2, counterDisplayRotationQuarterTurns(2))
        assertEquals(1, counterDisplayRotationQuarterTurns(3))
    }

    @Test
    fun `service reads display rotation from the Android channel`() =
        gang.runTest {
            var reads = 0
            val service = AndroidDisplayRotationService(readRotation = { reads += 1; 3 })

            assertEquals(3, service.currentQuarterTurns())
            assertEquals(1, reads)
        }

    @Test
    fun `default service reads the activity display`() =
        gang.runTest {
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            GangChatPlatform.attachActivity(activity)
            try {
                shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_270)
                assertEquals(3, AndroidDisplayRotationService().currentQuarterTurns())
            } finally {
                GangChatPlatform.detachActivity(activity)
            }
        }
}
