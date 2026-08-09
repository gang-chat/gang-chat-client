import 'package:flutter_test/flutter_test.dart';

import 'package:client/src/app/live_display.dart';
import 'package:client/src/protocol/models.dart';

void main() {
  test('liveParticipantByUserId returns matching participant', () {
    final live = _live(['alice', 'bob']);

    expect(liveParticipantByUserId(live, 'bob')?.user.displayName, 'User bob');
    expect(liveParticipantByUserId(live, 'missing'), isNull);
    expect(liveParticipantByUserId(null, 'bob'), isNull);
  });

  test('liveParticipantDisplayName uses fallback for missing live user', () {
    final live = _live(['alice']);

    expect(liveParticipantDisplayName(live, 'alice'), 'User alice');
    expect(liveParticipantDisplayName(live, 'missing'), '');
    expect(
      liveParticipantDisplayName(null, 'missing', fallback: 'Unknown'),
      'Unknown',
    );
  });

  test('liveParticipantDisplayName prefers room display name', () {
    final live = _liveWithParticipants([
      _participant('alice', roomDisplayName: 'Room Alice'),
    ]);

    expect(liveParticipantDisplayName(live, 'alice'), 'Room Alice');
  });

  test('visible live participants hide local user until join is ready', () {
    final live = _live(['alice', 'bob']);

    expect(
      visibleLiveParticipantsForStage(
        live.participants,
        currentUserId: 'alice',
        localParticipantReady: false,
      ).map((participant) => participant.user.id),
      ['bob'],
    );
    expect(
      visibleLiveParticipantsForStage(
        live.participants,
        currentUserId: 'alice',
        localParticipantReady: true,
      ).map((participant) => participant.user.id),
      ['alice', 'bob'],
    );
    expect(
      visibleLiveParticipantsForStage(
        live.participants,
        currentUserId: 'missing',
        localParticipantReady: false,
      ).map((participant) => participant.user.id),
      ['alice', 'bob'],
    );
  });

  test('visible live participants hide joining users', () {
    final live = _liveWithParticipants([
      _participant('alice', connectionState: 'joining'),
      _participant('bob', connectionState: 'online'),
    ]);

    expect(
      visibleLiveParticipantsForStage(
        live.participants,
        currentUserId: 'missing',
        localParticipantReady: true,
      ).map((participant) => participant.user.id),
      ['bob'],
    );
  });

  test('visible live participants show joining users already in LiveKit', () {
    final live = _liveWithParticipants([
      _participant('alice', connectionState: 'joining'),
      _participant('bob', connectionState: 'online'),
    ]);

    expect(
      visibleLiveParticipantsForStage(
        live.participants,
        currentUserId: 'missing',
        localParticipantReady: true,
        connectedParticipantIds: {'alice'},
      ).map((participant) => participant.user.id),
      ['alice', 'bob'],
    );
  });

  test('visible live participants keep reconnecting users in the roster', () {
    final live = _liveWithParticipants([
      _participant('alice', connectionState: 'reconnecting'),
      _participant('bob', connectionState: 'online'),
    ]);

    expect(
      visibleLiveParticipantsForStage(
        live.participants,
        currentUserId: 'missing',
        localParticipantReady: true,
      ).map((participant) => participant.user.id),
      ['alice', 'bob'],
    );
  });

  test('authoritative presence keeps reconnecting and excludes joining', () {
    final live = _liveWithParticipants([
      _participant('joining', connectionState: 'joining'),
      _participant('online', connectionState: 'online'),
      _participant('reconnecting', connectionState: 'reconnecting'),
      _participant('left', connectionState: 'left'),
    ]);

    expect(authoritativeLivePresenceUserIds(live), {'online', 'reconnecting'});
  });

  test('authoritative presence changes ignore a reconnect transition', () {
    final reconnecting = authoritativeLivePresenceChanges(
      before: {'alice', 'bob'},
      after: {'alice', 'bob'},
    );
    final reconciled = authoritativeLivePresenceChanges(
      before: {'alice', 'bob'},
      after: {'alice'},
    );

    expect(reconnecting.joined, isEmpty);
    expect(reconnecting.left, isEmpty);
    expect(reconciled.joined, isEmpty);
    expect(reconciled.left, {'bob'});
  });

  test(
    'visible live participants show connected muted users after reconnect',
    () {
      final live = _liveWithParticipants([
        _participant('alice', connectionState: 'joining', micMuted: true),
        _participant('bob', connectionState: 'online'),
      ]);

      expect(
        visibleLiveParticipantsForStage(
          live.participants,
          currentUserId: 'missing',
          localParticipantReady: true,
          connectedParticipantIds: {'alice'},
        ).map((participant) => participant.user.id),
        ['alice', 'bob'],
      );
    },
  );

  test(
    'live state detects connected LiveKit users missing from server state',
    () {
      final live = _live(['alice']);

      expect(
        liveStateMissingConnectedParticipants(
          live,
          connectedParticipantIds: {'alice'},
        ),
        isFalse,
      );
      expect(
        liveStateMissingConnectedParticipants(
          live,
          connectedParticipantIds: {'alice', 'bob'},
        ),
        isTrue,
      );
    },
  );

  test(
    'pickLiveStageShare prefers remote screen share then local fallback',
    () {
      final localShare = _Track('local', isScreenShare: true, isLocal: true);
      final remoteShare = _Track('remote', isScreenShare: true, isLocal: false);
      final camera = _Track('camera', isScreenShare: false, isLocal: false);

      expect(
        pickLiveStageShare(
          [camera, localShare, remoteShare],
          isScreenShare: (track) => track.isScreenShare,
          isLocal: (track) => track.isLocal,
        ),
        same(remoteShare),
      );
      expect(
        pickLiveStageShare(
          [camera, localShare],
          isScreenShare: (track) => track.isScreenShare,
          isLocal: (track) => track.isLocal,
        ),
        same(localShare),
      );
      expect(
        pickLiveStageShare(
          [camera],
          isScreenShare: (track) => track.isScreenShare,
          isLocal: (track) => track.isLocal,
        ),
        isNull,
      );
    },
  );

  test('stage share label and fullscreen guard are UI independent', () {
    expect(liveScreenShareStageLabel('Alice'), 'Alice 的屏幕');
    expect(liveScreenShareStageLabel(''), '屏幕共享');
    expect(
      canOpenLiveStageShareFullScreen(
        stageShareIdentity: 'alice',
        localUserId: 'bob',
      ),
      isTrue,
    );
    expect(
      canOpenLiveStageShareFullScreen(
        stageShareIdentity: 'alice',
        localUserId: 'alice',
      ),
      isFalse,
    );
  });

  test(
    'screen share viewers require an active share and exclude broadcaster',
    () {
      final broadcaster = _participant(
        'alice',
        screenSharing: true,
        screenViewers: [_user('alice'), _user('bob')],
      );
      final live = _liveWithParticipants([broadcaster]);

      expect(liveScreenShareViewers(live, 'alice').map((user) => user.id), [
        'bob',
      ]);
      expect(liveScreenShareViewers(live, 'missing'), isEmpty);
      expect(
        liveScreenShareViewers(
          _liveWithParticipants([
            _participant('alice', screenViewers: [_user('bob')]),
          ]),
          'alice',
        ),
        isEmpty,
      );
    },
  );

  test('live notices and failure messages stay outside UI', () {
    expect(liveForciblyRemovedNotice(), '你已被移出语音');
    expect(liveVoiceConnectFailureMessage('network'), '无法连接语音频道');
    expect(liveCameraOpenFailureMessage('denied'), '无法打开摄像头');
    expect(liveScreenShareFailureMessage('denied'), '无法共享屏幕');
  });

  test('liveScreenShareByIdentity resolves active fullscreen share', () {
    final screenShare = _Track('alice', isScreenShare: true, isLocal: false);
    final camera = _Track('alice', isScreenShare: false, isLocal: false);

    expect(
      liveScreenShareByIdentity(
        [camera, screenShare],
        identity: 'alice',
        trackIdentity: (track) => track.id,
        isScreenShare: (track) => track.isScreenShare,
      ),
      same(screenShare),
    );
    expect(
      liveScreenShareByIdentity(
        [screenShare],
        identity: null,
        trackIdentity: (track) => track.id,
        isScreenShare: (track) => track.isScreenShare,
      ),
      isNull,
    );
    expect(
      liveScreenShareByIdentity(
        [camera],
        identity: 'alice',
        trackIdentity: (track) => track.id,
        isScreenShare: (track) => track.isScreenShare,
      ),
      isNull,
    );
  });

  test('shouldExitMissingFullScreenShare exits only missing tracked share', () {
    final share = Object();

    expect(
      shouldExitMissingFullScreenShare(
        fullScreenShareIdentity: 'alice',
        fullScreenShare: null,
      ),
      isTrue,
    );
    expect(
      shouldExitMissingFullScreenShare(
        fullScreenShareIdentity: 'alice',
        fullScreenShare: share,
      ),
      isFalse,
    );
    expect(
      shouldExitMissingFullScreenShare(
        fullScreenShareIdentity: null,
        fullScreenShare: null,
      ),
      isFalse,
    );
  });

  test('ended local screen share patch gate follows joined selected room', () {
    expect(
      shouldPatchEndedLocalScreenShare(
        localScreenSharing: true,
        sessionScreenSharing: false,
        joinedLiveRoomId: 'room_1',
        selectedRoomId: 'room_1',
      ),
      isTrue,
    );
    expect(
      shouldPatchEndedLocalScreenShare(
        localScreenSharing: false,
        sessionScreenSharing: false,
        joinedLiveRoomId: 'room_1',
        selectedRoomId: 'room_1',
      ),
      isFalse,
    );
    expect(
      shouldPatchEndedLocalScreenShare(
        localScreenSharing: true,
        sessionScreenSharing: true,
        joinedLiveRoomId: 'room_1',
        selectedRoomId: 'room_1',
      ),
      isFalse,
    );
    expect(
      shouldPatchEndedLocalScreenShare(
        localScreenSharing: true,
        sessionScreenSharing: false,
        joinedLiveRoomId: 'room_2',
        selectedRoomId: 'room_1',
      ),
      isFalse,
    );
    expect(
      shouldPatchEndedLocalScreenShare(
        localScreenSharing: true,
        sessionScreenSharing: false,
        joinedLiveRoomId: null,
        selectedRoomId: 'room_1',
      ),
      isFalse,
    );
  });

  test('joined live room summary prefers selected detail then room list', () {
    final selected = _roomDetail(
      id: 'room_1',
      name: 'Alpha',
      remarkName: 'Project Alpha',
      defaultAvatarKey: 'green-2',
    );
    final rooms = [
      _roomCard(id: 'room_1', name: 'Old Alpha', defaultAvatarKey: 'blue-3'),
      _roomCard(
        id: 'room_2',
        name: 'Beta',
        remarkName: 'Room Beta',
        defaultAvatarKey: 'amber-2',
      ),
    ];

    final fromSelected = joinedLiveRoomSummary(
      joinedLiveRoomId: 'room_1',
      selectedRoom: selected,
      rooms: rooms,
    );

    expect(fromSelected?.roomId, 'room_1');
    expect(fromSelected?.displayName, 'Project Alpha');
    expect(fromSelected?.avatarLabel, 'Alpha');
    expect(fromSelected?.defaultAvatarKey, 'green-2');

    final fromList = joinedLiveRoomSummary(
      joinedLiveRoomId: 'room_2',
      selectedRoom: selected,
      rooms: rooms,
    );

    expect(fromList?.roomId, 'room_2');
    expect(fromList?.displayName, 'Room Beta');
    expect(fromList?.avatarLabel, 'Beta');
    expect(fromList?.defaultAvatarKey, 'amber-2');
  });

  test('joined live room summary hides when room cannot be resolved', () {
    expect(
      joinedLiveRoomSummary(
        joinedLiveRoomId: null,
        selectedRoom: null,
        rooms: const [],
      ),
      isNull,
    );
    expect(
      joinedLiveRoomSummary(
        joinedLiveRoomId: 'missing',
        selectedRoom: null,
        rooms: [_roomCard(id: 'room_1', name: 'Alpha')],
      ),
      isNull,
    );
  });

  test(
    'screen source selection keeps existing source or falls back to first',
    () {
      final screen = _Track('screen', isScreenShare: true, isLocal: true);
      final window = _Track('window', isScreenShare: true, isLocal: true);

      expect(
        reconcileLiveScreenSourceSelection(
          [screen, window],
          selectedId: 'window',
          sourceId: (source) => source.id,
        ),
        'window',
      );
      expect(
        reconcileLiveScreenSourceSelection(
          [screen, window],
          selectedId: 'missing',
          sourceId: (source) => source.id,
        ),
        'screen',
      );
      expect(
        reconcileLiveScreenSourceSelection(
          <_Track>[],
          selectedId: 'missing',
          sourceId: (source) => source.id,
        ),
        isNull,
      );
    },
  );

  test('screen source lookup and confirmation require a selected id', () {
    final screen = _Track('screen', isScreenShare: true, isLocal: true);

    expect(
      liveScreenSourceById(
        [screen],
        selectedId: 'screen',
        sourceId: (source) => source.id,
      ),
      same(screen),
    );
    expect(
      liveScreenSourceById(
        [screen],
        selectedId: 'missing',
        sourceId: (source) => source.id,
      ),
      isNull,
    );
    expect(
      liveScreenSourceById<_Track>(
        null,
        selectedId: 'screen',
        sourceId: (source) => source.id,
      ),
      isNull,
    );
    expect(canConfirmLiveScreenSourceSelection('screen'), isTrue);
    expect(canConfirmLiveScreenSourceSelection(null), isFalse);
  });

  test('screen source picker state tracks loading selection and failures', () {
    final screen = _Track('screen', isScreenShare: true, isLocal: true);
    final window = _Track('window', isScreenShare: true, isLocal: true);
    const initial = LiveScreenSourcePickerState<_Track>();

    expect(canLoadLiveScreenSources(initial), isTrue);

    final started = liveScreenSourceLoadStarted(initial);

    expect(started.loading, isTrue);
    expect(started.error, isNull);
    expect(canLoadLiveScreenSources(started), isFalse);

    final loaded = liveScreenSourceLoadSucceeded(
      state: started,
      sources: [screen, window],
      sourceId: (source) => source.id,
    );

    expect(loaded.loading, isFalse);
    expect(loaded.sources, [screen, window]);
    expect(loaded.selectedId, 'screen');
    expect(loaded.error, isNull);

    final selected = liveScreenSourceSelectedChanged(loaded, 'window');

    expect(selected.selectedId, 'window');

    final reloaded = liveScreenSourceLoadSucceeded(
      state: selected,
      sources: [screen, window],
      sourceId: (source) => source.id,
    );

    expect(reloaded.selectedId, 'window');

    final failed = liveScreenSourceLoadFailed(
      state: liveScreenSourceLoadStarted(reloaded),
      failure: StateError('sources failed'),
    );

    expect(failed.loading, isFalse);
    expect(failed.sources, [screen, window]);
    expect(failed.selectedId, 'window');
    expect(failed.error, '加载语音频道失败');
  });

  test(
    'screen source list helpers expose body selected and thumbnail states',
    () {
      final screen = _Track('screen', isScreenShare: true, isLocal: true);

      expect(
        liveScreenSourceListBodyState<_Track>(null),
        LiveScreenSourceListBodyState.loading,
      );
      expect(
        liveScreenSourceListBodyState(<_Track>[]),
        LiveScreenSourceListBodyState.empty,
      );
      expect(
        liveScreenSourceListBodyState([screen]),
        LiveScreenSourceListBodyState.results,
      );

      expect(
        liveScreenSourceSelected(
          screen,
          selectedId: 'screen',
          sourceId: (source) => source.id,
        ),
        isTrue,
      );
      expect(
        liveScreenSourceSelected(
          screen,
          selectedId: 'window',
          sourceId: (source) => source.id,
        ),
        isFalse,
      );

      expect(
        visibleLiveScreenSourceThumbnail(
          thumbnail: const [1, 2],
          imageError: null,
        ),
        [1, 2],
      );
      expect(
        visibleLiveScreenSourceThumbnail(
          thumbnail: const <int>[],
          imageError: null,
        ),
        isNull,
      );
      expect(
        visibleLiveScreenSourceThumbnail(
          thumbnail: const [1],
          imageError: Object(),
        ),
        isNull,
      );
    },
  );

  test(
    'liveParticipantTileState highlights speaking or broadcasting users',
    () {
      final idle = liveParticipantTileState(
        _participant('alice'),
        speaking: false,
      );
      expect(idle.broadcasting, isFalse);
      expect(idle.highlighted, isFalse);
      expect(idle.micMutedForDisplay, isFalse);
      expect(idle.micActive, isFalse);

      final speaking = liveParticipantTileState(
        _participant('alice'),
        speaking: true,
      );
      expect(speaking.highlighted, isTrue);
      expect(speaking.micActive, isTrue);

      final broadcasting = liveParticipantTileState(
        _participant('alice', cameraOn: true),
        speaking: false,
      );
      expect(broadcasting.broadcasting, isTrue);
      expect(broadcasting.highlighted, isTrue);
      expect(broadcasting.micActive, isFalse);
    },
  );

  test(
    'liveParticipantTileState treats voice-blocked users as force-muted',
    () {
      final state = liveParticipantTileState(
        _participant('alice', voiceBlocked: true),
        speaking: true,
      );

      expect(state.micMutedForDisplay, isTrue);
      expect(state.micActive, isFalse);
      expect(state.highlighted, isTrue);
    },
  );

  test('liveParticipantTileState keeps logical mute authoritative', () {
    final state = liveParticipantTileState(
      _participant('alice', micMuted: true),
      speaking: false,
      liveKitMicMuted: false,
    );

    expect(state.micMutedForDisplay, isTrue);
    expect(state.micActive, isFalse);

    final trackMuted = liveParticipantTileState(
      _participant('alice'),
      speaking: true,
      liveKitMicMuted: true,
    );

    expect(trackMuted.micMutedForDisplay, isTrue);
    expect(trackMuted.micActive, isFalse);

    final moderated = liveParticipantTileState(
      _participant('alice', micMuted: true, voiceBlocked: true),
      speaking: true,
      liveKitMicMuted: false,
    );

    expect(moderated.micMutedForDisplay, isTrue);
    expect(moderated.micActive, isFalse);
  });

  test(
    'liveParticipantTileState ignores provisional LiveKit mute while joining',
    () {
      final joining = liveParticipantTileState(
        _participant('alice', connectionState: 'joining'),
        speaking: false,
        liveKitMicMuted: true,
      );
      final online = liveParticipantTileState(
        _participant('alice', connectionState: 'online'),
        speaking: false,
        liveKitMicMuted: true,
      );
      final logicallyMuted = liveParticipantTileState(
        _participant('alice', connectionState: 'joining', micMuted: true),
        speaking: false,
        liveKitMicMuted: true,
      );

      expect(joining.micMutedForDisplay, isFalse);
      expect(online.micMutedForDisplay, isTrue);
      expect(logicallyMuted.micMutedForDisplay, isTrue);
    },
  );

  test('live control display helpers describe toggled states', () {
    final mutedMic = liveMicControlState(micMuted: true, voiceBlocked: false);
    expect(mutedMic.mutedForDisplay, isTrue);
    expect(mutedMic.active, isFalse);
    expect(mutedMic.enabled, isTrue);

    final liveMic = liveMicControlState(micMuted: false, voiceBlocked: false);
    expect(liveMic.mutedForDisplay, isFalse);
    expect(liveMic.active, isTrue);
    expect(liveMic.enabled, isTrue);

    final blockedMic = liveMicControlState(micMuted: false, voiceBlocked: true);
    expect(blockedMic.mutedForDisplay, isTrue);
    expect(blockedMic.active, isFalse);
    expect(blockedMic.enabled, isFalse);
  });
}

LiveState _live(List<String> userIds) {
  return _liveWithParticipants([
    for (final id in userIds) _participant(id, micMuted: true),
  ]);
}

LiveState _liveWithParticipants(List<LiveParticipant> participants) {
  return LiveState(
    roomId: 'room_1',
    participantCount: participants.length,
    participants: participants,
    updatedAt: DateTime.utc(2026, 6, 5),
  );
}

LiveParticipant _participant(
  String id, {
  bool micMuted = false,
  bool voiceBlocked = false,
  bool cameraOn = false,
  bool screenSharing = false,
  List<UserSummary> screenViewers = const <UserSummary>[],
  String connectionState = 'connected',
  String? roomDisplayName,
}) {
  return LiveParticipant(
    liveSessionId: 'live_$id',
    user: UserSummary(
      id: id,
      username: 'user_$id',
      displayName: 'User $id',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
      roomDisplayName: roomDisplayName,
    ),
    joinedAt: DateTime.utc(2026, 6, 5),
    micMuted: micMuted,
    headphonesMuted: false,
    voiceBlocked: voiceBlocked,
    cameraOn: cameraOn,
    screenSharing: screenSharing,
    screenViewers: screenViewers,
    connectionState: connectionState,
  );
}

UserSummary _user(String id) {
  return UserSummary(
    id: id,
    username: 'user_$id',
    displayName: 'User $id',
    avatarUrl: null,
    defaultAvatarKey: 'blue-3',
  );
}

class _Track {
  const _Track(this.id, {required this.isScreenShare, required this.isLocal});

  final String id;
  final bool isScreenShare;
  final bool isLocal;
}

RoomCard _roomCard({
  required String id,
  required String name,
  String? remarkName,
  String defaultAvatarKey = 'blue-3',
}) {
  return RoomCard(
    id: id,
    name: name,
    remarkName: remarkName,
    avatarUrl: null,
    defaultAvatarKey: defaultAvatarKey,
    memberCount: 2,
    liveParticipantCount: 0,
    liveAvatarPreview: const [],
    lastMessage: null,
    unreadCount: 0,
    updatedAt: DateTime.utc(2026, 6, 5),
  );
}

RoomDetail _roomDetail({
  required String id,
  required String name,
  String? remarkName,
  String defaultAvatarKey = 'blue-3',
}) {
  return RoomDetail(
    id: id,
    name: name,
    remarkName: remarkName,
    avatarUrl: null,
    defaultAvatarKey: defaultAvatarKey,
    memberCount: 2,
    myMembership: RoomMembership(
      joinedAt: DateTime.utc(2026, 6, 5),
      role: 'member',
    ),
    live: _live(const []),
    createdAt: DateTime.utc(2026, 6, 5),
    updatedAt: DateTime.utc(2026, 6, 5),
  );
}
