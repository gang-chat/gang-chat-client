package com.gangchat.client.app

import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.protocol.GangApi
import com.gangchat.client.shell.AndroidPushRegistration
import com.gangchat.client.shell.AndroidSystemService
import com.gangchat.client.testing.GangTestRule
import com.gangchat.client.testing.pumpEventQueue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Port of test/unit/app/android_push_registration_controller_test.dart. */
class AndroidPushRegistrationControllerTest {
    @get:Rule
    val gang = GangTestRule()

    @Test
    fun `registers initial token and refreshed token in order`() =
        gang.runTest {
            val android =
                _FakeAndroidSystemService(
                    AndroidPushRegistration(
                        provider = "fcm",
                        installationId = "install-1",
                        token = "token-1",
                        enabled = true,
                    ),
                )
            val api = _RecordingGangApi()
            val controller =
                AndroidPushRegistrationController(
                    api = api,
                    androidSystemService = android,
                )
            try {
                controller.start()
                android.emit(
                    AndroidPushRegistration(
                        provider = "fcm",
                        installationId = "install-1",
                        token = "token-2",
                        enabled = false,
                    ),
                )
                pumpEventQueue()
                controller.synchronize()

                assertEquals(
                    listOf(
                        "fcm/install-1/token-1/true",
                        "fcm/install-1/token-2/false",
                        "fcm/install-1/token-2/false",
                    ),
                    api.upserts,
                )
            } finally {
                controller.dispose()
                android.dispose()
            }
        }

    @Test
    fun `dispose keeps registration while explicit unregister deletes it`() =
        gang.runTest {
            val android =
                _FakeAndroidSystemService(
                    AndroidPushRegistration(
                        provider = "fcm",
                        installationId = "install-2",
                        token = "token-2",
                        enabled = true,
                    ),
                )
            val api = _RecordingGangApi()
            val controller =
                AndroidPushRegistrationController(
                    api = api,
                    androidSystemService = android,
                )
            controller.start()

            controller.unregister()
            assertEquals(listOf("fcm/install-2"), api.deletes)

            controller.dispose()
            assertEquals(1, api.deletes.size)
            android.dispose()
        }

    @Test
    fun `a failed upsert does not block the operations queued after it`() =
        gang.runTest {
            val android =
                _FakeAndroidSystemService(
                    AndroidPushRegistration(
                        provider = "fcm",
                        installationId = "install-3",
                        token = "token-1",
                        enabled = true,
                    ),
                )
            val api = _RecordingGangApi()
            api.failNextUpsert = true
            val controller = AndroidPushRegistrationController(api = api, androidSystemService = android)

            try {
                controller.start()
                throw AssertionError("expected the first upsert to fail")
            } catch (error: IllegalStateException) {
                assertEquals("offline", error.message)
            }
            controller.synchronize()

            assertEquals(listOf("fcm/install-3/token-1/true"), api.upserts)
            controller.dispose()
            android.dispose()
        }

    @Test
    fun `unsupported platforms and disposed controllers do nothing`() =
        gang.runTest {
            val unsupported = _FakeAndroidSystemService(null, supported = false)
            val api = _RecordingGangApi()
            val controller = AndroidPushRegistrationController(api = api, androidSystemService = unsupported)
            controller.start()
            controller.dispose()
            controller.synchronize()
            controller.unregister()

            assertEquals(emptyList<String>(), api.upserts)
            assertEquals(emptyList<String>(), api.deletes)
            unsupported.dispose()
        }

    private class _FakeAndroidSystemService(
        var registration: AndroidPushRegistration?,
        private val supported: Boolean = true,
    ) : AndroidSystemService() {
        private val _changes = StreamController.broadcast<AndroidPushRegistration>()

        override val isSupported: Boolean get() = supported

        override val pushRegistrationChanges: Stream<AndroidPushRegistration>
            get() = _changes.stream

        override suspend fun pushRegistration(): AndroidPushRegistration? = registration

        fun emit(value: AndroidPushRegistration) {
            registration = value
            _changes.add(value)
        }

        fun dispose() = _changes.close()
    }

    private class _RecordingGangApi : GangApi by _unimplemented() {
        val upserts = mutableListOf<String>()
        val deletes = mutableListOf<String>()
        var failNextUpsert = false

        override suspend fun upsertPushDevice(
            provider: String,
            installationId: String,
            token: String,
            enabled: Boolean,
        ) {
            if (failNextUpsert) {
                failNextUpsert = false
                throw IllegalStateException("offline")
            }
            upserts.add("$provider/$installationId/$token/$enabled")
        }

        override suspend fun deletePushDevice(
            provider: String,
            installationId: String,
        ) {
            deletes.add("$provider/$installationId")
        }
    }
}

private inline fun <reified T : Any> _unimplemented(): T {
    val name = T::class.java.simpleName
    return java.lang.reflect.Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.getOrNull(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "Unimplemented $name"
            else -> throw NotImplementedError("$name.${method.name} is not implemented by this fake")
        }
    } as T
}
