package com.gangchat.client.shell

import com.gangchat.client.foundation.Uri

class ExternalUriLauncher(
    val androidSystemService: AndroidSystemService = AndroidSystemService(),
) {
    suspend fun open(uri: Uri) {
        if (androidSystemService.isSupported) {
            androidSystemService.openUri(uri)
            return
        }
        throw UnsupportedOperationException("当前平台不支持打开链接")
    }
}
