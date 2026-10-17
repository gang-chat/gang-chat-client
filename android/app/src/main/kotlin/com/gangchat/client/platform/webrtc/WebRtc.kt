package com.gangchat.client.platform.webrtc

import android.content.Context
import android.hardware.Camera
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.platform.GangChatPlatform
import io.livekit.android.audio.AudioProcessorInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.WeakHashMap
import livekit.org.webrtc.audio.JavaAudioDeviceModule

/** flutter_webrtc's `MediaDeviceInfo` as the Android plugin reported it (`getSources`). */
class WebRtcMediaDeviceInfo(
    val deviceId: String,
    val label: String,
    val kind: String,
    val groupId: String?,
)

/**
 * The process-wide part of flutter_webrtc's Android plugin that Gang Chat
 * relied on, rebuilt on top of the LiveKit Android SDK:
 *
 * - `WebRTC.initialize(options)`: hardware AEC/NS on or off and the playback
 *   [AudioAttributes] of the audio device module. LiveKit creates one audio
 *   device module per room; [javaAudioDeviceModuleCustomizer] applies the
 *   options to each of them, as the plugin applied them to its single module.
 * - `Helper.setLocalAudioInputVolume`: app-local microphone gain after
 *   capture ([capturePostProcessing], the fork's `AudioProcessingAdapter`).
 * - `Helper.setAndroidAudioConfiguration`, `selectAudioOutput`,
 *   `selectAudioInput` and `navigator.mediaDevices.enumerateDevices` /
 *   `ondevicechange`, backed by [AudioSwitchManager] and the AudioManager.
 *
 * Everything runs on the main thread, like the plugin's method handlers.
 */
object WebRtc {
    private const val TAG = "FlutterWebRTCPlugin"

    /** Defaults to true for compatibility, like the fork's `androidUseHardwareAudioProcessing`. */
    @Volatile
    var useHardwareAudioProcessing: Boolean = true
        private set

    /** The playback attributes of the WebRTC audio device module, if configured. */
    @Volatile
    var audioAttributes: AudioAttributes? = null
        private set

    /** The fork's `capturePostProcessing` adapter: local microphone gain. */
    val capturePostProcessing = LocalAudioInputVolumeProcessor()

    private var audioSwitchManagerInstance: AudioSwitchManager? = null

    /** The process-wide audio switch; null until [initialize] ran with a context available. */
    val audioSwitchManager: AudioSwitchManager?
        get() {
            if (audioSwitchManagerInstance == null && GangChatPlatform.isInstalled) {
                audioSwitchManagerInstance = createAudioSwitchManager(GangChatPlatform.context)
            }
            return audioSwitchManagerInstance
        }

    private val deviceChangeController = StreamController.broadcast<Unit>()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** `navigator.mediaDevices.ondevicechange`: output routing topology changed. */
    val deviceChanges: Stream<Unit> get() = deviceChangeController.stream

    /** The input device chosen with [selectAudioInput]; applied to every room's module. */
    @Volatile
    private var preferredInput: AudioDeviceInfo? = null

    private val audioDeviceModules: MutableSet<JavaAudioDeviceModule> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    /**
     * `WebRTC.initialize(options:)`. Reads `androidUseHardwareAudioProcessing`
     * and `androidAudioConfiguration.androidAudioAttributes{Usage,Content}Type`
     * and starts the process-wide audio switch.
     */
    fun initialize(options: Map<String, Any?>) {
        val hardware = options["androidUseHardwareAudioProcessing"]
        // Defaults to true for compatibility. Apps can disable vendor hardware
        // AEC/NS and let WebRTC's software APM handle voice processing on
        // devices whose built-in effects introduce audible artifacts.
        useHardwareAudioProcessing = hardware as? Boolean ?: true

        @Suppress("UNCHECKED_CAST")
        val configuration = options["androidAudioConfiguration"] as? Map<String, Any?>
        if (configuration != null) {
            val usageType =
                WebRtcAudioUtils.getAudioAttributesUsageTypeForString(
                    configuration["androidAudioAttributesUsageType"] as? String,
                )
            val contentType =
                WebRtcAudioUtils.getAudioAttributesContentTypeFromString(
                    configuration["androidAudioAttributesContentType"] as? String,
                )
            // Warn if one is provided without the other.
            if ((usageType == null) xor (contentType == null)) {
                Log.w(TAG, "usageType and contentType must both be provided!")
            }
            if (usageType != null && contentType != null) {
                audioAttributes =
                    AudioAttributes.Builder()
                        .setUsage(usageType)
                        .setContentType(contentType)
                        .build()
            }
        }
        // The plugin created its AudioSwitch when it attached to the engine, so
        // device detection was live before any room.
        audioSwitchManager
    }

    private fun createAudioSwitchManager(context: Context): AudioSwitchManager? =
        try {
            AudioSwitchManager(context).also { manager ->
                manager.audioDeviceChangeListener = { devices, currentDevice ->
                    Log.w(TAG, "audioFocusChangeListener $devices $currentDevice")
                    mainHandler.post { deviceChangeController.add(Unit) }
                }
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Audio routing is unavailable", error)
            null
        }

    /**
     * Applies the initialization options to a LiveKit room's audio device
     * module, like the plugin's `initialize` built its module.
     */
    fun javaAudioDeviceModuleCustomizer(builder: JavaAudioDeviceModule.Builder) {
        val useHardware = useHardwareAudioProcessing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val useLowLatency = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        builder
            .setUseHardwareAcousticEchoCanceler(useHardware)
            .setUseLowLatency(useLowLatency)
            .setUseHardwareNoiseSuppressor(useHardware)
        audioAttributes?.let { builder.setAudioAttributes(it) }
    }

    /** Tracks a room's audio device module so input selection reaches it. */
    fun registerAudioDeviceModule(module: JavaAudioDeviceModule) {
        audioDeviceModules.add(module)
        if (useHardwareAudioProcessing && JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()) {
            module.setNoiseSuppressorEnabled(true)
        }
        val input = preferredInput
        if (input != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            module.setPreferredInputDevice(input)
        }
    }

    private var audioSwitchUsers = 0

    /**
     * Activates the audio switch for one user (a live room or a microphone
     * test). The plugin activated it on the first capture or peer connection
     * and deactivated it when the last peer connection closed.
     */
    fun acquireAudioSwitch() {
        synchronized(this) {
            audioSwitchUsers += 1
            if (audioSwitchUsers != 1) return
        }
        audioSwitchManager?.start()
    }

    /** Releases one [acquireAudioSwitch]; the last release deactivates the switch. */
    fun releaseAudioSwitch() {
        synchronized(this) {
            if (audioSwitchUsers == 0) return
            audioSwitchUsers -= 1
            if (audioSwitchUsers != 0) return
        }
        audioSwitchManager?.stop()
    }

    /** `Helper.setLocalAudioInputVolume(volume)`. */
    fun setLocalAudioInputVolume(volume: Double) {
        capturePostProcessing.setProcessingVolume(volume)
    }

    /** `Helper.setAndroidAudioConfiguration(configuration)`. */
    fun setAndroidAudioConfiguration(configuration: AndroidAudioConfiguration) {
        requireAudioSwitch().setAudioConfiguration(configuration)
    }

    /** `Helper.selectAudioOutput(deviceId)`. */
    fun selectAudioOutput(deviceId: String) {
        requireAudioSwitch().selectAudioOutput(AudioDeviceKind.fromTypeName(deviceId))
    }

    /** `Helper.selectAudioInput(deviceId)` (`setPreferredInputDevice`). */
    fun selectAudioInput(deviceId: String) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            throw UnsupportedOperationException("selectAudioInput")
        }
        val audioManager = GangChatPlatform.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val device =
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .firstOrNull { deviceId == getAudioDeviceId(it) } ?: return
        preferredInput = device
        synchronized(audioDeviceModules) {
            for (module in audioDeviceModules.toList()) module.setPreferredInputDevice(device)
        }
    }

    /** `Helper.setSpeakerphoneOn(enable)`. */
    fun setSpeakerphoneOn(enable: Boolean) {
        requireAudioSwitch().enableSpeakerphone(enable)
    }

    private fun requireAudioSwitch(): AudioSwitchManager =
        audioSwitchManager ?: throw IllegalStateException("WebRTC audio routing is not initialized")

    /**
     * `navigator.mediaDevices.enumerateDevices()` (the plugin's `getSources`):
     * cameras, the built-in / Bluetooth SCO / wired-headset microphones, and
     * the AudioSwitch outputs with their kind as device id.
     */
    @Suppress("DEPRECATION")
    fun enumerateDevices(): List<WebRtcMediaDeviceInfo> {
        val devices = mutableListOf<WebRtcMediaDeviceInfo>()
        for (index in 0 until Camera.getNumberOfCameras()) {
            val info = Camera.CameraInfo()
            try {
                Camera.getCameraInfo(index, info)
            } catch (error: Exception) {
                Log.e("CameraEnumerationAndroid", "getCameraInfo failed on index $index", error)
                continue
            }
            val facing = if (info.facing == 1) "front" else "back"
            devices.add(
                WebRtcMediaDeviceInfo(
                    deviceId = "$index",
                    label = "Camera $index, Facing $facing, Orientation ${info.orientation}",
                    kind = "videoinput",
                    groupId = "camera",
                ),
            )
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            devices.add(WebRtcMediaDeviceInfo("audio-1", "Audio", "audioinput", "microphone"))
        } else {
            val audioManager = GangChatPlatform.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            for (device in audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                ) {
                    devices.add(
                        WebRtcMediaDeviceInfo(
                            deviceId = getAudioDeviceId(device),
                            label = getAudioDeviceLabel(device),
                            kind = "audioinput",
                            groupId = getAudioGroupId(device),
                        ),
                    )
                }
            }
        }

        for (output in requireAudioSwitch().availableAudioDevices()) {
            val typeName = AudioDeviceKind.fromAudioDevice(output)?.typeName
            devices.add(
                WebRtcMediaDeviceInfo(
                    deviceId = typeName.toString(),
                    label = output.name,
                    kind = "audiooutput",
                    groupId = typeName,
                ),
            )
        }
        return devices
    }

    /** `AudioUtils.getAudioDeviceId`. */
    fun getAudioDeviceId(device: AudioDeviceInfo): String {
        val address = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) "" else device.address
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "microphone-$address"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired-headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth"
            else -> "${device.id}"
        }
    }

    /** `AudioUtils.getAudioGroupId`. */
    fun getAudioGroupId(device: AudioDeviceInfo): String =
        when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "microphone"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired-headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth"
            else -> "${device.type}"
        }

    /** `AudioUtils.getAudioDeviceLabel`. */
    fun getAudioDeviceLabel(device: AudioDeviceInfo): String {
        val address = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) "" else device.address
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone ($address)"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset Microphone"
            else -> device.productName.toString()
        }
    }
}

/**
 * The fork's `AudioProcessingAdapter` used as `capturePostProcessing`: scales
 * the captured microphone samples by the app-local input volume (0..1) after
 * WebRTC's own processing, so muting never touches the system input gain or
 * stops the shared input device.
 */
class LocalAudioInputVolumeProcessor : AudioProcessorInterface {
    @Volatile
    private var processingVolume = 1.0

    val volume: Double get() = processingVolume

    fun setProcessingVolume(volume: Double) {
        val value = if (volume.isNaN() || volume.isInfinite()) 1.0 else volume
        processingVolume = value.coerceIn(0.0, 1.0)
    }

    override fun isEnabled(): Boolean = true

    override fun getName(): String = "gang_chat_local_input_volume"

    override fun initializeAudioProcessing(sampleRateHz: Int, numChannels: Int) {}

    override fun resetAudioProcessing(newRate: Int) {}

    override fun processAudio(numBands: Int, numFrames: Int, buffer: ByteBuffer) {
        val volume = processingVolume
        if (volume < 0.999999) scaleFloatSamples(buffer, volume)
    }

    companion object {
        fun scaleFloatSamples(buffer: ByteBuffer, volume: Double) {
            // WebRTC exposes native float samples through a direct ByteBuffer. The
            // Java buffer's byte-order metadata is not guaranteed to match the
            // native sample layout, so always decode and encode using native order.
            val duplicate = buffer.duplicate().order(ByteOrder.nativeOrder())
            val start = duplicate.position()
            val end = duplicate.limit() - ((duplicate.limit() - start) % java.lang.Float.BYTES)
            var offset = start
            while (offset < end) {
                val sample = duplicate.getFloat(offset)
                if (!sample.isNaN() && !sample.isInfinite()) {
                    duplicate.putFloat(offset, (sample * volume).toFloat())
                } else {
                    duplicate.putFloat(offset, 0.0f)
                }
                offset += java.lang.Float.BYTES
            }
        }
    }
}
