part of 'home_shell.dart';

extension _HomeShellRealtime on _HomeShellState {
  void _startRealtime() {
    final previous = _realtimeEvents;
    if (previous != null) unawaited(previous.cancel());
    final previousStatus = _realtimeStatusEvents;
    if (previousStatus != null) unawaited(previousStatus.cancel());

    final realtime = _services.realtime;
    realtime.onReconnect = _onRealtimeReconnect;
    _realtimeStatus = realtime.status;
    _realtimeStatusEvents = realtime.statusChanges.listen(
      _onRealtimeStatusChanged,
    );
    _realtimeEvents = realtime.events.listen(_onRealtimeEvent);
    unawaited(realtime.start());
  }

  void _onRealtimeStatusChanged(RealtimeConnectionStatus status) {
    if (!mounted) return;
    _setHomeState(() => _realtimeStatus = status);
  }

  void _onRealtimeReconnect() {
    if (!mounted) return;
    unawaited(
      _androidPushRegistration?.synchronize().catchError((Object _) {}),
    );
    unawaited(_services.roomReads.retryPending());
    unawaited(_loadServersSilently());
    final selected = _selectedServerId;
    if (selected != null) unawaited(_refreshLiveSilently(selected));
    final joinedLiveRoomId = _joinedLiveRoomId;
    if (joinedLiveRoomId != null &&
        !_liveSessionController.isAttachedToRoom(joinedLiveRoomId)) {
      unawaited(_restoreLiveAfterRealtimeReconnect(joinedLiveRoomId));
    }
  }

  Future<void> _loadServersSilently() async {
    try {
      final servers = await _roomsController.loadRooms();
      if (!mounted) return;
      _setHomeState(() {
        var nextServers = _roomsController
            .patchRoomCardsRefreshed(rooms: servers)
            .rooms;
        final selectedRoomId = _selectedServerId;
        if (selectedRoomId != null) {
          nextServers = _roomsController
              .patchRoomUnreadCleared(
                rooms: nextServers,
                roomId: selectedRoomId,
              )
              .rooms;
        }
        _servers = nextServers;
      });
    } catch (_) {}
  }

  Future<void> _refreshLiveSilently(String roomId) async {
    try {
      final live = await _roomsController.getLiveState(roomId);
      if (!mounted) return;
      final patch = _roomsController.patchSelectedLiveRefreshed(
        live: live,
        selectedRoomId: _selectedServerId,
        currentLive: _live,
      );
      if (patch == null) return;
      _setHomeState(() => _live = patch.live);
      _syncJoinedLiveAudioFromSnapshot(patch.live);
    } catch (_) {}
  }

  void _onRealtimeEvent(RealtimeEvent event) {
    switch (event.type) {
      case 'live_participant_joined':
      case 'live_participant_left':
      case 'live_participant_updated':
      case 'live_participant_moderated':
      case 'live_room_finished':
        _applyLiveSnapshot(event.data);
        break;
      case 'room_added':
        _applyRoomAdded(event.data);
        break;
      case 'room_updated':
        _applyRoomUpdated(event.data);
        break;
      case 'room_deleted':
        _applyRoomDeleted(event.data);
        break;
      case 'room_invites_updated':
        _applyRoomInvitesUpdated();
        break;
      case 'room_applications_updated':
        _applyRoomApplicationsUpdated();
        break;
      case 'room_notifications_updated':
        _applyRoomNotificationsUpdated();
        break;
      case 'room_role_changed':
        _applyRoomRoleChanged(event.data);
        break;
      case 'room_member_profile_changed':
        _applyRoomMemberProfileChanged(event.data);
        break;
      case 'room_join_requests_updated':
        _applyRoomJoinRequestsUpdated(event.data);
        break;
      case 'account_suspended':
        unawaited(_logoutSuspendedAccount());
        break;
      case 'music_box_changed':
        _onMusicBoxChanged(event.data);
        break;
      default:
        break;
    }
  }

  Future<void> _logoutSuspendedAccount() async {
    if (_accountSuspensionLogoutInProgress) return;
    _accountSuspensionLogoutInProgress = true;
    await _logout();
  }

  void _applyLiveSnapshot(Map<String, dynamic> data) {
    final eventRoomId = data['room_id'] as String?;
    final liveJson = data['live'];
    if (eventRoomId == _joinedLiveRoomId && liveJson is Map) {
      try {
        _cacheJoinedLiveParticipantUsers(
          LiveState.fromJson(Map<String, Object?>.from(liveJson)),
        );
      } catch (_) {}
    }
    final patch = _roomsController.patchLiveSnapshot(
      rooms: _servers,
      selectedRoomId: _selectedServerId,
      data: data,
      joinedLiveRoomId: _joinedLiveRoomId,
      currentUserId: _currentUser.id,
      previousLive: _live,
    );
    if (patch == null || !mounted) return;
    _setHomeState(() {
      _servers = patch.rooms;
      if (patch.selectedLive != null) _live = patch.selectedLive;
    });
    final selectedLive = patch.selectedLive;
    if (selectedLive != null) {
      _syncJoinedLiveAudioFromSnapshot(selectedLive);
    }
  }

  void _applyRoomAdded(Map<String, dynamic> data) {
    final room = _roomsController.roomCardFromSnapshot(data);
    if (room == null || !mounted) return;
    _setHomeState(() {
      _servers = _roomsController
          .patchRoomCardUpserted(rooms: _servers, room: room)
          .rooms;
    });
  }

  void _applyRoomUpdated(Map<String, dynamic> data) {
    final previousJoinPolicy = _selectedRoom?.joinPolicy;
    final room = _roomsController.roomCardFromSnapshot(data);
    if (room == null || !mounted) return;
    final shouldNotifyMessage = _messageNotificationTracker.register(
      roomId: room.id,
      updateReason: data['update_reason']?.toString() ?? '',
      notificationPolicy: room.notificationPolicy,
      messageId: room.lastMessage?.id,
    );
    if (room.id == _joinedLiveRoomId) {
      _joinedLivePersonalAiVoiceAnnouncementsEnabled =
          room.aiVoiceAnnouncementsEnabled;
    }
    final shouldRefreshMessages =
        room.id == _selectedServerId &&
        room.lastMessage != null &&
        room.lastMessage!.id != _latestLoadedServerMessageId();
    final patch = _roomsController.patchRoomUpdated(
      rooms: _servers,
      incoming: room,
      selectedRoom: _selectedRoom,
    );
    int? selectedNewMessageCount;
    if (room.id == _selectedServerId && room.hasUnreadCount) {
      // Account-scoped room snapshots are authoritative for unread state.
      // Apply a read receipt from another device even when the last message is
      // already loaded locally and no message refresh is necessary.
      selectedNewMessageCount = room.unreadCount;
    } else if (shouldRefreshMessages) {
      for (final candidate in patch.rooms) {
        if (candidate.id == room.id) {
          selectedNewMessageCount = candidate.unreadCount;
          break;
        }
      }
    }
    _setHomeState(() {
      _servers = patch.rooms;
      _selectedRoom = patch.selectedRoom;
      if (selectedNewMessageCount != null) {
        _selectedRoomNewMessageCount = selectedNewMessageCount;
      }
      if (patch.shouldReloadMembers) _membersReloadToken++;
    });
    final selectedRoom = patch.selectedRoom;
    if (selectedRoom != null &&
        selectedRoom.id == room.id &&
        previousJoinPolicy != selectedRoom.joinPolicy) {
      unawaited(_refreshSelectedJoinRequestBadge(selectedRoom));
    }
    if (shouldRefreshMessages) {
      unawaited(_refreshSelectedMessagesSilently(room.id));
    }
    _syncAndroidNotificationState();
    if (shouldNotifyMessage) _notifyRealtimeRoomMessage(room);
  }

  void _notifyRealtimeRoomMessage(RoomCard room) {
    final androidSystemService = widget.androidSystemService;
    if (androidSystemService.isSupported && !_isAppForeground) {
      final message = room.lastMessage;
      if (message != null) {
        unawaited(
          androidSystemService
              .showRoomMessage(
                roomId: room.id,
                roomName: room.displayName,
                sender: message.senderDisplayName,
                body: message.bodyPreview,
                unreadCount: _androidUnreadCount,
                messageId: message.id,
              )
              .catchError((_) {}),
        );
      }
      return;
    }
    final volume = _headphonesMuted
        ? 0.0
        : normalizedAudioVolume(_liveSessionController.outputVolume);
    unawaited(
      _messageNotificationSoundPlayer.play(volume: volume).catchError((_) {}),
    );
    unawaited(widget.windowController.requestMessageAttention());
  }

  int get _androidUnreadCount =>
      _servers.fold<int>(0, (total, room) => total + room.unreadCount);

  void _syncAndroidNotificationState() {
    final service = widget.androidSystemService;
    if (!service.isSupported) return;
    for (final room in _servers) {
      if (room.unreadCount <= 0) {
        unawaited(service.cancelRoomNotification(room.id).catchError((_) {}));
      }
    }
    unawaited(service.syncBadge(_androidUnreadCount).catchError((_) {}));
  }

  String? _latestLoadedServerMessageId() {
    return _latestServerMessageId(_messages);
  }

  Future<void> _refreshSelectedMessagesSilently(String roomId) async {
    try {
      final messages = await _messagesController.loadMessages(roomId);
      if (!mounted || _selectedServerId != roomId) return;
      final serverClientIds = {
        for (final message in messages) message.clientMessageId,
      };
      final pending = [
        for (final message in _messages)
          if (message.pending &&
              !serverClientIds.contains(message.clientMessageId))
            message,
      ];
      final nextMessages = [...messages, ...pending];
      _setHomeState(() => _messages = nextMessages);
    } catch (_) {}
  }

  void _applyRoomDeleted(Map<String, dynamic> data) {
    final patch = _roomsController.patchRoomDeleted(
      rooms: _servers,
      selectedRoomId: _selectedServerId,
      selectedRoom: _selectedRoom,
      selectedRoomHasPendingJoinRequests: _selectedRoomHasPendingJoinRequests,
      messages: _messages,
      live: _live,
      livePanelOpen: _contentMode == _ContentMode.live,
      settingsOpen: _settingsOpen,
      joinedLiveRoomId: _joinedLiveRoomId,
      data: data,
    );
    if (patch == null || !mounted) return;
    _setHomeState(() {
      _discardRoomDraftInState(patch.roomId);
      _servers = patch.rooms;
      _selectedServerId = patch.selectedRoomId;
      _selectedRoom = patch.selectedRoom;
      _selectedRoomHasPendingJoinRequests =
          patch.selectedRoomHasPendingJoinRequests;
      _messages = patch.messages;
      _live = patch.live;
      _settingsOpen = patch.settingsOpen;
      _contentMode = patch.livePanelOpen
          ? _ContentMode.live
          : _ContentMode.chat;
      _joinedLiveRoomId = patch.joinedLiveRoomId;
      if (patch.wasSelected) {
        for (final entry in _stagedAttachments) {
          entry.uploadController.cancel();
        }
        _stagedAttachments.clear();
        _setComposerText('', saveDraft: false);
        _selectedRoomNewMessageCount = 0;
        _fileTransfers = const {};
        _roomError = null;
        _sendError = null;
        _narrowContentOpen = false;
        _resetMusicBox();
      }
    });
    if (widget.androidSystemService.isSupported) {
      unawaited(
        widget.androidSystemService
            .cancelRoomNotification(patch.roomId)
            .catchError((_) {}),
      );
      _syncAndroidNotificationState();
    }
    if (patch.shouldDisconnectLive) {
      unawaited(_liveSessionController.disconnect());
    }
  }

  /// Applies a `room_role_changed` event for the affected member (the current
  /// user). The shared room snapshot omits `my_role`, so this is the only way a
  /// promote/demote reaches the open room without a manual refetch. Updates the
  /// selected room's membership role in place so permission-gated UI (manage,
  /// review join requests) re-evaluates immediately.
  void _applyRoomRoleChanged(Map<String, dynamic> data) {
    final patch = _roomsController.patchRoomRoleChanged(
      selectedRoom: _selectedRoom,
      data: data,
    );
    if (patch == null || !mounted) return;
    _setHomeState(() {
      _selectedRoom = patch.selectedRoom;
      // A role change can flip whether the user may review join requests, so
      // bump the members reload token to re-pull that list if the panel's open.
      _membersReloadToken++;
    });
    unawaited(_refreshSelectedJoinRequestBadge(patch.selectedRoom));
  }

  /// Applies a `room_join_requests_updated` event: the pending join-request set
  /// for [roomId] changed (new request, or one approved/denied elsewhere). If
  /// it targets the open room, nudge the members panel to reload its request
  /// list via the reload token. Also refresh the notification badge, since a
  /// new request may need the current user's attention.
  void _applyRoomJoinRequestsUpdated(Map<String, dynamic> data) {
    final roomId = data['room_id'] as String?;
    if (!mounted) return;
    unawaited(_refreshPendingRoomInviteBadge());
    if (roomId == null || roomId != _selectedServerId) return;
    _setHomeState(() => _membersReloadToken++);
    unawaited(_refreshSelectedJoinRequestBadge());
  }

  void _applyRoomMemberProfileChanged(Map<String, dynamic> data) {
    final roomId = data['room_id'] as String?;
    if (!mounted || roomId == null || roomId != _selectedServerId) return;
    _setHomeState(() => _membersReloadToken++);
  }
}
