package com.gangchat.client.testing

import com.gangchat.client.foundation.AppCoroutines
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.plugins.FlutterSecureStorage
import com.gangchat.client.plugins.PathProvider
import com.gangchat.client.plugins.SharedPreferences
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Test harness standing in for `flutter_test`'s binding:
 *
 * - the app's event loop ([AppCoroutines], `Dispatchers.Main`) runs on a
 *   virtual-time [StandardTestDispatcher], so `Timer`s and delays are
 *   deterministic (Dart's `fakeAsync`);
 * - plugin facades start empty (`SharedPreferences.setMockInitialValues({})`,
 *   `FlutterSecureStorage.setMockInitialValues({})`) and [PathProvider] points
 *   at a fresh temporary directory;
 * - errors from fire-and-forget work fail the test, like uncaught async errors
 *   in Dart tests.
 *
 * Usage:
 * ```
 * @get:Rule val gang = GangTestRule()
 * @Test fun works() = gang.runTest { ...; pumpEventQueue() }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GangTestRule : TestWatcher() {
    val scheduler = TestCoroutineScheduler()
    val dispatcher: TestDispatcher = StandardTestDispatcher(scheduler)
    lateinit var tempDir: File
        private set

    private val uncaught = mutableListOf<Throwable>()
    private var previousHandler: ((Throwable) -> Unit)? = null

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
        AppCoroutines.reset()
        previousHandler = AppCoroutines.uncaughtErrorHandler
        AppCoroutines.uncaughtErrorHandler = { error -> synchronized(uncaught) { uncaught.add(error) } }
        SharedPreferences.setMockInitialValues(emptyMap())
        FlutterSecureStorage.setMockInitialValues(emptyMap())
        tempDir = Files.createTempDirectory("gang-test").toFile()
        PathProvider.install(
            PathProvider.Directories(
                applicationSupport = File(tempDir, "support"),
                applicationDocuments = File(tempDir, "documents"),
                applicationCache = File(tempDir, "cache"),
                temporary = File(tempDir, "tmp"),
                downloads = File(tempDir, "downloads"),
            ),
        )
    }

    override fun finished(description: Description) {
        AppCoroutines.reset()
        previousHandler?.let { AppCoroutines.uncaughtErrorHandler = it }
        Dispatchers.resetMain()
        DateTime.clock = defaultClock
        tempDir.deleteRecursively()
    }

    /** Runs [body] on the virtual-time event loop, then reports uncaught async errors. */
    fun runTest(timeout: Duration = Duration.parse("60s"), body: suspend TestScope.() -> Unit) =
        kotlinx.coroutines.test.runTest(dispatcher, timeout) {
            body()
            pumpEventQueue()
            val errors = synchronized(uncaught) { uncaught.toList().also { uncaught.clear() } }
            if (errors.isNotEmpty()) {
                val first = errors.first()
                errors.drop(1).forEach { first.addSuppressed(it) }
                throw AssertionError("Uncaught async error(s) during test", first)
            }
        }

    /** Errors reported so far by fire-and-forget work (and clears them). */
    fun takeUncaughtErrors(): List<Throwable> = synchronized(uncaught) { uncaught.toList().also { uncaught.clear() } }

    /** Pins `DateTime.now()` to [now] (micro precision). */
    fun setNow(now: DateTime) {
        DateTime.clock = { now.microsecondsSinceEpoch }
    }

    private companion object {
        val defaultClock = DateTime.clock
    }
}

/**
 * Dart's `pumpEventQueue()` / `await Future<void>.delayed(Duration.zero)`:
 * runs everything already queued on the event loop (without advancing time).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.pumpEventQueue() {
    testScheduler.runCurrent()
}

/** Dart's `fakeAsync` `async.elapse(duration)`: advances virtual time and runs due work. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.elapse(duration: Duration) {
    testScheduler.advanceTimeBy(duration)
    testScheduler.runCurrent()
}

/** Dart's `async.flushTimers()` / `pumpAndSettle` for logic: runs until idle. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.flushTimers() {
    testScheduler.advanceUntilIdle()
}

