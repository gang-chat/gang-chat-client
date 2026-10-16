package com.gangchat.client.foundation

/** Flutter's `Listenable`. */
interface Listenable {
    fun addListener(listener: () -> Unit)

    fun removeListener(listener: () -> Unit)

    companion object {
        /** Flutter's `Listenable.merge(listenables)`. */
        fun merge(listenables: Iterable<Listenable?>): Listenable = MergedListenable(listenables.filterNotNull())
    }
}

/** Flutter's `ValueListenable<T>`. */
interface ValueListenable<out T> : Listenable {
    val value: T
}

/**
 * Flutter's `ChangeNotifier`, with the same notification semantics:
 * listeners run synchronously in registration order; a listener removed during
 * a notification round is not called later in that round, and one added during
 * the round is not called until the next one.
 *
 * Listeners are compared with `==`, like Dart tear-offs: two bound references
 * `this::onChanged` are equal, so `addListener(::onChanged)` can be undone with
 * `removeListener(::onChanged)`. Two separate lambdas are never equal.
 */
open class ChangeNotifier : Listenable {
    private var listeners = arrayOfNulls<(() -> Unit)?>(0)
    private var count = 0
    private var notificationDepth = 0
    private var reentrantlyRemoved = 0
    private var disposed = false

    val hasListeners: Boolean get() = count > 0

    /** Debug aid matching Flutter's `debugAssertNotDisposed`. */
    protected val isDisposed: Boolean get() = disposed

    override fun addListener(listener: () -> Unit) {
        if (disposed) {
            debugPrint("A ${this::class.simpleName} was used after being disposed (addListener).")
            return
        }
        if (count == listeners.size) {
            listeners = if (count == 0) arrayOfNulls(1) else listeners.copyOf(count * 2)
        }
        listeners[count++] = listener
    }

    override fun removeListener(listener: () -> Unit) {
        for (i in 0 until count) {
            if (listeners[i] == listener) {
                if (notificationDepth > 0) {
                    // Defer compaction until notifyListeners returns, like Flutter.
                    listeners[i] = null
                    reentrantlyRemoved += 1
                } else {
                    removeAt(i)
                }
                return
            }
        }
    }

    open fun dispose() {
        disposed = true
        listeners = arrayOfNulls(0)
        count = 0
    }

    protected open fun notifyListeners() {
        if (disposed || count == 0) return
        notificationDepth += 1
        val end = count
        try {
            for (i in 0 until end) {
                val listener = listeners.getOrNull(i) ?: continue
                try {
                    listener()
                } catch (error: Throwable) {
                    if (error is VirtualMachineError) throw error
                    debugPrint(
                        "Exception while dispatching notifications for ${this::class.simpleName}: " +
                            "$error\n${error.stackTraceToString()}",
                    )
                }
            }
        } finally {
            notificationDepth -= 1
            if (notificationDepth == 0 && reentrantlyRemoved > 0) {
                val kept = arrayOfNulls<(() -> Unit)?>(maxOf(count - reentrantlyRemoved, 0))
                var j = 0
                for (i in 0 until count) {
                    val listener = listeners[i] ?: continue
                    kept[j++] = listener
                }
                listeners = kept
                count = j
                reentrantlyRemoved = 0
            }
        }
    }

    private fun removeAt(index: Int) {
        for (i in index until count - 1) listeners[i] = listeners[i + 1]
        count -= 1
        listeners[count] = null
    }
}

/** Flutter's `ValueNotifier<T>`: notifies when [value] changes by `==`. */
open class ValueNotifier<T>(initialValue: T) : ChangeNotifier(), ValueListenable<T> {
    private var currentValue: T = initialValue

    override var value: T
        get() = currentValue
        set(newValue) {
            if (currentValue == newValue) return
            currentValue = newValue
            notifyListeners()
        }

    override fun toString(): String = "${this::class.simpleName}($currentValue)"
}

private class MergedListenable(private val children: List<Listenable>) : Listenable {
    override fun addListener(listener: () -> Unit) {
        for (child in children) child.addListener(listener)
    }

    override fun removeListener(listener: () -> Unit) {
        for (child in children) child.removeListener(listener)
    }
}
