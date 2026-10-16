package com.gangchat.client.live

import com.gangchat.client.foundation.Uri

fun resolveLiveKitServerUrl(
    serverUrl: String,
    apiBaseUrl: String,
): String {
    val liveKitUri = Uri.tryParse(serverUrl)
    val apiUri = Uri.tryParse(apiBaseUrl)
    if (liveKitUri == null || apiUri == null) return serverUrl
    if (liveKitUri.host.isEmpty() || apiUri.host.isEmpty()) return serverUrl

    val host =
        if (_isLocalAdvertisedHost(liveKitUri.host) &&
            !_isLocalAdvertisedHost(apiUri.host)
        ) {
            apiUri.host
        } else {
            liveKitUri.host
        }

    return liveKitUri
        .replace(scheme = _webSocketScheme(liveKitUri), host = host)
        .toString()
}

private fun _webSocketScheme(uri: Uri): String {
    if (uri.scheme == "https" || uri.scheme == "wss") return "wss"
    return "ws"
}

private fun _isLocalAdvertisedHost(host: String): Boolean {
    val normalized = host.lowercase()
    return normalized == "localhost" ||
        normalized == "127.0.0.1" ||
        normalized == "::1" ||
        normalized == "0.0.0.0"
}
