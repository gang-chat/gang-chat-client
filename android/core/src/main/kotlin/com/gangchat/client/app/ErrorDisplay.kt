package com.gangchat.client.app

/**
 * Returns Simplified-Chinese copy suitable for a floating notice or error
 * panel without exposing platform exception class names and English details.
 */
fun userFacingErrorMessage(
    failure: Any,
    fallback: String = "操作失败，请稍后重试",
): String {
    val raw = _dartErrorText(failure).trim()
    if (_containsChinese(raw)) return raw

    val normalized = raw.lowercase()
    if (normalized.contains("cancelled") || normalized.contains("canceled")) {
        return "操作已取消"
    }
    if (normalized.contains("timeout") || normalized.contains("timed out")) {
        return "操作超时，请稍后重试"
    }
    if (normalized.contains("socket") ||
        normalized.contains("connection") ||
        normalized.contains("network") ||
        normalized.contains("handshake")
    ) {
        return "网络连接失败，请检查网络后重试"
    }
    if (normalized.contains("permission") ||
        normalized.contains("access denied")
    ) {
        return "没有权限完成此操作"
    }
    if (normalized.contains("not found") || normalized.contains("missing")) {
        return "所需内容不存在"
    }
    if (normalized.contains("unsupported") ||
        normalized.contains("not supported")
    ) {
        return "当前平台不支持此操作"
    }
    return fallback
}

private val _chinesePattern = Regex("[\\u3400-\\u9fff]")

private fun _containsChinese(value: String): Boolean {
    return _chinesePattern.containsMatchIn(value)
}

/**
 * `failure.toString()` as the Dart app printed it.
 *
 * PORT: ports throw Kotlin's standard exceptions where Dart threw
 * `StateError`, `ArgumentError`, `UnsupportedError`, `UnimplementedError` or
 * a plain `Exception(...)`. The JVM prints those with their qualified class
 * names ("java.lang.IllegalStateException: 一次最多上传 500 个表情"), which this
 * copy must not expose, so they get Dart's prefixes back ("Bad state: ...").
 * Exceptions with their own `toString()` (ported Dart exceptions) and every
 * other type keep their text.
 */
private fun _dartErrorText(failure: Any): String {
    if (failure !is Throwable) return failure.toString()
    val text = failure.toString()
    val message = failure.localizedMessage
    val className = failure.javaClass.name
    val jvmDefault = if (message == null) className else "$className: $message"
    if (text != jvmDefault) return text
    fun dart(prefix: String): String = if (message == null) prefix else "$prefix: $message"
    return when (failure.javaClass) {
        IllegalStateException::class.java -> dart("Bad state")
        IllegalArgumentException::class.java -> dart("Invalid argument(s)")
        UnsupportedOperationException::class.java -> dart("Unsupported operation")
        NotImplementedError::class.java -> dart("UnimplementedError")
        Exception::class.java -> dart("Exception")
        else -> text
    }
}
