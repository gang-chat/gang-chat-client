package com.gangchat.client.ui

/**
 * Dart's `GangScrollBehavior`: keeps desktop scrollbars in a dedicated
 * trailing gutter instead of painting their thumbs over list cards and
 * action buttons.
 *
 * Android keeps native touch scrolling unchanged (Material's behaviour: no
 * scrollbar, stretch overscroll), which is also Compose's default on
 * Android, so this port installs nothing; only the gutter constant remains
 * for code that reserves the same space.
 */
object GangScrollBehavior {
    const val verticalScrollbarGutter: Double = 8.0
}
