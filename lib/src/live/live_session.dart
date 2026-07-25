import 'dart:async';
import 'dart:io' show HttpOverrides, Platform;

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart' as rtc;
import 'package:livekit_client/livekit_client.dart' as lk;

import '../app/audio_device_info.dart';
import '../app/audio_device_store.dart';
import '../app/audio_levels.dart';
import '../protocol/models.dart' show musicBoxBotIdentity;
import 'audio_device_service.dart';
import 'audio_input_rebinder.dart';
import 'audio_output_rebinder.dart';
import 'screen_share_quality.dart';
import 'screen_audio_publisher.dart';
import 'system_audio_devices.dart';

/// Suffix of the hidden screen-audio aux participant identity
/// (`<ownerId>--screen-audio`). These participants publish screen-share audio
/// through an isolated factory and must be filtered out of the UI (they are
/// never real users). On the owner's own receiver, their audio must also be
/// muted to avoid self-echo. The `--` separator is URL-safe (unlike `#`,
/// which is truncated as a URL fragment in the LiveKit WebSocket signal path,
/// causing the aux identity to collide with the main room participant).
const _screenAudioAuxSuffix = '--screen-audio';

/// Whether [identity] is a hidden screen-audio aux participant.
bool _isScreenAudioAux(String identity) =>
    identity.endsWith(_screenAudioAuxSuffix);

/// Whether [identity] is the LOCAL user's own screen-audio aux participant
/// (the one whose audio would echo back if played). [localIdentity] is the
/// main room's local participant identity (== userID).
bool _isOwnScreenAudioAux(String identity, String? localIdentity) {
  if (localIdentity == null || localIdentity.isEmpty) return false;
  return identity == '$localIdentity$_screenAudioAuxSuffix';
}

bool isLivePresenceSoundParticipantIdentity(String identity) {
  return identity.isNotEmpty &&
      !_isScreenAudioAux(identity) &&
      identity != musicBoxBotIdentity;
}

enum LiveParticipantDepartureKind { left, removed }

LiveParticipantDepartureKind liveParticipantDepartureKind(
  lk.DisconnectReason? reason,
) {
  return reason == lk.DisconnectReason.participantRemoved
      ? LiveParticipantDepartureKind.removed
      : LiveParticipantDepartureKind.left;
}

/// Serializes remote-video subscription reconciliation and makes newer
/// selections supersede work that has not started yet.
///
/// LiveKit's subscribe/unsubscribe methods only enqueue signal messages; they
/// do not wait for the server to confirm the resulting state. Keeping each
/// media kind on a single ordered tail ensures that a stale unsubscribe cannot
/// be sent after the user's newer selection.
@visibleForTesting
class LatestLiveSubscriptionReconciler {
  Future<void> _tail = Future<void>.value();
  int _revision = 0;

  Future<void> schedule(
    Future<void> Function(bool Function() isCurrent) reconcile,
  ) {
    final revision = ++_revision;
    final previous = _tail;
    final next = () async {
      try {
        await previous;
      } catch (_) {}
      if (revision != _revision) return;
      await reconcile(() => revision == _revision);
    }();
    _tail = next.catchError((_) {});
    return next;
  }

  void invalidate() {
    _revision += 1;
  }
}

@visibleForTesting
bool shouldApplyLivePublishPermissionUpdate({
  required bool currentCanPublish,
  required bool oldCanPublish,
  required bool nextCanPublish,
}) {
  if (oldCanPublish == nextCanPublish) return false;
  return currentCanPublish != nextCanPublish;
}

String _screenShareAudioOwnerIdentity(String identity) {
  if (!_isScreenAudioAux(identity)) return identity;
  return identity.substring(0, identity.length - _screenAudioAuxSuffix.length);
}

/// A capturable desktop source (a whole screen or a single window), used to
/// populate the screen-share picker. Wraps flutter_webrtc's source so the UI
/// doesn't depend on the WebRTC types directly.
class ScreenSource {
  const ScreenSource({
    required this.id,
    required this.name,
    required this.thumbnail,
    required this.isWindow,
    String? thumbnailKey,
    this.thumbnailUpdates,
  }) : thumbnailKey = thumbnailKey ?? id;

  final String id;
  final String name;
  final Uint8List? thumbnail;
  final bool isWindow;
  final String thumbnailKey;
  final Stream<Uint8List>? thumbnailUpdates;
}

class _ScreenSourceThumbnailUpdate {
  const _ScreenSourceThumbnailUpdate({
    required this.sourceId,
    required this.thumbnail,
  });

  final String sourceId;
  final Uint8List thumbnail;
}

final _screenSourceWhitespacePattern = RegExp(r'\s+');
final _screenSourceThumbnailCache = <String, Uint8List>{};
final _screenSourceDirectThumbnailKeys = <String>{};
final _screenSourceThumbnailUpdateController =
    StreamController<_ScreenSourceThumbnailUpdate>.broadcast();
StreamSubscription<rtc.DesktopCapturerSource>?
_screenSourceThumbnailSubscription;

const _ignoredShareWindowNameParts = <String>[
  'nvidia geforce overlay',
  'nvidia overlay',
  'geforce overlay',
];

bool get supportsDesktopScreenShare =>
    !kIsWeb && (Platform.isWindows || Platform.isMacOS || Platform.isLinux);

@visibleForTesting
bool shouldRequestScreenShareAudio({
  required String? sourceId,
  required bool isDesktopSourcePickerPlatform,
  required bool isWindowsDesktop,
}) {
  // Screen audio is published by ScreenAudioPublisher through a separate
  // native factory/aux participant. getDisplayMedia still receives audio=true
  // so platform capture can be configured, but the returned stream remains
  // video-only and is never published on the main factory.
  return true;
}

// libwebrtc's RTCDesktopSource thumbnails shear on Retina screens (same stride
// bug the live share sidesteps via ScreenCaptureKit). For screen sources we grab
// the thumbnail natively (CGDisplayCreateImage) instead, which is undistorted.
const _screenThumbnailChannel = MethodChannel('gang_chat/screen_thumbnail');

Future<Uint8List?> _captureNativeScreenThumbnail(String displayId) async {
  if (kIsWeb || !Platform.isMacOS) return null;
  try {
    final bytes = await _screenThumbnailChannel.invokeMethod<Uint8List>(
      'captureScreenThumbnail',
      {'displayId': displayId, 'maxWidth': 320},
    );
    if (bytes != null && bytes.isNotEmpty) return bytes;
  } catch (_) {}
  return null;
}

@visibleForTesting
List<ScreenSource> filterScreenSourcesForPicker(
  Iterable<ScreenSource> sources,
) {
  final visible = <ScreenSource>[];
  final seenIds = <String>{};

  for (final source in sources) {
    final normalizedName = _normalizeScreenSourceName(source.name);
    if (source.isWindow) {
      if (_isIgnoredShareWindowName(normalizedName)) continue;
    }
    if (seenIds.add(source.thumbnailKey)) visible.add(source);
  }

  return visible;
}

String _normalizeScreenSourceName(String name) {
  return name.trim().toLowerCase().replaceAll(
    _screenSourceWhitespacePattern,
    ' ',
  );
}

bool _isIgnoredShareWindowName(String normalizedName) {
  return _ignoredShareWindowNameParts.any(normalizedName.contains);
}

String _thumbnailKeyForDesktopSource(rtc.DesktopCapturerSource source) {
  final type = source.type == rtc.SourceType.Screen ? 'screen' : 'window';
  return '$type:${source.id}';
}

Uint8List? _rememberScreenSourceThumbnail({
  required String sourceId,
  required Uint8List? thumbnail,
}) {
  if (thumbnail != null && thumbnail.isNotEmpty) {
    _screenSourceThumbnailCache[sourceId] = thumbnail;
    return thumbnail;
  }
  return _screenSourceThumbnailCache[sourceId];
}

void _rememberAndEmitScreenSourceThumbnail({
  required String sourceId,
  required Uint8List? thumbnail,
}) {
  final remembered = _rememberScreenSourceThumbnail(
    sourceId: sourceId,
    thumbnail: thumbnail,
  );
  if (remembered == null || remembered.isEmpty) return;
  _screenSourceThumbnailUpdateController.add(
    _ScreenSourceThumbnailUpdate(sourceId: sourceId, thumbnail: remembered),
  );
}

Stream<Uint8List> _cacheScreenSourceThumbnailUpdates(
  String sourceId,
  Stream<Uint8List> updates,
) {
  return updates.map((thumbnail) {
    _rememberAndEmitScreenSourceThumbnail(
      sourceId: sourceId,
      thumbnail: thumbnail,
    );
    return thumbnail;
  });
}

Stream<Uint8List> _screenSourceThumbnailUpdates(String sourceId) {
  return _screenSourceThumbnailUpdateController.stream
      .where((update) => update.sourceId == sourceId)
      .map((update) => update.thumbnail);
}

void _ensureScreenSourceThumbnailCacheSubscription() {
  _screenSourceThumbnailSubscription ??= rtc
      .desktopCapturer
      .onThumbnailChanged
      .stream
      .listen((source) {
        final thumbnailKey = _thumbnailKeyForDesktopSource(source);
        if (source.type == rtc.SourceType.Screen &&
            _screenSourceDirectThumbnailKeys.contains(thumbnailKey)) {
          return;
        }
        _rememberAndEmitScreenSourceThumbnail(
          sourceId: thumbnailKey,
          thumbnail: source.thumbnail,
        );
      });
}

Future<Uint8List?> _loadInitialScreenSourceThumbnail(
  rtc.DesktopCapturerSource source,
  String thumbnailKey,
) async {
  final isScreen = source.type == rtc.SourceType.Screen;
  if (isScreen) {
    // macOS reports the screen source id as the CGDirectDisplayID, which the
    // native capture path also uses to select the display.
    final directThumbnail = await _captureNativeScreenThumbnail(source.id);
    if (directThumbnail != null && directThumbnail.isNotEmpty) {
      _screenSourceDirectThumbnailKeys.add(thumbnailKey);
      return _rememberScreenSourceThumbnail(
        sourceId: thumbnailKey,
        thumbnail: directThumbnail,
      );
    }
    _screenSourceDirectThumbnailKeys.remove(thumbnailKey);
  }
  return _rememberScreenSourceThumbnail(
    sourceId: thumbnailKey,
    thumbnail: source.thumbnail,
  );
}

@visibleForTesting
Uint8List? cachedScreenSourceThumbnailForTest(String sourceId) {
  return _screenSourceThumbnailCache[sourceId];
}

@visibleForTesting
void resetScreenSourceThumbnailCacheForTest() {
  _screenSourceThumbnailCache.clear();
  _screenSourceDirectThumbnailKeys.clear();
}

@visibleForTesting
Stream<Uint8List> cacheScreenSourceThumbnailUpdatesForTest(
  String sourceId,
  Stream<Uint8List> updates,
) {
  return _cacheScreenSourceThumbnailUpdates(sourceId, updates);
}

@visibleForTesting
Stream<Uint8List> screenSourceThumbnailUpdatesForTest(String sourceId) {
  return _screenSourceThumbnailUpdates(sourceId);
}

/// A single video track currently published in the live room, tagged with
/// whose it is and whether it's a camera or a screen share. The UI uses these
/// to render participant video tiles.
class LiveVideoTrack {
  const LiveVideoTrack({
    required this.identity,
    required this.track,
    required this.isScreenShare,
    required this.isLocal,
  });

  final String identity;
  final lk.VideoTrack track;
  final bool isScreenShare;
  final bool isLocal;
}

/// Wraps a [lk.Room] and exposes a small UI-friendly snapshot of who is
/// currently speaking, whose microphone is muted, and which video tracks are
/// live.
///
/// The server signs LiveKit tokens with `identity == user.id`, so a LiveKit
/// participant's [lk.Participant.identity] matches `UserSummary.id` from the
/// protocol layer. The UI uses that as the join key.
class LiveSession extends ChangeNotifier {
  /// [inputRebinderFactory] builds the recovery hook that restarts local mic
  /// capture when the OS removes/recreates audio input endpoints (for example a
  /// Bluetooth headset dying and reconnecting).
  ///
  /// [outputRebinderFactory] builds the recovery hook that re-routes WebRTC
  /// audio output after the OS rebuilds audio endpoints (for example a headset
  /// being unplugged/replugged, or a Bluetooth profile flip). It is started
  /// while connected and stopped on disconnect; null disables the recovery
  /// (tests, unsupported platforms).
  LiveSession({
    AudioInputRebinder? Function(LiveSession session)? inputRebinderFactory,
    AudioOutputRebinder? Function(LiveSession session)? outputRebinderFactory,
    AudioDeviceStore? audioDeviceStore,
    ScreenAudioTokenProvider? screenAudioTokenProvider,
  }) : _inputRebinderFactory =
           inputRebinderFactory ??
           ((session) => _defaultInputRebinderFactory(
             session,
             audioDeviceStore: audioDeviceStore,
           )),
       _outputRebinderFactory =
           outputRebinderFactory ??
           ((session) => _defaultOutputRebinderFactory(
             session,
             audioDeviceStore: audioDeviceStore,
           )),
       _screenAudioTokenProvider = screenAudioTokenProvider;

  final AudioInputRebinder? Function(LiveSession session) _inputRebinderFactory;
  final AudioOutputRebinder? Function(LiveSession session)
  _outputRebinderFactory;
  AudioInputRebinder? _inputRebinder;
  AudioOutputRebinder? _outputRebinder;
  final ScreenAudioTokenProvider? _screenAudioTokenProvider;
  ScreenAudioPublisher? _screenAudioPublisher;

  lk.Room? _room;
  lk.CancelListenFunc? _cancelEvents;
  String? _roomName;
  bool _connecting = false;
  bool _screenSharing = false;
  bool _localMicrophonePublishUnavailable = false;
  String? _resolvedLiveKitUrl;
  bool _canPublish = true;
  bool _presenceCallbacksEnabled = false;
  double _inputVolume = defaultAudioVolume;
  double _outputVolume = defaultAudioVolume;
  double _musicBoxVolume = defaultAudioVolume;
  double _screenShareVolume = defaultAudioVolume;
  String? _watchedScreenShareIdentity;
  String? _watchedCameraIdentity;
  final LatestLiveSubscriptionReconciler _screenShareSubscriptionReconciler =
      LatestLiveSubscriptionReconciler();
  final LatestLiveSubscriptionReconciler _cameraSubscriptionReconciler =
      LatestLiveSubscriptionReconciler();
  final Map<String, double> _participantVoiceVolumes = <String, double>{};
  bool _outputMuted = false;
  // Local mic mute (Option A): muting keeps the capture running and only zeroes
  // the outgoing audio, so it never tears down the CoreAudio input. Tearing it
  // down on macOS lets a Bluetooth headset renegotiate HFP<->A2DP and corrupts
  // the output sample rate. This is the source of truth for the local mute
  // state, since the LiveKit track is intentionally never mute()d.
  bool _micMuted = false;
  // The deviceId to capture the mic from, in WebRTC's macOS format
  // (stringified CoreAudio AudioDeviceID). Null means the ADM's current/default
  // device. Applied when the mic track is published; a change while connected
  // republishes the track on the new device.
  String? _inputDeviceId;

  // Target max height (px) for the local screen share — one of
  // [screenShareHeightOptions]. Defaults to native (1080 cap). Applied at the
  // next share; a change while sharing re-scales the live publication.
  int _screenShareMaxHeight = defaultScreenShareMaxHeight;

  final Set<String> _speakingIdentities = <String>{};
  final Map<String, bool> _micMutedByIdentity = <String, bool>{};

  /// Fired when the server force-disconnects the local participant from
  /// LiveKit (an admin `kick` -> `RemoveParticipant`). The UI uses this to drop
  /// its joined state and exit the voice panel without auto-reconnecting. Only
  /// raised for involuntary removals, not for our own [disconnect].
  void Function()? onForciblyRemoved;

  /// Fired when LiveKit reports the local participant's publish permission
  /// changed (an admin `mute_mic` -> canPublish=false, or `restore_voice` ->
  /// canPublish=true). [canPublish] is the new value. The UI reconciles the
  /// mic button: disabled while false, re-enabled (still muted) when restored.
  void Function(bool canPublish)? onPublishPermissionChanged;

  /// Fired for real remote users after the initial room population is done.
  /// Hidden screen-audio and music-box participants are excluded.
  void Function(String participantIdentity)? onParticipantJoined;

  /// Fired when a real remote user leaves while the room is fully connected.
  void Function(
    String participantIdentity,
    LiveParticipantDepartureKind departureKind,
  )?
  onParticipantLeft;

  bool get isConnected =>
      _room?.connectionState == lk.ConnectionState.connected;
  bool get isConnecting => _connecting;
  bool get isScreenSharing => _screenSharing;
  String? get roomName => _roomName;

  bool isAttachedToRoom(String roomName) {
    if (_roomName != roomName) return false;
    if (_connecting) return true;
    final state = _room?.connectionState;
    return state == lk.ConnectionState.connected ||
        state == lk.ConnectionState.connecting ||
        state == lk.ConnectionState.reconnecting;
  }

  /// Whether LiveKit currently grants the local participant publish rights.
  /// False once an admin `mute_mic` revokes it; LiveKit is the source of
  /// truth here, not any locally-tracked flag.
  bool get canPublish => _canPublish;
  double get inputVolume => _inputVolume;
  double get outputVolume => _outputVolume;

  /// Per-remote-user relative volume for ordinary voice audio. Defaults to
  /// 100%, then multiplies with [outputVolume]. Screen-share audio is excluded
  /// and continues to use [screenShareVolume].
  double participantVoiceVolume(String userId) {
    return _participantVoiceVolumes[userId] ?? defaultParticipantVoiceVolume;
  }

  /// Local listening volume applied only to the music box bot's audio track,
  /// independent of [outputVolume] (which covers every other remote speaker).
  double get musicBoxVolume => _musicBoxVolume;

  /// Local listening volume applied only to remote screen-share audio tracks,
  /// independent of [outputVolume] (which covers ordinary voice tracks).
  double get screenShareVolume => _screenShareVolume;

  /// The screen-share owner whose remote share is currently being watched.
  /// Screen-share audio is muted unless it belongs to this identity.
  String? get watchedScreenShareIdentity => _watchedScreenShareIdentity;

  /// The camera owner whose remote camera is currently being watched.
  String? get watchedCameraIdentity => _watchedCameraIdentity;

  bool get outputMuted => _outputMuted;

  /// Target max height (px) for the local screen share.
  int get screenShareMaxHeight => _screenShareMaxHeight;

  /// Whether the local microphone is effectively muted. With Option A muting
  /// keeps a successfully published LiveKit track live and silences outgoing
  /// audio through app-local gain. A missing/failed publication must still be
  /// reported as muted so the local UI and server snapshot cannot claim that
  /// other participants can hear a track which does not exist.
  bool get localMicMuted {
    final local = _room?.localParticipant;
    if (local == null ||
        !_canPublish ||
        _shouldMuteLocalMicrophone ||
        _localMicrophonePublishUnavailable) {
      return true;
    }
    final publication = local.getTrackPublicationBySource(
      lk.TrackSource.microphone,
    );
    return publication == null || publication.muted;
  }

  Set<String> get speakingIdentities => Set.unmodifiable(_speakingIdentities);
  Set<String> get connectedParticipantIdentities {
    final room = _room;
    if (room == null) return const <String>{};
    final identities = <String>{};
    final localIdentity = room.localParticipant?.identity;
    if (localIdentity != null && localIdentity.isNotEmpty) {
      identities.add(localIdentity);
    }
    for (final participant in room.remoteParticipants.values) {
      if (_isScreenAudioAux(participant.identity)) continue;
      identities.add(participant.identity);
    }
    return Set.unmodifiable(identities);
  }

  Map<String, bool> get micMutedByIdentity =>
      Map.unmodifiable(_micMutedByIdentity);

  bool isSpeaking(String userId) => _speakingIdentities.contains(userId);

  /// All live video tracks (local + remote, camera + screen share) that the UI
  /// can currently render. Rebuilt on demand from the room's participants.
  List<LiveVideoTrack> get videoTracks {
    final room = _room;
    if (room == null) return const [];
    final result = <LiveVideoTrack>[];

    void collect(lk.Participant participant, {required bool isLocal}) {
      for (final pub in participant.videoTrackPublications) {
        final track = pub.track;
        if (track == null) continue;
        // A muted publication still has a (now-stopped) track attached. livekit
        // mutes rather than unpublishes the camera when it's turned off, so
        // rendering it would paint a black rectangle instead of letting the UI
        // fall back to the avatar. Skip muted tracks entirely.
        if (pub.muted) continue;
        if (!isLocal && pub is lk.RemoteTrackPublication && !pub.subscribed) {
          continue;
        }
        if (track is! lk.VideoTrack) continue;
        result.add(
          LiveVideoTrack(
            identity: participant.identity,
            track: track,
            isScreenShare: pub.source == lk.TrackSource.screenShareVideo,
            isLocal: isLocal,
          ),
        );
      }
    }

    final local = room.localParticipant;
    if (local != null) collect(local, isLocal: true);
    for (final remote in room.remoteParticipants.values) {
      if (_isScreenAudioAux(remote.identity)) continue;
      collect(remote, isLocal: false);
    }
    return result;
  }

  /// The first screen-share track for [userId], if any. Returns null when that
  /// participant isn't sharing (or the track isn't subscribed yet).
  LiveVideoTrack? screenShareFor(String userId) {
    for (final t in videoTracks) {
      if (t.identity == userId && t.isScreenShare) return t;
    }
    return null;
  }

  /// The first camera track for [userId], if any.
  LiveVideoTrack? cameraFor(String userId) {
    for (final t in videoTracks) {
      if (t.identity == userId && !t.isScreenShare) return t;
    }
    return null;
  }

  /// Connect to a LiveKit room. If already in [roomName], aligns mic state
  /// and returns. Otherwise disconnects from the current room first.
  Future<void> connect({
    required String url,
    required String token,
    required String roomName,
    required bool micMuted,
  }) async {
    if (_room != null && _roomName == roomName && isConnected) {
      await setMicMuted(micMuted);
      return;
    }
    // A click on a remote share selects it before joining. Preserve that
    // desired target while replacing the previous LiveKit room so the new
    // room's initial auto-subscription is not immediately cancelled.
    await _disconnect(clearWatchedTargets: false);

    _connecting = true;
    notifyListeners();

    final room = lk.Room(
      roomOptions: lk.RoomOptions(
        // Adaptive stream sizes the subscribed quality to the rendering
        // widget's pixel size. When a screen share is viewed in the small
        // (non-fullscreen) stage, that requests a low-quality layer; combined
        // with dynacast (which pauses layers nobody subscribes to at full
        // quality on the sender), the publisher drops to its lowest temporal
        // layer and the share renders at a few fps — choppy until you go
        // fullscreen and the renderer grows enough to request full quality.
        // Keep subscriptions at full quality so the share is smooth at any
        // tile size; the camera-tile bandwidth saving isn't worth a degraded
        // screen share for a small group.
        adaptiveStream: false,
        dynacast: true,
        // Don't stop the OS audio capture session when the mic is muted.
        // The default (true) calls MediaStreamTrack.stop() on mute, which
        // tears down the shared input device and can cut audio for other apps
        // using the same mic (e.g. WeChat voice). Muting now only stops
        // sending audio; the device stays open (system mic indicator stays
        // lit) and unmute is instant.
        defaultAudioCaptureOptions: lk.AudioCaptureOptions(
          stopAudioCaptureOnMute: false,
          deviceId: _inputDeviceId,
        ),
        defaultVideoPublishOptions: lk.VideoPublishOptions(
          // The capture rate (ScreenShareCaptureOptions.maxFrameRate) only sets
          // how fast the grabber samples the screen. What viewers actually
          // receive is governed by the *publish* encoding, which — when left
          // null — falls back to LiveKit's screenShareH1080FPS15 preset, hard
          // capped at 15fps. Set it explicitly so the encoder isn't the
          // bottleneck. maxBitrate must scale with the frame rate or the
          // encoder starves frames to stay under budget. 1080p60 is given a
          // 16 Mbps ceiling so the encoder isn't bitrate-starved.
          screenShareEncoding: lk.VideoEncoding(
            maxFramerate: 60,
            maxBitrate: 16000 * 1000,
          ),
        ),
      ),
    );
    _room = room;
    _roomName = roomName;
    _resolvedLiveKitUrl = url;
    _screenSharing = false;
    _localMicrophonePublishUnavailable = false;
    _canPublish = true;
    _presenceCallbacksEnabled = false;
    _cancelEvents = room.events.listen(_onEvent);

    try {
      await HttpOverrides.runZoned(
        () => room.connect(url, token),
        findProxyFromEnvironment: (uri, environment) => 'DIRECT',
      );
      // Seed publish permission from the token LiveKit just validated: a
      // voice-banned user joins with canPublish=false, and we must not try to
      // publish the mic below (LiveKit would reject it).
      _canPublish = room.localParticipant?.permissions.canPublish ?? true;
      _micMuted = micMuted;
      // Publish the microphone track. The OS prompts for permission on the
      // first publish. Skip it when publishing is blocked, and when joining
      // muted: the one unavoidable capture start is deferred to the first
      // unmute, which lazily publishes. Once published, muting is app-local
      // gain 0 after capture; it never writes system input gain or disables the
      // WebRTC track.
      if (_canPublish && !_shouldMuteLocalMicrophone) {
        await room.localParticipant?.setMicrophoneEnabled(
          true,
          audioCaptureOptions: _audioCaptureOptions(),
        );
      }
      await _applyLocalMicrophoneState();
      await _applyOutputVolume();
      await _applyMusicBoxVolume();
      await _applyScreenShareSubscriptions();
      await _applyCameraSubscriptions();
      await _applyScreenShareVolume();
      _refreshAllMicStates();
      _startInputRebinder();
      _startOutputRebinder();
    } catch (e) {
      await _cancelEvents?.call();
      _cancelEvents = null;
      _stopInputRebinder();
      _stopOutputRebinder();
      try {
        await room.dispose();
      } catch (_) {}
      _room = null;
      _roomName = null;
      _connecting = false;
      _screenSharing = false;
      _localMicrophonePublishUnavailable = false;
      _watchedScreenShareIdentity = null;
      _watchedCameraIdentity = null;
      _presenceCallbacksEnabled = false;
      unawaited(_stopScreenAudioPublisher());
      _speakingIdentities.clear();
      _micMutedByIdentity.clear();
      unawaited(_resetLocalInputVolume());
      notifyListeners();
      throw LiveSessionConnectException(url: url, cause: e);
    }

    _presenceCallbacksEnabled = true;
    _connecting = false;
    notifyListeners();
  }

  /// Mute/unmute the local microphone. Safe to call when not connected.
  ///
  /// Muting is app-local gain 0 after capture: the shared OS input device and
  /// WebRTC track stay live, and we do not write RTCAudioSource.volume (which
  /// can leak into system input volume on desktop). A 0 input-volume slider is
  /// treated as the same local mute.
  Future<void> setMicMuted(bool muted) async {
    _micMuted = muted;
    if (!muted) {
      _localMicrophonePublishUnavailable = false;
    }
    await _applyLocalMicrophoneState();
    _refreshAllMicStates();
    notifyListeners();
  }

  Future<void> setInputVolume(double volume) async {
    final wasMutedByVolume = _inputVolume <= 0;
    _inputVolume = normalizedAudioVolume(volume);
    if (wasMutedByVolume && _inputVolume > 0) {
      _localMicrophonePublishUnavailable = false;
    }
    await _applyLocalMicrophoneState();
  }

  /// Select the microphone capture device. [deviceId] is WebRTC's macOS format
  /// (stringified CoreAudio AudioDeviceID), or null for the system default. Takes
  /// effect at the next publish; when already publishing, republishes the mic on
  /// the new device, preserving the current mute state.
  Future<void> setInputDeviceId(String? deviceId) async {
    if (_inputDeviceId == deviceId) return;
    _inputDeviceId = deviceId;
    _localMicrophonePublishUnavailable = false;
    final local = _room?.localParticipant;
    if (local == null) return;
    // Republish only if a mic track exists; otherwise the new id is picked up
    // by the next publish (connect or first unmute).
    final hasTrack =
        local.getTrackPublicationBySource(lk.TrackSource.microphone) != null;
    if (!hasTrack) return;
    // Switching capture device unavoidably stops and restarts the input (one
    // HFP transition on macOS). That is acceptable here: it only happens on an
    // explicit device change, not on a mute toggle. Restore the mute state
    // through LiveKit without touching the system input gain.
    await _restartLocalMicrophoneCapture(
      local,
      markUnavailableOnFailure: false,
    );
  }

  Future<void> _rebindInputDeviceId(String? deviceId) async {
    _inputDeviceId = deviceId;
    _localMicrophonePublishUnavailable = false;
    final local = _room?.localParticipant;
    if (local == null) return;
    final hasTrack =
        local.getTrackPublicationBySource(lk.TrackSource.microphone) != null;
    if (!hasTrack) {
      await _applyLocalMicrophoneState();
      _refreshAllMicStates();
      notifyListeners();
      return;
    }
    await _restartLocalMicrophoneCapture(local, markUnavailableOnFailure: true);
  }

  Future<void> _restartLocalMicrophoneCapture(
    lk.LocalParticipant local, {
    required bool markUnavailableOnFailure,
  }) async {
    if (!_canPublish) return;
    try {
      await _applyLocalInputVolume();
      await local.setMicrophoneEnabled(
        false,
        audioCaptureOptions: _audioCaptureOptions(),
      );
      if (_room?.localParticipant != local) return;
      await _applyLocalInputVolume();
      await local.setMicrophoneEnabled(
        true,
        audioCaptureOptions: _audioCaptureOptions(),
      );
      if (_room?.localParticipant != local) return;
      _localMicrophonePublishUnavailable = false;
      await _applyLocalMicrophoneState();
      _refreshAllMicStates();
      notifyListeners();
    } catch (_) {
      if (markUnavailableOnFailure) {
        _markLocalMicrophonePublishUnavailable();
      }
      // Capture recovery is best-effort; keep the room connected on failure.
    }
  }

  Future<void> setOutputVolume(double volume) async {
    _outputVolume = normalizedAudioVolume(volume);
    await _applyOutputVolume();
  }

  Future<void> setParticipantVoiceVolume(String userId, double volume) async {
    final normalized = normalizedParticipantVoiceVolume(volume);
    if (normalized == defaultParticipantVoiceVolume) {
      _participantVoiceVolumes.remove(userId);
    } else {
      _participantVoiceVolumes[userId] = normalized;
    }
    await _applyParticipantVoiceVolume(userId);
  }

  Future<void> setMusicBoxVolume(double volume) async {
    _musicBoxVolume = normalizedAudioVolume(volume);
    await _applyMusicBoxVolume();
  }

  Future<void> setScreenShareVolume(double volume) async {
    _screenShareVolume = normalizedAudioVolume(volume);
    await _applyScreenShareVolume();
  }

  Future<void> setWatchedScreenShareIdentity(String? identity) async {
    final next = identity?.trim();
    final normalized = next == null || next.isEmpty ? null : next;
    final changed = _watchedScreenShareIdentity != normalized;
    _watchedScreenShareIdentity = normalized;
    await _applyScreenShareSubscriptions();
    await _applyScreenShareVolume();
    if (changed) notifyListeners();
  }

  Future<void> setWatchedCameraIdentity(String? identity) async {
    final next = identity?.trim();
    final normalized = next == null || next.isEmpty ? null : next;
    final changed = _watchedCameraIdentity != normalized;
    _watchedCameraIdentity = normalized;
    await _applyCameraSubscriptions();
    if (changed) notifyListeners();
  }

  Future<void> setOutputMuted(bool muted) async {
    _outputMuted = muted;
    await _applyOutputVolume();
  }

  /// Start or stop sharing the screen. [sourceId] is a desktop capturer source
  /// id (see [listScreenSources]); when null the platform default is used.
  /// Returns the resulting share state. Throws if capture fails (e.g. the user
  /// cancels the OS picker) — the caller is responsible for surfacing that.
  Future<bool> setScreenShareEnabled(bool enabled, {String? sourceId}) async {
    final local = _room?.localParticipant;
    if (local == null) return false;
    if (enabled) {
      await local.setCameraEnabled(false);
      // A concrete maxFrameRate is required: livekit's desktop
      // ScreenShareCaptureOptions sends `mandatory: {frameRate: maxFrameRate}`,
      // and when maxFrameRate is null (the default) that becomes a null value.
      // flutter_webrtc's native constraint parser then calls GetValue<int> on
      // that null, throwing std::bad_variant_access and hard-crashing the
      // process. Passing a real frame rate keeps the value a valid double.
      //
      // On macOS (flutter_webrtc 1.4.0+) full-screen capture goes through
      // ScreenCaptureKit, which samples at the display's native resolution and
      // ignores params.dimensions — so dimensions here only bounds non-SCKit
      // platforms. The encoding (and defaultVideoPublishOptions in connect())
      // still governs what viewers receive. To cap resolution on macOS we scale
      // the published encoding down after publishing (_applyScreenShareScale).
      final target = screenShareResolutionForHeight(_screenShareMaxHeight);
      final captureScreenAudio = shouldRequestScreenShareAudio(
        sourceId: sourceId,
        isDesktopSourcePickerPlatform: supportsDesktopScreenShare,
        isWindowsDesktop: !kIsWeb && Platform.isWindows,
      );
      final options = lk.ScreenShareCaptureOptions(
        captureScreenAudio: captureScreenAudio,
        sourceId: sourceId,
        maxFrameRate: 60.0,
        params: lk.VideoParameters(
          dimensions: lk.VideoDimensions(target.width, target.height),
          encoding: const lk.VideoEncoding(
            maxFramerate: 60,
            maxBitrate: 16000 * 1000,
          ),
        ),
      );
      try {
        await local.setScreenShareEnabled(
          true,
          // Never let livekit_client create an audio track on factory-1:
          // that track would pull from the mic ADM (not SCK), get published
          // alongside the video, and race the send-stream capture checker.
          // Screen audio is published separately on factory-2 by our
          // ScreenAudioPublisher. SCK still captures audio for the device
          // (forced in the native getDisplayMedia), but no audio track is
          // returned in the MediaStream.
          captureScreenAudio: false,
          screenShareCaptureOptions: options,
        );
        _screenSharing = true;
        await _applyScreenShareScale();
      } catch (_) {
        rethrow;
      }
      // Start the screen-audio aux publisher in the background so it never
      // blocks the screen-share video. If the aux room fails to connect (ICE,
      // token, etc.) the video share still works — just without independent
      // audio. Errors are logged, not surfaced: the user didn't ask to "share
      // audio", they asked to "share screen", and audio is best-effort.
      if (captureScreenAudio) {
        unawaited(
          _startScreenAudioPublisher().catchError((Object e) {
            debugPrint('screen-audio publisher failed: $e');
          }),
        );
      }
    } else {
      await local.setScreenShareEnabled(false);
      _screenSharing = false;
      await _stopScreenAudioPublisher();
    }
    notifyListeners();
    return _screenSharing;
  }

  /// Set the target max height for the local screen share. Persists in-session;
  /// re-scales the live publication immediately when a share is running.
  Future<void> setScreenShareMaxHeight(int height) async {
    final normalized = normalizedScreenShareMaxHeight(height);
    if (_screenShareMaxHeight == normalized) return;
    _screenShareMaxHeight = normalized;
    if (_screenSharing) {
      await _applyScreenShareScale();
    }
    notifyListeners();
  }

  /// Scale the published screen-share encoding so it is sent at no more than
  /// [_screenShareMaxHeight]. macOS captures at the display's native resolution
  /// (ScreenCaptureKit ignores capture dimensions), so the encoder's
  /// `scaleResolutionDownBy` is the only lever that actually reduces the pixels
  /// we send. Best-effort: a missing sender or stats leaves the share unscaled.
  Future<void> _applyScreenShareScale() async {
    final local = _room?.localParticipant;
    if (local == null) return;
    final pub = local.getTrackPublicationBySource(
      lk.TrackSource.screenShareVideo,
    );
    final track = pub?.track;
    if (track is! lk.LocalVideoTrack) return;
    final sender = track.sender;
    if (sender == null) return;

    // The source height we're capturing. getSenderStats() reports the encoded
    // frame size once frames flow; until then fall back to the configured
    // target so we never scale up.
    var sourceHeight = _screenShareMaxHeight;
    try {
      final stats = await track.getSenderStats();
      for (final s in stats) {
        final h = s.frameHeight;
        if (h != null && h > sourceHeight) sourceHeight = h.toInt();
      }
    } catch (_) {
      // No stats yet; the next call (or capture-side dimensions on
      // Windows/Linux) handles it.
    }

    final scale = screenShareScaleDownBy(
      sourceHeight: sourceHeight,
      targetHeight: _screenShareMaxHeight,
    );
    try {
      final params = sender.parameters;
      final encodings = params.encodings;
      if (encodings == null || encodings.isEmpty) return;
      var changed = false;
      for (final encoding in encodings) {
        if (encoding.scaleResolutionDownBy != scale) {
          encoding.scaleResolutionDownBy = scale;
          changed = true;
        }
      }
      if (changed) {
        params.encodings = encodings;
        await sender.setParameters(params);
      }
    } catch (_) {
      // setParameters is best-effort; a failure leaves the prior scale in place.
    }
  }

  /// Start or stop the local camera. Returns the resulting state. Throws if
  /// capture fails (no camera, permission denied) — the caller surfaces it.
  Future<bool> setCameraEnabled(bool enabled) async {
    final local = _room?.localParticipant;
    if (local == null) return false;
    if (enabled && _screenSharing) {
      await setScreenShareEnabled(false);
    }
    await local.setCameraEnabled(enabled);
    notifyListeners();
    return enabled;
  }

  /// Enumerate capturable desktop sources (screens and windows) for the
  /// share picker. Best-effort: returns an empty list if enumeration fails.
  static Future<List<ScreenSource>> listScreenSources() async {
    try {
      _ensureScreenSourceThumbnailCacheSubscription();
      final sources = await rtc.desktopCapturer.getSources(
        types: [rtc.SourceType.Screen, rtc.SourceType.Window],
        thumbnailSize: rtc.ThumbnailSize(640, 360),
      );
      final screenSources = <ScreenSource>[];
      for (final s in sources) {
        final isWindow = s.type == rtc.SourceType.Window;
        final name = s.name.trim();
        final thumbnailKey = _thumbnailKeyForDesktopSource(s);
        final thumbnail = await _loadInitialScreenSourceThumbnail(
          s,
          thumbnailKey,
        );
        screenSources.add(
          ScreenSource(
            id: s.id,
            name: name.isEmpty ? (isWindow ? '窗口' : '屏幕') : name,
            thumbnail: thumbnail,
            isWindow: isWindow,
            thumbnailKey: thumbnailKey,
            thumbnailUpdates: _screenSourceThumbnailUpdates(thumbnailKey),
          ),
        );
      }
      return filterScreenSourcesForPicker(screenSources);
    } catch (_) {
      return const [];
    }
  }

  /// Ask flutter_webrtc to refresh source thumbnails. On Windows the initial
  /// getSources() call does not include thumbnail bytes; they arrive through
  /// onThumbnailChanged after updateSources(). macOS screen thumbnails come
  /// from a native capture (to avoid the sheared RTCDesktopSource thumbnail),
  /// so refresh those directly rather than through onThumbnailChanged.
  static Future<void> refreshScreenSourceThumbnails() async {
    try {
      _ensureScreenSourceThumbnailCacheSubscription();
      await rtc.desktopCapturer.updateSources(
        types: [rtc.SourceType.Screen, rtc.SourceType.Window],
      );
      await _refreshNativeScreenThumbnails();
    } catch (_) {}
  }

  static Future<void> _refreshNativeScreenThumbnails() async {
    for (final thumbnailKey in _screenSourceDirectThumbnailKeys.toList()) {
      final displayId = thumbnailKey.startsWith('screen:')
          ? thumbnailKey.substring('screen:'.length)
          : thumbnailKey;
      final thumbnail = await _captureNativeScreenThumbnail(displayId);
      if (thumbnail != null && thumbnail.isNotEmpty) {
        _rememberAndEmitScreenSourceThumbnail(
          sourceId: thumbnailKey,
          thumbnail: thumbnail,
        );
      }
    }
  }

  Future<void> disconnect() => _disconnect();

  Future<void> _disconnect({bool clearWatchedTargets = true}) async {
    final room = _room;
    final cancel = _cancelEvents;
    _cancelEvents = null;
    _screenShareSubscriptionReconciler.invalidate();
    _cameraSubscriptionReconciler.invalidate();
    _stopInputRebinder();
    _stopOutputRebinder();
    _room = null;
    _roomName = null;
    _resolvedLiveKitUrl = null;
    _connecting = false;
    _screenSharing = false;
    if (clearWatchedTargets) {
      _watchedScreenShareIdentity = null;
      _watchedCameraIdentity = null;
    }
    _localMicrophonePublishUnavailable = false;
    _canPublish = true;
    _presenceCallbacksEnabled = false;
    _speakingIdentities.clear();
    _micMutedByIdentity.clear();
    await _resetLocalInputVolume();
    await _stopScreenAudioPublisher();
    if (cancel != null) {
      try {
        await cancel();
      } catch (_) {}
    }
    if (room != null) {
      try {
        await room.disconnect();
      } catch (_) {}
      try {
        await room.dispose();
      } catch (_) {}
      // After teardown, recover the Bluetooth A2DP sample rate the call may have
      // dragged down to HFP. Done last so the restored rate isn't immediately
      // re-overridden by a still-active capture stream, and only after a real
      // call (no room == nothing to undo). No-op off macOS.
      await _resetAudioOnLeave();
    }
    notifyListeners();
  }

  @override
  void dispose() {
    final room = _room;
    final cancel = _cancelEvents;
    _cancelEvents = null;
    _screenShareSubscriptionReconciler.invalidate();
    _cameraSubscriptionReconciler.invalidate();
    _stopInputRebinder();
    _stopOutputRebinder();
    _room = null;
    _roomName = null;
    _screenSharing = false;
    _watchedScreenShareIdentity = null;
    _watchedCameraIdentity = null;
    _localMicrophonePublishUnavailable = false;
    _presenceCallbacksEnabled = false;
    unawaited(_stopScreenAudioPublisher());
    _speakingIdentities.clear();
    _micMutedByIdentity.clear();
    unawaited(_resetLocalInputVolume());
    cancel?.call();
    if (room != null) {
      // Best-effort async cleanup; swallow errors.
      room.disconnect().catchError((_) {}).whenComplete(() {
        try {
          room.dispose();
        } catch (_) {}
        unawaited(_resetAudioOnLeave());
      });
    }
    super.dispose();
  }

  // Restore the Bluetooth A2DP sample rate after a call. On macOS, starting the
  // mic forces a BT headset into HFP (low sample rate); the native fork resets
  // the output device's nominal rate back to its clean value. Best-effort.
  Future<void> _resetAudioOnLeave() async {
    if (kIsWeb || !Platform.isMacOS) return;
    try {
      await rtc.Helper.gcResetAudioOnLeave();
    } catch (_) {
      // Recovery is best-effort; never let it surface as a teardown failure.
    }
  }

  // ---- Event handling -------------------------------------------------------

  void _onEvent(lk.RoomEvent event) {
    if (event is lk.ReconnectingEvent) {
      _presenceCallbacksEnabled = false;
      return;
    }
    if (event is lk.RoomReconnectedEvent) {
      _presenceCallbacksEnabled = true;
      _refreshAllMicStates();
      notifyListeners();
      return;
    }
    if (event is lk.ActiveSpeakersChangedEvent) {
      _speakingIdentities
        ..clear()
        ..addAll(
          event.speakers
              .where((p) => !_isScreenAudioAux(p.identity))
              .map((p) => p.identity),
        );
      notifyListeners();
      return;
    }
    if (event is lk.ParticipantConnectedEvent) {
      if (_isScreenAudioAux(event.participant.identity)) {
        // An aux participant joining still means a new screen-audio track may
        // arrive; apply the volume so the track plays at the right level.
        unawaited(_applyScreenShareVolume());
        return;
      }
      _micMutedByIdentity[event.participant.identity] = _isMicMuted(
        event.participant,
      );
      unawaited(_applyOutputVolume());
      unawaited(_applyMusicBoxVolume());
      unawaited(_applyScreenShareSubscriptions());
      unawaited(_applyCameraSubscriptions());
      unawaited(_applyScreenShareVolume());
      notifyListeners();
      if (_presenceCallbacksEnabled &&
          isLivePresenceSoundParticipantIdentity(event.participant.identity)) {
        onParticipantJoined?.call(event.participant.identity);
      }
      return;
    }
    if (event is lk.ParticipantDisconnectedEvent) {
      if (_isScreenAudioAux(event.participant.identity)) {
        unawaited(_applyScreenShareVolume());
        return;
      }
      _micMutedByIdentity.remove(event.participant.identity);
      _speakingIdentities.remove(event.participant.identity);
      notifyListeners();
      if (_presenceCallbacksEnabled &&
          isLivePresenceSoundParticipantIdentity(event.participant.identity)) {
        onParticipantLeft?.call(
          event.participant.identity,
          liveParticipantDepartureKind(event.reason),
        );
      }
      return;
    }
    // An admin mute_mic / restore_voice flips the local participant's publish
    // permission server-side. Headphone mute only changes canSubscribe, so
    // ignore permission events where canPublish did not actually change.
    if (event is lk.ParticipantPermissionsUpdatedEvent) {
      final local = _room?.localParticipant;
      if (local != null && event.participant.identity == local.identity) {
        final next = event.permissions.canPublish;
        if (shouldApplyLivePublishPermissionUpdate(
          currentCanPublish: _canPublish,
          oldCanPublish: event.oldPermissions.canPublish,
          nextCanPublish: next,
        )) {
          _canPublish = next;
          unawaited(_applyLocalMicrophoneState());
          notifyListeners();
          onPublishPermissionChanged?.call(next);
        }
      }
      return;
    }
    // Video subscription / publish lifecycle: rebuild the rendered track list.
    if (event is lk.TrackSubscribedEvent ||
        event is lk.TrackUnsubscribedEvent ||
        event is lk.LocalTrackPublishedEvent ||
        event is lk.LocalTrackUnpublishedEvent ||
        event is lk.TrackPublishedEvent ||
        event is lk.TrackUnpublishedEvent ||
        event is lk.TrackMutedEvent ||
        event is lk.TrackUnmutedEvent) {
      unawaited(_applyLocalMicrophoneState());
      unawaited(_applyOutputVolume());
      unawaited(_applyMusicBoxVolume());
      unawaited(_applyScreenShareVolume());
      _refreshAllMicStates();
      // A local screen-share track can be stopped from the OS (e.g. the
      // "stop sharing" bar); keep our flag honest.
      final wasScreenSharing = _screenSharing;
      _screenSharing = _localHasScreenShare();
      if (wasScreenSharing && !_screenSharing) {
        unawaited(_stopScreenAudioPublisher());
      }
      if (_watchedScreenShareIdentity != null &&
          !_remoteScreenSharePublicationExists(_watchedScreenShareIdentity!)) {
        _watchedScreenShareIdentity = null;
      }
      if (_watchedCameraIdentity != null &&
          !_remoteCameraPublicationExists(_watchedCameraIdentity!)) {
        _watchedCameraIdentity = null;
      }
      unawaited(_applyScreenShareSubscriptions());
      unawaited(_applyCameraSubscriptions());
      unawaited(_applyScreenShareVolume());
      notifyListeners();
      return;
    }
    if (event is lk.RoomDisconnectedEvent) {
      _presenceCallbacksEnabled = false;
      _screenSharing = false;
      _watchedScreenShareIdentity = null;
      _watchedCameraIdentity = null;
      _localMicrophonePublishUnavailable = false;
      unawaited(_stopScreenAudioPublisher());
      _speakingIdentities.clear();
      _micMutedByIdentity.clear();
      notifyListeners();
      // An admin kick force-disconnects us via RemoveParticipant; LiveKit
      // reports it as participantRemoved. Distinguish it from our own
      // disconnect() (which clears _cancelEvents before the event can fire, so
      // this handler never runs for a voluntary leave) and from transient
      // network drops, so the UI can exit the voice panel without auto-
      // reconnecting into a room we were just removed from.
      if (event.reason == lk.DisconnectReason.participantRemoved ||
          event.reason == lk.DisconnectReason.roomDeleted) {
        onForciblyRemoved?.call();
      }
      return;
    }
  }

  bool _localHasScreenShare() {
    final local = _room?.localParticipant;
    if (local == null) return false;
    return local.videoTrackPublications.any(
      (pub) =>
          pub.source == lk.TrackSource.screenShareVideo && pub.track != null,
    );
  }

  void _refreshAllMicStates() {
    final room = _room;
    if (room == null) return;
    final local = room.localParticipant;
    if (local != null) {
      _micMutedByIdentity[local.identity] = localMicMuted;
    }
    for (final p in room.remoteParticipants.values) {
      if (_isScreenAudioAux(p.identity)) continue;
      _micMutedByIdentity[p.identity] = _isMicMuted(p);
    }
  }

  static bool _isMicMuted(lk.Participant participant) {
    final pub = participant.getTrackPublicationBySource(
      lk.TrackSource.microphone,
    );
    return pub?.muted ?? true;
  }

  bool get _shouldMuteLocalMicrophone => _micMuted || _inputVolume <= 0;

  double get _effectiveInputVolume =>
      (!_canPublish || _shouldMuteLocalMicrophone) ? 0.0 : _inputVolume;

  lk.AudioCaptureOptions _audioCaptureOptions() {
    return lk.AudioCaptureOptions(
      stopAudioCaptureOnMute: false,
      deviceId: _inputDeviceId,
    );
  }

  void _markLocalMicrophonePublishUnavailable() {
    _localMicrophonePublishUnavailable = true;
  }

  /// Start the hidden screen-audio aux participant. The aux room publishes the
  /// screen-share audio track through an isolated WebRTC factory (second
  /// PeerConnectionFactory fed by the platform screen-audio capture path), so
  /// screen audio never shares an AudioState with the mic. Requires the main
  /// room to be connected (for the resolved LiveKit URL + room name) and a
  /// token provider to have been injected.
  Future<void> _startScreenAudioPublisher() async {
    if (_screenAudioPublisher != null) return;
    final provider = _screenAudioTokenProvider;
    final url = _resolvedLiveKitUrl;
    final name = _roomName;
    debugPrint(
      'screen-audio: _startScreenAudioPublisher '
      'provider=${provider != null} url=$url roomName=$name',
    );
    if (provider == null || url == null || name == null) return;
    _screenAudioPublisher = ScreenAudioPublisher(tokenProvider: provider);
    try {
      await _screenAudioPublisher!.start(liveKitUrl: url, roomName: name);
    } catch (e, st) {
      debugPrint('screen-audio: publisher start failed: $e\n$st');
      _screenAudioPublisher = null;
      rethrow;
    }
  }

  Future<void> _stopScreenAudioPublisher() async {
    final publisher = _screenAudioPublisher;
    _screenAudioPublisher = null;
    if (publisher != null) {
      await publisher.stop();
    }
  }

  Future<void> _applyLocalMicrophoneState() async {
    final local = _room?.localParticipant;
    await _applyLocalInputVolume();
    if (local == null) return;

    var microphonePublication = local.getTrackPublicationBySource(
      lk.TrackSource.microphone,
    );
    if (microphonePublication == null &&
        !_shouldMuteLocalMicrophone &&
        _canPublish &&
        !_localMicrophonePublishUnavailable) {
      try {
        await local.setMicrophoneEnabled(
          true,
          audioCaptureOptions: _audioCaptureOptions(),
        );
        microphonePublication = local.getTrackPublicationBySource(
          lk.TrackSource.microphone,
        );
      } catch (_) {
        _markLocalMicrophonePublishUnavailable();
        return;
      }
    }
    if (microphonePublication == null) return;

    // Keep the LiveKit track enabled. Local mute is represented by the
    // app-level live state plus capture gain 0, not by disabling the track.
    if (_canPublish && microphonePublication.muted) {
      try {
        await local.setMicrophoneEnabled(
          true,
          audioCaptureOptions: _audioCaptureOptions(),
        );
      } catch (_) {}
    }
    await _applyLocalInputVolume();
  }

  Future<void> _applyLocalInputVolume() async {
    try {
      await rtc.Helper.setLocalAudioInputVolume(_effectiveInputVolume);
    } catch (_) {}
  }

  Future<void> _resetLocalInputVolume() async {
    try {
      await rtc.Helper.setLocalAudioInputVolume(1.0);
    } catch (_) {}
  }

  Future<void> _applyOutputVolume() async {
    final room = _room;
    if (room == null) return;
    for (final participant in room.remoteParticipants.values) {
      // The music box bot has its own independent volume knob; leave it to
      // _applyMusicBoxVolume so the two don't fight over the same track.
      if (participant.identity == musicBoxBotIdentity) continue;
      await _applyParticipantVoiceVolumeToParticipant(participant);
    }
  }

  Future<void> _applyParticipantVoiceVolume(String userId) async {
    final participant = _room?.remoteParticipants[userId];
    if (participant == null || participant.identity == musicBoxBotIdentity) {
      return;
    }
    await _applyParticipantVoiceVolumeToParticipant(participant);
  }

  Future<void> _applyParticipantVoiceVolumeToParticipant(
    lk.RemoteParticipant participant,
  ) async {
    final baseVolume = _outputMuted ? 0.0 : _outputVolume;
    final volume = normalizedParticipantVoiceVolume(
      baseVolume * participantVoiceVolume(participant.identity),
    );
    for (final pub in participant.audioTrackPublications) {
      if (pub.source == lk.TrackSource.screenShareAudio) continue;
      final track = pub.track;
      if (track == null) continue;
      try {
        await rtc.Helper.setVolume(volume, track.mediaStreamTrack);
      } catch (_) {}
    }
  }

  Future<void> _applyMusicBoxVolume() async {
    final room = _room;
    if (room == null) return;
    for (final participant in room.remoteParticipants.values) {
      if (participant.identity != musicBoxBotIdentity) continue;
      for (final pub in participant.audioTrackPublications) {
        final track = pub.track;
        if (track == null) continue;
        try {
          await rtc.Helper.setVolume(_musicBoxVolume, track.mediaStreamTrack);
        } catch (_) {}
      }
    }
  }

  Future<void> _applyScreenShareVolume() async {
    final room = _room;
    if (room == null) return;
    final localIdentity = room.localParticipant?.identity;
    for (final participant in room.remoteParticipants.values) {
      // Self-echo suppression: the local user's own screen-audio aux
      // participant (`<ownId>--screen-audio`) is publishing audio the user
      // is already hearing locally (it's their own screen). Mute it to 0 so
      // it never plays back to them. Other users' aux tracks play at the
      // screen-share volume.
      final isOwnAux = _isOwnScreenAudioAux(
        participant.identity,
        localIdentity,
      );
      final ownerIdentity = _screenShareAudioOwnerIdentity(
        participant.identity,
      );
      final isWatched =
          _watchedScreenShareIdentity != null &&
          ownerIdentity == _watchedScreenShareIdentity;
      for (final pub in participant.audioTrackPublications) {
        if (pub.source != lk.TrackSource.screenShareAudio) continue;
        final track = pub.track;
        if (track == null) continue;
        try {
          await rtc.Helper.setVolume(
            isOwnAux || !isWatched ? 0.0 : _screenShareVolume,
            track.mediaStreamTrack,
          );
        } catch (_) {}
      }
    }
  }

  Future<void> _applyScreenShareSubscriptions() {
    return _screenShareSubscriptionReconciler.schedule(
      (isCurrent) => _reconcileVideoSubscriptions(
        source: lk.TrackSource.screenShareVideo,
        watchedIdentity: _watchedScreenShareIdentity,
        isCurrent: isCurrent,
      ),
    );
  }

  Future<void> _applyCameraSubscriptions() {
    return _cameraSubscriptionReconciler.schedule(
      (isCurrent) => _reconcileVideoSubscriptions(
        source: lk.TrackSource.camera,
        watchedIdentity: _watchedCameraIdentity,
        isCurrent: isCurrent,
      ),
    );
  }

  Future<void> _reconcileVideoSubscriptions({
    required lk.TrackSource source,
    required String? watchedIdentity,
    required bool Function() isCurrent,
  }) async {
    final room = _room;
    if (room == null) return;
    for (final participant in room.remoteParticipants.values) {
      if (!isCurrent() || !identical(_room, room)) return;
      if (_isScreenAudioAux(participant.identity)) continue;
      for (final pub in participant.videoTrackPublications) {
        if (!isCurrent() || !identical(_room, room)) return;
        if (pub.source != source) continue;
        final shouldSubscribe =
            watchedIdentity != null && participant.identity == watchedIdentity;
        try {
          if (shouldSubscribe) {
            await pub.subscribe();
          } else {
            await pub.unsubscribe();
          }
        } catch (_) {}
      }
    }
  }

  bool _remoteScreenSharePublicationExists(String identity) {
    final participant = _room?.remoteParticipants[identity];
    if (participant == null) return false;
    return participant.videoTrackPublications.any(
      (pub) => pub.source == lk.TrackSource.screenShareVideo,
    );
  }

  bool _remoteCameraPublicationExists(String identity) {
    final participant = _room?.remoteParticipants[identity];
    if (participant == null) return false;
    return participant.videoTrackPublications.any(
      (pub) => pub.source == lk.TrackSource.camera,
    );
  }

  // Re-bind WebRTC's audio output to the live default endpoint and re-apply
  // every track volume. Invoked by [_outputRebinder] after macOS swaps the
  // default output (the Bluetooth A2DP/HFP profile flip), where WebRTC would
  // otherwise keep rendering into the torn-down endpoint as noise.
  Future<void> _reapplyAudioRouting() async {
    if (_room == null) return;
    await _applyOutputVolume();
    await _applyMusicBoxVolume();
    await _applyScreenShareVolume();
  }

  void _startInputRebinder() {
    _stopInputRebinder();
    final rebinder = _inputRebinderFactory(this);
    _inputRebinder = rebinder;
    rebinder?.start();
  }

  void _stopInputRebinder() {
    final rebinder = _inputRebinder;
    _inputRebinder = null;
    if (rebinder != null) unawaited(rebinder.stop());
  }

  void _startOutputRebinder() {
    _stopOutputRebinder();
    final rebinder = _outputRebinderFactory(this);
    _outputRebinder = rebinder;
    rebinder?.start();
  }

  void _stopOutputRebinder() {
    final rebinder = _outputRebinder;
    _outputRebinder = null;
    if (rebinder != null) unawaited(rebinder.stop());
  }

  /// Drives the output-rebinder lifecycle without a live LiveKit connection, so
  /// the macOS A2DP/HFP recovery wiring can be exercised in tests. Production
  /// code reaches these through [connect]/[disconnect].
  @visibleForTesting
  void debugStartInputRebinder() => _startInputRebinder();

  @visibleForTesting
  void debugStopInputRebinder() => _stopInputRebinder();

  @visibleForTesting
  void debugStartOutputRebinder() => _startOutputRebinder();

  @visibleForTesting
  void debugStopOutputRebinder() => _stopOutputRebinder();
}

// Default desktop recovery for input hotplug/profile flips: resolve the
// preferred mic against the current device list, then restart the local
// microphone capture inside the existing room instead of reconnecting.
AudioInputRebinder? _defaultInputRebinderFactory(
  LiveSession session, {
  required AudioDeviceStore? audioDeviceStore,
}) {
  if (kIsWeb || !(Platform.isMacOS || Platform.isWindows)) return null;
  final systemAudio = SystemAudioDevices();
  const audioDevices = LiveAudioDeviceService();
  return AudioInputRebinder(
    deviceChanges: lk.Hardware.instance.onDeviceChange.stream.map((_) {}),
    currentInputDeviceId: () => preferredLiveInputDeviceId(
      audioDeviceStore: audioDeviceStore,
      audioDevices: audioDevices,
      systemAudio: systemAudio,
    ),
    rebindInput: session._rebindInputDeviceId,
  );
}

// Default desktop recovery for output hotplug/profile flips: re-select the
// user's stored output when it is available again; otherwise temporarily follow
// the OS default. The re-select forces WebRTC's ADM to rebuild playout routing,
// then volumes are re-applied.
AudioOutputRebinder? _defaultOutputRebinderFactory(
  LiveSession session, {
  required AudioDeviceStore? audioDeviceStore,
}) {
  if (kIsWeb || !(Platform.isMacOS || Platform.isWindows)) return null;
  final systemAudio = SystemAudioDevices();
  const audioDevices = LiveAudioDeviceService();
  return AudioOutputRebinder(
    deviceChanges: lk.Hardware.instance.onDeviceChange.stream.map((_) {}),
    currentOutputDeviceId: () => _preferredOutputDeviceId(
      audioDeviceStore: audioDeviceStore,
      audioDevices: audioDevices,
      systemAudio: systemAudio,
    ),
    selectOutput: (deviceId) async {
      await rtc.Helper.selectAudioOutput(deviceId);
    },
    onRebound: session._reapplyAudioRouting,
  );
}

Future<String?> _preferredOutputDeviceId({
  required AudioDeviceStore? audioDeviceStore,
  required LiveAudioDeviceService audioDevices,
  required SystemAudioDevices systemAudio,
}) async {
  final systemDefaultOutputId = await systemAudio.currentOutputDeviceId();
  if (audioDeviceStore == null) return systemDefaultOutputId;
  try {
    final stored = await audioDeviceStore.read();
    final devices = await audioDevices.enumerateDevices();
    final storedOutput = preferredStoredAudioDeviceFrom<AudioDeviceInfo>(
      devices,
      kind: 'audiooutput',
      storedDeviceId: stored.outputDeviceId,
      storedDeviceLabel: stored.outputDeviceLabel,
      storedDeviceGroupId: stored.outputDeviceGroupId,
      kindOf: audioDeviceInfoKind,
      deviceIdOf: audioDeviceInfoId,
      labelOf: audioDeviceInfoLabel,
      groupIdOf: audioDeviceInfoGroupId,
      systemDefaultDeviceId: systemDefaultOutputId,
    );
    return storedOutput?.deviceId ?? systemDefaultOutputId;
  } catch (_) {
    return systemDefaultOutputId;
  }
}

class LiveSessionConnectException implements Exception {
  const LiveSessionConnectException({required this.url, required this.cause});

  final String url;
  final Object cause;

  @override
  String toString() {
    return '无法连接语音服务';
  }
}
