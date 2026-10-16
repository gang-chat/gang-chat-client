package com.gangchat.client

import android.os.Build
import android.content.Intent
import android.media.AudioManager
import android.view.Surface
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var updateInstaller: AndroidUpdateInstaller? = null
    private var systemBridge: AndroidSystemBridge? = null
    private var musicPreviewPlayer: AndroidMusicPreviewPlayer? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "gang_chat/display_orientation",
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "getDisplayRotation" -> result.success(currentDisplayRotation())
                else -> result.notImplemented()
            }
        }
        updateInstaller = AndroidUpdateInstaller(this).also { installer ->
            MethodChannel(
                flutterEngine.dartExecutor.binaryMessenger,
                AndroidUpdateInstaller.channelName,
            ).setMethodCallHandler(installer::handleMethodCall)
        }
        val musicPreviewChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "gang_chat/music_preview",
        )
        musicPreviewPlayer = AndroidMusicPreviewPlayer(musicPreviewChannel).also { preview ->
            musicPreviewChannel.setMethodCallHandler(preview::handleMethodCall)
        }
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "gang_chat/android_audio_route",
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "setMusicBoxActive" -> {
                    val active = call.argument<Boolean>("active") == true
                    val liveSessionActive =
                        call.argument<Boolean>("liveSessionActive") == true
                    volumeControlStream = if (active) {
                        AudioManager.STREAM_MUSIC
                    } else {
                        AudioManager.USE_DEFAULT_STREAM_TYPE
                    }
                    // flutter_webrtc updates AudioSwitch's desired mode at
                    // runtime, but some OEM implementations (including OPPO)
                    // do not apply that mode to AudioManager until a later
                    // device-route cycle. This channel runs on Android's main
                    // thread, so make the active system mode authoritative now.
                    val audioManager = getSystemService(AudioManager::class.java)
                    audioManager.mode = when {
                        active -> AudioManager.MODE_NORMAL
                        liveSessionActive -> AudioManager.MODE_IN_COMMUNICATION
                        else -> AudioManager.MODE_NORMAL
                    }
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
        systemBridge = AndroidSystemBridge(this).also { bridge ->
            bridge.attach(
                MethodChannel(
                    flutterEngine.dartExecutor.binaryMessenger,
                    AndroidSystemBridge.channelName,
                ),
            )
            bridge.handleIntent(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (systemBridge?.onActivityResult(requestCode, resultCode, data) == true) return
        updateInstaller?.onActivityResult(requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        systemBridge?.onRequestPermissionsResult(requestCode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        systemBridge?.handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateInstaller?.onResume()
    }

    override fun onStart() {
        super.onStart()
        GangChatAppVisibility.isForeground = true
    }

    override fun onDestroy() {
        musicPreviewPlayer?.dispose()
        musicPreviewPlayer = null
        super.onDestroy()
    }

    override fun onStop() {
        GangChatAppVisibility.isForeground = false
        super.onStop()
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            windowManager.defaultDisplay.rotation
        }
    }

    companion object {
        const val notificationRoomIdExtra = "gang_chat_notification_room_id"
        const val pendingUpdateInstallExtra = "gang_chat_pending_update_install"
    }
}
