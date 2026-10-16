package com.gangchat.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMessagesTest {
    @Test
    fun `keeps existing Chinese server messages`() {
        assertEquals(
            "请先验证邮箱",
            localizedServerErrorMessage(
                code = "email_verification_required",
                statusCode = 400,
                message = "请先验证邮箱",
            ),
        )
    }

    @Test
    fun `localizes known English server messages`() {
        assertEquals(
            "表情不存在",
            localizedServerErrorMessage(
                code = "not_found",
                statusCode = 404,
                message = "sticker not found",
            ),
        )
        assertEquals(
            "登录会话已过期",
            localizedServerErrorMessage(
                code = "unauthorized",
                statusCode = 401,
                message = "session expired",
            ),
        )
    }

    @Test
    fun `uses Chinese code and status fallbacks for unknown messages`() {
        assertEquals(
            "请求内容不符合要求",
            localizedServerErrorMessage(
                code = "validation_failed",
                statusCode = 400,
                message = "field xyz is malformed",
            ),
        )
        assertEquals(
            "服务器暂时无法完成请求，请稍后重试",
            localizedServerErrorMessage(
                code = "unknown",
                statusCode = 503,
                message = "upstream exploded",
            ),
        )
    }

    @Test
    fun `reports a suspended account instead of a credential error`() {
        assertEquals(
            "账号已被封禁",
            localizedServerErrorMessage(
                code = "account_suspended",
                statusCode = 403,
                message = "account suspended",
            ),
        )
    }

    @Test
    fun `localizes duplicate music queue and playlist conflicts`() {
        assertEquals(
            "已在队列中",
            localizedServerErrorMessage(
                code = "music_box_item_already_queued",
                statusCode = 409,
                message = "music box item is already queued",
            ),
        )
        assertEquals(
            "点歌队列已达 200 首上限",
            localizedServerErrorMessage(
                code = "music_box_queue_limit_reached",
                statusCode = 409,
                message = "music box request queue reached its 200 item limit",
            ),
        )
        assertEquals(
            "没有权限执行此音乐盒操作",
            localizedServerErrorMessage(
                code = "music_box_permission_denied",
                statusCode = 403,
                message = "the current room role cannot perform this music box action",
            ),
        )
        assertEquals(
            "已在歌单内，不可重复添加",
            localizedServerErrorMessage(
                code = "playlist_item_already_exists",
                statusCode = 409,
                message = "music playlist item already exists",
            ),
        )
    }
}
