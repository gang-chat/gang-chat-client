package com.gangchat.client.platform.webrtc

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioSwitch

/**
 * flutter_webrtc's Android `AudioDeviceKind`: the output kinds the plugin
 * exposed as `audiooutput` device ids.
 */
enum class AudioDeviceKind(val typeName: String, val audioDeviceClass: Class<out AudioDevice>) {
    BLUETOOTH("bluetooth", AudioDevice.BluetoothHeadset::class.java),
    WIRED_HEADSET("wired-headset", AudioDevice.WiredHeadset::class.java),
    SPEAKER("speaker", AudioDevice.Speakerphone::class.java),
    EARPIECE("earpiece", AudioDevice.Earpiece::class.java),
    ;

    companion object {
        fun fromAudioDevice(audioDevice: AudioDevice): AudioDeviceKind? =
            entries.firstOrNull { it.audioDeviceClass == audioDevice.javaClass }

        fun fromTypeName(typeName: String?): AudioDeviceKind? = entries.firstOrNull { it.typeName == typeName }
    }
}

/**
 * Port of flutter_webrtc's Android `AudioSwitchManager`: one process-wide
 * twilio AudioSwitch that owns audio routing (Bluetooth / wired headset /
 * speaker / earpiece), audio focus and the system audio mode.
 *
 * Like the plugin, the switch is created (and starts detecting devices) when
 * the WebRTC layer initializes at app start, is activated while a live room
 * runs, and is deactivated when it ends. Runtime configuration changes
 * (`setAudioConfiguration`) apply to the live switch immediately. The LiveKit
 * SDK's own per-room AudioSwitchHandler is replaced by [LiveKitAudioHandler],
 * which delegates here, so routing and device lists behave like the Flutter
 * build: one switch for the whole process, available outside rooms too.
 *
 * AudioSwitch is not thread safe; every call runs on the main thread.
 */
class AudioSwitchManager(private val context: Context) {
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var loggingEnabled = false
    private var isActive = false

    /** Called with the device list and the selected device whenever either changes. */
    @Volatile
    var audioDeviceChangeListener: (List<AudioDevice>, AudioDevice?) -> Unit = { _, _ -> }

    var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { }

    var preferredDeviceList: List<Class<out AudioDevice>> =
        listOf(
            AudioDevice.BluetoothHeadset::class.java,
            AudioDevice.WiredHeadset::class.java,
            AudioDevice.Speakerphone::class.java,
            AudioDevice.Earpiece::class.java,
        )

    // AudioSwitch is not threadsafe, so all calls should be done on the main thread.
    private val handler = Handler(Looper.getMainLooper())

    private var audioSwitch: AudioSwitch? = null

    /** When true, requests audio focus on start and abandons it on stop. */
    private var manageAudioFocus = true

    /** The audio focus mode to use while started. */
    private var focusMode = AudioManager.AUDIOFOCUS_GAIN

    /** The audio mode to use while started. */
    private var audioMode = AudioManager.MODE_IN_COMMUNICATION

    /** The audio stream type to use when requesting audio focus on pre-O devices. */
    private var audioStreamType = AudioManager.STREAM_VOICE_CALL

    /** The audio attribute usage type to use when requesting audio focus on O+. */
    private var audioAttributeUsageType = AudioAttributes.USAGE_VOICE_COMMUNICATION

    /** The audio attribute content type to use when requesting audio focus on O+. */
    private var audioAttributeContentType = AudioAttributes.CONTENT_TYPE_SPEECH

    /**
     * On certain Android devices, audio routing does not function properly and
     * bluetooth microphones will not work unless audio mode is set to
     * MODE_IN_COMMUNICATION or MODE_IN_CALL. If this is true, routing is
     * attempted anyway.
     */
    private var forceHandleAudioRouting = false

    init {
        initAudioSwitch()
    }

    private fun initAudioSwitch() {
        if (audioSwitch != null) return
        handler.removeCallbacksAndMessages(null)
        handler.postAtFrontOfQueue {
            try {
                val switch =
                    AudioSwitch(
                        context,
                        loggingEnabled,
                        audioFocusChangeListener,
                        preferredDeviceList,
                    )
                switch.manageAudioFocus = manageAudioFocus
                switch.focusMode = focusMode
                switch.audioMode = audioMode
                switch.audioStreamType = audioStreamType
                switch.audioAttributeContentType = audioAttributeContentType
                switch.audioAttributeUsageType = audioAttributeUsageType
                switch.forceHandleAudioRouting = forceHandleAudioRouting
                audioSwitch = switch
                switch.start { devices, selected -> audioDeviceChangeListener(devices, selected) }
            } catch (error: RuntimeException) {
                Log.w(TAG, "AudioSwitch could not start", error)
            }
        }
    }

    fun start() {
        if (audioSwitch == null) return
        handler.removeCallbacksAndMessages(null)
        handler.postAtFrontOfQueue {
            if (!isActive) {
                audioSwitch?.activate()
                isActive = true
            }
        }
    }

    fun stop() {
        if (audioSwitch == null) return
        handler.removeCallbacksAndMessages(null)
        handler.postAtFrontOfQueue {
            if (isActive) {
                audioSwitch?.deactivate()
                isActive = false
            }
        }
    }

    fun setMicrophoneMute(mute: Boolean) {
        audioManager.isMicrophoneMute = mute
    }

    fun selectedAudioDevice(): AudioDevice? = requireNotNull(audioSwitch).selectedAudioDevice

    fun availableAudioDevices(): List<AudioDevice> = requireNotNull(audioSwitch).availableAudioDevices

    fun selectAudioOutput(audioDeviceClass: Class<out AudioDevice>) {
        handler.post {
            val audioDevice = availableAudioDevices().firstOrNull { it.javaClass == audioDeviceClass }
            if (audioDevice != null) audioSwitch?.selectDevice(audioDevice)
        }
    }

    private fun updatePreferredDeviceList(speakerOn: Boolean) {
        preferredDeviceList =
            buildList {
                add(AudioDevice.BluetoothHeadset::class.java)
                add(AudioDevice.WiredHeadset::class.java)
                if (speakerOn) {
                    add(AudioDevice.Speakerphone::class.java)
                    add(AudioDevice.Earpiece::class.java)
                } else {
                    add(AudioDevice.Earpiece::class.java)
                    add(AudioDevice.Speakerphone::class.java)
                }
            }
        val list = preferredDeviceList
        handler.post { audioSwitch?.setPreferredDeviceList(list) }
    }

    fun enableSpeakerphone(enable: Boolean) {
        updatePreferredDeviceList(enable)
        if (enable) {
            selectAudioOutput(AudioDevice.Speakerphone::class.java)
        } else {
            val audioDevice =
                availableAudioDevices().firstOrNull {
                    it is AudioDevice.BluetoothHeadset || it is AudioDevice.WiredHeadset || it is AudioDevice.Earpiece
                }
            if (audioDevice != null) {
                selectAudioOutput(audioDevice.javaClass)
            } else {
                handler.post { audioSwitch?.selectDevice(null) }
            }
        }
    }

    fun enableSpeakerButPreferBluetooth() {
        val audioDevice =
            availableAudioDevices().firstOrNull {
                it is AudioDevice.BluetoothHeadset || it is AudioDevice.WiredHeadset
            }
        if (audioDevice == null) {
            selectAudioOutput(AudioDevice.Speakerphone::class.java)
        } else {
            selectAudioOutput(audioDevice.javaClass)
        }
    }

    fun selectAudioOutput(kind: AudioDeviceKind?) {
        if (kind != null) selectAudioOutput(kind.audioDeviceClass)
    }

    /** `setAndroidAudioConfiguration`: applies each non-null value to the live switch. */
    fun setAudioConfiguration(configuration: AndroidAudioConfiguration) {
        val map = configuration.toMap()
        setManageAudioFocus(map["manageAudioFocus"] as? Boolean)
        setAudioMode(map["androidAudioMode"] as? String)
        setFocusMode(map["androidAudioFocusMode"] as? String)
        setAudioStreamType(map["androidAudioStreamType"] as? String)
        setAudioAttributesUsageType(map["androidAudioAttributesUsageType"] as? String)
        setAudioAttributesContentType(map["androidAudioAttributesContentType"] as? String)
        setForceHandleAudioRouting(map["forceHandleAudioRouting"] as? Boolean)
    }

    fun setManageAudioFocus(manage: Boolean?) {
        val switch = audioSwitch
        if (manage != null && switch != null) {
            manageAudioFocus = manage
            switch.manageAudioFocus = manage
        }
    }

    fun setAudioMode(audioModeString: String?) {
        val mode = WebRtcAudioUtils.getAudioModeForString(audioModeString) ?: return
        audioMode = mode
        audioSwitch?.audioMode = mode
    }

    fun setFocusMode(focusModeString: String?) {
        val mode = WebRtcAudioUtils.getFocusModeForString(focusModeString) ?: return
        focusMode = mode
        audioSwitch?.focusMode = mode
    }

    fun setAudioStreamType(streamTypeString: String?) {
        val streamType = WebRtcAudioUtils.getStreamTypeForString(streamTypeString) ?: return
        audioStreamType = streamType
        audioSwitch?.audioStreamType = streamType
    }

    fun setAudioAttributesUsageType(usageTypeString: String?) {
        val usageType = WebRtcAudioUtils.getAudioAttributesUsageTypeForString(usageTypeString) ?: return
        audioAttributeUsageType = usageType
        audioSwitch?.audioAttributeUsageType = usageType
    }

    fun setAudioAttributesContentType(contentTypeString: String?) {
        val contentType = WebRtcAudioUtils.getAudioAttributesContentTypeFromString(contentTypeString) ?: return
        audioAttributeContentType = contentType
        audioSwitch?.audioAttributeContentType = contentType
    }

    fun setForceHandleAudioRouting(force: Boolean?) {
        val switch = audioSwitch
        if (force != null && switch != null) {
            forceHandleAudioRouting = force
            switch.forceHandleAudioRouting = force
        }
    }

    @SuppressLint("NewApi")
    fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
    }

    private companion object {
        const val TAG = "AudioSwitchManager"
    }
}
