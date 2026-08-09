import '../protocol/models.dart';
import 'error_display.dart';

class JoinedLiveRoomSummary {
  const JoinedLiveRoomSummary({
    required this.roomId,
    required this.displayName,
    required this.avatarLabel,
    required this.avatarUrl,
    required this.defaultAvatarKey,
  });

  final String roomId;
  final String displayName;
  final String avatarLabel;
  final String? avatarUrl;
  final String defaultAvatarKey;
}

class LiveParticipantTileState {
  const LiveParticipantTileState({
    required this.broadcasting,
    required this.highlighted,
    required this.micMutedForDisplay,
    required this.micActive,
  });

  final bool broadcasting;
  final bool highlighted;
  final bool micMutedForDisplay;
  final bool micActive;
}

class AuthoritativeLivePresenceChanges {
  const AuthoritativeLivePresenceChanges({
    required this.joined,
    required this.left,
  });

  final Set<String> joined;
  final Set<String> left;
}

/// User identities which the server still considers present in the voice
/// channel. A reconnecting participant deliberately stays present during the
/// bounded recovery window; a first-time `joining` placeholder does not become
/// audible/visible presence until the app-level join reaches `online`.
Set<String> authoritativeLivePresenceUserIds(LiveState live) {
  return {
    for (final participant in live.participants)
      if (liveParticipantConnectionReady(participant) ||
          participant.connectionState.trim().toLowerCase() == 'reconnecting')
        participant.user.id,
  };
}

/// Computes join/leave cues from two authoritative server snapshots.
///
/// LiveKit can emit `ParticipantDisconnectedEvent` at the start of a temporary
/// reconnect while the server correctly keeps that person in the roster. Cues
/// must therefore follow this snapshot transition rather than the raw media
/// callback, otherwise users hear a leave sound while the member remains.
AuthoritativeLivePresenceChanges authoritativeLivePresenceChanges({
  required Set<String> before,
  required Set<String> after,
}) {
  return AuthoritativeLivePresenceChanges(
    joined: after.difference(before),
    left: before.difference(after),
  );
}

class LiveMicControlState {
  const LiveMicControlState({
    required this.mutedForDisplay,
    required this.active,
    required this.enabled,
  });

  final bool mutedForDisplay;
  final bool active;
  final bool enabled;
}

enum LiveScreenSourceListBodyState { loading, empty, results }

class LiveScreenSourcePickerState<T> {
  const LiveScreenSourcePickerState({
    this.sources,
    this.selectedId,
    this.loading = false,
    this.error,
  });

  final List<T>? sources;
  final String? selectedId;
  final bool loading;
  final String? error;

  LiveScreenSourcePickerState<T> copyWith({
    List<T>? sources,
    Object? selectedId = _liveScreenSourceSelectedIdUnchanged,
    bool? loading,
    Object? error = _liveScreenSourceErrorUnchanged,
  }) {
    return LiveScreenSourcePickerState<T>(
      sources: sources ?? this.sources,
      selectedId: identical(selectedId, _liveScreenSourceSelectedIdUnchanged)
          ? this.selectedId
          : selectedId as String?,
      loading: loading ?? this.loading,
      error: identical(error, _liveScreenSourceErrorUnchanged)
          ? this.error
          : error as String?,
    );
  }
}

const Object _liveScreenSourceSelectedIdUnchanged = Object();
const Object _liveScreenSourceErrorUnchanged = Object();

LiveParticipant? liveParticipantByUserId(LiveState? live, String userId) {
  if (live == null) return null;
  for (final participant in live.participants) {
    if (participant.user.id == userId) return participant;
  }
  return null;
}

String liveParticipantDisplayName(
  LiveState? live,
  String userId, {
  String fallback = '',
}) {
  final participant = liveParticipantByUserId(live, userId);
  if (participant == null) return fallback;
  return liveUserDisplayName(participant.user, fallback: fallback);
}

String liveUserDisplayName(UserSummary user, {String fallback = ''}) {
  final roomName = _nonEmpty(user.roomDisplayName);
  if (roomName != null) return roomName;
  final displayName = _nonEmpty(user.displayName);
  if (displayName != null) return displayName;
  final username = _nonEmpty(user.username);
  if (username != null) return username;
  return fallback;
}

List<LiveParticipant> visibleLiveParticipantsForStage(
  Iterable<LiveParticipant> participants, {
  required String currentUserId,
  required bool localParticipantReady,
  Set<String> connectedParticipantIds = const <String>{},
}) {
  return [
    for (final participant in participants)
      if ((localParticipantReady || participant.user.id != currentUserId) &&
          liveParticipantVisibleInRoster(
            participant,
            connectedParticipantIds: connectedParticipantIds,
          ))
        participant,
  ];
}

bool liveParticipantConnectionReady(LiveParticipant participant) {
  final state = participant.connectionState.trim().toLowerCase();
  return state == 'online' || state == 'connected' || state == 'joined';
}

bool liveParticipantVisibleInRoster(
  LiveParticipant participant, {
  Set<String> connectedParticipantIds = const <String>{},
}) {
  final connectionState = participant.connectionState.trim().toLowerCase();
  // A participant marked reconnecting remains part of the server's live
  // count during its bounded reconnect grace period. Keep that same member in
  // the open roster even while LiveKit is rebuilding the remote-participant
  // map, otherwise the room card says X users while the channel shows X - 1.
  // A provisional first-time "joining" participant is still hidden until
  // LiveKit confirms it; mic mute is independent of roster visibility.
  return liveParticipantConnectionReady(participant) ||
      connectionState == 'reconnecting' ||
      connectedParticipantIds.contains(participant.user.id);
}

bool liveStateMissingConnectedParticipants(
  LiveState? live, {
  required Set<String> connectedParticipantIds,
}) {
  if (live == null || connectedParticipantIds.isEmpty) return false;
  final liveUserIds = {
    for (final participant in live.participants) participant.user.id,
  };
  return connectedParticipantIds.any((id) => !liveUserIds.contains(id));
}

LiveParticipantTileState liveParticipantTileState(
  LiveParticipant participant, {
  required bool speaking,
  bool? liveKitMicMuted,
}) {
  final broadcasting = participant.cameraOn || participant.screenSharing;
  // App mute uses capture gain 0 while deliberately leaving the published
  // LiveKit microphone track enabled. The server snapshot is therefore the
  // authority for logical mute. A newly connected LiveKit participant exists
  // briefly before its microphone publication arrives, which LiveKit reports
  // as muted. Only let media state tighten the display after the participant
  // has completed the app-level join; otherwise an open-mic join visibly
  // flashes as muted. A real publish failure is reported to the server before
  // connection_state becomes ready, so it still renders muted.
  final liveKitMicMutedForDisplay =
      liveParticipantConnectionReady(participant) && liveKitMicMuted == true;
  final micMutedForDisplay =
      participant.micBlocked ||
      participant.voiceBlocked ||
      participant.micMuted ||
      liveKitMicMutedForDisplay;
  return LiveParticipantTileState(
    broadcasting: broadcasting,
    highlighted: speaking || broadcasting,
    micMutedForDisplay: micMutedForDisplay,
    micActive: !micMutedForDisplay && speaking,
  );
}

T? pickLiveStageShare<T>(
  Iterable<T> tracks, {
  required bool Function(T track) isScreenShare,
  required bool Function(T track) isLocal,
}) {
  T? localShare;
  for (final track in tracks) {
    if (!isScreenShare(track)) continue;
    if (!isLocal(track)) return track;
    localShare ??= track;
  }
  return localShare;
}

T? liveScreenShareByIdentity<T>(
  Iterable<T> tracks, {
  required String? identity,
  required String Function(T track) trackIdentity,
  required bool Function(T track) isScreenShare,
}) {
  if (identity == null) return null;
  for (final track in tracks) {
    if (isScreenShare(track) && trackIdentity(track) == identity) return track;
  }
  return null;
}

bool shouldExitMissingFullScreenShare({
  required String? fullScreenShareIdentity,
  required Object? fullScreenShare,
}) {
  return fullScreenShareIdentity != null && fullScreenShare == null;
}

bool shouldPatchEndedLocalScreenShare({
  required bool localScreenSharing,
  required bool sessionScreenSharing,
  required String? joinedLiveRoomId,
  required String? selectedRoomId,
}) {
  return localScreenSharing &&
      !sessionScreenSharing &&
      joinedLiveRoomId != null &&
      joinedLiveRoomId == selectedRoomId;
}

JoinedLiveRoomSummary? joinedLiveRoomSummary({
  required String? joinedLiveRoomId,
  required RoomDetail? selectedRoom,
  required Iterable<RoomCard> rooms,
}) {
  final roomId = _nonEmpty(joinedLiveRoomId);
  if (roomId == null) return null;

  final selected = selectedRoom;
  if (selected != null && selected.id == roomId) {
    return JoinedLiveRoomSummary(
      roomId: selected.id,
      displayName: _roomDetailDisplayName(selected),
      avatarLabel: _roomDetailAvatarLabel(selected),
      avatarUrl: selected.avatarUrl,
      defaultAvatarKey: selected.defaultAvatarKey,
    );
  }

  for (final room in rooms) {
    if (room.id != roomId) continue;
    return JoinedLiveRoomSummary(
      roomId: room.id,
      displayName: room.displayName,
      avatarLabel: _roomCardAvatarLabel(room),
      avatarUrl: room.avatarUrl,
      defaultAvatarKey: room.defaultAvatarKey,
    );
  }

  return null;
}

String? reconcileLiveScreenSourceSelection<T>(
  Iterable<T> sources, {
  required String? selectedId,
  required String Function(T source) sourceId,
}) {
  T? firstSource;
  for (final source in sources) {
    firstSource ??= source;
    if (sourceId(source) == selectedId) return selectedId;
  }
  return firstSource == null ? null : sourceId(firstSource);
}

T? liveScreenSourceById<T>(
  Iterable<T>? sources, {
  required String? selectedId,
  required String Function(T source) sourceId,
}) {
  if (selectedId == null || sources == null) return null;
  for (final source in sources) {
    if (sourceId(source) == selectedId) return source;
  }
  return null;
}

LiveScreenSourceListBodyState liveScreenSourceListBodyState<T>(
  Iterable<T>? sources,
) {
  if (sources == null) return LiveScreenSourceListBodyState.loading;
  if (sources.isEmpty) return LiveScreenSourceListBodyState.empty;
  return LiveScreenSourceListBodyState.results;
}

bool liveScreenSourceSelected<T>(
  T source, {
  required String? selectedId,
  required String Function(T source) sourceId,
}) {
  return selectedId != null && sourceId(source) == selectedId;
}

bool canConfirmLiveScreenSourceSelection(String? selectedId) {
  return selectedId != null;
}

bool canLoadLiveScreenSources<T>(LiveScreenSourcePickerState<T> state) {
  return !state.loading;
}

LiveScreenSourcePickerState<T> liveScreenSourceLoadStarted<T>(
  LiveScreenSourcePickerState<T> state,
) {
  return state.copyWith(loading: true, error: null);
}

LiveScreenSourcePickerState<T> liveScreenSourceLoadSucceeded<T>({
  required LiveScreenSourcePickerState<T> state,
  required Iterable<T> sources,
  required String Function(T source) sourceId,
}) {
  final nextSources = sources.toList();
  return state.copyWith(
    sources: nextSources,
    selectedId: reconcileLiveScreenSourceSelection(
      nextSources,
      selectedId: state.selectedId,
      sourceId: sourceId,
    ),
    loading: false,
    error: null,
  );
}

LiveScreenSourcePickerState<T> liveScreenSourceLoadFailed<T>({
  required LiveScreenSourcePickerState<T> state,
  required Object failure,
}) {
  return state.copyWith(
    loading: false,
    error: userFacingErrorMessage(failure, fallback: '加载语音频道失败'),
  );
}

LiveScreenSourcePickerState<T> liveScreenSourceSelectedChanged<T>(
  LiveScreenSourcePickerState<T> state,
  String? selectedId,
) {
  return state.copyWith(selectedId: selectedId);
}

T? visibleLiveScreenSourceThumbnail<T extends Iterable<int>>({
  required T? thumbnail,
  required Object? imageError,
}) {
  if (thumbnail == null || thumbnail.isEmpty || imageError != null) {
    return null;
  }
  return thumbnail;
}

bool canOpenLiveStageShareFullScreen({
  required String stageShareIdentity,
  required String localUserId,
}) {
  return stageShareIdentity != localUserId;
}

String liveScreenShareStageLabel(String displayName) {
  return displayName.isEmpty ? '屏幕共享' : '$displayName 的屏幕';
}

List<UserSummary> liveScreenShareViewers(
  LiveState? live,
  String broadcasterUserId,
) {
  final participant = liveParticipantByUserId(live, broadcasterUserId);
  if (participant == null || !participant.screenSharing) {
    return const <UserSummary>[];
  }
  return participant.screenViewers
      .where((viewer) => viewer.id != broadcasterUserId)
      .toList(growable: false);
}

LiveMicControlState liveMicControlState({
  required bool micMuted,
  required bool voiceBlocked,
}) {
  if (voiceBlocked) {
    return const LiveMicControlState(
      mutedForDisplay: true,
      active: false,
      enabled: false,
    );
  }
  return LiveMicControlState(
    mutedForDisplay: micMuted,
    active: !micMuted,
    enabled: true,
  );
}

String liveForciblyRemovedNotice() {
  return '你已被移出语音';
}

String liveVoiceConnectFailureMessage(Object error) {
  return '无法连接语音频道';
}

String liveCameraOpenFailureMessage(Object error) {
  return '无法打开摄像头';
}

String liveScreenShareFailureMessage(Object error) {
  return '无法共享屏幕';
}

String _roomDetailDisplayName(RoomDetail room) {
  return _nonEmpty(room.remarkName) ?? room.name;
}

String _roomDetailAvatarLabel(RoomDetail room) {
  return _nonEmpty(room.name) ?? room.rid;
}

String _roomCardAvatarLabel(RoomCard room) {
  return _nonEmpty(room.name) ?? room.rid;
}

String? _nonEmpty(String? value) {
  final trimmed = value?.trim();
  if (trimmed == null || trimmed.isEmpty) return null;
  return trimmed;
}
