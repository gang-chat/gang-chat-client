import 'dart:async';

import '../protocol/api_client.dart';
import '../protocol/models.dart';
import 'room_live_state.dart' as room_live_state;

class LiveDeparturePatch {
  const LiveDeparturePatch({required this.live, required this.rooms});

  final LiveState? live;
  final List<RoomCard> rooms;
}

class LiveLocalDeparturePatch {
  const LiveLocalDeparturePatch({
    required this.live,
    required this.rooms,
    required this.joinedLiveRoomId,
    required this.joiningLive,
    required this.cameraOn,
    required this.screenSharing,
    required this.voiceBlocked,
  });

  final LiveState? live;
  final List<RoomCard> rooms;
  final String? joinedLiveRoomId;
  final bool joiningLive;
  final bool cameraOn;
  final bool screenSharing;
  final bool voiceBlocked;
}

class LivePublishPermissionPatch {
  const LivePublishPermissionPatch({
    required this.micMuted,
    required this.voiceBlocked,
  });

  final bool micMuted;
  final bool voiceBlocked;
}

class LiveOutputMutePatch {
  const LiveOutputMutePatch({required this.headphonesMuted});

  final bool headphonesMuted;
}

enum LiveModerationAction {
  kick('kick'),
  muteMic('mute_mic'),
  blockVoice('block_voice'),
  restoreVoice('restore_voice'),
  restoreHeadphones('restore_headphones');

  const LiveModerationAction(this.apiValue);

  final String apiValue;
}

class LiveJoinResultPatch {
  const LiveJoinResultPatch({
    required this.micMuted,
    required this.headphonesMuted,
    required this.cameraOn,
    required this.screenSharing,
    required this.voiceBlocked,
    required this.live,
    required this.rooms,
  });

  final bool micMuted;
  final bool headphonesMuted;
  final bool cameraOn;
  final bool screenSharing;
  final bool voiceBlocked;
  final LiveState live;
  final List<RoomCard> rooms;
}

class LiveStateUpdatePatch {
  const LiveStateUpdatePatch({
    required this.micMuted,
    required this.headphonesMuted,
    required this.cameraOn,
    required this.screenSharing,
    required this.voiceBlocked,
    required this.live,
  });

  final bool micMuted;
  final bool headphonesMuted;
  final bool cameraOn;
  final bool screenSharing;
  final bool voiceBlocked;
  final LiveState? live;
}

/// Keeps mutations of the current user's live state in request order.
///
/// The live endpoint updates a single participant row. Sending optimistic UI
/// changes concurrently allows an older request to arrive last and overwrite a
/// newer microphone, headphones, camera, or screen-share choice. Failed
/// requests are delivered to their caller without breaking later requests.
class LiveStateRequestQueue {
  Future<void> _tail = Future<void>.value();
  int _pendingCount = 0;

  bool get isIdle => _pendingCount == 0;

  Future<T> run<T>(Future<T> Function() request) {
    final completer = Completer<T>();
    _pendingCount += 1;
    _tail = _tail.then((_) async {
      try {
        final result = await request();
        _pendingCount -= 1;
        completer.complete(result);
      } catch (error, stackTrace) {
        _pendingCount -= 1;
        completer.completeError(error, stackTrace);
      }
    });
    return completer.future;
  }
}

class LiveJoinStatePatch {
  const LiveJoinStatePatch({
    required this.joinedLiveRoomId,
    required this.joiningLive,
    required this.livePanelOpen,
    required this.error,
  });

  final String? joinedLiveRoomId;
  final bool joiningLive;
  final bool livePanelOpen;
  final String? error;
}

class LiveJoinPreviousRoomDisconnectedPatch {
  const LiveJoinPreviousRoomDisconnectedPatch({
    required this.live,
    required this.rooms,
    required this.joinedLiveRoomId,
    required this.joiningLive,
    required this.livePanelOpen,
    required this.error,
  });

  final LiveState? live;
  final List<RoomCard> rooms;
  final String? joinedLiveRoomId;
  final bool joiningLive;
  final bool livePanelOpen;
  final String? error;
}

String? joinedLiveRoomToDisconnectBeforeJoin({
  required String? joinedLiveRoomId,
  required String targetRoomId,
}) {
  if (joinedLiveRoomId == null || joinedLiveRoomId == targetRoomId) {
    return null;
  }
  return joinedLiveRoomId;
}

bool canPatchSelectedLiveState({
  required String? joinedLiveRoomId,
  required String? selectedRoomId,
}) {
  return joinedLiveRoomId != null && joinedLiveRoomId == selectedRoomId;
}

bool canApplyPickedScreenShareSource({
  required String pickedForRoomId,
  required String? joinedLiveRoomId,
  required String? selectedRoomId,
}) {
  return joinedLiveRoomId == pickedForRoomId &&
      selectedRoomId == pickedForRoomId;
}

bool isBenignGoneLiveStatePatch(Object error) {
  return error is ApiException && error.statusCode == 409;
}

bool shouldSyncLiveKitMicAfterServerPatch({
  required bool? requestedMicMuted,
  required bool serverMicMuted,
}) {
  return requestedMicMuted != null && serverMicMuted != requestedMicMuted;
}

LiveParticipant liveParticipantWithExclusiveMedia(LiveParticipant participant) {
  if (!participant.cameraOn || !participant.screenSharing) return participant;
  return participant.copyWith(cameraOn: false);
}

LiveOutputMutePatch liveOutputMuteToggled({required bool headphonesMuted}) {
  return LiveOutputMutePatch(headphonesMuted: !headphonesMuted);
}

class LiveController {
  LiveController({required this.api});

  final GangApi api;

  Future<LiveJoinResult> joinLive({
    required String roomId,
    required String source,
    required bool micMuted,
    required bool headphonesMuted,
  }) {
    return api.joinLive(
      roomId: roomId,
      clientLiveSessionId: newClientId('clive'),
      source: source,
      micMuted: micMuted,
      headphonesMuted: headphonesMuted,
    );
  }

  Future<LiveParticipant> updateMyState({
    required String roomId,
    bool? micMuted,
    bool? headphonesMuted,
    bool? cameraOn,
    bool? cameraMirrored,
    bool? screenSharing,
    String? connectionState,
  }) {
    return api.updateMyLiveState(
      roomId: roomId,
      micMuted: micMuted,
      headphonesMuted: headphonesMuted,
      cameraOn: cameraOn,
      cameraMirrored: cameraMirrored,
      screenSharing: screenSharing,
      connectionState: connectionState,
    );
  }

  Future<LiveParticipant> leaveLive({required String roomId}) {
    return api.updateMyLiveState(roomId: roomId, connectionState: 'left');
  }

  Future<void> updateMyLiveScreenView({
    required String roomId,
    String? broadcasterUserId,
  }) {
    return api.updateMyLiveScreenView(
      roomId: roomId,
      broadcasterUserId: broadcasterUserId,
    );
  }

  Future<void> kickParticipant({
    required String roomId,
    required String userId,
  }) {
    return moderateParticipant(
      roomId: roomId,
      userId: userId,
      action: LiveModerationAction.kick,
    );
  }

  Future<void> moderateParticipant({
    required String roomId,
    required String userId,
    required LiveModerationAction action,
  }) {
    return api.moderateLiveParticipant(
      roomId: roomId,
      userId: userId,
      action: action.apiValue,
    );
  }

  LiveJoinStatePatch patchJoinStarted({required String? joinedLiveRoomId}) {
    return LiveJoinStatePatch(
      joinedLiveRoomId: joinedLiveRoomId,
      joiningLive: true,
      livePanelOpen: true,
      error: null,
    );
  }

  LiveJoinPreviousRoomDisconnectedPatch patchJoinPreviousRoomDisconnected({
    required LiveState? live,
    required List<RoomCard> rooms,
    required String previousRoomId,
    required String userId,
    required bool livePanelOpen,
    required String? error,
  }) {
    final departure = removeUserFromLive(
      live: live,
      rooms: rooms,
      roomId: previousRoomId,
      userId: userId,
    );
    return LiveJoinPreviousRoomDisconnectedPatch(
      live: departure.live,
      rooms: departure.rooms,
      joinedLiveRoomId: null,
      joiningLive: true,
      livePanelOpen: livePanelOpen,
      error: error,
    );
  }

  LiveJoinStatePatch patchJoinConnected({
    required String roomId,
    required bool livePanelOpen,
    required String? error,
  }) {
    return LiveJoinStatePatch(
      joinedLiveRoomId: roomId,
      joiningLive: true,
      livePanelOpen: livePanelOpen,
      error: error,
    );
  }

  LiveJoinStatePatch patchJoinFinished({
    required String? joinedLiveRoomId,
    required bool livePanelOpen,
    required String? error,
  }) {
    return LiveJoinStatePatch(
      joinedLiveRoomId: joinedLiveRoomId,
      joiningLive: false,
      livePanelOpen: livePanelOpen,
      error: error,
    );
  }

  LiveLocalDeparturePatch patchLocalDeparture({
    required LiveState? live,
    required List<RoomCard> rooms,
    required String? joinedLiveRoomId,
    required String userId,
    required bool joiningLive,
  }) {
    final roomId = joinedLiveRoomId;
    final departure = roomId == null
        ? LiveDeparturePatch(live: live, rooms: rooms)
        : removeUserFromLive(
            live: live,
            rooms: rooms,
            roomId: roomId,
            userId: userId,
          );
    return LiveLocalDeparturePatch(
      live: departure.live,
      rooms: departure.rooms,
      joinedLiveRoomId: null,
      joiningLive: joiningLive,
      cameraOn: false,
      screenSharing: false,
      voiceBlocked: false,
    );
  }

  LivePublishPermissionPatch patchPublishPermission({
    required bool canPublish,
    required bool micMuted,
  }) {
    return LivePublishPermissionPatch(
      micMuted: canPublish ? micMuted : true,
      voiceBlocked: !canPublish,
    );
  }

  LiveJoinResultPatch patchJoinResult({
    required List<RoomCard> rooms,
    required LiveJoinResult result,
    bool showMicUnmutedWhenAllowed = false,
  }) {
    final participant = liveParticipantWithExclusiveMedia(
      _joinResultParticipantForDisplay(
        result.participant,
        showMicUnmutedWhenAllowed: showMicUnmutedWhenAllowed,
      ),
    );
    final live = _replaceParticipantInLive(result.live, participant);
    return LiveJoinResultPatch(
      micMuted: participant.micMuted,
      headphonesMuted: participant.headphonesMuted,
      cameraOn: participant.cameraOn,
      screenSharing: participant.screenSharing,
      voiceBlocked: participant.voiceBlocked,
      live: live,
      rooms: room_live_state.patchRoomLiveCount(
        rooms: rooms,
        roomId: live.roomId,
        live: live,
      ),
    );
  }

  LiveParticipant _joinResultParticipantForDisplay(
    LiveParticipant participant, {
    required bool showMicUnmutedWhenAllowed,
  }) {
    if (!showMicUnmutedWhenAllowed ||
        participant.voiceBlocked ||
        participant.micBlocked ||
        !participant.micMuted) {
      return participant;
    }
    return participant.copyWith(micMuted: false);
  }

  LiveState _replaceParticipantInLive(
    LiveState live,
    LiveParticipant participant,
  ) {
    var replaced = false;
    var changed = false;
    final participants = live.participants.map((item) {
      if (item.liveSessionId != participant.liveSessionId) return item;
      replaced = true;
      final merged = _mergeLiveParticipant(participant, fallback: item);
      if (identical(item, merged)) return item;
      changed = true;
      return merged;
    }).toList();
    if (!replaced || !changed) return live;
    return LiveState(
      roomId: live.roomId,
      participantCount: live.participantCount,
      participants: participants,
      updatedAt: live.updatedAt,
    );
  }

  LiveStateUpdatePatch patchStateUpdate({
    required LiveState? live,
    required LiveParticipant participant,
  }) {
    final normalized = liveParticipantWithExclusiveMedia(participant);
    return LiveStateUpdatePatch(
      micMuted: normalized.micMuted,
      headphonesMuted: normalized.headphonesMuted,
      cameraOn: normalized.cameraOn,
      screenSharing: normalized.screenSharing,
      voiceBlocked: normalized.voiceBlocked,
      live: mergeParticipant(live, normalized),
    );
  }

  LiveDeparturePatch removeUserFromLive({
    required LiveState? live,
    required List<RoomCard> rooms,
    required String roomId,
    required String userId,
  }) {
    return LiveDeparturePatch(
      live: _removeUserFromLiveState(
        live: live,
        roomId: roomId,
        userId: userId,
      ),
      rooms: _removeUserFromRoomLivePreview(
        rooms: rooms,
        roomId: roomId,
        userId: userId,
      ),
    );
  }

  LiveState? mergeParticipant(LiveState? live, LiveParticipant participant) {
    if (live == null) return null;
    var replaced = false;
    final participants = live.participants.map((item) {
      if (item.liveSessionId != participant.liveSessionId) return item;
      replaced = true;
      return _mergeLiveParticipant(participant, fallback: item);
    }).toList();
    return LiveState(
      roomId: live.roomId,
      participantCount: replaced
          ? live.participantCount
          : live.participantCount + 1,
      participants: replaced
          ? participants
          : [...participants, liveParticipantWithExclusiveMedia(participant)],
      // A participant PATCH response does not carry a room-snapshot revision.
      // Keep the last server revision until the authoritative realtime/refresh
      // snapshot arrives. Stamping this with the client clock can make every
      // later server snapshot look stale after reconnect (especially with
      // clock skew), leaving remote mic/headphones state frozen.
      updatedAt: live.updatedAt,
    );
  }

  LiveParticipant _mergeLiveParticipant(
    LiveParticipant participant, {
    required LiveParticipant fallback,
  }) {
    return liveParticipantWithExclusiveMedia(
      participant.copyWith(user: participant.user.mergeMissing(fallback.user)),
    );
  }

  LiveState? patchModeratedParticipant({
    required LiveState? live,
    required LiveParticipant participant,
    required LiveModerationAction action,
  }) {
    final moderated = switch (action) {
      LiveModerationAction.muteMic => participant.copyWith(
        micMuted: true,
        micBlocked: true,
        voiceBlocked: true,
      ),
      LiveModerationAction.blockVoice => participant.copyWith(
        headphonesMuted: true,
        headphonesBlocked: true,
        headphonesListening: false,
      ),
      LiveModerationAction.restoreVoice => participant.copyWith(
        micMuted: false,
        micBlocked: false,
        voiceBlocked: false,
      ),
      LiveModerationAction.restoreHeadphones => participant.copyWith(
        headphonesMuted: false,
        headphonesBlocked: false,
        headphonesListening: true,
      ),
      LiveModerationAction.kick => participant,
    };
    if (action == LiveModerationAction.kick) return live;
    return mergeParticipant(live, moderated);
  }

  LiveState? _removeUserFromLiveState({
    required LiveState? live,
    required String roomId,
    required String userId,
  }) {
    if (live == null || live.roomId != roomId) return live;
    final remaining = live.participants
        .where((participant) => participant.user.id != userId)
        .toList();
    if (remaining.length == live.participants.length) return live;
    return LiveState(
      roomId: live.roomId,
      participantCount: remaining.length,
      participants: remaining,
      updatedAt: live.updatedAt,
    );
  }

  List<RoomCard> _removeUserFromRoomLivePreview({
    required List<RoomCard> rooms,
    required String roomId,
    required String userId,
  }) {
    final idx = rooms.indexWhere((room) => room.id == roomId);
    if (idx < 0) return rooms;

    final existing = rooms[idx];
    final remainingPreview = existing.liveAvatarPreview
        .where((user) => user.id != userId)
        .toList();
    final nextCount = existing.liveParticipantCount > 0
        ? existing.liveParticipantCount - 1
        : 0;
    if (remainingPreview.length == existing.liveAvatarPreview.length &&
        nextCount == existing.liveParticipantCount) {
      return rooms;
    }
    final next = [...rooms];
    next[idx] = RoomCard(
      id: existing.id,
      name: existing.name,
      rid: existing.rid,
      visibility: existing.visibility,
      remarkName: existing.remarkName,
      description: existing.description,
      notificationPolicy: existing.notificationPolicy,
      isPinned: existing.isPinned,
      avatarUrl: existing.avatarUrl,
      defaultAvatarKey: existing.defaultAvatarKey,
      memberCount: existing.memberCount,
      onlineMemberCount: existing.onlineMemberCount,
      liveParticipantCount: nextCount,
      liveAvatarPreview: remainingPreview,
      lastMessage: existing.lastMessage,
      unreadCount: existing.unreadCount,
      hasUnreadCount: existing.hasUnreadCount,
      unreadMentionCount: existing.unreadMentionCount,
      hasUnreadMentionCount: existing.hasUnreadMentionCount,
      hasPendingJoinRequests: existing.hasPendingJoinRequests,
      aiVoiceAnnouncementsEnabled: existing.aiVoiceAnnouncementsEnabled,
      updatedAt: existing.updatedAt,
    );
    return next;
  }
}
