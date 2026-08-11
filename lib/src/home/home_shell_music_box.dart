part of 'home_shell.dart';

/// Music box orchestration: loading the per-room snapshot, search/queue/control
/// writes, the local progress ticker, and SSE-driven refreshes. Audio itself is
/// not handled here — once the user has joined the room's LiveKit session the
/// music box bot's track arrives as an ordinary remote audio track.
extension _HomeShellMusicBox on _HomeShellState {
  /// Resets all music box state. Called on room switch and account change so a
  /// stale snapshot never bleeds across rooms.
  void _resetMusicBox() {
    _musicBoxLoadRetry?.cancel();
    _musicBoxLoadRetry = null;
    _musicBoxLoadFailures = 0;
    _musicBoxSearchDebounce?.cancel();
    _musicBoxSearchDebounce = null;
    _musicBoxSearchSerial++;
    _musicBoxRoomSerial++;
    _musicBoxLoadingRoomId = null;
    _musicBox = null;
    _musicBoxOpen = false;
    _musicBoxSearchResults = const [];
    _musicBoxSearching = false;
    _musicBoxSearchError = null;
    _musicBoxSource = music_box_display.musicBoxDefaultSource;
    if (_musicBoxSearchController.text.isNotEmpty) {
      _musicBoxSearchController.clear();
    }
    _lastMusicBoxSearchText = _musicBoxSearchController.text;
  }

  /// Fetches the snapshot for [roomId]. A successful disabled snapshot still
  /// hides the optional entry. Transient failures preserve the last valid
  /// snapshot; clearing it would make both the inline player and open panel
  /// disappear during token refreshes or brief network interruptions.
  Future<void> _loadMusicBox(String roomId) async {
    if (_musicBoxLoadingRoomId == roomId) return;
    final roomSerial = _musicBoxRoomSerial;
    _musicBoxLoadRetry?.cancel();
    _musicBoxLoadRetry = null;
    _musicBoxLoadingRoomId = roomId;
    try {
      final state = await _musicBoxController
          .getState(roomId)
          .timeout(const Duration(seconds: 12));
      if (!mounted ||
          _selectedServerId != roomId ||
          roomSerial != _musicBoxRoomSerial) {
        return;
      }
      _musicBoxLoadFailures = 0;
      _setHomeState(() => _musicBox = state);
    } catch (error) {
      assert(() {
        debugPrint('music-box: failed to load state for $roomId: $error');
        return true;
      }());
      // Keep the last authoritative snapshot. When this was the first load,
      // schedule a bounded retry so one stalled request cannot hide the module
      // for the rest of the room session.
      _scheduleMusicBoxLoadRetry(roomId, roomSerial);
    } finally {
      if (roomSerial == _musicBoxRoomSerial &&
          _musicBoxLoadingRoomId == roomId) {
        _musicBoxLoadingRoomId = null;
      }
    }
  }

  void _scheduleMusicBoxLoadRetry(String roomId, int roomSerial) {
    if (!mounted ||
        _selectedServerId != roomId ||
        roomSerial != _musicBoxRoomSerial ||
        (_musicBoxLoadRetry?.isActive ?? false)) {
      return;
    }
    const retryDelays = <Duration>[
      Duration(seconds: 2),
      Duration(seconds: 5),
      Duration(seconds: 15),
      Duration(seconds: 30),
      Duration(minutes: 1),
    ];
    final delay =
        retryDelays[_musicBoxLoadFailures.clamp(0, retryDelays.length - 1)];
    _musicBoxLoadFailures++;
    _musicBoxLoadRetry = Timer(delay, () {
      _musicBoxLoadRetry = null;
      if (!mounted ||
          _selectedServerId != roomId ||
          roomSerial != _musicBoxRoomSerial) {
        return;
      }
      unawaited(_loadMusicBox(roomId));
    });
  }

  void _ensureMusicBoxLoaded(String roomId) {
    if (_selectedServerId != roomId || _musicBox != null) return;
    unawaited(_loadMusicBox(roomId));
  }

  void _toggleMusicBoxPanel() {
    _setHomeState(() => _musicBoxOpen = !_musicBoxOpen);
  }

  /// Switches the search source and re-runs the current query against it.
  void _changeMusicBoxSource(String source) {
    final normalizedSource = music_box_display.normalizedMusicBoxSource(source);
    if (normalizedSource == _musicBoxSource) return;
    _setHomeState(() => _musicBoxSource = normalizedSource);
    final keyword = _musicBoxSearchController.text.trim();
    if (keyword.isEmpty) return;
    _musicBoxSearchDebounce?.cancel();
    _setHomeState(() {
      _musicBoxSearchResults = const [];
      _musicBoxSearching = true;
      _musicBoxSearchError = null;
    });
    unawaited(_searchMusicBox(keyword));
  }

  /// Applies an authoritative snapshot from a write response or SSE event,
  /// overwriting local state wholesale per the server contract.
  void _applyMusicBoxSnapshot(MusicBoxState state) {
    if (!mounted) return;
    if (!shouldAcceptMusicBoxSnapshot(_musicBox, state)) return;
    _setHomeState(() => _musicBox = state);
  }

  void _onMusicBoxChanged(Map<String, dynamic> event) {
    final roomId = event['room_id'] as String?;
    if (roomId == null || roomId != _selectedServerId) return;
    // The realtime client flattens the event envelope, merging the payload's
    // fields up alongside `room_id` (see LiveStreamClient._emit). The snapshot
    // therefore lives at the top level of [event], not under a `data` key.
    _applyMusicBoxSnapshot(
      MusicBoxState.fromJson(event.cast<String, Object?>()),
    );
  }

  // --- Writes -----------------------------------------------------------

  Future<void> _searchMusicBox(String keyword) async {
    final roomId = _selectedServerId;
    final trimmed = keyword.trim();
    if (roomId == null) return;
    if (trimmed.isEmpty) {
      _setHomeState(() {
        _musicBoxSearchResults = const [];
        _musicBoxSearching = false;
        _musicBoxSearchError = null;
      });
      return;
    }
    final serial = ++_musicBoxSearchSerial;
    _setHomeState(() {
      _musicBoxSearching = true;
      _musicBoxSearchError = null;
    });
    try {
      final results = await _musicBoxController.search(
        roomId: roomId,
        keyword: trimmed,
        source: music_box_display.normalizedMusicBoxSource(_musicBoxSource),
      );
      if (!mounted || serial != _musicBoxSearchSerial) return;
      _setHomeState(() {
        _musicBoxSearchResults = results;
        _musicBoxSearching = false;
      });
    } catch (error) {
      if (!mounted || serial != _musicBoxSearchSerial) return;
      _setHomeState(() {
        _musicBoxSearching = false;
        _musicBoxSearchError = _musicBoxSearchErrorMessage(error);
      });
    }
  }

  /// Debounced search driven by the search field's controller listener.
  ///
  /// Flips the searching state immediately so the panel swaps to the results
  /// view (and shows a spinner) on the first keystroke, then debounces the
  /// actual network call.
  void _handleMusicBoxSearchChanged() {
    final rawKeyword = _musicBoxSearchController.text;
    if (rawKeyword == _lastMusicBoxSearchText) return;
    _lastMusicBoxSearchText = rawKeyword;
    _musicBoxSearchDebounce?.cancel();
    final keyword = rawKeyword.trim();
    if (keyword.isEmpty) {
      unawaited(_searchMusicBox(''));
      return;
    }
    _setHomeState(() {
      _musicBoxSearching = true;
      _musicBoxSearchError = null;
    });
    _musicBoxSearchDebounce = Timer(
      const Duration(milliseconds: 350),
      () => unawaited(_searchMusicBox(keyword)),
    );
  }

  Future<void> _queueMusicBoxTrack(MusicBoxSearchResult result) async {
    final roomId = _selectedServerId;
    if (roomId == null) return;
    try {
      final state = await _musicBoxController.queueSearchResult(
        roomId: roomId,
        result: result,
      );
      _applyMusicBoxSnapshot(state);
      _showMusicBoxNotice(
        '已加入队列：${result.name}',
        tone: FloatingNoticeTone.success,
      );
    } catch (error) {
      _showMusicBoxNotice(
        _musicBoxWriteErrorMessage(error),
        tone: FloatingNoticeTone.error,
      );
    }
  }

  Future<void> _removeMusicBoxItem(MusicBoxQueueItem item) async {
    final roomId = _selectedServerId;
    if (roomId == null) return;
    try {
      final state = await _musicBoxController.removeItem(
        roomId: roomId,
        itemId: item.id,
      );
      _applyMusicBoxSnapshot(state);
      _showMusicBoxNotice(
        '已从队列删除：${item.title}',
        tone: FloatingNoticeTone.success,
      );
    } catch (error) {
      _showMusicBoxNotice(
        _musicBoxWriteErrorMessage(error),
        tone: FloatingNoticeTone.error,
      );
    }
  }

  Future<void> _controlMusicBox(
    String action, {
    MusicBoxPlaybackMode? mode,
  }) async {
    final roomId = _selectedServerId;
    if (roomId == null) return;
    try {
      final state = await _musicBoxController.control(
        roomId: roomId,
        action: action,
        mode: mode,
        currentState: _musicBox,
      );
      _applyMusicBoxSnapshot(state);
    } catch (error) {
      _showMusicBoxNotice(
        _musicBoxWriteErrorMessage(error),
        tone: FloatingNoticeTone.error,
      );
    }
  }

  Future<void> _changeMusicBoxPlaybackMode(MusicBoxPlaybackMode mode) {
    return _controlMusicBox('set_mode', mode: mode);
  }

  void _toggleMusicBoxPlayback() {
    final state = _musicBox;
    if (state == null) return;
    final action = music_box_display.musicBoxPrimaryTransport(state);
    unawaited(
      _controlMusicBox(music_box_display.musicBoxTransportApiAction(action)),
    );
  }

  // --- Error mapping ----------------------------------------------------

  String _musicBoxSearchErrorMessage(Object error) {
    if (error is ApiException && error.statusCode == 502) {
      return '搜索服务暂时不可用，请稍后重试';
    }
    return '搜索失败，请重试';
  }

  String _musicBoxWriteErrorMessage(Object error) {
    if (error is ApiException) {
      switch (error.code) {
        case 'music_box_unavailable':
          return '音乐盒当前不可用';
        case 'music_box_item_already_queued':
          return '已在队列中';
      }
    }
    return '操作失败，请重试';
  }

  void _showMusicBoxNotice(
    String message, {
    FloatingNoticeTone tone = FloatingNoticeTone.info,
  }) {
    if (!mounted) return;
    showFloatingNotice(context, message, tone: tone);
  }
}
