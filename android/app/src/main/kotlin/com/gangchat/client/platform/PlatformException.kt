package com.gangchat.client.platform

/**
 * Flutter's `PlatformException`: a failure reported by the native side of a
 * platform service. The codes are the ones the Flutter host's MethodChannel
 * handlers used (`invalid_uri`, `permission_denied`, `downloads_unavailable`,
 * ...), because ported Dart code switches on them.
 */
class PlatformException(
    val code: String,
    override val message: String? = null,
    val details: Any? = null,
) : Exception(message) {
    override fun toString(): String = "PlatformException($code, $message, $details, null)"
}

/**
 * Flutter's `MissingPluginException`: the platform implementation is not
 * installed (for example in a unit test that never started the app).
 */
class MissingPluginException(override val message: String? = null) : Exception(message) {
    override fun toString(): String = "MissingPluginException($message)"
}
