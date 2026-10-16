package com.gangchat.client.foundation

/** Flutter's `kDebugMode`; the app sets it from `BuildConfig.DEBUG`. */
@Volatile
var kDebugMode: Boolean = true

/** Flutter's `kReleaseMode`. */
val kReleaseMode: Boolean get() = !kDebugMode

/** Where [debugPrint] writes; the app routes it to logcat. */
@Volatile
var debugPrintSink: (String) -> Unit = { println(it) }

/** Flutter's `debugPrint(message)`. */
fun debugPrint(message: String?) {
    debugPrintSink(message ?: "null")
}

/** Flutter's `@visibleForTesting`. */
@Retention(AnnotationRetention.SOURCE)
annotation class VisibleForTesting

/** Flutter's `@protected`, documentation only. */
@Retention(AnnotationRetention.SOURCE)
annotation class Protected

/** Flutter's `@mustCallSuper`, documentation only. */
@Retention(AnnotationRetention.SOURCE)
annotation class MustCallSuper
