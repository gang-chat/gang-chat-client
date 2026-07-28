import 'dart:async';

import '../live/live_session.dart';
import '../live/screen_audio_publisher.dart';
import '../live/audio_device_restorer.dart';
import '../live/livekit_url.dart';
import '../live/system_audio_devices.dart';
import '../protocol/models.dart';
import 'audio_levels.dart';
import 'audio_device_store.dart';

typedef AudioDeviceRestorer = Future<String?> Function(AudioDeviceStore store);

class LiveSessionController {
  LiveSessionController({
    required this.apiBaseUrl,
    required this.audioDeviceStore,
    LiveSession? session,
    AudioDeviceRestorer? audioDeviceRestorer,
    ScreenAudioTokenProvider? screenAudioTokenProvider,
  }) : session =
           session ??
           LiveSession(
             audioDeviceStore: audioDeviceStore,
             screenAudioTokenProvider: screenAudioTokenProvider,
           ),
       _audioDeviceRestorer =
           audioDeviceRestorer ??
           ((store) async {
             // On desktop the OS-selected mic is known via the native channel;
             // pass it so a room join follows the system default when the user
             // has not pinned a device. Unsupported platforms return null.
             final systemAudio = SystemAudioDevices();
             String? systemDefaultInputId;
             String? systemDefaultOutputId;
             try {
               final ids = await Future.wait<String?>([
                 systemAudio.currentInputDeviceId(),
                 systemAudio.currentOutputDeviceId(),
               ]);
               systemDefaultInputId = ids[0];
               systemDefaultOutputId = ids[1];
             } finally {
               await systemAudio.dispose();
             }
             final restored = await restoreStoredAudioDevices(
               store,
               systemDefaultInputId: systemDefaultInputId,
               systemDefaultOutputId: systemDefaultOutputId,
             );
             // The device the published mic should capture from: the resolved
             // input (stored preference or system default), else null to leave
             // LiveKit on the ADM's current device.
             return restored.input?.deviceId ?? systemDefaultInputId;
           });

  final String apiBaseUrl;
  final AudioDeviceStore audioDeviceStore;
  final LiveSession session;
  final AudioDeviceRestorer _audioDeviceRestorer;
  final Set<String> _restoredParticipantVoiceVolumeIds = <String>{};
  final Set<String> _restoringParticipantVoiceVolumeIds = <String>{};
  final Map<String, double> _participantVoiceVolumeBeforeMute =
      <String, double>{};

  Future<List<ScreenSource>> listScreenSources() {
    return LiveSession.listScreenSources();
  }

  Future<void> refreshScreenSourceThumbnails() {
    return LiveSession.refreshScreenSourceThumbnails();
  }

  bool get isScreenSharing => session.isScreenSharing;
  bool get canFlipCamera => session.canFlipCamera;
  bool get isConnected => session.isConnected;
  bool get localMicMuted => session.localMicMuted;
  String? get roomName => session.roomName;
  bool isAttachedToRoom(String roomName) => session.isAttachedToRoom(roomName);
  Set<String> get speakingIdentities => session.speakingIdentities;
  Set<String> get connectedParticipantIdentities =>
      session.connectedParticipantIdentities;
  Map<String, bool> get micMutedByIdentity => session.micMutedByIdentity;
  List<LiveVideoTrack> get videoTracks => session.videoTracks;

  LiveVideoTrack? cameraFor(String userId) => session.cameraFor(userId);

  void attachSessionCallbacks({
    required void Function() onChanged,
    required void Function() onForciblyRemoved,
    required void Function() onUnexpectedlyDisconnected,
    required void Function(bool canPublish) onPublishPermissionChanged,
    required void Function(String participantIdentity) onParticipantJoined,
    required void Function(
      String participantIdentity,
      LiveParticipantDepartureKind departureKind,
    )
    onParticipantLeft,
  }) {
    session.addListener(onChanged);
    session.onForciblyRemoved = onForciblyRemoved;
    session.onUnexpectedlyDisconnected = onUnexpectedlyDisconnected;
    session.onPublishPermissionChanged = onPublishPermissionChanged;
    session.onParticipantJoined = onParticipantJoined;
    session.onParticipantLeft = onParticipantLeft;
  }

  void detachSessionCallbacks({required void Function() onChanged}) {
    session.removeListener(onChanged);
    session.onForciblyRemoved = null;
    session.onUnexpectedlyDisconnected = null;
    session.onPublishPermissionChanged = null;
    session.onParticipantJoined = null;
    session.onParticipantLeft = null;
  }

  Future<void> setMicMuted(bool muted) => session.setMicMuted(muted);

  Future<void> setCameraEnabled(bool enabled) {
    return session.setCameraEnabled(enabled);
  }

  Future<bool> flipCamera() => session.flipCamera();

  Future<void> setScreenShareEnabled(bool enabled, {String? sourceId}) {
    return session.setScreenShareEnabled(enabled, sourceId: sourceId);
  }

  double get inputVolume => session.inputVolume;

  Future<void> setInputVolume(double volume) async {
    await session.setInputVolume(volume);
    try {
      await audioDeviceStore.writeInputVolume(session.inputVolume);
    } catch (_) {
      // A failed persist shouldn't undo the live change.
    }
  }

  /// Pin the microphone capture device. Keeps [LiveSession]'s tracked input id
  /// in sync so a later mute/unmute republish stays on the chosen device. The
  /// native ADM is routed separately by the Settings picker's selectAudioInput;
  /// this only keeps the LiveKit republish path consistent. Persisted so a
  /// future room join restores the same device.
  Future<void> setInputDeviceId(String? deviceId) async {
    await session.setInputDeviceId(deviceId);
    try {
      if (deviceId != null) {
        await audioDeviceStore.writeInputDeviceId(deviceId);
      }
    } catch (_) {
      // A failed persist shouldn't undo the live change.
    }
  }

  double get outputVolume => session.outputVolume;

  double participantVoiceVolume(String userId) {
    return session.participantVoiceVolume(userId);
  }

  Future<bool> restoreParticipantVoiceVolume(String userId) async {
    if (userId.isEmpty ||
        _restoredParticipantVoiceVolumeIds.contains(userId) ||
        _restoringParticipantVoiceVolumeIds.contains(userId)) {
      return false;
    }
    _restoringParticipantVoiceVolumeIds.add(userId);
    final before = session.participantVoiceVolume(userId);
    try {
      final volume = await audioDeviceStore.readParticipantVoiceVolume(userId);
      if (_restoredParticipantVoiceVolumeIds.contains(userId)) return false;
      await session.setParticipantVoiceVolume(userId, volume);
      final restored = session.participantVoiceVolume(userId);
      if (restored > 0) _participantVoiceVolumeBeforeMute[userId] = restored;
      _restoredParticipantVoiceVolumeIds.add(userId);
      return restored != before;
    } catch (_) {
      _restoredParticipantVoiceVolumeIds.add(userId);
      return false;
    } finally {
      _restoringParticipantVoiceVolumeIds.remove(userId);
    }
  }

  Future<void> setOutputVolume(double volume) async {
    await session.setOutputVolume(volume);
    try {
      await audioDeviceStore.writeOutputVolume(session.outputVolume);
    } catch (_) {
      // A failed persist shouldn't undo the live change.
    }
  }

  Future<void> setParticipantVoiceVolume(String userId, double volume) async {
    await session.setParticipantVoiceVolume(
      userId,
      normalizedParticipantVoiceVolume(volume),
    );
    final normalized = session.participantVoiceVolume(userId);
    if (normalized > 0) _participantVoiceVolumeBeforeMute[userId] = normalized;
    _restoredParticipantVoiceVolumeIds.add(userId);
    try {
      await audioDeviceStore.writeParticipantVoiceVolume(userId, normalized);
    } catch (_) {
      // A failed persist shouldn't undo the live change; it just won't survive
      // the next launch.
    }
  }

  Future<void> toggleParticipantVoiceMuted(String userId) {
    final current = session.participantVoiceVolume(userId);
    if (current <= 0) {
      return setParticipantVoiceVolume(
        userId,
        _participantVoiceVolumeBeforeMute[userId] ??
            defaultParticipantVoiceVolume,
      );
    }
    _participantVoiceVolumeBeforeMute[userId] = current;
    return setParticipantVoiceVolume(userId, 0);
  }

  double get musicBoxVolume => session.musicBoxVolume;

  Future<void> setMusicBoxVolume(double volume) async {
    await session.setMusicBoxVolume(volume);
    try {
      await audioDeviceStore.writeMusicBoxVolume(volume);
    } catch (_) {
      // A failed persist shouldn't undo the live volume change; it just won't
      // survive the next launch.
    }
  }

  double get screenShareVolume => session.screenShareVolume;

  String? get watchedScreenShareIdentity => session.watchedScreenShareIdentity;

  String? get watchedCameraIdentity => session.watchedCameraIdentity;

  Future<void> setScreenShareVolume(double volume) async {
    await session.setScreenShareVolume(volume);
    try {
      await audioDeviceStore.writeScreenShareVolume(session.screenShareVolume);
    } catch (_) {
      // A failed persist shouldn't undo the live change.
    }
  }

  Future<void> setWatchedScreenShareIdentity(String? identity) {
    return session.setWatchedScreenShareIdentity(identity);
  }

  Future<void> setWatchedCameraIdentity(String? identity) {
    return session.setWatchedCameraIdentity(identity);
  }

  int get screenShareMaxHeight => session.screenShareMaxHeight;

  /// Apply and persist the screen-share resolution cap. Takes effect on the
  /// next share, and re-scales the current share live when one is running.
  Future<void> setScreenShareMaxHeight(int height) async {
    await session.setScreenShareMaxHeight(height);
    try {
      await audioDeviceStore.writeScreenShareMaxHeight(height);
    } catch (_) {
      // A failed persist shouldn't undo the live change; it just won't survive
      // the next launch.
    }
  }

  Future<void> setOutputMuted(bool muted) => session.setOutputMuted(muted);

  Future<void> connectWithRetry(
    LiveJoinResult result, {
    bool Function()? isCancelled,
  }) async {
    Object? lastError;
    for (var attempt = 0; attempt < 2; attempt += 1) {
      if (attempt > 0) {
        await Future<void>.delayed(const Duration(milliseconds: 650));
      }
      if (isCancelled?.call() ?? false) throw '已取消加入直播';
      try {
        final liveKitUrl = resolveLiveKitServerUrl(
          serverUrl: result.liveKit.serverUrl,
          apiBaseUrl: apiBaseUrl,
        );
        await restoreStoredAudioSettings();
        await session.connect(
          url: liveKitUrl,
          token: result.liveKit.token,
          roomName: result.liveKit.roomName,
          micMuted: result.participant.micMuted,
        );
        return;
      } catch (e) {
        lastError = e;
        await disconnect();
      }
    }
    throw lastError ?? 'LiveKit 连接失败';
  }

  Future<void> restoreStoredAudioSettings() async {
    try {
      final stored = await audioDeviceStore.read();
      await session.setInputVolume(stored.inputVolume);
      await session.setOutputVolume(stored.outputVolume);
      await session.setMusicBoxVolume(stored.musicBoxVolume);
      await session.setScreenShareVolume(stored.screenShareVolume);
      await session.setScreenShareMaxHeight(stored.screenShareMaxHeight);
      // Capture the published mic from the device the restorer resolved (the
      // user's pinned device, or the macOS system default). Null leaves LiveKit
      // on the ADM's current device. Without this the publish path ignores the
      // picker entirely, since defaultAudioCaptureOptions has no deviceId.
      final inputDeviceId = await _audioDeviceRestorer(audioDeviceStore);
      await session.setInputDeviceId(inputDeviceId);
    } catch (_) {
      // Joining voice should still work with LiveKit's current/default device
      // if a stored local preference cannot be applied.
    }
  }

  Future<void> disconnect({Duration? timeout}) async {
    final disconnect = session.disconnect().catchError((_) {});
    if (timeout == null) {
      await disconnect;
      return;
    }
    Timer? timeoutTimer;
    final timeoutCompleter = Completer<void>();
    timeoutTimer = Timer(timeout, () {
      if (!timeoutCompleter.isCompleted) timeoutCompleter.complete();
    });
    try {
      await Future.any([disconnect, timeoutCompleter.future]);
    } catch (_) {}
    timeoutTimer.cancel();
  }

  void dispose() => session.dispose();
}
