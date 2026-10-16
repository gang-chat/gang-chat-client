package com.gangchat.client.config

/**
 * The operating system the app runs on. The native client only runs on
 * Android, so the desktop checks are constant `false`; ports keep calling them
 * where the Dart code did only when the branch matters for readability, and
 * otherwise drop the non-Android branches.
 */
object HostPlatform {
    const val isWindows: Boolean = false
    const val isMacOS: Boolean = false
    const val isLinux: Boolean = false
    const val isAndroid: Boolean = true

    const val operatingSystem: String = "android"

    const val pathSeparator: String = "/"

    val environment: Map<String, String> get() = System.getenv()

    const val resolvedExecutable: String = ""
}

/** Flutter's `kIsWeb`: never true in the native client. */
const val kIsWeb: Boolean = false
