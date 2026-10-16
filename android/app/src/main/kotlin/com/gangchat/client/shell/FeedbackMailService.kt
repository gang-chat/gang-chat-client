package com.gangchat.client.shell

import com.gangchat.client.foundation.Uri

class FeedbackMailDraft(
    val from: String,
    val to: String,
    val subject: String,
    val body: String,
) {
    fun toMailtoUri(): Uri {
        return Uri(
            scheme = "mailto",
            path = to,
            queryParameters = linkedMapOf("subject" to subject, "body" to body, "from" to from),
        )
    }
}

class FeedbackMailService(
    val androidSystemService: AndroidSystemService = AndroidSystemService(),
) {
    suspend fun openDraft(draft: FeedbackMailDraft) {
        openMailto(draft.toMailtoUri())
    }

    suspend fun openMailto(uri: Uri) {
        if (androidSystemService.isSupported) {
            androidSystemService.openMailto(uri)
            return
        }
        throw UnsupportedOperationException("当前平台不支持打开邮件客户端")
    }
}
