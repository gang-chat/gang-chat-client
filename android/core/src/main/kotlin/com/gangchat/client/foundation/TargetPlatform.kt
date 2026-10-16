package com.gangchat.client.foundation

/** Flutter's `TargetPlatform` (package:flutter/foundation.dart). */
enum class TargetPlatform { android, fuchsia, iOS, linux, macOS, windows }

/** Flutter's `debugDefaultTargetPlatformOverride`, for tests. */
@Volatile
var debugDefaultTargetPlatformOverride: TargetPlatform? = null

/** Flutter's `defaultTargetPlatform`: always Android in the native client. */
val defaultTargetPlatform: TargetPlatform
    get() = debugDefaultTargetPlatformOverride ?: TargetPlatform.android
