part of 'home_shell.dart';

extension _HomeShellLiveActions on _HomeShellState {
  void _setLiveStageSelection(LiveStageSelection? selection) {
    final roomId = _selectedServerId;
    if (roomId == null) return;
    _setHomeState(() => _liveStageSelections[roomId] = selection);
    _syncWatchedLiveStageSelection(selection);
  }

  void _enterLiveFullScreen(LiveVideoTrack track) {
    _setHomeState(() => _fullScreenLiveTrack = track);
    if (!track.isLocal) {
      if (track.isScreenShare) {
        unawaited(
          _liveSessionController.setWatchedScreenShareIdentity(track.identity),
        );
      } else {
        unawaited(
          _liveSessionController.setWatchedCameraIdentity(track.identity),
        );
      }
    }
    unawaited(_setSystemFullScreen(true));
  }

  void _exitLiveFullScreen() {
    _setHomeState(() => _fullScreenLiveTrack = null);
    _syncWatchedLiveStageSelection(_liveStageSelections[_selectedServerId]);
    unawaited(_setSystemFullScreen(false));
  }

  void _syncWatchedLiveStageSelection(LiveStageSelection? selection) {
    String? screenShareIdentity;
    String? cameraIdentity;
    if (selection?.mode == LiveStageSelectionMode.track &&
        selection?.identity != _currentUser.id) {
      if (selection?.isScreenShare == true) {
        screenShareIdentity = selection?.identity;
      } else {
        cameraIdentity = selection?.identity;
      }
    }
    unawaited(
      _liveSessionController.setWatchedScreenShareIdentity(screenShareIdentity),
    );
    unawaited(_liveSessionController.setWatchedCameraIdentity(cameraIdentity));

    final roomId = _joinedLiveRoomId;
    if (roomId == null) return;
    final previousSync = _liveScreenViewSyncTail;
    _liveScreenViewSyncTail = () async {
      try {
        await previousSync;
      } catch (_) {}
      if (!mounted || _joinedLiveRoomId != roomId) return;
      bool isStillCurrentTarget() {
        final currentSelection = _liveStageSelections[roomId];
        final currentIdentity =
            currentSelection?.mode == LiveStageSelectionMode.track &&
                currentSelection?.isScreenShare == true &&
                currentSelection?.identity != _currentUser.id
            ? currentSelection?.identity
            : null;
        return currentIdentity == screenShareIdentity;
      }

      for (var attempt = 0; attempt < 3; attempt += 1) {
        if (!mounted ||
            _joinedLiveRoomId != roomId ||
            !isStillCurrentTarget()) {
          return;
        }
        try {
          await _liveController.updateMyLiveScreenView(
            roomId: roomId,
            broadcasterUserId: screenShareIdentity,
          );
          return;
        } catch (error) {
          final shouldRetry =
              screenShareIdentity != null &&
              error is ApiException &&
              error.code == 'screen_share_not_active' &&
              attempt < 2;
          if (!shouldRetry) return;
          await Future<void>.delayed(
            Duration(milliseconds: 120 * (attempt + 1)),
          );
        }
      }
    }();
  }

  Future<void> _setSystemFullScreen(bool fullScreen) async {
    await widget.windowController.setFullScreenMediaPlaybackActive(fullScreen);
    if (!_supportsWindowManagement) return;
    try {
      if (await windowManager.isFullScreen() != fullScreen) {
        await windowManager.setFullScreen(fullScreen);
      }
    } catch (_) {}
  }

  LiveVideoTrack? _resolveFullScreenLiveTrack() {
    final selected = _fullScreenLiveTrack;
    if (selected == null) return null;
    for (final track in _liveSessionController.videoTracks) {
      if (track.identity == selected.identity &&
          track.isScreenShare == selected.isScreenShare) {
        return track;
      }
    }
    return null;
  }

  void _onLiveSessionChanged() {
    if (!mounted) return;
    final joinedLiveRoomId = _joinedLiveRoomId;
    final connectedParticipantIds =
        _liveSessionController.connectedParticipantIdentities;
    if (joinedLiveRoomId != null &&
        joinedLiveRoomId == _selectedServerId &&
        live_display.liveStateMissingConnectedParticipants(
          _live ?? _selectedRoom?.live,
          connectedParticipantIds: connectedParticipantIds,
        )) {
      unawaited(_syncLiveConnectedParticipants(joinedLiveRoomId));
    }
    if (live_display.shouldPatchEndedLocalScreenShare(
      localScreenSharing: _screenSharing,
      sessionScreenSharing: _liveSessionController.isScreenSharing,
      joinedLiveRoomId: _joinedLiveRoomId,
      selectedRoomId: _selectedServerId,
    )) {
      unawaited(_patchLiveState(screenSharing: false));
    }
    if (_fullScreenLiveTrack != null && _resolveFullScreenLiveTrack() == null) {
      _fullScreenLiveTrack = null;
      _syncWatchedLiveStageSelection(_liveStageSelections[_selectedServerId]);
      unawaited(_setSystemFullScreen(false));
    }
    _setHomeState(() {});
  }

  void _onLiveParticipantJoined(String participantIdentity) {
    _playLivePresenceSound(
      LivePresenceSound.joined,
      participantIdentity: participantIdentity,
      announcementAction: LivePresenceAnnouncementAction.joined,
    );
  }

  void _onLiveParticipantLeft(
    String participantIdentity,
    LiveParticipantDepartureKind departureKind,
  ) {
    _playLivePresenceSound(
      LivePresenceSound.left,
      participantIdentity: participantIdentity,
      announcementAction: departureKind == LiveParticipantDepartureKind.removed
          ? LivePresenceAnnouncementAction.removed
          : LivePresenceAnnouncementAction.left,
    );
  }

  void _playLivePresenceSound(
    LivePresenceSound sound, {
    String? participantIdentity,
    LivePresenceAnnouncementAction? announcementAction,
    bool continueAfterRoomExit = false,
  }) {
    final roomId = _joinedLiveRoomId;
    if (roomId == null) return;
    final playback = _livePresenceEventTail.then(
      (_) => _queueLivePresenceAudio(
        sound,
        roomId: roomId,
        participantIdentity: participantIdentity,
        announcementAction: announcementAction,
        continueAfterRoomExit: continueAfterRoomExit,
      ),
    );
    _livePresenceEventTail = playback.catchError((_) {});
  }

  Future<void> _queueLivePresenceAudio(
    LivePresenceSound sound, {
    required String roomId,
    required String? participantIdentity,
    required LivePresenceAnnouncementAction? announcementAction,
    required bool continueAfterRoomExit,
  }) async {
    if (!continueAfterRoomExit && _joinedLiveRoomId != roomId) return;
    final volume = _headphonesMuted
        ? 0.0
        : normalizedAudioVolume(_liveSessionController.outputVolume);
    LivePresenceAnnouncement? announcement;
    // This personal preference gates only synthesized speech. The coordinator
    // below always receives the join/leave cue, even when announcements are off.
    final shouldAnnounce = shouldSpeakLivePresenceAnnouncement(
      enabled: _joinedLivePersonalAiVoiceAnnouncementsEnabled,
      participantIdentity: participantIdentity,
      currentUserIdentity: _currentUser.id,
    );
    if (shouldAnnounce && announcementAction != null) {
      final user = await _resolveJoinedLiveParticipantUser(
        roomId,
        participantIdentity!,
      );
      if (user != null && _joinedLiveRoomId == roomId) {
        announcement = room_display.livePresenceAnnouncementForUser(
          user: user,
          action: announcementAction,
        );
      }
    }
    await _livePresenceAudioCoordinator.play(
      sound,
      volume: volume,
      announcement: announcement,
    );
  }

  Future<UserSummary?> _resolveJoinedLiveParticipantUser(
    String roomId,
    String participantIdentity,
  ) async {
    final cached = _joinedLiveParticipantUsers[participantIdentity];
    if (cached != null) return cached;
    try {
      final live = await _roomsController.getLiveState(roomId);
      if (_joinedLiveRoomId != roomId) return null;
      _cacheJoinedLiveParticipantUsers(live);
      return _joinedLiveParticipantUsers[participantIdentity];
    } catch (_) {
      return null;
    }
  }

  void _cacheJoinedLiveParticipantUsers(LiveState live) {
    if (live.roomId != _joinedLiveRoomId) return;
    for (final participant in live.participants) {
      _joinedLiveParticipantUsers[participant.user.id] = participant.user;
    }
  }

  Future<void> _syncLiveConnectedParticipants(String roomId) async {
    if (_syncingLiveConnectedParticipants) return;
    _syncingLiveConnectedParticipants = true;
    try {
      await _refreshLiveSilently(roomId);
    } finally {
      if (mounted) _syncingLiveConnectedParticipants = false;
    }
  }

  void _onForciblyRemovedFromLive() {
    if (!mounted) return;
    _playLivePresenceSound(LivePresenceSound.left, continueAfterRoomExit: true);
    final patch = _liveController.patchLocalDeparture(
      live: _live,
      rooms: _servers,
      joinedLiveRoomId: _joinedLiveRoomId,
      userId: _currentUser.id,
      joiningLive: false,
    );
    _setHomeState(() => _applyLiveLocalDeparturePatch(patch));
    unawaited(_liveSessionController.disconnect());
  }

  void _onPublishPermissionChanged(bool canPublish) {
    if (!mounted) return;
    final patch = _liveController.patchPublishPermission(
      canPublish: canPublish,
      micMuted: _micMuted,
    );
    _setHomeState(() => _applyLivePublishPermissionPatch(patch));
  }

  void _applyLiveJoinStatePatch(LiveJoinStatePatch patch) {
    _joinedLiveRoomId = patch.joinedLiveRoomId;
    _joiningLive = patch.joiningLive;
    _contentMode = patch.livePanelOpen ? _ContentMode.live : _contentMode;
    _roomError = patch.error;
    if (patch.joinedLiveRoomId == null) {
      _joinedLivePersonalAiVoiceAnnouncementsEnabled = false;
      _joinedLiveParticipantUsers.clear();
    }
  }

  void _applyLiveJoinPreviousRoomDisconnectedPatch(
    LiveJoinPreviousRoomDisconnectedPatch patch,
  ) {
    _live = patch.live;
    _servers = _roomsController
        .patchRoomCardsRefreshed(rooms: patch.rooms)
        .rooms;
    _joinedLiveRoomId = patch.joinedLiveRoomId;
    _joiningLive = patch.joiningLive;
    _contentMode = patch.livePanelOpen ? _ContentMode.live : _contentMode;
    _roomError = patch.error;
  }

  void _applyLiveLocalDeparturePatch(LiveLocalDeparturePatch patch) {
    _live = patch.live;
    _servers = _roomsController
        .patchRoomCardsRefreshed(rooms: patch.rooms)
        .rooms;
    _joinedLiveRoomId = patch.joinedLiveRoomId;
    _joiningLive = patch.joiningLive;
    _cameraOn = patch.cameraOn;
    _screenSharing = patch.screenSharing;
    _voiceBlocked = patch.voiceBlocked;
    if (patch.joinedLiveRoomId == null) {
      _joinedLivePersonalAiVoiceAnnouncementsEnabled = false;
      _joinedLiveParticipantUsers.clear();
    }
  }

  void _applyLivePublishPermissionPatch(LivePublishPermissionPatch patch) {
    _voiceBlocked = patch.voiceBlocked;
    _micMuted = patch.micMuted;
  }

  void _applyLiveJoinResultPatch(LiveJoinResultPatch patch) {
    _micMuted = patch.micMuted;
    _cameraOn = patch.cameraOn;
    _screenSharing = patch.screenSharing;
    _voiceBlocked = patch.voiceBlocked;
    _live = patch.live;
    _servers = _roomsController
        .patchRoomCardsRefreshed(rooms: patch.rooms)
        .rooms;
  }

  LiveJoinResult _withCurrentRoomLiveDisplayNameInJoinResult(
    LiveJoinResult result,
    String roomId,
  ) {
    final participant = _withCurrentRoomLiveDisplayName(
      result.participant,
      roomId,
    );
    final live = _withCurrentRoomLiveDisplayNameInLive(result.live, roomId);
    if (identical(participant, result.participant) &&
        identical(live, result.live)) {
      return result;
    }
    return LiveJoinResult(
      liveKit: result.liveKit,
      participant: participant,
      live: live,
    );
  }

  LiveState _withCurrentRoomLiveDisplayNameInLive(
    LiveState live,
    String roomId,
  ) {
    if (live.roomId != roomId) return live;
    var changed = false;
    final participants = [
      for (final participant in live.participants)
        _withCurrentRoomLiveDisplayName(
          participant,
          roomId,
          changed: () {
            changed = true;
          },
        ),
    ];
    if (!changed) return live;
    return LiveState(
      roomId: live.roomId,
      participantCount: live.participantCount,
      participants: participants,
      updatedAt: live.updatedAt,
    );
  }

  LiveParticipant _withCurrentRoomLiveDisplayName(
    LiveParticipant participant,
    String roomId, {
    void Function()? changed,
  }) {
    if (participant.user.id != _currentUser.id) return participant;
    final displayName = _selectedRoom?.id == roomId
        ? _selectedRoom?.personalProfile.displayName?.trim()
        : null;
    if (displayName == null || displayName.isEmpty) return participant;
    if (participant.user.roomDisplayName?.trim() == displayName) {
      return participant;
    }
    changed?.call();
    return participant.copyWith(
      user: participant.user.copyWith(roomDisplayName: displayName),
    );
  }

  void _setMicMutedLocally(bool muted, {bool syncVolume = true}) {
    if (syncVolume) {
      final volume = muted ? 0.0 : _restoredInputVolume();
      if (muted) {
        _rememberInputVolume(_liveSessionController.inputVolume);
      }
      unawaited(_liveSessionController.setInputVolume(volume));
    }
    unawaited(_liveSessionController.setMicMuted(muted));
    if (_micMuted != muted) {
      _setHomeState(() => _micMuted = muted);
    }
  }

  void _toggleMicMute() {
    final muted = !_micMuted;
    final unmuteHeadphones = !muted && _headphonesMuted;
    _setMicMutedLocally(muted);
    if (unmuteHeadphones) {
      _setHeadphonesMuted(false, patchState: false);
    }
    unawaited(
      _patchLiveState(
        micMuted: muted,
        headphonesMuted: unmuteHeadphones ? false : null,
        syncLiveKitMic: false,
      ),
    );
  }

  void _toggleHeadphonesMute() {
    final patch = liveOutputMuteToggled(headphonesMuted: _headphonesMuted);
    _setHeadphonesMuted(patch.headphonesMuted);
  }

  void _setHeadphonesMuted(
    bool muted, {
    bool syncVolume = true,
    bool patchState = true,
  }) {
    final headphonesChanged = _headphonesMuted != muted;
    final muteMic = muted && !_micMuted;
    if (!headphonesChanged && !muteMic) return;

    _setHomeState(() {
      if (muteMic) {
        _micMuted = true;
      }
      if (headphonesChanged) {
        _headphonesMuted = muted;
      }
    });

    if (muteMic) {
      _rememberInputVolume(_liveSessionController.inputVolume);
      unawaited(_liveSessionController.setInputVolume(0));
      unawaited(_liveSessionController.setMicMuted(true));
    }
    if (headphonesChanged && syncVolume) {
      final volume = muted ? 0.0 : _restoredOutputVolume();
      if (muted) {
        _rememberOutputVolume(_liveSessionController.outputVolume);
      }
      unawaited(_liveSessionController.setOutputVolume(volume));
    }
    if (headphonesChanged) {
      unawaited(_liveSessionController.setOutputMuted(muted));
    }
    // Report the headphone state to the server so other participants see it on
    // the live snapshot. Best-effort: a failure here doesn't undo the local
    // mute, which already took effect on the LiveKit session above.
    if (patchState) {
      final micMutedPatch = muteMic || (!muted && _micMuted) ? true : null;
      unawaited(
        _patchLiveState(
          micMuted: micMutedPatch,
          headphonesMuted: headphonesChanged ? muted : null,
          syncLiveKitMic: micMutedPatch == null,
        ),
      );
    }
  }

  void _changeInputVolume(double volume) {
    final normalized = normalizedAudioVolume(volume);
    if (normalized == 0) {
      _rememberInputVolume(_liveSessionController.inputVolume);
    } else {
      _rememberInputVolume(normalized);
    }
    unawaited(_liveSessionController.setInputVolume(normalized));
    final muted = normalized == 0;
    if (!muted && _voiceBlocked) return;
    final micChanged = muted != _micMuted;
    if (micChanged) {
      _setMicMutedLocally(muted, syncVolume: false);
    }
    final unmuteHeadphones = !muted && _headphonesMuted;
    if (unmuteHeadphones) {
      _setHeadphonesMuted(false, patchState: false);
    }
    if (!micChanged && !unmuteHeadphones) return;
    unawaited(
      _patchLiveState(
        micMuted: micChanged ? muted : null,
        headphonesMuted: unmuteHeadphones ? false : null,
        syncLiveKitMic: false,
      ),
    );
  }

  void _changeOutputVolume(double volume) {
    final normalized = normalizedAudioVolume(volume);
    if (normalized == 0) {
      _rememberOutputVolume(_liveSessionController.outputVolume);
    } else {
      _rememberOutputVolume(normalized);
    }
    unawaited(_liveSessionController.setOutputVolume(normalized));
    _setHeadphonesMuted(normalized == 0, syncVolume: false);
  }

  void _changeScreenShareVolume(double volume) {
    final normalized = normalizedAudioVolume(volume);
    if (normalized > 0) {
      _lastScreenShareVolumeBeforeMute = rememberedAudioVolume(normalized);
    }
    unawaited(_liveSessionController.setScreenShareVolume(normalized));
  }

  void _toggleScreenShareAudioMute() {
    final current = normalizedAudioVolume(
      _liveSessionController.screenShareVolume,
    );
    if (current <= 0) {
      _changeScreenShareVolume(
        restoredAudioVolume(_lastScreenShareVolumeBeforeMute),
      );
      return;
    }
    _lastScreenShareVolumeBeforeMute = rememberedAudioVolume(current);
    _changeScreenShareVolume(0);
  }

  double _participantVoiceVolume(String userId) {
    unawaited(_restoreParticipantVoiceVolume(userId));
    return _liveSessionController.participantVoiceVolume(userId);
  }

  void _changeParticipantVoiceVolume(String userId, double volume) {
    unawaited(_setParticipantVoiceVolume(userId, volume));
  }

  void _toggleParticipantVoiceMute(String userId) {
    unawaited(_toggleParticipantVoiceMuted(userId));
  }

  Future<void> _restoreParticipantVoiceVolume(String userId) async {
    final changed = await _liveSessionController.restoreParticipantVoiceVolume(
      userId,
    );
    if (!changed || !mounted) return;
    _setHomeState(() {});
  }

  Future<void> _setParticipantVoiceVolume(String userId, double volume) async {
    await _liveSessionController.setParticipantVoiceVolume(userId, volume);
    if (!mounted) return;
    _setHomeState(() {});
  }

  Future<void> _toggleParticipantVoiceMuted(String userId) async {
    await _liveSessionController.toggleParticipantVoiceMuted(userId);
    if (!mounted) return;
    _setHomeState(() {});
  }

  bool _canModerateLiveParticipant(LiveParticipant participant) {
    final room = _selectedRoom;
    if (room == null ||
        _busyLiveMemberRemovalIds.contains(participant.user.id) ||
        _busyLiveMemberModerationIds.contains(participant.user.id)) {
      return false;
    }
    return _liveParticipantPermission(
      participant: participant,
      room: room,
    ).canRemoveMember;
  }

  bool _canRemoveLiveParticipant(LiveParticipant participant) {
    final room = _selectedRoom;
    if (room == null ||
        _busyLiveMemberRemovalIds.contains(participant.user.id)) {
      return false;
    }
    return _liveParticipantPermission(
      participant: participant,
      room: room,
    ).canRemoveMember;
  }

  member_filter.RoomMemberPermissionState _liveParticipantPermission({
    required LiveParticipant participant,
    required RoomDetail room,
  }) {
    final managementPermission = room_display.roomManagementPermissionState(
      room: room,
      currentUser: _currentUser,
    );
    return member_filter.roomMemberPermissionState(
      member: _roomMemberFromLiveParticipant(participant, room),
      currentUser: _currentUser,
      canEditCreatorOnly: managementPermission.canEditCreatorOnly,
      canManageMembers: room.isAdmin || _currentUser.isSuperuser,
      ownerUserId: room.createdBy?.id,
    );
  }

  Future<void> _toggleLiveParticipantMicModeration(
    LiveParticipant participant,
  ) {
    final blocked = participant.micBlocked || participant.voiceBlocked;
    final action = blocked
        ? LiveModerationAction.restoreVoice
        : LiveModerationAction.muteMic;
    return _moderateLiveParticipant(participant, action);
  }

  Future<void> _toggleLiveParticipantHeadphonesModeration(
    LiveParticipant participant,
  ) {
    final action = participant.headphonesBlocked
        ? LiveModerationAction.restoreHeadphones
        : LiveModerationAction.blockVoice;
    return _moderateLiveParticipant(participant, action);
  }

  Future<void> _moderateLiveParticipant(
    LiveParticipant participant,
    LiveModerationAction action,
  ) async {
    final room = _selectedRoom;
    if (room == null || !_canModerateLiveParticipant(participant)) return;
    final member = _roomMemberFromLiveParticipant(participant, room);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) =>
          _LiveMemberModerationConfirmDialog(member: member, action: action),
    );
    if (confirmed != true || !mounted) return;
    final userId = participant.user.id;
    if (_busyLiveMemberModerationIds.contains(userId)) return;
    _setHomeState(() {
      _busyLiveMemberModerationIds.add(userId);
      _roomError = null;
    });
    try {
      await _liveController.moderateParticipant(
        roomId: room.id,
        userId: userId,
        action: action,
      );
      if (!mounted) return;
      final live = _liveController.patchModeratedParticipant(
        live: _live,
        participant: participant,
        action: action,
      );
      _setHomeState(() {
        _busyLiveMemberModerationIds.remove(userId);
        _live = live;
        _roomError = null;
      });
    } catch (error) {
      if (!mounted) return;
      _setHomeState(() {
        _busyLiveMemberModerationIds.remove(userId);
        _roomError = userFacingErrorMessage(error);
      });
    }
  }

  Future<void> _removeLiveParticipant(LiveParticipant participant) async {
    final room = _selectedRoom;
    if (room == null || !_canRemoveLiveParticipant(participant)) return;
    final member = _roomMemberFromLiveParticipant(participant, room);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => _LiveMemberRemoveConfirmDialog(member: member),
    );
    if (confirmed != true || !mounted) return;
    final userId = participant.user.id;
    if (_busyLiveMemberRemovalIds.contains(userId)) return;
    _setHomeState(() {
      _busyLiveMemberRemovalIds.add(userId);
      _roomError = null;
    });
    try {
      await _liveController.kickParticipant(roomId: room.id, userId: userId);
      if (!mounted) return;
      final patch = _liveController.removeUserFromLive(
        live: _live,
        rooms: _servers,
        roomId: room.id,
        userId: userId,
      );
      _setHomeState(() {
        _busyLiveMemberRemovalIds.remove(userId);
        _live = patch.live;
        _servers = patch.rooms;
        _roomError = null;
      });
    } catch (error) {
      if (!mounted) return;
      _setHomeState(() {
        _busyLiveMemberRemovalIds.remove(userId);
        _roomError = userFacingErrorMessage(error);
      });
    }
  }

  RoomMember _roomMemberFromLiveParticipant(
    LiveParticipant participant,
    RoomDetail room,
  ) {
    final role =
        participant.user.roomRole ??
        (participant.user.id == room.createdBy?.id ? 'owner' : 'member');
    return RoomMember(
      user: participant.user,
      role: role,
      joinedAt: participant.joinedAt,
      roomDisplayName: participant.user.roomDisplayName,
    );
  }

  void _syncSettingsAudioVolumeToLive(String kind, double volume) {
    if (kind == 'audioinput') {
      _changeInputVolume(volume);
      return;
    }
    if (kind == 'audiooutput') {
      _changeOutputVolume(volume);
    }
  }

  void _rememberInputVolume(double volume) {
    _lastInputVolumeBeforeMute = rememberedAudioVolume(volume);
  }

  void _rememberOutputVolume(double volume) {
    _lastOutputVolumeBeforeMute = rememberedAudioVolume(volume);
  }

  double _restoredInputVolume() {
    return restoredAudioVolume(_lastInputVolumeBeforeMute);
  }

  double _restoredOutputVolume() {
    return restoredAudioVolume(_lastOutputVolumeBeforeMute);
  }

  Future<void> _joinLive(String source) async {
    final room = _selectedRoom;
    if (room == null || _joiningLive) return;

    if (widget.androidSystemService.isSupported) {
      try {
        await widget.androidSystemService.requestBluetoothConnectPermission();
      } catch (_) {
        // Speaker/earpiece routing remains usable when nearby-device access is
        // denied or unavailable.
      }
      if (!mounted) return;
    }

    _setHomeState(
      () => _applyLiveJoinStatePatch(
        _liveController.patchJoinStarted(joinedLiveRoomId: _joinedLiveRoomId),
      ),
    );

    final previousLiveRoomId = joinedLiveRoomToDisconnectBeforeJoin(
      joinedLiveRoomId: _joinedLiveRoomId,
      targetRoomId: room.id,
    );
    if (previousLiveRoomId != null) {
      _playLivePresenceSound(
        LivePresenceSound.left,
        continueAfterRoomExit: true,
      );
      await _notifyLiveLeft(previousLiveRoomId);
      await _liveSessionController.disconnect();
      if (mounted) {
        _setHomeState(() {
          _applyLiveJoinPreviousRoomDisconnectedPatch(
            _liveController.patchJoinPreviousRoomDisconnected(
              live: _live,
              rooms: _servers,
              previousRoomId: previousLiveRoomId,
              userId: _currentUser.id,
              livePanelOpen: true,
              error: _roomError,
            ),
          );
        });
      }
    }

    try {
      final result = await _liveController.joinLive(
        roomId: room.id,
        source: source,
      );
      if (!mounted) return;
      final displayResult = _withCurrentRoomLiveDisplayNameInJoinResult(
        result,
        room.id,
      );
      _setHomeState(() {
        _applyLiveJoinResultPatch(
          _liveController.patchJoinResult(
            rooms: _servers,
            result: displayResult,
            showMicUnmutedWhenAllowed: true,
          ),
        );
        _joinedLivePersonalAiVoiceAnnouncementsEnabled =
            room.aiVoiceAnnouncementsEnabled;
        _joinedLiveParticipantUsers.clear();
        for (final participant in displayResult.live.participants) {
          _joinedLiveParticipantUsers[participant.user.id] = participant.user;
        }
      });
      try {
        await _liveSessionController.connectWithRetry(
          displayResult,
          isCancelled: () => !mounted,
        );
      } catch (error) {
        if (mounted) {
          _setHomeState(() => _roomError = userFacingErrorMessage(error));
        }
        return;
      }
      if (!mounted) return;
      _setHomeState(
        () => _applyLiveJoinStatePatch(
          _liveController.patchJoinConnected(
            roomId: room.id,
            livePanelOpen: true,
            error: _roomError,
          ),
        ),
      );
      _playLivePresenceSound(LivePresenceSound.joined);
      // Publish the ready state atomically so other clients do not render the
      // server's initial `joining + muted` placeholder as a visible member.
      await _patchLiveState(
        micMuted: _voiceBlocked ? null : false,
        connectionState: 'online',
      );
      // Only publish the viewer relation after this participant is online.
      // Otherwise the screen-view snapshot can race the ready-state snapshot
      // and leave every client displaying the viewer as still "joining".
      _syncWatchedLiveStageSelection(_liveStageSelections[room.id]);
    } catch (error) {
      if (mounted) {
        _setHomeState(() => _roomError = userFacingErrorMessage(error));
      }
    } finally {
      if (mounted) {
        _setHomeState(
          () => _applyLiveJoinStatePatch(
            _liveController.patchJoinFinished(
              joinedLiveRoomId: _joinedLiveRoomId,
              livePanelOpen: true,
              error: _roomError,
            ),
          ),
        );
      }
    }
  }

  Future<void> _restoreLiveAfterRealtimeReconnect(String roomId) async {
    if (_joiningLive || _joinedLiveRoomId != roomId) return;
    if (_liveSessionController.isAttachedToRoom(roomId)) {
      await _refreshLiveSilently(roomId);
      return;
    }

    final previousMicMuted = _micMuted;
    final previousHeadphonesMuted = _headphonesMuted;
    final previousCameraOn = _cameraOn;
    final previousScreenSharing = _screenSharing;
    _setHomeState(() {
      _joiningLive = true;
      _roomError = null;
    });

    try {
      final result = await _liveController.joinLive(
        roomId: roomId,
        source: 'reconnect',
      );
      if (!mounted || _joinedLiveRoomId != roomId) return;

      final displayResult = _withCurrentRoomLiveDisplayNameInJoinResult(
        result,
        roomId,
      );
      final joinPatch = _liveController.patchJoinResult(
        rooms: _servers,
        result: displayResult,
      );
      _setHomeState(() {
        _micMuted = joinPatch.micMuted;
        _cameraOn = joinPatch.cameraOn;
        _screenSharing = joinPatch.screenSharing;
        _voiceBlocked = joinPatch.voiceBlocked;
        _servers = joinPatch.rooms;
        if (_selectedServerId == result.live.roomId) {
          _live = joinPatch.live;
        }
      });

      try {
        await _liveSessionController.connectWithRetry(
          result,
          isCancelled: () => !mounted || _joinedLiveRoomId != roomId,
        );
      } catch (error) {
        if (mounted) {
          _setHomeState(() => _roomError = userFacingErrorMessage(error));
        }
        return;
      }
      if (!mounted || _joinedLiveRoomId != roomId) return;

      final canPublish = !joinPatch.voiceBlocked;
      await _restoreLiveParticipantState(
        roomId: roomId,
        micMuted: canPublish ? previousMicMuted : true,
        headphonesMuted: previousHeadphonesMuted,
        cameraOn: canPublish && previousCameraOn,
        screenSharing:
            canPublish &&
            previousScreenSharing &&
            _liveSessionController.isScreenSharing,
      );
      _syncWatchedLiveStageSelection(_liveStageSelections[roomId]);
    } catch (error) {
      if (mounted) {
        _setHomeState(() => _roomError = userFacingErrorMessage(error));
      }
    } finally {
      if (mounted) _setHomeState(() => _joiningLive = false);
    }
  }

  Future<void> _restoreLiveParticipantState({
    required String roomId,
    required bool micMuted,
    required bool headphonesMuted,
    required bool cameraOn,
    required bool screenSharing,
  }) async {
    var restoredCameraOn = cameraOn;
    try {
      await _liveSessionController.setOutputMuted(headphonesMuted);
    } catch (_) {}
    try {
      await _liveSessionController.setCameraEnabled(restoredCameraOn);
    } catch (error) {
      restoredCameraOn = false;
      if (mounted) {
        _setHomeState(() => _roomError = userFacingErrorMessage(error));
      }
    }
    if (!screenSharing && _liveSessionController.isScreenSharing) {
      try {
        await _liveSessionController.setScreenShareEnabled(false);
      } catch (_) {}
    }

    final liveKitMicFuture = _liveSessionController
        .setMicMuted(micMuted)
        .catchError((_) {});
    try {
      final participant = await _liveController.updateMyState(
        roomId: roomId,
        micMuted: micMuted,
        headphonesMuted: headphonesMuted,
        cameraOn: restoredCameraOn,
        screenSharing: screenSharing,
        connectionState: 'online',
      );
      final displayParticipant = _withCurrentRoomLiveDisplayName(
        participant,
        roomId,
      );
      await liveKitMicFuture;
      if (shouldSyncLiveKitMicAfterServerPatch(
        requestedMicMuted: micMuted,
        serverMicMuted: displayParticipant.micMuted,
      )) {
        try {
          await _liveSessionController.setMicMuted(displayParticipant.micMuted);
        } catch (_) {}
      }
      if (!mounted || _joinedLiveRoomId != roomId) return;
      final updateSelectedLive = _selectedServerId == roomId;
      final patch = _liveController.patchStateUpdate(
        live: updateSelectedLive ? _live : null,
        participant: displayParticipant,
      );
      _setHomeState(() {
        _micMuted = patch.micMuted;
        _headphonesMuted = displayParticipant.headphonesMuted;
        _cameraOn = patch.cameraOn;
        _screenSharing = patch.screenSharing;
        _voiceBlocked = patch.voiceBlocked;
        if (updateSelectedLive) _live = patch.live;
      });
    } catch (error) {
      await liveKitMicFuture;
      if (!mounted || isBenignGoneLiveStatePatch(error)) return;
      _setHomeState(() => _roomError = userFacingErrorMessage(error));
    }
  }

  Future<void> _leaveLive() async {
    final roomId = _joinedLiveRoomId;
    if (roomId == null) return;
    _playLivePresenceSound(LivePresenceSound.left, continueAfterRoomExit: true);
    final shouldKeepLivePanelOpen = _contentMode == _ContentMode.live;
    final patch = _liveController.patchLocalDeparture(
      live: _live,
      rooms: _servers,
      joinedLiveRoomId: roomId,
      userId: _currentUser.id,
      joiningLive: true,
    );
    _setHomeState(() => _applyLiveLocalDeparturePatch(patch));
    try {
      await _notifyLiveLeft(roomId);
      await _liveSessionController.disconnect();
    } catch (error) {
      if (mounted) {
        _setHomeState(() => _roomError = userFacingErrorMessage(error));
      }
    } finally {
      if (mounted) {
        _setHomeState(
          () => _applyLiveJoinStatePatch(
            _liveController.patchJoinFinished(
              joinedLiveRoomId: _joinedLiveRoomId,
              livePanelOpen: shouldKeepLivePanelOpen,
              error: _roomError,
            ),
          ),
        );
      }
    }
  }

  Future<void> _notifyLiveLeft(String roomId) async {
    try {
      await _liveController.leaveLive(roomId: roomId);
    } catch (error) {
      if (!mounted || isBenignGoneLiveStatePatch(error)) return;
      _setHomeState(() => _roomError = userFacingErrorMessage(error));
    }
  }

  Future<void> _patchLiveState({
    bool? micMuted,
    bool? headphonesMuted,
    bool? cameraOn,
    bool? cameraMirrored,
    bool? screenSharing,
    String? connectionState,
    bool syncLiveKitMic = true,
  }) async {
    final roomId = _joinedLiveRoomId;
    if (roomId == null) return;
    final updateSelectedLive = canPatchSelectedLiveState(
      joinedLiveRoomId: roomId,
      selectedRoomId: _selectedServerId,
    );

    try {
      var effectiveMicMuted = micMuted;
      final shouldApplyLocalMic =
          micMuted != null && (syncLiveKitMic || micMuted == false);
      if (shouldApplyLocalMic) {
        try {
          await _liveSessionController.setMicMuted(micMuted);
        } catch (_) {}
        if (micMuted == false) {
          effectiveMicMuted = _liveSessionController.localMicMuted;
        }
      }
      final participant = await _liveController.updateMyState(
        roomId: roomId,
        micMuted: effectiveMicMuted,
        headphonesMuted: headphonesMuted,
        cameraOn: cameraOn,
        cameraMirrored: cameraMirrored,
        screenSharing: screenSharing,
        connectionState: connectionState,
      );
      final displayParticipant = _withCurrentRoomLiveDisplayName(
        participant,
        roomId,
      );
      if (shouldSyncLiveKitMicAfterServerPatch(
        requestedMicMuted: effectiveMicMuted,
        serverMicMuted: displayParticipant.micMuted,
      )) {
        try {
          await _liveSessionController.setMicMuted(displayParticipant.micMuted);
        } catch (_) {}
      }
      if (!mounted) return;
      final patch = _liveController.patchStateUpdate(
        live: updateSelectedLive ? _live : null,
        participant: displayParticipant,
      );
      _setHomeState(() {
        _micMuted = patch.micMuted;
        _cameraOn = patch.cameraOn;
        _screenSharing = patch.screenSharing;
        _voiceBlocked = patch.voiceBlocked;
        if (updateSelectedLive) _live = patch.live;
      });
    } catch (error) {
      if (!mounted || isBenignGoneLiveStatePatch(error)) return;
      _setHomeState(() => _roomError = userFacingErrorMessage(error));
    }
  }

  Future<void> _toggleCamera() async {
    final useTransitionGuard =
        !kIsWeb && defaultTargetPlatform == TargetPlatform.android;
    if (!useTransitionGuard) {
      await _toggleCameraImpl();
      return;
    }
    if (_switchingAndroidLocalVideoSource) return;
    _switchingAndroidLocalVideoSource = true;
    try {
      await _toggleCameraImpl();
    } finally {
      _switchingAndroidLocalVideoSource = false;
    }
  }

  Future<bool> _flipLocalCamera() async {
    try {
      final flipped = await _liveSessionController.flipCamera();
      if (!flipped && mounted) {
        _setHomeState(() => _roomError = '当前设备无法翻转摄像头');
      }
      return flipped;
    } catch (error) {
      if (mounted) {
        _setHomeState(() => _roomError = userFacingErrorMessage(error));
      }
      return false;
    }
  }

  Future<bool> _setLocalCameraMirrored(bool mirrored) async {
    final roomId = _joinedLiveRoomId;
    if (roomId == null) return false;
    final updateSelectedLive = canPatchSelectedLiveState(
      joinedLiveRoomId: roomId,
      selectedRoomId: _selectedServerId,
    );
    try {
      final participant = await _liveController.updateMyState(
        roomId: roomId,
        cameraMirrored: mirrored,
      );
      final displayParticipant = _withCurrentRoomLiveDisplayName(
        participant,
        roomId,
      );
      if (!mounted) return true;
      final patch = _liveController.patchStateUpdate(
        live: updateSelectedLive ? _live : null,
        participant: displayParticipant,
      );
      _setHomeState(() {
        if (updateSelectedLive) _live = patch.live;
      });
      return displayParticipant.cameraMirrored == mirrored;
    } catch (error) {
      if (mounted && !isBenignGoneLiveStatePatch(error)) {
        _setHomeState(() => _roomError = userFacingErrorMessage(error));
      }
      return false;
    }
  }

  Future<void> _toggleCameraImpl() async {
    final roomId = _joinedLiveRoomId;
    if (roomId == null ||
        !canPatchSelectedLiveState(
          joinedLiveRoomId: roomId,
          selectedRoomId: _selectedServerId,
        )) {
      return;
    }
    final enable = !_cameraOn;
    if (enable && _screenSharing) {
      try {
        await _liveSessionController.setScreenShareEnabled(false);
        if (mounted && _screenSharing) {
          _setHomeState(() => _screenSharing = false);
        }
        await _patchLiveState(screenSharing: false);
      } catch (error) {
        if (mounted) {
          _setHomeState(() => _roomError = userFacingErrorMessage(error));
        }
        return;
      }
    }
    try {
      await _liveSessionController.setCameraEnabled(enable);
    } catch (error) {
      if (mounted) {
        _setHomeState(() => _roomError = userFacingErrorMessage(error));
      }
      return;
    }
    await _patchLiveState(
      cameraOn: enable,
      screenSharing: enable ? false : null,
    );
  }

  Future<void> _toggleScreenShare() async {
    final useTransitionGuard =
        !kIsWeb && defaultTargetPlatform == TargetPlatform.android;
    if (!useTransitionGuard) {
      await _toggleScreenShareImpl();
      return;
    }
    if (_switchingAndroidLocalVideoSource) return;
    _switchingAndroidLocalVideoSource = true;
    try {
      await _toggleScreenShareImpl();
    } finally {
      _switchingAndroidLocalVideoSource = false;
    }
  }

  Future<void> _toggleScreenShareImpl() async {
    if (!supportsLocalScreenShare) return;
    final roomId = _joinedLiveRoomId;
    if (roomId == null ||
        !canPatchSelectedLiveState(
          joinedLiveRoomId: roomId,
          selectedRoomId: _selectedServerId,
        )) {
      return;
    }

    if (_screenSharing) {
      try {
        await _liveSessionController.setScreenShareEnabled(false);
      } catch (_) {}
      await _patchLiveState(screenSharing: false);
      return;
    }

    ScreenSource? source;
    if (supportsDesktopScreenShare) {
      source = await showDialog<ScreenSource>(
        context: context,
        builder: (context) => LiveScreenSharePicker(
          loadSources: _liveSessionController.listScreenSources,
          refreshThumbnails:
              _liveSessionController.refreshScreenSourceThumbnails,
        ),
      );
      if (source == null || !mounted) return;
    }
    if (!canApplyPickedScreenShareSource(
      pickedForRoomId: roomId,
      joinedLiveRoomId: _joinedLiveRoomId,
      selectedRoomId: _selectedServerId,
    )) {
      return;
    }

    final restoreCameraOnFailure = _cameraOn;
    if (_cameraOn) {
      try {
        await _liveSessionController.setCameraEnabled(false);
        if (mounted && _cameraOn) _setHomeState(() => _cameraOn = false);
      } catch (error) {
        if (mounted) {
          _setHomeState(() => _roomError = userFacingErrorMessage(error));
        }
        return;
      }
    }

    try {
      await _liveSessionController.setScreenShareEnabled(
        true,
        sourceId: source?.id,
      );
    } catch (error) {
      if (restoreCameraOnFailure) {
        try {
          await _liveSessionController.setCameraEnabled(true);
          if (mounted) _setHomeState(() => _cameraOn = true);
        } catch (_) {}
      }
      if (mounted) {
        _setHomeState(() => _roomError = userFacingErrorMessage(error));
      }
      return;
    }
    await _patchLiveState(screenSharing: true, cameraOn: false);
  }
}

class _LiveMemberRemoveConfirmDialog extends StatelessWidget {
  const _LiveMemberRemoveConfirmDialog({required this.member});

  final RoomMember member;

  @override
  Widget build(BuildContext context) {
    return DialogFrame(
      title: '踢出语音频道',
      icon: Icons.warning_amber_outlined,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '取消',
          onPressed: () => Navigator.of(context).pop(false),
        ),
        ResponsiveDialogAction(
          label: '踢出',
          tone: ButtonTone.danger,
          onPressed: () => Navigator.of(context).pop(true),
        ),
      ],
      child: Text(
        '确定要将 ${member_filter.roomMemberDisplayName(member)} 踢出语音频道吗？',
        style: UiTypography.body,
      ),
    );
  }
}

class _LiveMemberModerationConfirmDialog extends StatelessWidget {
  const _LiveMemberModerationConfirmDialog({
    required this.member,
    required this.action,
  });

  final RoomMember member;
  final LiveModerationAction action;

  @override
  Widget build(BuildContext context) {
    return DialogFrame(
      title: _title,
      icon: Icons.warning_amber_outlined,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '取消',
          onPressed: () => Navigator.of(context).pop(false),
        ),
        ResponsiveDialogAction(
          label: _confirmLabel,
          tone: ButtonTone.danger,
          onPressed: () => Navigator.of(context).pop(true),
        ),
      ],
      child: Text(_body, style: UiTypography.body),
    );
  }

  String get _name => member_filter.roomMemberDisplayName(member);

  String get _title {
    return switch (action) {
      LiveModerationAction.muteMic => '麦克风静音此用户',
      LiveModerationAction.blockVoice => '耳机静音此用户',
      LiveModerationAction.restoreVoice => '取消麦克风静音',
      LiveModerationAction.restoreHeadphones => '取消耳机静音',
      LiveModerationAction.kick => '踢出语音频道',
    };
  }

  String get _confirmLabel {
    return switch (action) {
      LiveModerationAction.muteMic => '麦克风静音',
      LiveModerationAction.blockVoice => '耳机静音',
      LiveModerationAction.restoreVoice => '取消麦克风静音',
      LiveModerationAction.restoreHeadphones => '取消耳机静音',
      LiveModerationAction.kick => '踢出',
    };
  }

  String get _body {
    return switch (action) {
      LiveModerationAction.muteMic => '确定要将 $_name 麦克风静音吗？对方的麦克风不会上传到语音频道。',
      LiveModerationAction.blockVoice => '确定要将 $_name 耳机静音吗？对方将无法听到语音频道。',
      LiveModerationAction.restoreVoice => '确定要取消 $_name 的麦克风静音吗？麦克风会恢复到正常状态。',
      LiveModerationAction.restoreHeadphones =>
        '确定要取消 $_name 的耳机静音吗？耳机会恢复到正常状态。',
      LiveModerationAction.kick => '确定要将 $_name 踢出语音频道吗？',
    };
  }
}
