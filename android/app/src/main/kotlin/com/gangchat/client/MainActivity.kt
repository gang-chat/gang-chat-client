package com.gangchat.client

import android.os.Build
import android.content.Intent
import android.view.Surface
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var updateInstaller: AndroidUpdateInstaller? = null
    private var systemBridge: AndroidSystemBridge? = null

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
    }
}
