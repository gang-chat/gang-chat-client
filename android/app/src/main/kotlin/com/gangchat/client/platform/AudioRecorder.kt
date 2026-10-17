package com.gangchat.client.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Range
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.gangchat.client.foundation.AppCoroutines
import com.gangchat.client.foundation.Stream
import com.gangchat.client.foundation.StreamController
import com.gangchat.client.foundation.Timer
import com.gangchat.client.foundation.timeout
import com.gangchat.client.foundation.unawaited
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The `record` plugin's `AudioEncoder`; the app records AAC-LC on Android. */
enum class AudioEncoder { aacLc }

/**
 * The `record` plugin's `RecordConfig`, reduced to the fields the app sets.
 * Everything else keeps record 7.1's defaults on Android: the default audio
 * source, auto gain / echo cancellation / noise suppression off, Bluetooth
 * SCO managed, and recording paused when another app takes audio focus.
 */
class RecordConfig(
    val encoder: AudioEncoder = AudioEncoder.aacLc,
    val bitRate: Int = 128000,
    val sampleRate: Int = 44100,
    val numChannels: Int = 2,
)

/** The `record` plugin's `Amplitude` (dBFS). */
class Amplitude(val current: Double, val max: Double)

/** The `record` plugin's `RecordState`. */
enum class RecordState { pause, record, stop }

/**
 * The subset of the `record` plugin's `AudioRecorder` the app uses, with the
 * behaviour of record 7.1 / record_android 2.1 when recording to a file:
 *
 * - PCM 16-bit from [AudioRecord] (default source) on a dedicated thread,
 *   encoded by a [MediaCodec] AAC encoder and written by [MediaMuxer] into an
 *   MPEG-4 (`.m4a`) container. Encoder selection is the plugin's: the first
 *   AAC encoder that accepts the format, after snapping the channel count to
 *   the default input device and, if needed, the bit rate, sample rate and
 *   channels to the encoder's capabilities. Presentation times come from the
 *   PCM byte position, like the plugin's.
 * - Audio focus (`AUDIOFOCUS_GAIN`, media / speech) is requested when
 *   recording starts and abandoned when it stops; losing focus pauses the
 *   recording.
 * - A connected Bluetooth SCO headset becomes the communication device while
 *   recording (`manageBluetooth`). Unlike the plugin, the route is left alone
 *   during a voice call, and releasing it (on [stop], [cancel] and [dispose];
 *   the plugin kept it after [stop]) restores the previous device instead of
 *   clearing the communication device, which reset a call's audio route.
 * - [stop] waits until the file is finalized and returns its path; [cancel]
 *   signals the recording thread, which deletes the file once it finishes.
 * - Calls are serialized, like the plugin's per-recorder semaphore. Failures
 *   inside the recording thread are logged, as the plugin reported them only
 *   on the (unobserved) state stream; [stop] still returns the path.
 */
open class AudioRecorder(context: Context? = null) {
    private val explicitContext: Context? = context?.applicationContext ?: context
    private val context: Context get() = explicitContext ?: GangChatPlatform.context
    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _mutex = Mutex()
    private var _created = false
    private var _disposed = false
    private var _session: _RecordSession? = null
    private var _lastPath: String? = null
    private var _maxAmplitude = defaultAmplitude
    private var _bluetooth: _BluetoothSco? = null
    private var _permissionRequests = 0

    private var _amplitudeTimer: Timer? = null
    private var _amplitudeController: StreamController<Amplitude>? = null
    private var _amplitudeInterval: Duration = 300.milliseconds

    // -- Dart AudioRecorder ----------------------------------------------------

    /** Checks and optionally requests the microphone permission. */
    open suspend fun hasPermission(request: Boolean = true): Boolean =
        _safeCall { _hasPermission(request) }

    /** Starts recording [config] into the file at [path]. */
    open suspend fun start(config: RecordConfig, path: String) {
        _safeCall { _start(config, path) }
    }

    /** Stops recording and returns the output path, once the file is complete. */
    open suspend fun stop(): String? = _safeCall { _stop() }

    /** Stops and discards the recording (the file is deleted). */
    open suspend fun cancel() {
        _safeCall {
            _session?.cancelRecording()
            _bluetooth?.stop()
            _bluetooth = null
        }
    }

    open suspend fun isRecording(): Boolean = _safeCall { _session?.isRecording == true }

    /** Current and maximum amplitude of this recording, in dBFS. */
    open suspend fun getAmplitude(): Amplitude = _safeCall { _amplitude() }

    /** Requests the amplitude at [interval] while recording. */
    open fun onAmplitudeChanged(interval: Duration): Stream<Amplitude> {
        val existing = _amplitudeController
        if (existing != null) {
            if (_amplitudeInterval != interval) {
                _amplitudeInterval = interval
                _startAmplitudeMonitoring()
            }
            return existing.stream
        }
        val controller = StreamController.broadcast<Amplitude>()
        _amplitudeController = controller
        _amplitudeInterval = interval
        _startAmplitudeMonitoring()
        return controller.stream
    }

    open suspend fun dispose() {
        _safeCall {
            _amplitudeTimer?.cancel()
            _amplitudeController?.close()
            _amplitudeController = null
            // The native recorder stopped (keeping the file) without waiting.
            _session?.stopRecording()
            _bluetooth?.stop()
            _bluetooth = null
            _disposed = true
        }
    }

    // -- Implementation ----------------------------------------------------------

    private suspend fun <T> _safeCall(block: suspend () -> T): T =
        _mutex.withLock {
            if (_disposed) {
                throw PlatformException(
                    "record",
                    "Recorder has not yet been created or has already been disposed.",
                )
            }
            block()
        }

    private suspend fun _hasPermission(request: Boolean): Boolean {
        val activity = GangChatPlatform.activity ?: return false
        val granted =
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted || !request) return granted

        val result = CompletableDeferred<Boolean>()
        val launcher: ActivityResultLauncher<String> =
            activity.activityResultRegistry.register(
                "gang_chat_record_audio_${_permissionRequests++}",
                ActivityResultContracts.RequestPermission(),
            ) { allowed -> result.complete(allowed) }
        try {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
            return result.await()
        } finally {
            launcher.unregister()
        }
    }

    private suspend fun _start(config: RecordConfig, path: String) {
        val previous = _session
        if (previous != null) {
            // The plugin stopped a running recording before starting the next
            // one. A cancelled or disposed one may still be finishing.
            previous.stopRecording()
            previous.finished.await()
            _onSessionStopped(previous)
        }
        val bluetooth = _bluetooth ?: _BluetoothSco().also { _bluetooth = it }
        bluetooth.maybeStart()

        _created = true
        _lastPath = path
        val session = _RecordSession(config, path)
        _session = session
        session.start()
        session.started.await()
        // A session whose thread already failed has run its stop handling;
        // requesting focus now would leave it held (a race in the plugin).
        if (!session.finished.isCompleted) session.focus.request()
    }

    private suspend fun _stop(): String? {
        if (!_created) return null
        val session = _session
        if (session != null) {
            session.stopRecording()
            session.finished.await()
            _onSessionStopped(session)
        }
        _bluetooth?.stop()
        _bluetooth = null
        return _lastPath
    }

    private fun _amplitude(): Amplitude {
        if (!_created) return Amplitude(current = 0.0, max = 0.0)
        val amplitude = _session?.amplitude ?: defaultAmplitude
        if (amplitude > _maxAmplitude) _maxAmplitude = amplitude
        return Amplitude(current = amplitude, max = _maxAmplitude)
    }

    /** The native `onStop`: runs once per session on the event loop. */
    private fun _onSessionStopped(session: _RecordSession) {
        if (session.stopHandled) return
        session.stopHandled = true
        if (_session === session) _session = null
        session.focus.abandon()
        _maxAmplitude = defaultAmplitude
        _onState(RecordState.stop)
    }

    private fun _onState(state: RecordState) {
        when (state) {
            RecordState.pause, RecordState.stop -> _amplitudeTimer?.cancel()
            RecordState.record -> _startAmplitudeMonitoring()
        }
    }

    private fun _startAmplitudeMonitoring() {
        _amplitudeTimer?.cancel()
        if (_amplitudeController == null) return
        _amplitudeTimer =
            Timer.periodic(_amplitudeInterval) { _ ->
                unawaited { _updateAmplitudeAtInterval() }
            }
    }

    private suspend fun _updateAmplitudeAtInterval() {
        val controller = _amplitudeController
        val shouldUpdate =
            controller != null &&
                !controller.isClosed &&
                controller.hasListener &&
                _amplitudeTimer?.isActive == true
        if (shouldUpdate && isRecording()) {
            val amplitude = getAmplitude()
            val current = _amplitudeController
            if (current != null && !current.isClosed) current.add(amplitude)
        }
    }

    private fun _postToLoop(block: () -> Unit) {
        AppCoroutines.scope.launch(AppCoroutines.loop) { block() }
    }

    // -- Recording thread (record_android's RecordThread) -------------------------

    private inner class _RecordSession(val config: RecordConfig, val path: String) {
        private val _recording = AtomicBoolean(false)
        private val _paused = AtomicBoolean(false)
        private val _pausedSemaphore = Semaphore(0, true)

        @Volatile private var _cancelled = false

        @Volatile private var _thread: Thread? = null

        @Volatile private var _reader: _PcmReader? = null

        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val focus = _AudioFocus { pauseRecording() }
        var stopHandled = false

        val isRecording: Boolean get() = _thread != null && _recording.get()
        val isPaused: Boolean get() = _thread != null && _paused.get()
        val amplitude: Double get() = _reader?.amplitudeDb ?: defaultAmplitude

        fun start() {
            val thread = Thread({ _run() }, "RecordThread-$path")
            thread.isDaemon = true
            _thread = thread
            thread.start()
        }

        fun pauseRecording() {
            if (isRecording) {
                _recording.set(true)
                _paused.set(true)
            }
        }

        fun stopRecording() {
            if (isRecording) {
                _recording.set(false)
                _paused.set(false)
                _pausedSemaphore.release()
            }
        }

        fun cancelRecording() {
            _cancelled = true
            if (isRecording) {
                stopRecording()
            } else if (_thread == null) {
                _deleteFile(path)
            }
        }

        private fun _run() {
            var reader: _PcmReader? = null
            var encoder: _AacMp4Encoder? = null
            try {
                val format = _selectAacFormat(config)
                reader = _PcmReader(format)
                reader.start()
                encoder = _AacMp4Encoder(format, path)
                encoder.start()
                _reader = reader

                _recording.set(true)
                _paused.set(false)
                _postToLoop { _onState(RecordState.record) }
                started.complete(Unit)

                while (isRecording) {
                    if (isPaused) {
                        _postToLoop { _onState(RecordState.pause) }
                        _pausedSemaphore.acquire()
                    } else {
                        val buffer = reader.read()
                        if (buffer.isNotEmpty()) encoder.encode(buffer)
                    }
                }
            } catch (error: Exception) {
                _onFailure(error)
            } finally {
                started.complete(Unit)
                _reader = null
                _stopAndRelease(reader, encoder)
            }
        }

        private fun _stopAndRelease(reader: _PcmReader?, encoder: _AacMp4Encoder?) {
            try {
                try {
                    reader?.release()
                } catch (error: Exception) {
                    _onFailure(error)
                }
                try {
                    encoder?.stop()
                } catch (error: Exception) {
                    _onFailure(error)
                }
                if (_cancelled) _deleteFile(path)
            } catch (error: Exception) {
                _onFailure(error)
            } finally {
                _recording.set(false)
                _thread = null
                finished.complete(Unit)
                _postToLoop { _onSessionStopped(this) }
            }
        }
    }

    // -- Audio focus (record_android's AudioSessionManager) ------------------------

    private inner class _AudioFocus(private val onFocusLoss: () -> Unit) {
        private var listener: AudioManager.OnAudioFocusChangeListener? = null
        private var focusRequest: AudioFocusRequest? = null

        @Suppress("DEPRECATION")
        fun request() {
            val changeListener =
                AudioManager.OnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                    ) {
                        onFocusLoss()
                    }
                    // Regaining focus does not resume: record's default
                    // interruption mode is `pause`.
                }
            listener = changeListener
            if (Build.VERSION.SDK_INT >= 26) {
                val request =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(changeListener, Handler(Looper.getMainLooper()))
                        .build()
                focusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                audioManager.requestAudioFocus(
                    changeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN,
                )
            }
        }

        @Suppress("DEPRECATION")
        fun abandon() {
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                listener?.let { audioManager.abandonAudioFocus(it) }
            }
            listener = null
        }
    }

    // -- Bluetooth SCO (record_android's BluetoothManager / BluetoothReceiver) ----

    private inner class _BluetoothSco {
        private val devices = HashSet<AudioDeviceInfo>()
        private var receiver: BroadcastReceiver? = null
        private var deviceCallback: AudioDeviceCallback? = null
        private var connected: CompletableDeferred<Unit>? = null
        private var startNotified = false

        /** Whether this recorder changed the route and has to put it back. */
        private var routed = false

        /** The device this recorder made the communication device (API 31+). */
        private var routedDevice: AudioDeviceInfo? = null

        /** The communication device before that, restored on release (API 31+). */
        private var previousDevice: AudioDeviceInfo? = null

        /** Routes capture to a connected SCO headset before recording starts. */
        suspend fun maybeStart() {
            if (receiver != null) return
            // A voice call owns the route; recording captures from the call's
            // device as it is.
            if (isInVoiceCall(context)) return
            val waiter = CompletableDeferred<Unit>()
            connected = waiter
            val scoReceiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                        if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED && !startNotified) {
                            startNotified = true
                            connected?.complete(Unit)
                        }
                    }
                }
            receiver = scoReceiver
            ContextCompat.registerReceiver(
                context,
                scoReceiver,
                IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            val callback =
                object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                        devices.addAll(_filterSources(addedDevices.asList()))
                    }

                    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                        devices.removeAll(_filterSources(removedDevices.asList()).toSet())
                        val hasBluetoothSco = devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                        if (!hasBluetoothSco && audioManager.isBluetoothScoAvailableOffCall) {
                            _releaseRoute()
                        }
                    }
                }
            deviceCallback = callback
            audioManager.registerAudioDeviceCallback(callback, null)
            devices.addAll(_filterSources(audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).asList()))

            val hasBluetoothSco = devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            if (!hasBluetoothSco || !audioManager.isBluetoothScoAvailableOffCall) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startNotified = true
                val current = audioManager.communicationDevice
                if (current?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return
                for (device in audioManager.availableCommunicationDevices) {
                    if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                        if (audioManager.setCommunicationDevice(device)) {
                            routed = true
                            routedDevice = device
                            previousDevice = current
                        }
                        return
                    }
                }
                return
            }
            @Suppress("DEPRECATION")
            if (!audioManager.isBluetoothScoOn) {
                audioManager.startBluetoothSco()
                routed = true
            }
            // The SCO link comes up asynchronously (the sticky broadcast also
            // reports one that is already connected). The plugin waited for it
            // indefinitely; give up after a few seconds and record anyway.
            try {
                timeout(scoConnectTimeout) { waiter.await() }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }

        fun stop() {
            _releaseRoute()
            deviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
            deviceCallback = null
            connected = null
            startNotified = false
            receiver?.let {
                try {
                    context.unregisterReceiver(it)
                } catch (_: IllegalArgumentException) {
                }
            }
            receiver = null
        }

        /**
         * Puts back the route this recorder changed. A voice call that started
         * meanwhile owns the route, and so does anyone who changed the
         * communication device after us.
         */
        private fun _releaseRoute() {
            if (!routed) return
            routed = false
            val ours = routedDevice
            val previous = previousDevice
            routedDevice = null
            previousDevice = null
            if (isInVoiceCall(context)) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (audioManager.communicationDevice?.id != ours?.id) return
                val restorable =
                    previous?.let { p -> audioManager.availableCommunicationDevices.firstOrNull { it.id == p.id } }
                if (restorable != null) {
                    audioManager.setCommunicationDevice(restorable)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                if (audioManager.isBluetoothScoOn) audioManager.stopBluetoothSco()
            }
        }
    }

    companion object {
        private const val tag = "AudioRecorder"
        private const val defaultAmplitude = -160.0
        private val scoConnectTimeout = 5.seconds

        private fun _onFailure(error: Exception) {
            Log.e(tag, error.message, error)
        }

        private fun _deleteFile(path: String?) {
            if (path == null) return
            try {
                val file = File(path)
                if (file.exists()) file.delete()
            } catch (_: SecurityException) {
            }
        }

        private fun _filterSources(devices: List<AudioDeviceInfo>): List<AudioDeviceInfo> =
            devices.filter {
                it.isSource &&
                    it.type != 18 && // TYPE_TELEPHONY
                    it.type != 25 && // TYPE_REMOTE_SUBMIX
                    it.type != 28 // TYPE_ECHO_REFERENCE
            }

        /** The encoder format record_android builds for an AAC [config]. */
        internal fun aacMediaFormat(config: RecordConfig): MediaFormat =
            MediaFormat().apply {
                setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC)
                setInteger(MediaFormat.KEY_SAMPLE_RATE, config.sampleRate)
                setInteger(MediaFormat.KEY_CHANNEL_COUNT, config.numChannels)
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitRate)
                when (config.encoder) {
                    AudioEncoder.aacLc ->
                        setInteger(
                            MediaFormat.KEY_AAC_PROFILE,
                            MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                        )
                }
            }

        /** The first AAC encoder accepting [config], after the plugin's adjustments. */
        internal fun _selectAacFormat(config: RecordConfig): _AacFormat {
            val mediaFormat = aacMediaFormat(config)
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecs.codecInfos) {
                if (!info.isEncoder) continue
                try {
                    val caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC)
                    if (caps != null && _adjustToCapabilities(caps, config, mediaFormat)) {
                        return _AacFormat(info.name, mediaFormat)
                    }
                } catch (_: IllegalArgumentException) {
                    // Type not supported by this codec.
                }
            }
            throw Exception("No codec found for given config $mediaFormat. You should try with other values.")
        }

        private fun _adjustToCapabilities(
            caps: MediaCodecInfo.CodecCapabilities,
            config: RecordConfig,
            mediaFormat: MediaFormat,
        ): Boolean {
            _adjustToDeviceCapabilities(config, mediaFormat)
            if (caps.isFormatSupported(mediaFormat)) return true
            val audioCapabilities = caps.audioCapabilities
            if (audioCapabilities != null) {
                mediaFormat.setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    _checkBounds(audioCapabilities.bitrateRange, config.bitRate),
                )
                val sampleRates = audioCapabilities.supportedSampleRates
                if (sampleRates != null) {
                    mediaFormat.setInteger(
                        MediaFormat.KEY_SAMPLE_RATE,
                        _nearestValue(sampleRates, mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)),
                    )
                }
                mediaFormat.setInteger(
                    MediaFormat.KEY_CHANNEL_COUNT,
                    _checkBounds(
                        Range(1, audioCapabilities.maxInputChannelCount),
                        mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    ),
                )
            }
            return caps.isFormatSupported(mediaFormat)
        }

        private fun _adjustToDeviceCapabilities(config: RecordConfig, mediaFormat: MediaFormat) {
            val device = _defaultInputDevice() ?: return
            // The PCM reader only supports mono or stereo.
            val channelCounts = device.channelCounts.filter { it <= 2 }.toIntArray()
            if (channelCounts.isNotEmpty()) {
                mediaFormat.setInteger(
                    MediaFormat.KEY_CHANNEL_COUNT,
                    _nearestValue(channelCounts, config.numChannels),
                )
            }
        }

        @SuppressLint("MissingPermission")
        private fun _defaultInputDevice(): AudioDeviceInfo? {
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val sampleRate = 8000
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (bufferSize <= 0) return null
            val probe =
                try {
                    AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, channelConfig, audioFormat, bufferSize)
                } catch (_: Exception) {
                    return null
                }
            if (probe.state != AudioRecord.STATE_INITIALIZED) {
                probe.release()
                return null
            }
            return try {
                probe.startRecording()
                probe.routedDevice
            } catch (_: Exception) {
                null
            } finally {
                try {
                    probe.stop()
                } catch (_: Exception) {
                }
                probe.release()
            }
        }

        private fun _checkBounds(range: Range<Int>, value: Int): Int {
            if (range.lower > value) return range.lower
            if (range.upper < value) return range.upper
            return value
        }

        private fun _nearestValue(values: IntArray, value: Int): Int {
            var distance = abs(values[0] - value)
            var index = 0
            for (candidate in 1 until values.size) {
                val candidateDistance = abs(values[candidate] - value)
                if (candidateDistance < distance) {
                    index = candidate
                    distance = candidateDistance
                }
            }
            return values[index]
        }
    }
}

/** An AAC encoder name and the (possibly adjusted) format it accepted. */
internal class _AacFormat(val codecName: String, val mediaFormat: MediaFormat)

/** record_android's PCMReader: 16-bit PCM from the default source. */
@SuppressLint("MissingPermission")
private class _PcmReader(format: _AacFormat) {
    private val sampleRate = format.mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    private val channelConfig =
        if (format.mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }
    private val bufferSize: Int = _calculateBufferSize()
    private val readBuffer = ShortArray(bufferSize / 2)
    private val reader: AudioRecord = _createReader()
    private val effects = _AudioEffects(reader.audioSessionId)

    @Volatile var amplitudeDb: Double = -160.0
        private set

    fun start() {
        reader.startRecording()
    }

    fun read(): ByteArray {
        val read = reader.read(readBuffer, 0, readBuffer.size)
        if (read < 0) throw Exception(_readFailureReason(read))
        if (read > 0) amplitudeDb = _amplitudeDb(read)
        val bytes = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until read) bytes.putShort(readBuffer[i])
        return bytes.array()
    }

    fun release() {
        if (reader.recordingState == AudioRecord.RECORDSTATE_RECORDING) reader.stop()
        effects.release()
        reader.release()
    }

    private fun _createReader(): AudioRecord {
        val created =
            try {
                AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            } catch (error: IllegalArgumentException) {
                throw Exception("Unable to instantiate PCM reader.", error)
            }
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            throw Exception("PCM reader failed to initialize.")
        }
        return created
    }

    private fun _calculateBufferSize(): Int {
        val minBufferSize =
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            throw Exception("Recording config is not supported by the hardware, or an invalid config was provided.")
        }
        // Double the minimum buffer size for safety margin.
        return minBufferSize * 2
    }

    private fun _amplitudeDb(size: Int): Double {
        var max = 0
        for (i in 0 until size) max = maxOf(max, abs(readBuffer[i].toInt()))
        if (max == 0) return -160.0
        return 0.0.coerceAtMost(20 * log10(max / 32767.0))
    }

    private fun _readFailureReason(errorCode: Int): String {
        val message =
            when (errorCode) {
                AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION: Failure due to improper method use"
                AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE: Invalid value used"
                AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT: Object no longer valid, needs recreation"
                AudioRecord.ERROR -> "ERROR: Generic operation failure"
                else -> "Unknown error code: $errorCode"
            }
        return "Error when reading audio data: $message"
    }
}

/** record_android's AudioEffectsManager: the effects exist but stay disabled. */
private class _AudioEffects(audioSessionId: Int) {
    private val agc: AutomaticGainControl? =
        if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(audioSessionId) else null
    private val aec: AcousticEchoCanceler? =
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(audioSessionId) else null
    private val ns: NoiseSuppressor? =
        if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(audioSessionId) else null

    init {
        agc?.enabled = false
        aec?.enabled = false
        ns?.enabled = false
    }

    fun release() {
        agc?.release()
        aec?.release()
        ns?.release()
    }
}

/**
 * record_android's MediaCodecEncoder + MuxerContainer, driven synchronously
 * from the recording thread: AAC frames go into an MPEG-4 container that is
 * created once the encoder reports its output format and codec-specific data.
 */
private class _AacMp4Encoder(format: _AacFormat, private val path: String) {
    private val mediaFormat = format.mediaFormat
    private val codec: MediaCodec = MediaCodec.createByCodecName(format.codecName)
    private val info = MediaCodec.BufferInfo()

    // Bytes of 16-bit PCM per microsecond, in the plugin's float arithmetic.
    private val rate: Float =
        (16 / 8.0f) *
            mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) *
            mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) *
            1e-6f
    private var inputPosition = 0L
    private var stopped = false
    private var released = false
    private var muxer: MediaMuxer? = null
    private var muxerStarted = false
    private var track = -1
    private var containerSetup = false
    private var pendingFormat: MediaFormat? = null
    private var pendingCsd: ByteArray? = null
    private var csdReceived = false

    fun start() {
        try {
            codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (error: Exception) {
            codec.release()
            released = true
            throw error
        }
    }

    fun encode(bytes: ByteArray) {
        if (stopped) return
        try {
            var offset = 0
            var waitingSince = System.nanoTime()
            while (offset < bytes.size) {
                val index = codec.dequeueInputBuffer(dequeueTimeoutUs)
                if (index < 0) {
                    _drain(untilEndOfStream = false)
                    if (System.nanoTime() - waitingSince > stallTimeoutNs) {
                        throw IllegalStateException("AAC encoder stopped accepting input")
                    }
                    continue
                }
                val buffer = codec.getInputBuffer(index)!!
                val size = min(buffer.capacity(), bytes.size - offset)
                buffer.put(bytes, offset, size)
                codec.queueInputBuffer(index, 0, size, _presentationTimeUs(inputPosition), 0)
                inputPosition += size
                offset += size
                waitingSince = System.nanoTime()
            }
            _drain(untilEndOfStream = false)
        } catch (error: Exception) {
            // Like the plugin: encoding stops, whatever was written is kept,
            // and the recording thread carries on until stopped.
            stopped = true
            Log.e("AudioRecorder", error.message, error)
            _release()
        }
    }

    /** Sends end-of-stream, writes the remaining frames and finalizes the file. */
    fun stop() {
        if (released) return
        try {
            if (!stopped) {
                stopped = true
                val waitingSince = System.nanoTime()
                while (true) {
                    val index = codec.dequeueInputBuffer(dequeueTimeoutUs)
                    if (index >= 0) {
                        codec.queueInputBuffer(
                            index,
                            0,
                            0,
                            _presentationTimeUs(inputPosition),
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        break
                    }
                    _drain(untilEndOfStream = false)
                    if (System.nanoTime() - waitingSince > stallTimeoutNs) {
                        throw IllegalStateException("AAC encoder did not accept end of stream")
                    }
                }
                _drain(untilEndOfStream = true)
            }
        } finally {
            _release()
        }
    }

    private fun _presentationTimeUs(position: Long): Long = (position / rate).toLong()

    private fun _drain(untilEndOfStream: Boolean) {
        val waitingSince = System.nanoTime()
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (untilEndOfStream) dequeueTimeoutUs else 0L)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!untilEndOfStream) return
                    if (System.nanoTime() - waitingSince > stallTimeoutNs) {
                        throw IllegalStateException("AAC encoder did not finish")
                    }
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!containerSetup) {
                        pendingFormat = codec.outputFormat
                        _trySetupContainer()
                    }
                }
                index >= 0 -> {
                    val endOfStream = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    _processOutputBuffer(index)
                    if (endOfStream) return
                }
            }
        }
    }

    private fun _processOutputBuffer(index: Int) {
        if (info.size != 0) {
            val isCsd = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
            val out = codec.getOutputBuffer(index)
            if (isCsd) {
                pendingCsd =
                    out?.let {
                        it.position(info.offset)
                        it.limit(info.offset + info.size)
                        ByteArray(info.size).also { csd -> it.get(csd) }
                    }
                csdReceived = true
                _trySetupContainer()
            } else if (out != null && containerSetup) {
                muxer?.writeSampleData(track, out, info)
            }
        }
        codec.releaseOutputBuffer(index, false)
    }

    private fun _trySetupContainer() {
        if (containerSetup) return
        val format = pendingFormat ?: return
        if (!format.containsKey("csd-0") && !csdReceived) return
        containerSetup = true
        val created = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = created
        if (!format.containsKey("csd-0")) {
            pendingCsd?.let { format.setByteBuffer("csd-0", ByteBuffer.wrap(it)) }
        }
        track = created.addTrack(format)
        created.start()
        muxerStarted = true
    }

    private fun _release() {
        if (released) return
        released = true
        try {
            try {
                codec.stop()
            } finally {
                codec.release()
            }
        } finally {
            val current = muxer
            muxer = null
            if (current != null) {
                try {
                    if (muxerStarted) current.stop()
                } finally {
                    muxerStarted = false
                    current.release()
                }
            }
        }
    }

    companion object {
        private const val dequeueTimeoutUs = 10_000L
        private const val stallTimeoutNs = 2_000_000_000L
    }
}
