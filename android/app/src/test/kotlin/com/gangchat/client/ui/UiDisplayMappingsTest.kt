package com.gangchat.client.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.gangchat.client.app.RoomMemberPresence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class UiDisplayMappingsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `fileIconForMime maps common mime families`() {
        assertEquals(Icons.Outlined.Image, fileIconForMime("image/png"))
        assertEquals(Icons.Outlined.PictureAsPdf, fileIconForMime("application/pdf"))
        assertEquals(Icons.Outlined.FolderZip, fileIconForMime("application/zip"))
        assertEquals(Icons.AutoMirrored.Outlined.InsertDriveFile, fileIconForMime("application/octet-stream"))
    }

    @Test
    fun `roomMemberPresenceIcon maps presence states`() {
        assertEquals(Icons.Filled.Call, roomMemberPresenceIcon(RoomMemberPresence.live))
        assertEquals(Icons.Filled.Circle, roomMemberPresenceIcon(RoomMemberPresence.online))
        assertEquals(Icons.Outlined.Circle, roomMemberPresenceIcon(RoomMemberPresence.offline))
    }

    @Test
    fun `PresencePill maps presence tones to unified colors`() {
        compose.setUiContent { _ ->
            Row {
                PresencePill(label = "语音", tone = PresencePillTone.voice)
                PresencePill(label = "在线", tone = PresencePillTone.online)
                PresencePill(label = "离线", tone = PresencePillTone.offline)
            }
        }

        assertEquals(UiColors.presenceVoice, compose.onNodeWithText("语音").textColor())
        assertEquals(UiColors.presenceOnline, compose.onNodeWithText("在线").textColor())
        assertEquals(UiColors.presenceOffline, compose.onNodeWithText("离线").textColor())
    }

    @Test
    fun `RoleBadge maps role labels to blue yellow orange red colors`() {
        compose.setUiContent { _ ->
            Row {
                RoleBadge(label = "成员")
                RoleBadge(label = "管理员")
                RoleBadge(label = "创建者")
                RoleBadge(label = "超级用户")
            }
        }

        assertEquals(UiColors.roleMember, compose.onNodeWithText("成员").textColor())
        assertEquals(UiColors.roleAdmin, compose.onNodeWithText("管理员").textColor())
        assertEquals(UiColors.roleCreator, compose.onNodeWithText("创建者").textColor())
        assertEquals(UiColors.roleSuperuser, compose.onNodeWithText("超级用户").textColor())
    }

    @Test
    fun `genderMark normalizes common gender values`() {
        assertEquals("♂", genderMark("M")?.symbol)
        assertEquals("♀", genderMark("woman")?.symbol)
        assertNull(genderMark("unknown"))
    }
}
