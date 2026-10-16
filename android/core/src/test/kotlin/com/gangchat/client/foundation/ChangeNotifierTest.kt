package com.gangchat.client.foundation

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeNotifierTest {
    private class Counter : ChangeNotifier() {
        fun bump() = notifyListeners()
    }

    @Test
    fun removalDuringNotificationSkipsLaterListeners() {
        val counter = Counter()
        val calls = mutableListOf<String>()
        lateinit var second: () -> Unit
        val first: () -> Unit = {
            calls.add("first")
            counter.removeListener(second)
        }
        second = { calls.add("second") }
        counter.addListener(first)
        counter.addListener(second)
        counter.bump()
        assertEquals(listOf("first"), calls)
        counter.bump()
        assertEquals(listOf("first", "first"), calls)
    }

    @Test
    fun boundReferencesCanBeRemoved() {
        val counter = Counter()
        val listener = Listener()
        counter.addListener(listener::onChanged)
        counter.bump()
        counter.removeListener(listener::onChanged)
        counter.bump()
        assertEquals(1, listener.count)
    }

    @Test
    fun valueNotifierOnlyNotifiesOnChange() {
        val notifier = ValueNotifier(1)
        var count = 0
        notifier.addListener { count++ }
        notifier.value = 1
        notifier.value = 2
        assertEquals(1, count)
    }

    private class Listener {
        var count = 0

        fun onChanged() {
            count++
        }
    }
}
