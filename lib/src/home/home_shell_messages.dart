part of 'home_shell.dart';

extension _HomeShellMessages on _HomeShellState {
  Message? _latestServerMessage(List<Message> messages) {
    for (final message in messages.reversed) {
      if (!message.pending) return message;
    }
    return null;
  }

  String? _latestServerMessageId(List<Message> messages) {
    return _latestServerMessage(messages)?.id;
  }

  void _clearRoomUnreadCount(String roomId) {
    _servers = _roomsController
        .patchRoomUnreadCleared(rooms: _servers, roomId: roomId)
        .rooms;
  }

  Future<void> _markRoomReadFromMessages(
    String roomId,
    List<Message> messages,
  ) async {
    if (_selectedServerId != roomId) return;
    final lastReadMessage = _latestServerMessage(messages);
    if (lastReadMessage == null) return;
    final lastReadMessageId = lastReadMessage.id;

    if (_services.roomReads.isSynced(
      roomId: roomId,
      messageId: lastReadMessageId,
    )) {
      if (mounted) _setHomeState(() => _clearRoomUnreadCount(roomId));
      return;
    }
    if (mounted) _setHomeState(() => _clearRoomUnreadCount(roomId));

    await _services.roomReads.markRead(
      roomId: roomId,
      lastReadMessageId: lastReadMessageId,
      messageCreatedAt: lastReadMessage.createdAt,
    );
  }

  void _clearSelectedRoomNewMessagePrompt() {
    if (_selectedRoomNewMessageCount == 0) return;
    final roomId = _selectedServerId;
    final messages = _messages;
    _setHomeState(() => _selectedRoomNewMessageCount = 0);
    if (roomId != null) {
      unawaited(_markRoomReadFromMessages(roomId, messages));
    }
  }

  void _handleComposerDraftChanged() {
    if (_updatingComposerFromDraft) return;
    if (_tryCompleteSingleComposerMentionAfterSpace()) return;
    _composerController.pruneInvalidConfirmedMentions(notify: false);
    _saveComposerDraftValue(_composerController.text);
    _updateComposerMentionState();
  }

  void _storeSelectedComposerDraft() {
    _setHomeState(_saveDraftInState);
  }

  void _restoreComposerDraftForRoom(String roomId) {
    _stagedAttachments
      ..clear()
      ..addAll(_stagedAttachmentDrafts[roomId] ?? const []);
    _setComposerText(_messageDrafts[roomId] ?? '', saveDraft: false);
    _clearComposerMentionState();
  }

  List<MessageQuote> get _selectedComposerQuotes {
    final roomId = _selectedServerId;
    return roomId == null
        ? const <MessageQuote>[]
        : _messageQuoteDrafts[roomId] ?? const <MessageQuote>[];
  }

  Message? get _selectedComposerComponent {
    final roomId = _selectedServerId;
    return roomId == null ? null : _messageComponentDrafts[roomId];
  }

  void _removeSelectedComposerComponent() {
    final roomId = _selectedServerId;
    if (roomId == null || !_messageComponentDrafts.containsKey(roomId)) return;
    _setHomeState(() {
      final next = Map<String, Message>.of(_messageComponentDrafts)
        ..remove(roomId);
      _messageComponentDrafts = Map<String, Message>.unmodifiable(next);
      _saveDraftInState();
    });
  }

  void _quoteChatMessage(Message message) {
    if (!_canQuoteChatMessage(message)) return;
    final current =
        _messageQuoteDrafts[message.roomId] ?? const <MessageQuote>[];
    if (current.any((quote) => quote.messageId == message.id)) {
      _composerPanelController.closePanel();
      _composerPanelController.requestInputFocus();
      return;
    }
    _setHomeState(() {
      final next = Map<String, List<MessageQuote>>.of(_messageQuoteDrafts);
      next[message.roomId] = List<MessageQuote>.unmodifiable([
        ...current,
        message_display.messageQuoteSnapshot(message),
      ]);
      _messageQuoteDrafts = Map<String, List<MessageQuote>>.unmodifiable(next);
      _saveDraftInState();
      _sendError = null;
    });
    _composerPanelController.closePanel();
    _composerPanelController.requestInputFocus();
  }

  void _removeSelectedComposerQuote(String messageId) {
    final roomId = _selectedServerId;
    if (roomId == null || !_messageQuoteDrafts.containsKey(roomId)) return;
    _setHomeState(() {
      final remaining = <MessageQuote>[
        for (final quote
            in _messageQuoteDrafts[roomId] ?? const <MessageQuote>[])
          if (quote.messageId != messageId) quote,
      ];
      final next = Map<String, List<MessageQuote>>.of(_messageQuoteDrafts);
      if (remaining.isEmpty) {
        next.remove(roomId);
      } else {
        next[roomId] = List<MessageQuote>.unmodifiable(remaining);
      }
      _messageQuoteDrafts = Map<String, List<MessageQuote>>.unmodifiable(next);
      _saveDraftInState();
    });
  }

  void _setComposerText(String text, {required bool saveDraft}) {
    final nextValue = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
    if (_composerController.value == nextValue) {
      if (saveDraft) _saveComposerDraftValue(text);
      return;
    }

    _composerController.clearConfirmedMentions();
    final previousUpdating = _updatingComposerFromDraft;
    _updatingComposerFromDraft = true;
    _composerController.value = nextValue;
    _updatingComposerFromDraft = previousUpdating;
    if (saveDraft) _saveComposerDraftValue(text);
  }

  void _saveComposerDraftValue(String text) {
    _setHomeState(() => _saveDraftInState(text: text));
  }

  List<message_mentions.MessageMentionOption> _buildComposerMentionOptions(
    message_mentions.MessageMentionQuery? query,
  ) {
    final roomId = _selectedServerId;
    if (query == null ||
        roomId == null ||
        _composerMentionMembersRoomId != roomId) {
      return const [];
    }
    return message_mentions.messageMentionOptions(
      members: _composerMentionMembers,
      query: query.query,
      ownerUserId: _selectedRoom?.createdBy?.id,
      excludedUserId: _currentUser.id,
      limit: 50,
    );
  }

  void _setComposerMentionQuery(message_mentions.MessageMentionQuery? query) {
    _composerMentionQuery = query;
    _composerMentionOptions = _buildComposerMentionOptions(query);
    _composerMentionSelectedIndex = 0;
  }

  void _refreshComposerMentionOptions() {
    _composerMentionOptions = _buildComposerMentionOptions(
      _composerMentionQuery,
    );
    _composerMentionSelectedIndex = _clampComposerMentionSelectedIndex(
      _composerMentionSelectedIndex,
    );
  }

  int _clampComposerMentionSelectedIndex(int index) {
    if (_composerMentionOptions.isEmpty) return 0;
    return index.clamp(0, _composerMentionOptions.length - 1).toInt();
  }

  void _highlightComposerMentionSelection(int index) {
    if (_composerMentionOptions.isEmpty) return;
    final nextIndex = _clampComposerMentionSelectedIndex(index);
    if (_composerMentionSelectedIndex == nextIndex) return;
    _setHomeState(() => _composerMentionSelectedIndex = nextIndex);
  }

  bool _navigateComposerMentionSelection(
    ComposerSuggestionNavigation navigation,
  ) {
    if (_composerMentionQuery == null || _composerMentionOptions.isEmpty) {
      return false;
    }
    final current = _clampComposerMentionSelectedIndex(
      _composerMentionSelectedIndex,
    );
    final delta = navigation == ComposerSuggestionNavigation.previous ? -1 : 1;
    final count = _composerMentionOptions.length;
    final nextIndex = (current + delta + count) % count;
    _setHomeState(() => _composerMentionSelectedIndex = nextIndex);
    _composerPanelController.requestInputFocus();
    return true;
  }

  bool _confirmComposerMentionSelection(ComposerSuggestionAction action) {
    if (action != ComposerSuggestionAction.confirm) return false;
    return _completeComposerMentionSelection(
      includeTerminatingSpace: true,
      synthesizeTerminatingSpace: true,
    );
  }

  void _clearComposerMentionState() {
    if (_composerMentionQuery == null &&
        _composerMentionOptions.isEmpty &&
        !_loadingComposerMentionMembers) {
      return;
    }
    _setHomeState(() {
      _setComposerMentionQuery(null);
      _loadingComposerMentionMembers = false;
      _loadingComposerMentionMembersRoomId = null;
    });
  }

  void _updateComposerMentionState() {
    final roomId = _selectedServerId;
    if (roomId == null) {
      if (_composerMentionQuery != null) {
        _setHomeState(() => _setComposerMentionQuery(null));
      }
      return;
    }
    final selection = _composerController.selection;
    final query = !selection.isValid || !selection.isCollapsed
        ? null
        : message_mentions.activeMessageMentionQuery(
            text: _composerController.text,
            cursorOffset: selection.extentOffset,
          );
    if (query == null ||
        _composerController.hasConfirmedMention(
          start: query.start,
          end: query.end,
        )) {
      if (_composerMentionQuery != null) {
        _setHomeState(() => _setComposerMentionQuery(null));
      }
      return;
    }

    final previous = _composerMentionQuery;
    if (previous == null ||
        previous.start != query.start ||
        previous.end != query.end ||
        previous.query != query.query) {
      _setHomeState(() => _setComposerMentionQuery(query));
    }
    _ensureComposerMentionMembers(roomId);
  }

  Future<void> _ensureComposerMentionMembers(String roomId) async {
    if (_composerMentionMembersRoomId == roomId ||
        (_loadingComposerMentionMembers &&
            _loadingComposerMentionMembersRoomId == roomId)) {
      return;
    }
    final serial = ++_composerMentionMembersSerial;
    _setHomeState(() {
      _loadingComposerMentionMembers = true;
      _loadingComposerMentionMembersRoomId = roomId;
    });
    try {
      final members = await _roomsController.loadAllRoomMembers(roomId);
      if (!mounted ||
          serial != _composerMentionMembersSerial ||
          _selectedServerId != roomId) {
        return;
      }
      _setHomeState(() {
        _composerMentionMembers = List.unmodifiable(members);
        _composerMentionMembersRoomId = roomId;
        _loadingComposerMentionMembers = false;
        _loadingComposerMentionMembersRoomId = null;
        _refreshComposerMentionOptions();
      });
    } catch (_) {
      if (!mounted ||
          serial != _composerMentionMembersSerial ||
          _selectedServerId != roomId) {
        return;
      }
      _setHomeState(() {
        _composerMentionMembers = const [];
        _composerMentionMembersRoomId = roomId;
        _loadingComposerMentionMembers = false;
        _loadingComposerMentionMembersRoomId = null;
        _refreshComposerMentionOptions();
      });
    }
  }

  void _selectComposerMention(message_mentions.MessageMentionOption option) {
    final value = _composerController.value;
    final selection = value.selection;
    final query = selection.isValid && selection.isCollapsed
        ? message_mentions.activeMessageMentionQuery(
            text: value.text,
            cursorOffset: selection.extentOffset,
          )
        : _composerMentionQuery;
    if (query == null) return;

    final replacement = message_mentions.messageMentionInsertText(option);
    final nextText = value.text.replaceRange(
      query.start,
      query.end,
      replacement,
    );
    final nextOffset = query.start + replacement.length;
    final nextValue = value.copyWith(
      text: nextText,
      selection: TextSelection.collapsed(offset: nextOffset),
      composing: TextRange.empty,
    );
    _composerController.value = nextValue;
    _composerController.addConfirmedMention(
      start: query.start,
      label: option.label,
    );
    _saveComposerDraftValue(nextText);
    if (_composerMentionQuery != null) {
      _setHomeState(() => _setComposerMentionQuery(null));
    }
    _composerPanelController.requestInputFocus();
  }

  bool _tryCompleteSingleComposerMentionAfterSpace() {
    final value = _composerController.value;
    final selection = value.selection;
    if (!selection.isValid || !selection.isCollapsed) return false;
    final query = _composerMentionQuery;
    if (query == null) return false;
    final cursor = selection.extentOffset;
    if (cursor != query.end + 1) return false;
    if (query.start < 0 || query.end >= value.text.length) return false;
    if (value.text.codeUnitAt(query.end) != 0x20) return false;
    return _completeComposerMentionSelection(includeTerminatingSpace: true);
  }

  bool _completeComposerMentionSelection({
    required bool includeTerminatingSpace,
    bool synthesizeTerminatingSpace = false,
  }) {
    final query = _composerMentionQuery;
    if (query == null) return false;
    final value = _composerController.value;
    final selection = value.selection;
    if (!selection.isValid || !selection.isCollapsed) return false;
    var text = value.text;
    if (synthesizeTerminatingSpace) {
      if (!includeTerminatingSpace || selection.extentOffset != query.end) {
        return false;
      }
      text = text.replaceRange(query.end, query.end, ' ');
    }
    final replaceEnd = includeTerminatingSpace ? query.end + 1 : query.end;
    if (query.start < 0 ||
        query.end > text.length ||
        replaceEnd > text.length) {
      return false;
    }
    if (includeTerminatingSpace && text.codeUnitAt(query.end) != 0x20) {
      return false;
    }
    if (text.substring(query.start, query.end) != '@${query.query}') {
      return false;
    }
    if (_composerController.hasConfirmedMention(
      start: query.start,
      end: query.end,
    )) {
      _setHomeState(() => _setComposerMentionQuery(null));
      return false;
    }

    final options = _composerMentionOptions;
    if (options.isEmpty) return false;
    final option =
        options[_clampComposerMentionSelectedIndex(
          _composerMentionSelectedIndex,
        )];
    final replacement = message_mentions.messageMentionInsertText(option);
    final nextText = text.replaceRange(query.start, replaceEnd, replacement);
    final nextOffset = query.start + replacement.length;
    final nextValue = value.copyWith(
      text: nextText,
      selection: TextSelection.collapsed(offset: nextOffset),
      composing: TextRange.empty,
    );

    final previousUpdating = _updatingComposerFromDraft;
    _updatingComposerFromDraft = true;
    _composerController.value = nextValue;
    _updatingComposerFromDraft = previousUpdating;
    _composerController.addConfirmedMention(
      start: query.start,
      label: option.label,
    );
    _saveComposerDraftValue(nextText);
    _setHomeState(() => _setComposerMentionQuery(null));
    _composerPanelController.requestInputFocus();
    return true;
  }

  void _mentionUserFromAvatar(UserSummary user) {
    final label = _mentionLabelForUser(user);
    if (label.isEmpty) return;
    final value = _composerController.value;
    final needsLeadingSpace =
        value.text.isNotEmpty && !_endsWithMentionBoundary(value.text);
    final prefix = needsLeadingSpace ? ' ' : '';
    final insertion = '$prefix@$label ';
    final selection = value.selection;
    final insertAt = selection.isValid ? selection.end : value.text.length;
    final nextText = value.text.replaceRange(insertAt, insertAt, insertion);
    final mentionStart = insertAt + prefix.length;
    final nextOffset = insertAt + insertion.length;
    _composerController.value = value.copyWith(
      text: nextText,
      selection: TextSelection.collapsed(offset: nextOffset),
      composing: TextRange.empty,
    );
    _composerController.addConfirmedMention(start: mentionStart, label: label);
    _saveComposerDraftValue(nextText);
    _composerPanelController.requestInputFocus();
  }

  String _mentionLabelForUser(UserSummary user) {
    final roomName = user.roomDisplayName?.trim();
    if (roomName != null && roomName.isNotEmpty) return roomName;
    final displayName = user.displayName.trim();
    if (displayName.isNotEmpty) return displayName;
    return user.username.trim();
  }

  bool _endsWithMentionBoundary(String text) {
    if (text.isEmpty) return true;
    final codeUnit = text.codeUnitAt(text.length - 1);
    return codeUnit <= 0x20 || codeUnit == 0x3000;
  }

  void _saveDraftInState({String? text}) {
    final roomId = _selectedServerId;
    if (roomId == null) return;
    final draftText = text ?? _composerController.text;
    final hasAttachments = _stagedAttachments.isNotEmpty;
    final hasQuote =
        (_messageQuoteDrafts[roomId] ?? const <MessageQuote>[]).isNotEmpty;
    final hasComponent = _messageComponentDrafts.containsKey(roomId);
    final hasVisibleDraft =
        draftText.trim().isNotEmpty ||
        hasAttachments ||
        hasQuote ||
        hasComponent;
    final current = _messageDrafts[roomId];
    final currentAttachments = _stagedAttachmentDrafts[roomId] ?? const [];
    final sameText = current == draftText;
    final sameAttachments = _sameStagedAttachments(
      currentAttachments,
      _stagedAttachments,
    );
    if (!hasVisibleDraft && current == null && currentAttachments.isEmpty) {
      return;
    }
    if (hasVisibleDraft && sameText && sameAttachments) return;

    final nextText = Map<String, String>.of(_messageDrafts);
    final nextAttachments = Map<String, List<_StagedAttachment>>.of(
      _stagedAttachmentDrafts,
    );
    if (hasVisibleDraft) {
      nextText[roomId] = draftText;
      nextAttachments[roomId] = List.unmodifiable(_stagedAttachments);
    } else {
      nextText.remove(roomId);
      nextAttachments.remove(roomId);
    }
    _messageDrafts = Map.unmodifiable(nextText);
    _stagedAttachmentDrafts = Map.unmodifiable(nextAttachments);
  }

  bool _sameStagedAttachments(
    List<_StagedAttachment> a,
    List<_StagedAttachment> b,
  ) {
    if (a.length != b.length) return false;
    for (var index = 0; index < a.length; index++) {
      if (!identical(a[index], b[index])) return false;
    }
    return true;
  }

  void _discardRoomDraftInState(String roomId, {bool cancelUploads = true}) {
    final attachments = _stagedAttachmentDrafts[roomId] ?? const [];
    if (cancelUploads) {
      for (final entry in attachments) {
        entry.uploadController.cancel();
      }
    }
    if (_messageDrafts.containsKey(roomId)) {
      final next = Map<String, String>.of(_messageDrafts)..remove(roomId);
      _messageDrafts = Map.unmodifiable(next);
    }
    if (_stagedAttachmentDrafts.containsKey(roomId)) {
      final next = Map<String, List<_StagedAttachment>>.of(
        _stagedAttachmentDrafts,
      )..remove(roomId);
      _stagedAttachmentDrafts = Map.unmodifiable(next);
    }
    if (_messageQuoteDrafts.containsKey(roomId)) {
      final next = Map<String, List<MessageQuote>>.of(_messageQuoteDrafts)
        ..remove(roomId);
      _messageQuoteDrafts = Map<String, List<MessageQuote>>.unmodifiable(next);
    }
    if (_messageComponentDrafts.containsKey(roomId)) {
      final next = Map<String, Message>.of(_messageComponentDrafts)
        ..remove(roomId);
      _messageComponentDrafts = Map<String, Message>.unmodifiable(next);
    }
  }

  void _cancelAllDraftAttachments() {
    final seen = <_StagedAttachment>{};
    for (final entry in _stagedAttachments) {
      if (seen.add(entry)) entry.uploadController.cancel();
    }
    for (final attachments in _stagedAttachmentDrafts.values) {
      for (final entry in attachments) {
        if (seen.add(entry)) entry.uploadController.cancel();
      }
    }
  }

  bool _isStagedAttachmentActive(_StagedAttachment entry) {
    if (_stagedAttachments.contains(entry)) return true;
    for (final attachments in _stagedAttachmentDrafts.values) {
      if (attachments.contains(entry)) return true;
    }
    return false;
  }

  Map<String, String> get _sidebarRoomDrafts {
    final roomIds = <String>{
      ..._messageDrafts.keys,
      ..._stagedAttachmentDrafts.keys,
      ..._messageQuoteDrafts.keys,
      ..._messageComponentDrafts.keys,
    };
    final previews = <String, String>{};
    for (final roomId in roomIds) {
      final preview = _roomDraftPreviewFor(roomId);
      if (preview != null) previews[roomId] = preview;
    }
    return Map.unmodifiable(previews);
  }

  String? _roomDraftPreviewFor(String roomId) {
    final attachments = _stagedAttachmentDrafts[roomId] ?? const [];
    final attachment = attachments.isEmpty ? null : attachments.first;
    final component = _messageComponentDrafts[roomId];
    if (component != null) {
      return component.playlistAttachment != null
          ? '[歌单] ${component.playlistAttachment!.playlist!.name}'
          : '[歌曲] ${component.musicTrackAttachment!.track!.title}';
    }
    return room_display.roomDraftPreviewText(
      text: _messageDrafts[roomId],
      attachmentFilename: attachment?.file.name,
      attachmentMimeType: attachment?.file.mimeType,
      hasQuote:
          (_messageQuoteDrafts[roomId] ?? const <MessageQuote>[]).isNotEmpty,
    );
  }

  ChatMessageActions get _chatMessageActions {
    return ChatMessageActions(
      onCopy: _copyChatMessage,
      onQuote: _quoteChatMessage,
      onOpenQuote: _openQuotedMessage,
      onDeleteForMe: _deleteMessageForMe,
      onRecall: _recallChatMessage,
      canRecall: _canRecallChatMessage,
      canQuote: _canQuoteChatMessage,
      onReeditRecalledText: _reeditRecalledTextMessage,
      canReeditRecalledText: _canReeditRecalledTextMessage,
      canInspectRecalledText: _canInspectRecalledTextMessage,
      onViewSharedPlaylist: _viewSharedPlaylistMessage,
      sharedTrackPreviewController: _sharedMessageTrackPreviewController,
      loadSharedTrackPlaylists: _loadSharedTrackPlaylists,
      onAddSharedTrackToPlaylist: _addSharedTrackToPlaylist,
    );
  }

  Future<List<MusicTrackPlaylistTarget>> _loadSharedTrackPlaylists(
    String roomId,
  ) async {
    final api = _services.api;
    final targets = <MusicTrackPlaylistTarget>[];
    if (api is PersonalMusicPlaylistApi) {
      final page = await (api as PersonalMusicPlaylistApi)
          .listPersonalMusicPlaylists(pageSize: 50);
      targets.addAll(page.playlists.map(MusicTrackPlaylistTarget.personal));
    }
    if (api is RoomMusicPlaylistApi) {
      final page = await (api as RoomMusicPlaylistApi).listRoomMusicPlaylists(
        roomId: roomId,
        pageSize: 50,
      );
      targets.addAll(
        page.playlists.map(
          (playlist) =>
              MusicTrackPlaylistTarget.room(playlist: playlist, roomId: roomId),
        ),
      );
    }
    return targets;
  }

  Future<void> _addSharedTrackToPlaylist(
    SharedMusicTrack track,
    MusicTrackPlaylistTarget target,
  ) async {
    final api = _services.api;
    final searchResult = MusicBoxSearchResult(
      trackId: track.trackId,
      name: track.title,
      artists: track.artists,
      source: track.source,
    );
    if (target.roomScoped) {
      final roomId = target.roomId;
      if (api is! RoomMusicPlaylistApi || roomId == null || roomId.isEmpty) {
        throw StateError('当前版本不支持添加到房间歌单');
      }
      await (api as RoomMusicPlaylistApi).addRoomMusicPlaylistItem(
        roomId: roomId,
        playlistId: target.playlist.id,
        track: searchResult,
        durationMs: track.durationMs > 0 ? track.durationMs : null,
      );
      return;
    }
    if (api is! PersonalMusicPlaylistApi) {
      throw StateError('当前版本不支持添加到个人歌单');
    }
    await (api as PersonalMusicPlaylistApi).addPersonalMusicPlaylistItem(
      playlistId: target.playlist.id,
      track: searchResult,
      durationMs: track.durationMs > 0 ? track.durationMs : null,
    );
  }

  Future<void> _viewSharedPlaylistMessage(
    BuildContext context,
    Message message,
    SharedMusicPlaylist playlist,
  ) async {
    final api = _services.api;
    if (api is! PersonalMusicPlaylistApi) {
      if (context.mounted) {
        showFloatingErrorNotice(context, '当前版本暂不支持查看歌单');
      }
      return;
    }
    final personalApi = api as PersonalMusicPlaylistApi;
    final tracks = playlist.items
        .map(
          (item) => MusicTrackCardData(
            id: item.id,
            source: item.source,
            trackId: item.trackId,
            title: item.title,
            artists: item.artists,
            durationMs: item.durationMs,
          ),
        )
        .toList(growable: false);
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => MusicPlaylistSnapshotDialog(
        title: playlist.name,
        tracks: tracks,
        previewApi: api is MusicTrackPreviewApi
            ? api as MusicTrackPreviewApi
            : null,
        previewPlatformFactory: widget.musicTrackPreviewPlatformFactory,
        loadPersonalPlaylists: () =>
            personalApi.listPersonalMusicPlaylists(pageSize: 50),
        onAddToPlaylist: (track, target) {
          return personalApi.addPersonalMusicPlaylistItem(
            playlistId: target.id,
            track: MusicBoxSearchResult(
              trackId: track.trackId,
              name: track.title,
              artists: track.artists,
              source: track.source,
            ),
            durationMs: track.durationMs > 0 ? track.durationMs : null,
          );
        },
        onClone: api is SharedMusicPlaylistCloneApi
            ? () => (api as SharedMusicPlaylistCloneApi)
                  .cloneSharedMusicPlaylistToPersonal(
                    roomId: message.roomId,
                    messageId: message.id,
                  )
            : null,
        contentKey: ValueKey<String>(
          'shared-music-playlist-view-${playlist.id}',
        ),
        trackListKey: ValueKey<String>(
          'shared-music-playlist-tracks-${playlist.id}',
        ),
        cloneButtonKey: ValueKey<String>(
          'shared-music-playlist-clone-${playlist.id}',
        ),
        doneButtonKey: ValueKey<String>(
          'shared-music-playlist-done-${playlist.id}',
        ),
        cloneErrorMessage: (error) {
          if (error is! ApiException) return '克隆歌单失败：$error';
          return switch (error.code) {
            'playlist_limit_reached' => '克隆失败：我的歌单已达 50 个上限',
            'not_found' => '克隆失败：该歌单消息已不可用',
            _ => '克隆歌单失败：$error',
          };
        },
      ),
    );
  }

  bool _canQuoteChatMessage(Message message) {
    return !message.pending && !message.failed && !message.isRemoved;
  }

  Future<void> _openQuotedMessage(
    BuildContext context,
    MessageQuote quote,
  ) async {
    final roomId = _selectedServerId;
    if (roomId == null ||
        _locallyDeletedMessageKeys.contains(quote.messageId)) {
      if (context.mounted) {
        showFloatingErrorNotice(context, '原消息已被删除或撤回');
      }
      return;
    }

    Message? target = _messageById(_messages, quote.messageId);
    if (target == null) {
      try {
        final loaded = await _messagesController.loadMessagesUntil(
          roomId: roomId,
          messageId: quote.messageId,
        );
        if (!mounted || _selectedServerId != roomId) return;
        target = _messageById(loaded, quote.messageId);
        if (target != null) {
          _setHomeState(() => _messages = _mergeMessages(_messages, loaded));
        }
      } catch (error) {
        if (context.mounted) {
          showFloatingErrorNotice(
            context,
            userFacingErrorMessage(error, fallback: '无法跳转到原消息'),
          );
        }
        return;
      }
    }

    if (target == null || target.isRemoved || _isMessageDeletedForMe(target)) {
      if (context.mounted) {
        showFloatingErrorNotice(context, '原消息已被删除或撤回');
      }
      return;
    }
    _setHomeState(() {
      _settingsOpen = false;
      _contentMode = _ContentMode.chat;
      _focusedMessageId = quote.messageId;
    });
  }

  Message? _messageById(List<Message> messages, String messageId) {
    for (final message in messages) {
      if (message.id == messageId) return message;
    }
    return null;
  }

  List<Message> _mergeMessages(List<Message> current, List<Message> loaded) {
    final byId = <String, Message>{};
    for (final message in loaded) {
      byId[message.id] = message;
    }
    for (final message in current) {
      byId[message.id] = message;
    }
    final merged = byId.values.toList(growable: false)
      ..sort((a, b) {
        final byTime = a.createdAt.compareTo(b.createdAt);
        return byTime != 0 ? byTime : a.id.compareTo(b.id);
      });
    return merged;
  }

  List<Message> _visibleMessagesForMe(List<Message> messages) {
    if (_locallyDeletedMessageKeys.isEmpty) return messages;
    return [
      for (final message in messages)
        if (!_isMessageDeletedForMe(message)) message,
    ];
  }

  int _visibleNewMessageCount(List<Message> visibleMessages) {
    final count = _selectedRoomNewMessageCount;
    if (count <= 0) return 0;
    final firstUnreadIndex = _messages.length - count;
    if (firstUnreadIndex <= 0) return visibleMessages.length;
    var visibleUnread = 0;
    for (var i = firstUnreadIndex; i < _messages.length; i++) {
      if (!_isMessageDeletedForMe(_messages[i])) visibleUnread++;
    }
    return visibleUnread;
  }

  bool _isMessageDeletedForMe(Message message) {
    return _locallyDeletedMessageKeys.contains(message.id) ||
        _locallyDeletedMessageKeys.contains(message.clientMessageId);
  }

  Future<void> _copyChatMessage(BuildContext context, Message message) async {
    try {
      await _copyChatMessageContents(context, message);
      if (!context.mounted) return;
      _showChatMessageActionNotice(
        context,
        '已复制',
        tone: FloatingNoticeTone.success,
      );
    } catch (error) {
      if (!context.mounted) return;
      _showChatMessageActionNotice(
        context,
        userFacingErrorMessage(error, fallback: '复制消息失败'),
        tone: FloatingNoticeTone.error,
      );
    }
  }

  Future<void> _copyChatMessageContents(
    BuildContext context,
    Message message,
  ) async {
    final isMusicComponent =
        message.playlistAttachment != null ||
        message.musicTrackAttachment != null;
    if (isMusicComponent) {
      final text = message_display.messageClipboardText(message);
      if (text.isEmpty) {
        throw Exception('这条消息没有可复制的内容');
      }
      // Pending messages only carry a local client id and cannot be resolved
      // to the immutable server snapshot used by structured paste.
      _copiedMessageComponent = message.pending ? null : message;
      try {
        await _clipboardService.writeText(text);
      } catch (_) {
        if (identical(_copiedMessageComponent, message)) {
          _copiedMessageComponent = null;
        }
        rethrow;
      }
      return;
    }
    _copiedMessageComponent = null;
    final attachments = _copyableMessageAttachments(message);
    if (attachments.isNotEmpty) {
      await _copyMessageAttachments(context, message, attachments);
      return;
    }
    final text = message_display.messageClipboardText(message);
    if (text.isEmpty) {
      throw Exception('这条消息没有可复制的内容');
    }
    await _clipboardService.writeText(text);
  }

  List<MessageAttachment> _copyableMessageAttachments(Message message) {
    return [
      for (final attachment in message.attachments)
        if (attachment.asset != null &&
            (attachment.type == 'file' ||
                attachment.type == 'sticker' ||
                attachment.type == 'audio'))
          attachment,
    ];
  }

  Future<void> _copyMessageAttachments(
    BuildContext context,
    Message message,
    List<MessageAttachment> attachments,
  ) async {
    final config = AppConfigScope.of(context);
    if (attachments.length == 1) {
      final attachment = attachments.single;
      final asset = attachment.asset;
      final resolvedUrl = config.resolveAssetUrl(asset?.url);
      if (resolvedUrl != null &&
          resolvedUrl.isNotEmpty &&
          file_display.isImageMimeType(asset?.mimeType)) {
        try {
          await _imagePreviewActions.onCopyToClipboard(resolvedUrl);
          return;
        } catch (_) {
          // Fall through to file clipboard so platforms without image clipboard
          // support can still paste the sticker/image as a file.
        }
      }
    }

    final paths = <String>[];
    final fallbackLines = <String>[];
    final body = message.body.trimRight();
    if (body.isNotEmpty) fallbackLines.add(body);
    for (final attachment in attachments) {
      final asset = attachment.asset;
      final resolvedUrl = config.resolveAssetUrl(asset?.url);
      if (resolvedUrl == null || resolvedUrl.isEmpty || asset == null) {
        continue;
      }
      fallbackLines.add(resolvedUrl);
      final path = await _downloadAttachmentToClipboardFile(
        url: resolvedUrl,
        title: _attachmentClipboardFilename(attachment),
        asset: asset,
      );
      if (path != null) paths.add(path);
    }

    if (paths.isNotEmpty && await _clipboardService.writeFilePaths(paths)) {
      return;
    }
    if (fallbackLines.isNotEmpty) {
      await _clipboardService.writeText(fallbackLines.join('\n'));
      return;
    }
    throw Exception('这条消息没有可复制的内容');
  }

  Future<String?> _downloadAttachmentToClipboardFile({
    required String url,
    required String title,
    required UploadedAsset asset,
  }) async {
    final baseDirectory = await getTemporaryDirectory();
    final directory = Directory(
      '${baseDirectory.path}${Platform.pathSeparator}gang-chat-clipboard',
    );
    await directory.create(recursive: true);
    final filename = _safeClipboardFilename(title);
    final path = _uniqueDestinationPath(
      directory: directory.path,
      filename: filename,
    );
    final request = MediaCacheRequest.tryFromUrl(
      url: url,
      filename: filename,
      mimeType: asset.mimeType,
      expectedBytes: asset.sizeBytes,
      namespace: 'clipboard',
    );
    if (request != null) {
      final cached = await _mediaCacheController.getOrDownload(
        request: request,
      );
      await cached.copy(path);
      return path;
    }

    final uri = Uri.tryParse(url);
    if (uri == null) return null;
    final client = http.Client();
    try {
      final response = await client.get(uri);
      if (response.statusCode < 200 || response.statusCode >= 300) {
        return null;
      }
      await File(path).writeAsBytes(response.bodyBytes, flush: true);
      return path;
    } finally {
      client.close();
    }
  }

  String _attachmentClipboardFilename(MessageAttachment attachment) {
    if (attachment.type == 'sticker') {
      return message_display.stickerPreviewFilename(attachment);
    }
    return file_display.fileAttachmentTitle(attachment);
  }

  String _safeClipboardFilename(String value) {
    final basename = file_display.basename(value);
    final safe = basename
        .replaceAll(RegExp(r'[\\/:*?"<>|\x00-\x1F]+'), '_')
        .replaceAll(RegExp(r'_+'), '_')
        .trim();
    return safe.isEmpty || safe == '_' ? 'file' : safe;
  }

  Future<void> _deleteMessageForMe(
    BuildContext context,
    Message message,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => DialogFrame(
        title: '删除消息',
        icon: Icons.delete_outline,
        adaptiveActions: [
          ResponsiveDialogAction(
            label: '取消',
            onPressed: () => Navigator.of(context).pop(false),
          ),
          ResponsiveDialogAction(
            label: '删除',
            tone: ButtonTone.danger,
            onPressed: () => Navigator.of(context).pop(true),
          ),
        ],
        child: Text('这只会从你的聊天记录中删除，其他人仍然可以看到这条消息。', style: UiTypography.body),
      ),
    );
    if (confirmed != true || !mounted) return;
    _setHomeState(() {
      _locallyDeletedMessageKeys
        ..add(message.id)
        ..add(message.clientMessageId);
    });
  }

  Future<void> _recallChatMessage(BuildContext context, Message message) async {
    if (!_canRecallChatMessage(message)) return;
    final recallingOther = message.sender.id != _currentUser.id;
    if (recallingOther) {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => DialogFrame(
          title: '撤回消息',
          icon: Icons.undo_rounded,
          adaptiveActions: [
            ResponsiveDialogAction(
              label: '取消',
              onPressed: () => Navigator.of(context).pop(false),
            ),
            ResponsiveDialogAction(
              label: '撤回',
              tone: ButtonTone.danger,
              onPressed: () => Navigator.of(context).pop(true),
            ),
          ],
          child: Text(
            '确认撤回 ${room_display.userPrimaryName(message.sender)} 的这条消息？',
            style: UiTypography.body,
          ),
        ),
      );
      if (confirmed != true) return;
    }

    try {
      final result = await _messagesController.recallMessage(
        roomId: message.roomId,
        messageId: message.id,
      );
      if (!mounted) return;
      final recalled = result.message;
      if (recalled != null) {
        _setHomeState(() {
          _messages = _messagesController.replaceByMessageId(
            _messages,
            recalled,
          );
        });
        unawaited(_loadServers());
        if (context.mounted) {
          _showChatMessageActionNotice(
            context,
            '已撤回',
            tone: FloatingNoticeTone.success,
          );
        }
        return;
      }
      if (result.isPending && context.mounted) {
        _showChatMessageActionNotice(
          context,
          '已提交撤回申请',
          tone: FloatingNoticeTone.success,
        );
      }
    } catch (error) {
      if (!context.mounted) return;
      _showChatMessageActionNotice(
        context,
        userFacingErrorMessage(error, fallback: '撤回消息失败'),
        tone: FloatingNoticeTone.error,
      );
    }
  }

  bool _canRecallChatMessage(Message message) {
    if (message.pending ||
        message.failed ||
        message.isRemoved ||
        message.type == message_display.kSystemMessageType) {
      return false;
    }
    if (message.sender.id == _currentUser.id) return true;
    final room = _selectedRoom;
    if (room == null || (!room.isAdmin && !_currentUser.isSuperuser)) {
      return false;
    }
    return _currentUserRoomRank(room) > _messageSenderRoomRank(message.sender);
  }

  void _reeditRecalledTextMessage(Message message) {
    if (!_canReeditRecalledTextMessage(message)) return;
    _setComposerText(message.body, saveDraft: true);
    _composerPanelController.closePanel();
    if (_contentMode != _ContentMode.chat) {
      _setHomeState(() => _contentMode = _ContentMode.chat);
    }
  }

  bool _canReeditRecalledTextMessage(Message message) {
    if (!message.isRecalled ||
        message.isForceDeleted ||
        message.type != 'text' ||
        message.body.isEmpty) {
      return false;
    }
    if (message.sender.id != _currentUser.id) return false;
    final actor = message.recalledBy ?? message.sender;
    return actor.id == _currentUser.id;
  }

  bool _canInspectRecalledTextMessage(Message message) {
    if (!message.isRecalled ||
        message.isForceDeleted ||
        message.type != 'text' ||
        message.body.isEmpty) {
      return false;
    }
    final actor = message.recalledBy ?? message.sender;
    if (message.sender.id == _currentUser.id) {
      return actor.id != _currentUser.id;
    }
    final room = _selectedRoom;
    if (room == null) return false;
    return _currentUserRoomRank(room) > _messageSenderRoomRank(message.sender);
  }

  int _currentUserRoomRank(RoomDetail room) {
    if (_currentUser.isSuperuser) return 4;
    return _roomRoleRank(room.myMembership.role);
  }

  int _messageSenderRoomRank(UserSummary sender) {
    if (sender.isSuperuser) return 4;
    return _roomRoleRank(sender.roomRole);
  }

  int _roomRoleRank(String? role) {
    switch ((role ?? '').toLowerCase()) {
      case 'owner':
      case 'creator':
        return 3;
      case 'admin':
      case 'administrator':
        return 2;
      case 'member':
        return 1;
      default:
        return 0;
    }
  }

  void _showChatMessageActionNotice(
    BuildContext context,
    String message, {
    FloatingNoticeTone tone = FloatingNoticeTone.info,
  }) {
    showFloatingNotice(context, message, tone: tone);
  }

  Future<void> _sendText(String value) async {
    final body = value.trimRight();
    final component = _selectedComposerComponent;
    if (component != null) {
      if (body.trim().isNotEmpty) {
        _setHomeState(() => _sendError = '歌单或歌曲组件不能与普通文字同时发送，请先清空文字或移除组件');
        return;
      }
      await _sendComposed(
        body: '',
        type: component.type,
        attachments: [
          MessageAttachment(
            type: component.type,
            sourceMessageId: component.id,
          ),
        ],
        clearComposer: true,
      );
      return;
    }
    // When files are staged, the message goes out as a file message carrying
    // them as attachments (the body rides along). Otherwise it's plain text.
    if (_stagedAttachments.isNotEmpty) {
      await _sendStagedAttachments(body);
      return;
    }
    await _sendComposed(
      body: body,
      type: 'text',
      attachments: const [],
      clearComposer: true,
    );
  }

  Future<void> _sendSticker(Sticker sticker) async {
    final draft = message_display.stickerMessageDraft(sticker);
    await _sendComposed(
      body: draft.body,
      type: draft.type,
      attachments: draft.attachments,
      clearComposer: false,
    );
  }

  Future<void> _sendComposed({
    required String body,
    required String type,
    required List<MessageAttachment> attachments,
    required bool clearComposer,
  }) async {
    final room = _selectedRoom;
    if (room == null || _sending) return;
    if (!canSendComposedMessage(
      body: body,
      type: type,
      attachments: attachments,
    )) {
      return;
    }

    final quotes = _messageQuoteDrafts[room.id] ?? const <MessageQuote>[];
    String? clientMessageId;
    _setHomeState(() {
      _sending = true;
      _sendError = null;
    });

    try {
      final confirmedMentionLabels = _composerController
          .confirmedMentionLabels();
      final sent = await _messagesController.sendComposedMessage(
        roomId: room.id,
        sender: _currentUser.toSummary().copyWith(
          roomDisplayName: _selectedRoom?.personalProfile.displayName,
        ),
        body: body,
        type: type,
        attachments: attachments,
        mentions:
            _composerMentionMembersRoomId == room.id &&
                confirmedMentionLabels.isNotEmpty
            ? message_mentions.messageMentionDescriptors(
                text: body,
                members: _composerMentionMembers,
                confirmedLabels: confirmedMentionLabels,
              )
            : const [],
        quotes: quotes,
        onPending: (pending) {
          clientMessageId = pending.clientMessageId;
          if (!mounted) return;
          _setHomeState(() {
            _messages = _messagesController.patchPendingMessage(
              messages: _messages,
              pending: pending,
            );
            if (quotes.isNotEmpty) {
              final next = Map<String, List<MessageQuote>>.of(
                _messageQuoteDrafts,
              )..remove(room.id);
              _messageQuoteDrafts =
                  Map<String, List<MessageQuote>>.unmodifiable(next);
            }
          });
          if (clearComposer) {
            _setHomeState(() {
              final next = Map<String, Message>.of(_messageComponentDrafts)
                ..remove(room.id);
              _messageComponentDrafts = Map<String, Message>.unmodifiable(next);
            });
            _setComposerText('', saveDraft: true);
          } else if (quotes.isNotEmpty) {
            _saveComposerDraftValue(_composerController.text);
          }
        },
      );
      if (!mounted || _selectedServerId != room.id) return;
      _setHomeState(() {
        _messages = _messagesController.patchSentMessage(
          messages: _messages,
          sent: sent,
        );
      });
      unawaited(_loadServers());
    } catch (error) {
      if (!mounted) return;
      _setHomeState(() {
        _sendError = userFacingErrorMessage(error);
        if (clientMessageId != null) {
          _messages = _messagesController.patchFailedMessage(
            messages: _messages,
            clientMessageId: clientMessageId!,
          );
        }
      });
    } finally {
      if (mounted) _setHomeState(() => _sending = false);
    }
  }

  /// Load the personal + room sticker packs that back the composer panel.
  /// Personal packs are read from the on-disk cache first for an instant
  /// render, then refreshed from the server alongside the room's packs.
  Future<void> _loadStickerPacks({bool forceReload = false}) async {
    final roomId = _selectedServerId;
    if (roomId == null) return;
    if (!sticker_display.shouldLoadStickerPanel(
      state: _stickerPanelState,
      forceReload: forceReload,
    )) {
      return;
    }

    _setHomeState(
      () => _stickerPanelState = sticker_display.stickerPanelLoadStarted(
        _stickerPanelState,
      ),
    );

    try {
      final cachedPersonal = forceReload
          ? null
          : await _stickerPacksController.readCachedPersonalPacks(
              userId: _currentUser.id,
            );
      if (!mounted || _selectedServerId != roomId) return;
      if (cachedPersonal != null) {
        _setHomeState(
          () => _stickerPanelState = sticker_display
              .stickerPanelCachedPersonalApplied(
                state: _stickerPanelState,
                packs: cachedPersonal,
              ),
        );
      }
      final shouldFetchPersonal = forceReload || cachedPersonal == null;
      final packs = await Future.wait([
        shouldFetchPersonal
            ? _stickerPacksController.loadPersonalPacks(
                userId: _currentUser.id,
                forceReload: true,
              )
            : Future<List<StickerPack>>.value(cachedPersonal),
        _stickerPacksController.loadRoomPacks(roomId),
      ]);
      if (!mounted || _selectedServerId != roomId) return;
      _setHomeState(
        () => _stickerPanelState = sticker_display.stickerPanelLoadSucceeded(
          state: _stickerPanelState,
          personalPacks: packs[0],
          roomPacks: packs[1],
        ),
      );
    } catch (error) {
      if (!mounted || _selectedServerId != roomId) return;
      _setHomeState(
        () => _stickerPanelState = sticker_display.stickerPanelLoadFailed(
          state: _stickerPanelState,
          failure: error,
        ),
      );
    } finally {
      if (mounted && _selectedServerId == roomId) {
        _setHomeState(
          () => _stickerPanelState = sticker_display.stickerPanelLoadFinished(
            _stickerPanelState,
          ),
        );
      }
    }
  }

  void _changeStickerSource(sticker_display.StickerPanelSource source) {
    _setHomeState(
      () => _stickerPanelState = sticker_display.stickerPanelSourceChanged(
        _stickerPanelState,
        source,
      ),
    );
  }

  // --- Voice messages ---------------------------------------------------

  /// Begin a click-to-record voice clip. Starts the recorder, then drives a
  /// 1s ticker that updates the displayed duration and stops automatically at
  /// the max length.
  Future<void> _startVoiceRecording() async {
    if (!_voiceState.isIdle || _selectedRoom == null) return;
    try {
      await _voiceRecorder.start();
    } catch (error) {
      if (!mounted) return;
      _setHomeState(
        () => _voiceState = const voice_display.VoiceRecorderState().copyWith(
          error: userFacingErrorMessage(error),
        ),
      );
      return;
    }
    if (!mounted) {
      unawaited(_voiceRecorder.cancel());
      return;
    }
    _voiceStartedAt = DateTime.now();
    _setHomeState(() => _voiceState = voice_display.voiceRecordingStarted());
    _voiceTicker?.cancel();
    _voiceTicker = Timer.periodic(const Duration(seconds: 1), (_) {
      _onVoiceTick();
    });
  }

  void _onVoiceTick() {
    if (!mounted || !_voiceState.isRecording) return;
    final startedAt = _voiceStartedAt;
    if (startedAt == null) return;
    final elapsed = DateTime.now().difference(startedAt);
    _setHomeState(
      () => _voiceState = voice_display.voiceRecordingTicked(
        _voiceState,
        elapsed,
      ),
    );
    if (voice_display.voiceRecordingReachedLimit(elapsed)) {
      unawaited(_stopVoiceRecording());
    }
  }

  /// Stop recording and move to the review state where the user sends or
  /// discards the clip.
  Future<void> _stopVoiceRecording() async {
    if (!_voiceState.isRecording) return;
    _voiceTicker?.cancel();
    _voiceTicker = null;
    final startedAt = _voiceStartedAt;
    final elapsed = startedAt == null
        ? _voiceState.elapsed
        : DateTime.now().difference(startedAt);
    _voiceStartedAt = null;
    String? path;
    try {
      path = await _voiceRecorder.stop();
    } catch (error) {
      if (!mounted) return;
      _setHomeState(
        () => _voiceState = const voice_display.VoiceRecorderState().copyWith(
          error: userFacingErrorMessage(error),
        ),
      );
      return;
    }
    if (!mounted) return;
    _setHomeState(
      () => _voiceState = voice_display.voiceRecordingStopped(
        state: _voiceState,
        path: path,
        elapsed: elapsed,
      ),
    );
  }

  /// Discard the current recording or review clip and reset to idle.
  Future<void> _cancelVoiceRecording() async {
    if (_voiceState.isIdle) return;
    _voiceTicker?.cancel();
    _voiceTicker = null;
    _voiceStartedAt = null;
    final wasRecording = _voiceState.isRecording;
    final path = _voiceState.recordingPath;
    _setHomeState(() => _voiceState = voice_display.voiceRecordingCancelled());
    try {
      if (wasRecording) {
        await _voiceRecorder.cancel();
      } else {
        // Reviewed-but-discarded clip: drop the temp file.
        await _voiceRecorder.discardClip(path);
      }
    } catch (_) {
      // Cleanup failures are non-fatal; the state is already reset.
    }
  }

  /// Upload and send the reviewed voice clip as a playable audio message,
  /// reusing the shared file transfer pipeline (pending bubble + transfer).
  ///
  /// Once the clip's bytes are read and the pending bubble is created we reset
  /// the recorder and retract the voice panel, so the in-flight clip lives in
  /// the message list (with its own pending spinner) instead of trapping the
  /// composer on the send screen. Delivery success/failure is then reflected on
  /// the bubble, mirroring how text and file messages behave.
  Future<void> _sendVoiceMessage() async {
    final room = _selectedRoom;
    final path = _voiceState.recordingPath;
    if (room == null || path == null || !_voiceState.canSend) return;

    final duration = _voiceState.elapsed;
    final quotes = _messageQuoteDrafts[room.id] ?? const <MessageQuote>[];

    _setHomeState(
      () => _voiceState = voice_display.voiceSendStarted(_voiceState),
    );

    Uint8List bytes;
    try {
      bytes = await _voiceRecorder.readClip(path);
    } catch (error) {
      if (!mounted) return;
      // Read failed before any bubble existed — keep the review panel open so
      // the user can retry sending the same clip.
      _setHomeState(
        () => _voiceState = voice_display.voiceSendFailed(
          state: _voiceState,
          failure: error,
        ),
      );
      return;
    }

    // Bytes are buffered in memory now; the clip file is no longer needed.
    unawaited(_voiceRecorder.discardClip(path));

    final filename = voice_display.voiceMessageFilename(DateTime.now());
    String? clientMessageId;
    try {
      final sent = await _messagesController.sendVoiceMessage(
        roomId: room.id,
        sender: _currentUser.toSummary().copyWith(
          roomDisplayName: _selectedRoom?.personalProfile.displayName,
        ),
        filename: filename,
        sizeBytes: bytes.length,
        mimeType: voice_display.kVoiceMessageMimeType,
        duration: duration,
        quotes: quotes,
        readBytes: () async => bytes,
        onPending: (pending) {
          clientMessageId = pending.clientMessageId;
          if (!mounted) return;
          _setHomeState(() {
            _applyFileMessageStatePatch(
              _messagesController.patchPendingFileMessage(
                messages: _messages,
                fileTransfers: _fileTransfers,
                pending: pending,
              ),
            );
            // The clip now lives as a pending bubble; retract the recorder.
            _voiceState = voice_display.voiceSendSucceeded();
            if (quotes.isNotEmpty) {
              final next = Map<String, List<MessageQuote>>.of(
                _messageQuoteDrafts,
              )..remove(room.id);
              _messageQuoteDrafts =
                  Map<String, List<MessageQuote>>.unmodifiable(next);
              _saveDraftInState();
            }
          });
          _composerPanelController.closePanel();
        },
        onProgress: (pending, {required sentBytes, required totalBytes}) {
          if (!mounted) return;
          final patch = _messagesController.patchFileTransferProgress(
            messages: _messages,
            fileTransfers: _fileTransfers,
            pending: pending,
            sentBytes: sentBytes,
            totalBytes: totalBytes,
          );
          if (patch == null) return;
          _setHomeState(() => _applyFileMessageStatePatch(patch));
        },
        onUploaded: (pending, attachment) {
          if (!mounted) return;
          _setHomeState(
            () => _applyFileMessageStatePatch(
              _messagesController.patchUploadedFileMessage(
                messages: _messages,
                fileTransfers: _fileTransfers,
                pending: pending,
                attachment: attachment,
              ),
            ),
          );
        },
      );
      if (!mounted || _selectedServerId != room.id) return;
      final activeClientMessageId = clientMessageId;
      if (activeClientMessageId != null) {
        _setHomeState(
          () => _applyFileMessageStatePatch(
            _messagesController.patchSentFileMessage(
              messages: _messages,
              fileTransfers: _fileTransfers,
              clientMessageId: activeClientMessageId,
              sent: sent,
            ),
          ),
        );
      }
      unawaited(_loadServers());
    } catch (error) {
      if (!mounted) return;
      final activeClientMessageId = clientMessageId;
      if (activeClientMessageId != null) {
        // The pending bubble owns the failure now — surface "发送失败" on it.
        _setHomeState(
          () => _applyFileMessageStatePatch(
            _messagesController.patchFailedFileMessage(
              messages: _messages,
              fileTransfers: _fileTransfers,
              clientMessageId: activeClientMessageId,
              failure: error,
            ),
          ),
        );
      } else {
        // Failed before the bubble existed — fall back to the review panel.
        _setHomeState(
          () => _voiceState = voice_display.voiceSendFailed(
            state: _voiceState,
            failure: error,
          ),
        );
      }
    }
  }

  /// Stop the in-progress recording and immediately send it. Mirrors the
  /// "发送" control shown while recording.
  Future<void> _finishAndSendVoice() async {
    if (_voiceState.isRecording) {
      await _stopVoiceRecording();
      if (!mounted) return;
    }
    if (_voiceState.canSend) {
      await _sendVoiceMessage();
    }
  }

  /// Apply a [FileMessageStatePatch] to the local message + transfer maps.
  void _applyFileMessageStatePatch(FileMessageStatePatch patch) {
    _messages = patch.messages;
    _fileTransfers = patch.fileTransfers;
  }

  // --- Composer attachments ---------------------------------------------
  //
  // Files picked here upload immediately (eagerly), so by send time the assets
  // are usually already in hand and the message goes out in one round trip. The
  // message itself still rides the shared composed-send path as a `file` message
  // carrying the assets as attachments — a workaround until the backend offers a
  // single multipart "send message with files" call. Eager upload can leave an
  // orphan asset behind if the user removes a chip or never sends; that cleanup
  // is the backend's responsibility (asset TTL / unreferenced sweep).

  /// View models for the chips shown above the composer input.
  List<composer_attachment.ComposerAttachmentView> get _stagedAttachmentViews {
    return [
      for (final entry in _stagedAttachments)
        composer_attachment.ComposerAttachmentView(
          id: entry.id,
          filename: entry.file.name,
          status: entry.status,
          sizeBytes: entry.sizeBytes,
          mimeType: entry.file.mimeType,
          progress: entry.progress,
          errorMessage: entry.errorMessage,
        ),
    ];
  }

  /// Open the system file picker (multi-select), stage the chosen files on the
  /// composer, and start uploading each one right away. Does not send anything;
  /// the finished assets go out with the next message.
  Future<void> _pickAttachments() async {
    if (_selectedRoom == null || _pickingAttachments) return;
    List<SelectedFile> files;
    _setHomeState(() {
      _pickingAttachments = true;
      _sendError = null;
    });
    try {
      files = await _fileSelectionService.openFiles();
    } catch (error) {
      if (!mounted) return;
      _setHomeState(() => _sendError = userFacingErrorMessage(error));
      return;
    } finally {
      if (mounted) _setHomeState(() => _pickingAttachments = false);
    }
    if (!mounted || files.isEmpty) return;

    _stageAttachmentFiles(files);
  }

  /// Stage any files/image on the clipboard as attachments. Returns true when
  /// something was staged, so the paste handler can suppress the default text
  /// paste — on macOS a copied file also exposes its name as plain text, which
  /// would otherwise land in the composer.
  Future<bool> _canPasteAttachments() async {
    if (_selectedRoom == null) return false;
    try {
      if (await _clipboardContainsCopiedMessageComponent()) return true;
      final paths = await _clipboardService.readFilePaths();
      if (file_display.normalizedFilePaths(paths).isNotEmpty) return true;
      final image = await _clipboardService.readImageFile();
      return image != null;
    } catch (_) {
      return false;
    }
  }

  Future<bool> _pasteAttachments() async {
    if (_selectedRoom == null) return false;
    try {
      if (await _clipboardContainsCopiedMessageComponent()) {
        if (!mounted) return false;
        if (_composerController.text.trim().isNotEmpty) {
          _setHomeState(() => _sendError = '请先清空输入内容，再粘贴歌单或歌曲组件');
          return true;
        }
        if (_stagedAttachments.isNotEmpty) {
          _setHomeState(() => _sendError = '请先移除已选文件，再粘贴歌单或歌曲');
          return true;
        }
        final roomId = _selectedServerId;
        final component = _copiedMessageComponent;
        if (roomId == null || component == null) return false;
        _setHomeState(() {
          final next = Map<String, Message>.of(_messageComponentDrafts);
          next[roomId] = component;
          _messageComponentDrafts = Map<String, Message>.unmodifiable(next);
          _sendError = null;
          _saveDraftInState();
        });
        _composerPanelController.closePanel();
        _composerPanelController.requestInputFocus();
        return true;
      }
      final paths = await _clipboardService.readFilePaths();
      if (!mounted) return false;
      final normalized = file_display.normalizedFilePaths(paths);
      if (normalized.isNotEmpty) {
        _stageAttachmentPaths(normalized);
        return true;
      }
      final image = await _clipboardService.readImageFile();
      if (!mounted || image == null) return false;
      final filename = file_display.clipboardImageUploadFilename(
        timestamp: DateTime.now(),
        sequence: ++_clipboardImagePasteSerial,
        mimeType: image.mimeType,
      );
      _stageAttachmentFiles([
        SelectedFile.fromBytes(
          name: filename,
          mimeType: image.mimeType,
          bytes: image.bytes,
        ),
      ]);
      return true;
    } catch (error) {
      if (!mounted) return false;
      _setHomeState(
        () => _sendError = file_display.clipboardFilesReadFailureMessage(error),
      );
      return false;
    }
  }

  Future<bool> _clipboardContainsCopiedMessageComponent() async {
    final component = _copiedMessageComponent;
    if (component == null || component.isRemoved) return false;
    final clipboardText = await _clipboardService.readText();
    return message_display.messageClipboardTextMatches(
      component,
      clipboardText,
    );
  }

  void _handleDroppedFiles(FileDropEvent event) {
    if (!_composerContainsDropPoint(event.x, event.y)) return;
    _stageAttachmentPaths(event.paths);
  }

  bool _composerContainsDropPoint(double x, double y) {
    if (_selectedRoom == null ||
        _settingsOpen ||
        _contentMode != _ContentMode.chat) {
      return false;
    }
    final context = _composerDropKey.currentContext;
    final renderObject = context?.findRenderObject();
    if (renderObject is! RenderBox || !renderObject.hasSize) return false;
    final topLeft = renderObject.localToGlobal(Offset.zero);
    return (topLeft & renderObject.size).contains(Offset(x, y));
  }

  void _stageAttachmentPaths(Iterable<String> paths) {
    final normalized = file_display.normalizedFilePaths(paths);
    if (normalized.isEmpty) return;
    _stageAttachmentFiles(_fileSelectionService.filesFromPaths(normalized));
  }

  void _stageAttachmentFiles(List<SelectedFile> files) {
    if (_selectedRoom == null || files.isEmpty) return;
    final fresh = <_StagedAttachment>[];
    _setHomeState(() {
      final roomId = _selectedServerId;
      if (roomId != null && _messageComponentDrafts.containsKey(roomId)) {
        final next = Map<String, Message>.of(_messageComponentDrafts)
          ..remove(roomId);
        _messageComponentDrafts = Map<String, Message>.unmodifiable(next);
      }
      for (final file in files) {
        final entry = _StagedAttachment(
          id: _messagesController.mintClientId('att'),
          file: file,
        );
        _stagedAttachments.add(entry);
        fresh.add(entry);
      }
      _saveDraftInState();
      _sendError = null;
    });

    for (final entry in fresh) {
      unawaited(_uploadStagedAttachment(entry));
    }
  }

  /// Upload (or re-upload) a single staged file, streaming progress into its
  /// chip. Safe to call again after a failure to retry.
  Future<void> _uploadStagedAttachment(_StagedAttachment entry) async {
    if (!_isStagedAttachmentActive(entry)) return;
    _setHomeState(() {
      entry.status = composer_attachment.ComposerAttachmentStatus.uploading;
      entry.progress = null;
      entry.error = null;
      entry.errorMessage = null;
    });

    try {
      final bytes = await entry.file.readAsBytes();
      // Drop out if the chip was removed (or the room switched) while reading.
      if (!_isStagedAttachmentActive(entry)) return;
      if (bytes.isEmpty) throw StateError(file_display.fileEmptyMessage());
      final asset = await _messagesController.uploadFileAsset(
        bytes: bytes,
        filename: entry.file.name,
        controller: entry.uploadController,
        onProgress: ({required sentBytes, required totalBytes}) {
          if (!mounted || !_isStagedAttachmentActive(entry)) return;
          _setHomeState(() {
            entry.sizeBytes = totalBytes;
            entry.progress = totalBytes > 0 ? sentBytes / totalBytes : null;
          });
        },
      );
      if (!_isStagedAttachmentActive(entry)) return;
      _setHomeState(() {
        entry.asset = asset;
        entry.sizeBytes = asset.sizeBytes;
        entry.progress = 1;
        entry.status = composer_attachment.ComposerAttachmentStatus.uploaded;
      });
    } on UploadCancelledException {
      // Cancellation means the chip was removed; nothing left to update.
    } catch (error) {
      if (!mounted || !_isStagedAttachmentActive(entry)) return;
      _setHomeState(() {
        entry.error = error;
        entry.errorMessage = _stagedAttachmentErrorMessage(error);
        entry.status = composer_attachment.ComposerAttachmentStatus.failed;
      });
    }
  }

  /// Retry a staged upload that previously failed.
  void _retryAttachment(String id) {
    final entry = _stagedAttachmentById(id);
    if (entry == null || entry.isUploaded) return;
    unawaited(_uploadStagedAttachment(entry));
  }

  /// Drop a single staged file, cancelling its upload if still in flight.
  void _removeAttachment(String id) {
    final index = _stagedAttachments.indexWhere((entry) => entry.id == id);
    if (index < 0) return;
    final entry = _stagedAttachments[index];
    entry.uploadController.cancel();
    _setHomeState(() {
      _stagedAttachments.removeAt(index);
      _saveDraftInState();
    });
  }

  _StagedAttachment? _stagedAttachmentById(String id) {
    for (final entry in _stagedAttachments) {
      if (entry.id == id) return entry;
    }
    return null;
  }

  /// Send the staged files as one composed message alongside any typed [body].
  /// Uploads were kicked off at pick time, so this waits for any still in
  /// flight, refuses to send while uploads are failed, then collects the
  /// finished assets.
  Future<void> _sendStagedAttachments(String body) async {
    final room = _selectedRoom;
    if (room == null || _stagedAttachments.isEmpty || _sending) return;

    // Wait for any uploads still in flight before deciding what to send.
    final pending = _stagedAttachments
        .where(
          (entry) =>
              entry.status ==
              composer_attachment.ComposerAttachmentStatus.uploading,
        )
        .toList();
    if (pending.isNotEmpty) {
      _setHomeState(() {
        _sending = true;
        _sendError = null;
      });
      await Future.wait(pending.map(_awaitUpload));
      if (!mounted || _selectedServerId != room.id) {
        if (mounted) _setHomeState(() => _sending = false);
        return;
      }
      _setHomeState(() => _sending = false);
    }

    // Refuse to send a partial batch; surface the failures for retry/removal.
    if (_stagedAttachments.any(
      (entry) =>
          entry.status == composer_attachment.ComposerAttachmentStatus.failed,
    )) {
      _setHomeState(() => _sendError = '部分文件上传失败，请重试或移除后再发送');
      return;
    }

    final attachments = [
      for (final entry in _stagedAttachments)
        if (entry.asset != null)
          _messagesController.fileAttachment(
            name: entry.file.name,
            asset: entry.asset!,
          ),
    ];
    if (attachments.isEmpty) return;

    // Hand off to the shared composed send (which owns the pending/sent/failed
    // bubble and the _sending flag).
    _setHomeState(() {
      _stagedAttachments.clear();
    });
    await _sendComposed(
      body: body,
      type: 'file',
      attachments: attachments,
      clearComposer: true,
    );
  }

  /// Block until [entry]'s in-flight upload settles (success or failure).
  Future<void> _awaitUpload(_StagedAttachment entry) async {
    while (mounted &&
        _stagedAttachments.contains(entry) &&
        entry.status ==
            composer_attachment.ComposerAttachmentStatus.uploading) {
      await Future<void>.delayed(const Duration(milliseconds: 50));
    }
  }

  String _stagedAttachmentErrorMessage(Object error) {
    if (error is StateError) return error.message;
    return userFacingErrorMessage(error, fallback: '上传失败，请重试');
  }
}
