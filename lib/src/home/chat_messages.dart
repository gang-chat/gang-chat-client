part of 'chat_pane.dart';

/// How close (in logical pixels) to the newest message counts as "at bottom"
/// for saved scroll anchors and the jump-to-latest affordance.
const double _autoScrollFollowThreshold = 120;
const double _chatMessageListTopPadding = 18;
const _messageListCacheExtent = ScrollCacheExtent.pixels(1200);
const _messageBubblePadding = EdgeInsets.symmetric(
  horizontal: 12,
  vertical: 10,
);

/// A remembered scroll position for one room's message list. The list widget is
/// torn down and rebuilt whenever the pane swaps (e.g. opening the live
/// channel and coming back), so we stash the last position here keyed by room
/// id and restore it on the next mount instead of snapping to the bottom.
class _ScrollAnchor {
  const _ScrollAnchor({required this.offset, required this.atBottom});

  final double offset;
  final bool atBottom;
}

class _ViewportAnchor {
  const _ViewportAnchor({required this.clientMessageId, required this.top});

  final String clientMessageId;
  final double top;
}

/// Callbacks for file-attachment downloads, bundled so they can travel from
/// [ChatPane] down to each [_FileAttachmentTile] without threading five
/// separate parameters through every intermediate widget.
class ChatFileDownloadActions {
  const ChatFileDownloadActions({
    required this.onDownload,
    required this.onPause,
    required this.onResume,
    required this.onCancel,
    required this.onDismiss,
  });

  /// Start downloading [attachment] (the [index]th attachment of [message]).
  /// [resolvedUrl] is the absolute asset URL, already resolved by the widget
  /// layer against [AppConfig].
  final void Function(
    Message message,
    MessageAttachment attachment,
    int index,
    String resolvedUrl,
  )
  onDownload;
  final ValueChanged<String> onPause;
  final ValueChanged<String> onResume;
  final ValueChanged<String> onCancel;
  final ValueChanged<String> onDismiss;
}

class ChatVoicePlaybackActions {
  const ChatVoicePlaybackActions({
    required this.activeMessageId,
    required this.onToggle,
    this.activePosition = Duration.zero,
    this.activeDuration = Duration.zero,
  });

  const ChatVoicePlaybackActions.disabled()
    : activeMessageId = null,
      onToggle = null,
      activePosition = Duration.zero,
      activeDuration = Duration.zero;

  final String? activeMessageId;
  final Duration activePosition;
  final Duration activeDuration;
  final void Function(String messageId, String resolvedUrl)? onToggle;

  bool isPlaying(String messageId) {
    return activeMessageId == messageId;
  }

  double progressFor(String messageId, {Duration? fallbackDuration}) {
    if (!isPlaying(messageId)) return 0;
    final duration = activeDuration > Duration.zero
        ? activeDuration
        : fallbackDuration;
    return voice_display.voicePlaybackProgress(
      position: activePosition,
      duration: duration,
    );
  }
}

class ChatMessageActions {
  const ChatMessageActions({
    required this.onCopy,
    this.onQuote = _syncNoop,
    this.onOpenQuote = _noopQuote,
    required this.onDeleteForMe,
    required this.onRecall,
    required this.canRecall,
    this.canQuote = _neverCanRecall,
    this.onReeditRecalledText = _syncNoop,
    this.canReeditRecalledText = _neverCanRecall,
    this.canInspectRecalledText = _neverCanRecall,
    this.onViewSharedPlaylist = _noopSharedPlaylist,
  });

  const ChatMessageActions.disabled()
    : onCopy = _noop,
      onQuote = _syncNoop,
      onOpenQuote = _noopQuote,
      onDeleteForMe = _noop,
      onRecall = _noop,
      canRecall = _neverCanRecall,
      canQuote = _neverCanRecall,
      onReeditRecalledText = _syncNoop,
      canReeditRecalledText = _neverCanRecall,
      canInspectRecalledText = _neverCanRecall,
      onViewSharedPlaylist = _noopSharedPlaylist;

  final Future<void> Function(BuildContext context, Message message) onCopy;
  final void Function(Message message) onQuote;
  final Future<void> Function(BuildContext context, MessageQuote quote)
  onOpenQuote;
  final Future<void> Function(BuildContext context, Message message)
  onDeleteForMe;
  final Future<void> Function(BuildContext context, Message message) onRecall;
  final bool Function(Message message) canRecall;
  final bool Function(Message message) canQuote;
  final void Function(Message message) onReeditRecalledText;
  final bool Function(Message message) canReeditRecalledText;
  final bool Function(Message message) canInspectRecalledText;
  final Future<void> Function(
    BuildContext context,
    Message message,
    SharedMusicPlaylist playlist,
  )
  onViewSharedPlaylist;

  static Future<void> _noop(BuildContext context, Message message) async {}
  static Future<void> _noopQuote(
    BuildContext context,
    MessageQuote quote,
  ) async {}
  static void _syncNoop(Message message) {}

  static bool _neverCanRecall(Message message) => false;

  static Future<void> _noopSharedPlaylist(
    BuildContext context,
    Message message,
    SharedMusicPlaylist playlist,
  ) async {}
}

class _MessageStage extends StatefulWidget {
  const _MessageStage({
    super.key,
    required this.roomId,
    required this.currentUser,
    required this.currentUserRoomDisplayName,
    required this.currentUserRoomRole,
    required this.ownerUserId,
    required this.roomReady,
    required this.loading,
    required this.error,
    required this.timestampNow,
    required this.messages,
    required this.newMessageCount,
    required this.focusMessageId,
    required this.onFocusMessageHandled,
    required this.mentionMembers,
    required this.mentionMembersReady,
    required this.fileTransfers,
    required this.fileDownloads,
    required this.live,
    required this.downloadActions,
    required this.voicePlaybackActions,
    required this.imagePreviewActions,
    required this.messageActions,
    required this.onRetry,
    required this.onViewedNewMessages,
    required this.bottomInset,
    this.onResolveSenderProfile,
    this.onResolveRoomProfile,
    this.onEnterProfileRoom,
    this.senderProfileActionBuilder,
    this.onMentionUser,
  });

  final String? roomId;
  final CurrentUser currentUser;
  final String currentUserRoomDisplayName;
  final String? currentUserRoomRole;
  final String? ownerUserId;
  final bool roomReady;
  final bool loading;
  final String? error;
  final DateTime timestampNow;
  final List<Message> messages;
  final int newMessageCount;
  final String? focusMessageId;
  final ValueChanged<String>? onFocusMessageHandled;
  final List<RoomMember> mentionMembers;
  final bool mentionMembersReady;
  final Map<String, FileTransferState> fileTransfers;
  final Map<String, FileTransferState> fileDownloads;
  final LiveState? live;
  final ChatFileDownloadActions downloadActions;
  final ChatVoicePlaybackActions voicePlaybackActions;
  final ChatImagePreviewActions imagePreviewActions;
  final ChatMessageActions messageActions;
  final VoidCallback onRetry;
  final VoidCallback onViewedNewMessages;
  // Breathing room at the bottom of the list, between the last message and the
  // composer row that now sits below it.
  final double bottomInset;
  final Future<UserSummary> Function(UserSummary sender)?
  onResolveSenderProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterProfileRoom;
  final UserProfileActionBuilder? senderProfileActionBuilder;
  final ValueChanged<UserSummary>? onMentionUser;

  @override
  State<_MessageStage> createState() => _MessageStageState();
}

class _MessageStageState extends State<_MessageStage> {
  // Last known scroll position per room, surviving widget teardown when the
  // pane swaps (live channel, settings, etc.). Static so it outlives the State.
  static final Map<String, _ScrollAnchor> _scrollAnchors = {};

  bool _showDetailedTimestamps = false;
  bool _showLatestButton = false;
  bool _showNewMessageJumpButton = false;
  bool _newMessageJumpDismissed = false;
  String? _retainedNewMessageClientId;
  int _retainedNewMessageLabelCount = 0;
  late final ScrollController _scrollController;
  final GlobalKey _messageListKey = GlobalKey();
  final GlobalKey _oldestMessageKey = GlobalKey();
  final GlobalKey _firstNewMessageKey = GlobalKey();
  final Map<String, GlobalKey> _messageRowKeys = {};
  final Set<String> _viewedUnreadMentionClientIds = {};
  String? _highlightedMentionClientId;
  String? _highlightedMessageId;
  String? _handledFocusMessageId;
  Timer? _highlightedMentionTimer;
  Timer? _highlightedMessageTimer;
  double _underflowBottomSpacer = 0;
  bool _underflowAlignmentScheduled = false;
  bool _latestButtonVisibilityScheduled = false;
  bool _newMessageJumpVisibilityScheduled = false;
  bool _messageListReady = false;
  bool _incomingUnreadWaitingForUserScroll = false;
  bool _restoringIncomingViewport = false;
  // Guards queued scroll requests so overlapping requests don't stack up; the newest
  // request wins by bumping this token.
  int _scrollToBottomToken = 0;

  @override
  void initState() {
    super.initState();
    _captureNewMessageAnchorFromWidget();
    // Seed the controller with the remembered offset so the very first layout
    // lands in place — restoring via a post-frame jump would render one frame
    // pinned to the latest message first, which reads as a flash.
    final anchor = _anchorForRoom(widget.roomId);
    final restoring = anchor != null && !anchor.atBottom;
    _scrollController = ScrollController(
      initialScrollOffset: restoring ? anchor.offset : 0,
    );
    _scrollController.addListener(_handleScrollChanged);
    // If we're not restoring a browsed-history position, land at the newest
    // message when the room first opens.
    if (!restoring) {
      _scrollToBottom(animated: false);
    }
    _scheduleLatestButtonVisibilitySync();
    _prepareFocusedMessageHighlight(widget.focusMessageId);
    _scheduleFocusedMessageJump(widget.focusMessageId);
  }

  @override
  void didUpdateWidget(covariant _MessageStage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.focusMessageId != widget.focusMessageId &&
        widget.focusMessageId == null) {
      _handledFocusMessageId = null;
    }
    if (widget.focusMessageId != null &&
        oldWidget.messages.isNotEmpty &&
        widget.messages.isEmpty) {
      _highlightedMessageTimer?.cancel();
      _highlightedMessageTimer = null;
      _highlightedMessageId = null;
      _handledFocusMessageId = null;
    }
    if (oldWidget.roomId != widget.roomId) {
      _showDetailedTimestamps = false;
      _showLatestButton = false;
      _showNewMessageJumpButton = false;
      _newMessageJumpDismissed = false;
      _viewedUnreadMentionClientIds.clear();
      _highlightedMentionClientId = null;
      _highlightedMessageId = null;
      _handledFocusMessageId = null;
      _highlightedMentionTimer?.cancel();
      _highlightedMentionTimer = null;
      _highlightedMessageTimer?.cancel();
      _highlightedMessageTimer = null;
      _incomingUnreadWaitingForUserScroll = false;
      _restoringIncomingViewport = false;
      _clearRetainedNewMessageAnchor();
      _captureNewMessageAnchorFromWidget();
      _messageRowKeys.clear();
      _underflowBottomSpacer = 0;
      _messageListReady = false;
      // New room: restore its remembered position, or snap to latest.
      final anchor = _anchorForRoom(widget.roomId);
      if (anchor != null && !anchor.atBottom) {
        _restoreScroll(anchor.offset);
      } else {
        _scrollToBottom(animated: false);
      }
      _prepareFocusedMessageHighlight(widget.focusMessageId);
      _scheduleFocusedMessageJump(widget.focusMessageId);
      return;
    }
    if (oldWidget.focusMessageId != widget.focusMessageId ||
        oldWidget.messages.length != widget.messages.length) {
      _prepareFocusedMessageHighlight(widget.focusMessageId);
      _scheduleFocusedMessageJump(widget.focusMessageId);
    }
    final addedMessages = _addedMessagesSince(oldWidget);
    final preserveIncomingViewport = _shouldPreserveViewportForIncoming(
      addedMessages,
    );
    final viewportAnchor = preserveIncomingViewport
        ? _captureViewportAnchorFrom(oldWidget.messages)
        : null;

    if (oldWidget.newMessageCount != widget.newMessageCount) {
      _showNewMessageJumpButton = false;
      if (widget.newMessageCount > 0) {
        _newMessageJumpDismissed = false;
        _viewedUnreadMentionClientIds.removeWhere(
          (id) => !_currentUnreadMentionClientIds.contains(id),
        );
        _captureNewMessageAnchorFromWidget();
      } else {
        _incomingUnreadWaitingForUserScroll = false;
        _viewedUnreadMentionClientIds.clear();
      }
    }
    if (oldWidget.messages.length != widget.messages.length ||
        (oldWidget.messages.isNotEmpty &&
            widget.messages.isNotEmpty &&
            oldWidget.messages.last.clientMessageId !=
                widget.messages.last.clientMessageId)) {
      _underflowBottomSpacer = 0;
      _showNewMessageJumpButton = false;
      if (widget.newMessageCount > 0) {
        _newMessageJumpDismissed = false;
        _captureNewMessageAnchorFromWidget();
      }
    }
    if (_shouldFollowToBottom(oldWidget, addedMessages)) {
      _scrollToBottom(animated: true);
    } else if (viewportAnchor != null) {
      _incomingUnreadWaitingForUserScroll =
          _scrollController.hasClients &&
          _scrollController.position.maxScrollExtent > 0;
      _restoreViewportAnchorAfterIncoming(viewportAnchor);
    }
  }

  @override
  void dispose() {
    _rememberScrollPosition();
    _highlightedMentionTimer?.cancel();
    _highlightedMessageTimer?.cancel();
    _scrollController.removeListener(_handleScrollChanged);
    _scrollController.dispose();
    super.dispose();
  }

  _ScrollAnchor? _anchorForRoom(String? roomId) {
    if (roomId == null) return null;
    return _scrollAnchors[roomId];
  }

  // Records the live scroll offset (and whether it's pinned to the latest
  // message) so a later remount can restore it. The list is reversed, so the
  // newest message sits at the minimum scroll extent — "at bottom" means the
  // offset is within the follow threshold of it. Called on every scroll and on
  // dispose.
  void _rememberScrollPosition() {
    final roomId = widget.roomId;
    if (roomId == null || !_scrollController.hasClients) return;
    final position = _scrollController.position;
    _scrollAnchors[roomId] = _ScrollAnchor(
      offset: position.pixels,
      atBottom: _distanceFromBottom() <= _autoScrollFollowThreshold,
    );
  }

  // Restores a remembered offset once the list has laid out, clamped to the
  // current scroll extent in case the content shrank while we were away.
  void _restoreScroll(double offset) {
    final token = ++_scrollToBottomToken;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted ||
          token != _scrollToBottomToken ||
          !_scrollController.hasClients) {
        return;
      }
      final position = _scrollController.position;
      _scrollController.jumpTo(offset.clamp(0.0, position.maxScrollExtent));
      _rememberScrollPosition();
      _syncLatestButtonVisibility();
    });
  }

  List<Message> _addedMessagesSince(_MessageStage oldWidget) {
    if (widget.messages.isEmpty) return const [];
    final grew = widget.messages.length > oldWidget.messages.length;
    final lastChanged =
        oldWidget.messages.isNotEmpty &&
        widget.messages.isNotEmpty &&
        oldWidget.messages.last.clientMessageId !=
            widget.messages.last.clientMessageId;
    if (!grew && !lastChanged) return const [];
    final oldClientMessageIds = {
      for (final message in oldWidget.messages) message.clientMessageId,
    };
    return [
      for (final message in widget.messages)
        if (!oldClientMessageIds.contains(message.clientMessageId)) message,
    ];
  }

  bool _shouldPreserveViewportForIncoming(List<Message> addedMessages) {
    if (widget.newMessageCount <= 0 || addedMessages.isEmpty) return false;
    return addedMessages.any(
      (message) => message.sender.id != widget.currentUser.id,
    );
  }

  // Decides whether an update introduced a new message worth following. We
  // always chase our own outgoing messages; messages from others remain unread
  // until the divider actually enters the viewport.
  bool _shouldFollowToBottom(
    _MessageStage oldWidget,
    List<Message> addedMessages,
  ) {
    final messages = widget.messages;
    if (messages.isEmpty) return false;
    if (addedMessages.isNotEmpty) {
      return addedMessages.last.sender.id == widget.currentUser.id;
    }
    final lastChanged =
        oldWidget.messages.isNotEmpty &&
        messages.last.clientMessageId !=
            oldWidget.messages.last.clientMessageId;
    if (!lastChanged) return false;
    if (messages.last.sender.id == widget.currentUser.id) return true;
    return false;
  }

  bool _isNearBottom() {
    if (!_scrollController.hasClients) return true;
    return _distanceFromBottom() <= _autoScrollFollowThreshold;
  }

  double _distanceFromBottom() {
    final position = _scrollController.position;
    return math.max(0, position.pixels - position.minScrollExtent);
  }

  bool _userInLive(String userId) {
    return live_display.liveParticipantByUserId(widget.live, userId) != null;
  }

  UserSummary get _currentUserMentionIdentity {
    return widget.currentUser.toSummary().copyWith(
      roomDisplayName: widget.currentUserRoomDisplayName,
      roomRole: widget.currentUserRoomRole,
    );
  }

  void _handleScrollChanged() {
    if (_incomingUnreadWaitingForUserScroll && !_restoringIncomingViewport) {
      _incomingUnreadWaitingForUserScroll = false;
    }
    _rememberScrollPosition();
    _syncLatestButtonVisibility();
    _scheduleNewMessageJumpVisibilitySync();
  }

  void _syncLatestButtonVisibility() {
    if (!_scrollController.hasClients) return;
    final next = !_isNearBottom();
    if (next == _showLatestButton) return;
    setState(() => _showLatestButton = next);
  }

  void _scheduleLatestButtonVisibilitySync() {
    if (_latestButtonVisibilityScheduled) return;
    _latestButtonVisibilityScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _latestButtonVisibilityScheduled = false;
      if (!mounted) return;
      _rememberScrollPosition();
      _syncLatestButtonVisibility();
    });
  }

  void _captureNewMessageAnchorFromWidget() {
    final count = _effectiveWidgetNewMessageCount;
    if (count <= 0) return;
    final index = widget.messages.length - count;
    if (index < 0 || index >= widget.messages.length) return;
    _retainedNewMessageClientId = widget.messages[index].clientMessageId;
    _retainedNewMessageLabelCount = _newMessageLabelCountFromWidget;
  }

  void _clearRetainedNewMessageAnchor() {
    _retainedNewMessageClientId = null;
    _retainedNewMessageLabelCount = 0;
  }

  void _scheduleUnderflowAlignment() {
    if (_underflowAlignmentScheduled) return;
    _underflowAlignmentScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _underflowAlignmentScheduled = false;
      if (!mounted) return;
      final listBox =
          _messageListKey.currentContext?.findRenderObject() as RenderBox?;
      final oldestBox =
          _oldestMessageKey.currentContext?.findRenderObject() as RenderBox?;
      if (listBox == null) return;
      if (oldestBox == null) {
        if (_underflowBottomSpacer > 0 || !_messageListReady) {
          setState(() {
            _underflowBottomSpacer = 0;
            _messageListReady = true;
          });
        }
        return;
      }

      final listTop = listBox.localToGlobal(Offset.zero).dy;
      final oldestTop = oldestBox.localToGlobal(Offset.zero).dy;
      final targetTop = listTop + _chatMessageListTopPadding;
      final delta = oldestTop - targetTop;
      final nextSpacer = math.max(0.0, _underflowBottomSpacer + delta);
      final spacerChanged = (nextSpacer - _underflowBottomSpacer).abs() >= 0.5;
      if (!spacerChanged && _messageListReady) return;
      setState(() {
        if (spacerChanged) _underflowBottomSpacer = nextSpacer;
        _messageListReady = true;
      });
    });
  }

  void _scheduleNewMessageJumpVisibilitySync() {
    if (_newMessageJumpVisibilityScheduled) return;
    _newMessageJumpVisibilityScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _newMessageJumpVisibilityScheduled = false;
      if (!mounted) return;
      if (_firstNewMessageIsVisible() && !_newMessageJumpDismissed) {
        if (!_incomingUnreadWaitingForUserScroll) {
          _markNewMessagesViewed();
        }
        return;
      }
      final next = _shouldShowNewMessageJumpButton();
      if (next == _showNewMessageJumpButton) return;
      setState(() => _showNewMessageJumpButton = next);
    });
  }

  bool _shouldShowNewMessageJumpButton() {
    if (_firstNewMessageIndex == null || _newMessageJumpDismissed) {
      return false;
    }
    if (!_messageListReady || !_scrollController.hasClients) return false;
    final listBox =
        _messageListKey.currentContext?.findRenderObject() as RenderBox?;
    if (listBox == null || !listBox.hasSize) return false;
    final anchorBox =
        _firstNewMessageKey.currentContext?.findRenderObject() as RenderBox?;
    if (anchorBox == null || !anchorBox.hasSize) {
      // If the first-new anchor is not in the sliver cache, it is above the
      // visible window and needs the one-shot jump affordance.
      return true;
    }
    final listTop = listBox.localToGlobal(Offset.zero).dy;
    final anchorTop = anchorBox.localToGlobal(Offset.zero).dy;
    return anchorTop < listTop + 1;
  }

  bool _firstNewMessageAnchorIsVisible() {
    if (_firstNewMessageIndex == null) return false;
    final anchorBox =
        _firstNewMessageKey.currentContext?.findRenderObject() as RenderBox?;
    return _renderBoxIntersectsMessageList(anchorBox);
  }

  bool _firstNewMessageIsVisible() {
    if (_firstNewMessageAnchorIsVisible()) return true;
    final firstIndex = _firstNewMessageIndex;
    if (firstIndex == null ||
        firstIndex < 0 ||
        firstIndex >= widget.messages.length) {
      return false;
    }
    final message = widget.messages[firstIndex];
    final rowBox =
        _messageRowKeys[message.clientMessageId]?.currentContext
                ?.findRenderObject()
            as RenderBox?;
    return _renderBoxIntersectsMessageList(rowBox);
  }

  bool _renderBoxIntersectsMessageList(RenderBox? box) {
    final listBox =
        _messageListKey.currentContext?.findRenderObject() as RenderBox?;
    if (listBox == null || box == null || !listBox.hasSize || !box.hasSize) {
      return false;
    }
    final listTop = listBox.localToGlobal(Offset.zero).dy;
    final listBottom = listTop + listBox.size.height;
    final boxTop = box.localToGlobal(Offset.zero).dy;
    final boxBottom = boxTop + box.size.height;
    return boxBottom >= listTop && boxTop <= listBottom;
  }

  // The list is reversed, so the latest message lives at the minimum scroll
  // extent. New rooms can render directly at the final bottom position instead
  // of painting history first and jumping on the next frame.
  void _scrollToBottom({required bool animated}) {
    final token = ++_scrollToBottomToken;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted ||
          token != _scrollToBottomToken ||
          !_scrollController.hasClients) {
        return;
      }
      final position = _scrollController.position;
      final target = position.minScrollExtent;
      if ((position.pixels - target).abs() < 1) {
        _syncLatestButtonVisibility();
        return;
      }
      if (animated) {
        unawaited(
          _scrollController
              .animateTo(
                target,
                duration: const Duration(milliseconds: 220),
                curve: Curves.easeOutCubic,
              )
              .whenComplete(() {
                if (mounted) _syncLatestButtonVisibility();
              }),
        );
        return;
      }
      _scrollController.jumpTo(target);
      _syncLatestButtonVisibility();
    });
  }

  GlobalKey _messageRowKey(String clientMessageId) {
    return _messageRowKeys.putIfAbsent(clientMessageId, GlobalKey.new);
  }

  void _trimMessageRowKeys() {
    final activeIds = {
      for (final message in widget.messages) message.clientMessageId,
    };
    _messageRowKeys.removeWhere(
      (id, key) => !activeIds.contains(id) || key.currentContext == null,
    );
  }

  _ViewportAnchor? _captureViewportAnchorFrom(List<Message> messages) {
    final listBox =
        _messageListKey.currentContext?.findRenderObject() as RenderBox?;
    if (listBox == null || !listBox.hasSize) return null;
    final listTop = listBox.localToGlobal(Offset.zero).dy;
    final listBottom = listTop + listBox.size.height;
    for (final message in messages.reversed) {
      final box =
          _messageRowKeys[message.clientMessageId]?.currentContext
                  ?.findRenderObject()
              as RenderBox?;
      if (box == null || !box.hasSize) continue;
      final top = box.localToGlobal(Offset.zero).dy;
      final bottom = top + box.size.height;
      if (bottom >= listTop && top <= listBottom) {
        return _ViewportAnchor(
          clientMessageId: message.clientMessageId,
          top: top,
        );
      }
    }
    return null;
  }

  void _restoreViewportAnchorAfterIncoming(_ViewportAnchor anchor) {
    final token = ++_scrollToBottomToken;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted ||
          token != _scrollToBottomToken ||
          !_scrollController.hasClients) {
        return;
      }
      final position = _scrollController.position;
      final anchorBox =
          _messageRowKeys[anchor.clientMessageId]?.currentContext
                  ?.findRenderObject()
              as RenderBox?;
      final listBox =
          _messageListKey.currentContext?.findRenderObject() as RenderBox?;
      if (anchorBox != null &&
          anchorBox.hasSize &&
          listBox != null &&
          listBox.hasSize) {
        final viewport = RenderAbstractViewport.of(anchorBox);
        final listTop = listBox.localToGlobal(Offset.zero).dy;
        final desiredTopInList = (anchor.top - listTop).clamp(
          0.0,
          listBox.size.height,
        );
        final alignment = listBox.size.height <= 0
            ? 0.0
            : desiredTopInList / listBox.size.height;
        final target = viewport
            .getOffsetToReveal(anchorBox, alignment)
            .offset
            .clamp(position.minScrollExtent, position.maxScrollExtent)
            .toDouble();
        if ((position.pixels - target).abs() > 0.5) {
          _jumpToDuringIncomingViewportRestore(target);
        }
        _correctViewportAnchorAfterIncoming(anchor, token, remainingPasses: 2);
        return;
      }
      _finishViewportAnchorRestore();
    });
  }

  void _correctViewportAnchorAfterIncoming(
    _ViewportAnchor anchor,
    int token, {
    required int remainingPasses,
  }) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted ||
          token != _scrollToBottomToken ||
          !_scrollController.hasClients) {
        return;
      }
      final anchorBox =
          _messageRowKeys[anchor.clientMessageId]?.currentContext
                  ?.findRenderObject()
              as RenderBox?;
      if (anchorBox == null || !anchorBox.hasSize) {
        _finishViewportAnchorRestore();
        return;
      }
      final top = anchorBox.localToGlobal(Offset.zero).dy;
      final delta = top - anchor.top;
      if (delta.abs() > 0.5) {
        final position = _scrollController.position;
        final target = (position.pixels - delta)
            .clamp(position.minScrollExtent, position.maxScrollExtent)
            .toDouble();
        if ((position.pixels - target).abs() > 0.5) {
          _jumpToDuringIncomingViewportRestore(target);
        }
        if (remainingPasses > 0) {
          _correctViewportAnchorAfterIncoming(
            anchor,
            token,
            remainingPasses: remainingPasses - 1,
          );
          return;
        }
      }
      _finishViewportAnchorRestore();
    });
  }

  void _finishViewportAnchorRestore() {
    _rememberScrollPosition();
    _syncLatestButtonVisibility();
    _scheduleNewMessageJumpVisibilitySync();
  }

  void _jumpToDuringIncomingViewportRestore(double target) {
    _restoringIncomingViewport = true;
    try {
      _scrollController.jumpTo(target);
    } finally {
      _restoringIncomingViewport = false;
    }
  }

  int get _effectiveWidgetNewMessageCount {
    if (widget.messages.isEmpty || widget.newMessageCount <= 0) return 0;
    return widget.newMessageCount.clamp(0, widget.messages.length).toInt();
  }

  int get _newMessageLabelCountFromWidget {
    return widget.newMessageCount < 0 ? 0 : widget.newMessageCount;
  }

  int get _activeNewMessageCount {
    final widgetCount = _effectiveWidgetNewMessageCount;
    if (widgetCount > 0) return widgetCount;
    if (_retainedNewMessageIndex == null) return 0;
    return _retainedNewMessageLabelCount
        .clamp(0, widget.messages.length)
        .toInt();
  }

  int get _newMessageLabelCount {
    final widgetCount = _effectiveWidgetNewMessageCount;
    final raw = widgetCount > 0
        ? _newMessageLabelCountFromWidget
        : _retainedNewMessageLabelCount;
    return math.max(0, raw - _viewedUnreadMentionClientIds.length);
  }

  int? get _retainedNewMessageIndex {
    final retainedId = _retainedNewMessageClientId;
    if (retainedId == null) return null;
    final index = widget.messages.indexWhere(
      (message) => message.clientMessageId == retainedId,
    );
    return index == -1 ? null : index;
  }

  int? get _firstNewMessageIndex {
    final widgetCount = _effectiveWidgetNewMessageCount;
    if (widgetCount > 0) return widget.messages.length - widgetCount;
    return _retainedNewMessageIndex;
  }

  List<int> get _unreadMentionIndexes {
    final firstIndex = _firstNewMessageIndex;
    if (firstIndex == null) return const [];
    final indexes = <int>[];
    final currentUser = _currentUserMentionIdentity;
    for (var index = firstIndex; index < widget.messages.length; index++) {
      final message = widget.messages[index];
      if (_viewedUnreadMentionClientIds.contains(message.clientMessageId)) {
        continue;
      }
      if (message.pending || message.isRemoved) continue;
      if (message.sender.id == widget.currentUser.id) continue;
      if (message_mentions.messageMentionsUser(
        text: message.body,
        mentions: message.mentions,
        user: currentUser,
        ownerUserId: widget.ownerUserId,
      )) {
        indexes.add(index);
      }
    }
    return indexes;
  }

  Set<String> get _currentUnreadMentionClientIds {
    return {
      for (final index in _unreadMentionIndexes)
        widget.messages[index].clientMessageId,
    };
  }

  int? get _nextUnreadMentionIndex {
    final indexes = _unreadMentionIndexes;
    if (indexes.isEmpty) return null;
    return indexes.last;
  }

  void _markNewMessagesViewed() {
    if (_newMessageJumpDismissed) return;
    setState(() {
      _newMessageJumpDismissed = true;
      _showNewMessageJumpButton = false;
      _incomingUnreadWaitingForUserScroll = false;
      _viewedUnreadMentionClientIds.clear();
    });
    widget.onViewedNewMessages();
  }

  void _markUnreadMentionViewed(Message message) {
    _viewedUnreadMentionClientIds.add(message.clientMessageId);
  }

  void _scrollToNextUnreadMention() {
    final index = _nextUnreadMentionIndex;
    if (index == null || index < 0 || index >= widget.messages.length) {
      _scrollToFirstNewMessage();
      return;
    }
    final message = widget.messages[index];
    setState(() {
      _highlightedMentionClientId = message.clientMessageId;
      _markUnreadMentionViewed(message);
      _showNewMessageJumpButton = _shouldShowNewMessageJumpButton();
    });
    _highlightedMentionTimer?.cancel();
    _highlightedMentionTimer = Timer(const Duration(milliseconds: 2600), () {
      if (!mounted || _highlightedMentionClientId != message.clientMessageId) {
        return;
      }
      setState(() => _highlightedMentionClientId = null);
    });
    _ensureMessageVisible(message);
  }

  void _scrollToFirstNewMessage() {
    _markNewMessagesViewed();
    if (_ensureFirstNewMessageVisible()) {
      return;
    }
    if (!_scrollController.hasClients) return;
    final count = _activeNewMessageCount;
    if (count <= 0) return;
    final position = _scrollController.position;
    final averageExtent = widget.messages.length <= 1
        ? position.maxScrollExtent
        : position.maxScrollExtent / (widget.messages.length - 1);
    final firstIndex = _firstNewMessageIndex;
    final distanceFromBottom = firstIndex == null
        ? count - 1
        : widget.messages.length - 1 - firstIndex;
    final target =
        (position.minScrollExtent + averageExtent * distanceFromBottom)
            .clamp(position.minScrollExtent, position.maxScrollExtent)
            .toDouble();
    unawaited(
      _scrollController
          .animateTo(
            target,
            duration: const Duration(milliseconds: 240),
            curve: Curves.easeOutCubic,
          )
          .whenComplete(() {
            if (mounted) _ensureFirstNewMessageVisible();
          }),
    );
  }

  bool _ensureFirstNewMessageVisible() {
    final context = _firstNewMessageKey.currentContext;
    if (context == null) return false;
    Scrollable.ensureVisible(
      context,
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOutCubic,
      alignment: 0.12,
    );
    return true;
  }

  void _ensureMessageVisible(Message message) {
    final context = _messageRowKeys[message.clientMessageId]?.currentContext;
    if (context == null) return;
    Scrollable.ensureVisible(
      context,
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOutCubic,
      alignment: 0.18,
    );
  }

  void _scheduleFocusedMessageJump(String? messageId) {
    if (messageId == null || messageId.isEmpty) return;
    if (_handledFocusMessageId == messageId) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final message = _messageById(messageId);
      if (message == null) return;
      final focusedMessage = message;
      final focusedMessageId = focusedMessage.id;
      _handledFocusMessageId = focusedMessageId;
      setState(() => _highlightedMessageId = focusedMessageId);
      _ensureMessageVisible(focusedMessage);
      _highlightedMessageTimer?.cancel();
      _highlightedMessageTimer = Timer(const Duration(milliseconds: 2600), () {
        if (!mounted || _highlightedMessageId != focusedMessageId) return;
        setState(() => _highlightedMessageId = null);
      });
      widget.onFocusMessageHandled?.call(focusedMessageId);
    });
  }

  void _prepareFocusedMessageHighlight(String? messageId) {
    if (messageId == null || messageId.isEmpty) return;
    if (_handledFocusMessageId == messageId && _highlightedMessageId == null) {
      return;
    }
    final message = _messageById(messageId);
    if (message == null) return;
    _highlightedMessageId = message.id;
  }

  Message? _messageById(String messageId) {
    for (final item in widget.messages) {
      if (item.id == messageId) return item;
    }
    return null;
  }

  bool _messageTargetsCurrentUser(Message message) {
    if (message.pending || message.isRemoved) return false;
    if (message.sender.id == widget.currentUser.id) return false;
    return message_mentions.messageMentionsUser(
      text: message.body,
      mentions: message.mentions,
      user: _currentUserMentionIdentity,
      ownerUserId: widget.ownerUserId,
    );
  }

  bool _messageIsHighlighted(Message message) {
    if (message.clientMessageId == _highlightedMentionClientId) return true;
    if (message.id == _highlightedMessageId) return true;
    final focusMessageId = widget.focusMessageId;
    return focusMessageId != null &&
        focusMessageId.isNotEmpty &&
        _handledFocusMessageId != focusMessageId &&
        message.id == focusMessageId;
  }

  bool get _waitingForMentionRenderContext {
    if (widget.mentionMembersReady) return false;
    return widget.messages.any(_messageNeedsMentionRenderContext);
  }

  bool _messageNeedsMentionRenderContext(Message message) {
    if (message.pending || message.isRemoved || message.type != 'text') {
      return false;
    }
    if (message.mentions.isNotEmpty) return true;
    return message_mentions.messageMentionRanges(message.body).isNotEmpty;
  }

  void _toggleDetailedTimestamps() {
    setState(() {
      _showDetailedTimestamps = !_showDetailedTimestamps;
      _underflowBottomSpacer = 0;
    });
  }

  @override
  Widget build(BuildContext context) {
    _trimMessageRowKeys();
    if (widget.error != null && !widget.roomReady) {
      return FloatingNoticeEmitter(
        notices: [
          FloatingNotice(
            message: widget.error!,
            tone: FloatingNoticeTone.error,
            duration: null,
          ),
        ],
        child: _CenteredState(
          icon: Icons.error_outline,
          title: '无法加载聊天',
          detail: '请稍后重试',
          action: Button(
            icon: const Icon(Icons.refresh),
            onPressed: widget.onRetry,
            child: const Text('重试'),
          ),
        ),
      );
    }

    if ((widget.loading && widget.messages.isEmpty) ||
        _waitingForMentionRenderContext) {
      return const Center(
        child: SizedBox.square(
          dimension: 24,
          child: CircularProgressIndicator(
            strokeWidth: 2.2,
            color: UiColors.accent,
          ),
        ),
      );
    }

    if (widget.messages.isEmpty) {
      return const _CenteredState(
        icon: Icons.forum_outlined,
        title: '还没有消息',
        detail: '在下方开始对话吧',
      );
    }

    _scheduleUnderflowAlignment();
    _scheduleLatestButtonVisibilitySync();

    final now = widget.timestampNow;
    final firstNewMessageIndex = _firstNewMessageIndex;
    final showNewMessageJump =
        firstNewMessageIndex != null &&
        !_newMessageJumpDismissed &&
        _showNewMessageJumpButton;
    final nextUnreadMentionIndex = _nextUnreadMentionIndex;
    final showMentionJump =
        showNewMessageJump && nextUnreadMentionIndex != null;
    final newMessageJumpTooltip = showMentionJump
        ? '${widget.currentUserRoomDisplayName}@我'
        : '查看 $_newMessageLabelCount 条未读消息';
    _scheduleNewMessageJumpVisibilitySync();
    final list = ListView.separated(
      key: _messageListKey,
      controller: _scrollController,
      reverse: true,
      physics: const ClampingScrollPhysics(),
      scrollCacheExtent: _messageListCacheExtent,
      padding: EdgeInsets.fromLTRB(
        _chatHorizontalPadding,
        _chatMessageListTopPadding,
        _chatHorizontalPadding,
        widget.bottomInset,
      ),
      itemCount: widget.messages.length + 1,
      separatorBuilder: (context, index) =>
          index == 0 ? const SizedBox.shrink() : const SizedBox(height: 12),
      itemBuilder: (context, index) {
        if (index == 0) return SizedBox(height: _underflowBottomSpacer);

        final messageIndex = index - 1;
        final chronologicalIndex = widget.messages.length - 1 - messageIndex;
        final message = widget.messages[chronologicalIndex];
        final systemEvent = message_display.systemMessageEvent(message);
        final previous = chronologicalIndex == 0
            ? null
            : widget.messages[chronologicalIndex - 1];
        final showTimestamp = message_display.shouldShowChatTimestamp(
          current: message.createdAt,
          previous: previous?.createdAt,
          now: now,
        );
        final timestamp = _showDetailedTimestamps
            ? message_display.formatDetailedChatTimestamp(message.createdAt)
            : message_display.formatChatTimestamp(message.createdAt, now: now);
        final showNewMessageDivider =
            chronologicalIndex == firstNewMessageIndex;
        final row = Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (showNewMessageDivider) ...[
              KeyedSubtree(
                key: _firstNewMessageKey,
                child: const _NewMessagesDivider(),
              ),
              const SizedBox(height: 10),
            ],
            if (showTimestamp) ...[
              _MessageTimeDivider(
                label: timestamp,
                onTap: _toggleDetailedTimestamps,
              ),
              const SizedBox(height: 10),
            ],
            if (systemEvent != null)
              ChatSystemMessageContent(
                message: message,
                event: systemEvent,
                highlighted: _messageIsHighlighted(message),
                currentUser: widget.currentUser,
                ownerUserId: widget.ownerUserId,
                live: widget.live,
                messageActions: widget.messageActions,
                onResolveSenderProfile: widget.onResolveSenderProfile,
                onResolveRoomProfile: widget.onResolveRoomProfile,
                onEnterProfileRoom: widget.onEnterProfileRoom,
                profileActionBuilder: widget.senderProfileActionBuilder,
              )
            else if (message.isRemoved)
              ChatRemovedMessageContent(
                message: message,
                highlighted: _messageIsHighlighted(message),
                currentUser: widget.currentUser,
                ownerUserId: widget.ownerUserId,
                live: widget.live,
                messageActions: widget.messageActions,
                onResolveSenderProfile: widget.onResolveSenderProfile,
                onResolveRoomProfile: widget.onResolveRoomProfile,
                onEnterProfileRoom: widget.onEnterProfileRoom,
                profileActionBuilder: widget.senderProfileActionBuilder,
              )
            else
              _MessageRow(
                message: message,
                outgoing: message.sender.id == widget.currentUser.id,
                timestampNow: now,
                showDetailedTimestamps: _showDetailedTimestamps,
                currentUser: widget.currentUser,
                currentUserMentionIdentity: _currentUserMentionIdentity,
                ownerUserId: widget.ownerUserId,
                mentionMembers: widget.mentionMembers,
                transfer: widget.fileTransfers[message.clientMessageId],
                fileDownloads: widget.fileDownloads,
                downloadActions: widget.downloadActions,
                voicePlaybackActions: widget.voicePlaybackActions,
                imagePreviewActions: widget.imagePreviewActions,
                messageActions: widget.messageActions,
                mentionHighlighted: _messageIsHighlighted(message),
                mentionTargetsCurrentUser: _messageTargetsCurrentUser(message),
                inLive: _userInLive(message.sender.id),
                onResolveSenderProfile: widget.onResolveSenderProfile,
                onResolveRoomProfile: widget.onResolveRoomProfile,
                onEnterProfileRoom: widget.onEnterProfileRoom,
                profileActionBuilder: widget.senderProfileActionBuilder,
                onMentionUser: widget.onMentionUser,
                isUserInLive: _userInLive,
              ),
          ],
        );
        final keyedRow = KeyedSubtree(
          key: _messageRowKey(message.clientMessageId),
          child: row,
        );
        if (chronologicalIndex == 0) {
          return KeyedSubtree(key: _oldestMessageKey, child: keyedRow);
        }
        return keyedRow;
      },
    );

    return Stack(
      children: [
        Positioned.fill(
          child: Opacity(
            opacity: _messageListReady ? 1 : 0,
            child: KeyedSubtree(
              key: const ValueKey('chat-message-list'),
              child: list,
            ),
          ),
        ),
        Positioned(
          right: _chatFloatingEdgeInset,
          bottom: 12,
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 120),
            switchInCurve: Curves.easeOutCubic,
            switchOutCurve: Curves.easeInCubic,
            child: _showLatestButton
                ? PressableSurface(
                    key: const ValueKey('chat-jump-to-latest'),
                    width: 34,
                    height: 34,
                    hoverLift: 0,
                    baseDepth: 0,
                    backgroundColor: UiColors.selected,
                    selectedBackgroundColor: UiColors.selected,
                    pressedBackgroundColor: const Color(0xFF14211B),
                    borderColor: UiColors.selectedBorder,
                    selectedBorderColor: UiColors.selectedBorder,
                    tooltip: '跳到最新消息',
                    onPressed: () => _scrollToBottom(animated: true),
                    child: const Center(
                      child: Icon(
                        Icons.keyboard_arrow_down_rounded,
                        color: UiColors.accent,
                        size: 18,
                      ),
                    ),
                  )
                : const SizedBox.shrink(
                    key: ValueKey('chat-jump-to-latest-hidden'),
                  ),
          ),
        ),
        Positioned(
          top: 12,
          right: _chatFloatingEdgeInset,
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 120),
            switchInCurve: Curves.easeOutCubic,
            switchOutCurve: Curves.easeInCubic,
            child: showNewMessageJump
                ? PressableSurface(
                    key: const ValueKey('chat-jump-to-first-new'),
                    width: 34,
                    height: 34,
                    hoverLift: 0,
                    baseDepth: 0,
                    backgroundColor: UiColors.selected,
                    selectedBackgroundColor: UiColors.selected,
                    pressedBackgroundColor: const Color(0xFF14211B),
                    borderColor: UiColors.selectedBorder,
                    selectedBorderColor: UiColors.selectedBorder,
                    tooltip: newMessageJumpTooltip,
                    onPressed: showMentionJump
                        ? _scrollToNextUnreadMention
                        : _scrollToFirstNewMessage,
                    child: const Center(
                      child: Icon(
                        Icons.keyboard_arrow_up_rounded,
                        color: UiColors.accent,
                        size: 18,
                      ),
                    ),
                  )
                : const SizedBox.shrink(
                    key: ValueKey('chat-jump-to-first-new-hidden'),
                  ),
          ),
        ),
      ],
    );
  }
}

class _NewMessagesDivider extends StatelessWidget {
  const _NewMessagesDivider();

  @override
  Widget build(BuildContext context) {
    final lineColor = UiColors.accent.withValues(alpha: 0.58);
    return Row(
      children: [
        Expanded(child: Divider(height: 1, thickness: 1, color: lineColor)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10),
          child: Text(
            '未读消息',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: UiTypography.label.copyWith(
              color: UiColors.accent,
              fontSize: 12,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
        Expanded(child: Divider(height: 1, thickness: 1, color: lineColor)),
      ],
    );
  }
}

class _MessageTimeDivider extends StatelessWidget {
  const _MessageTimeDivider({required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onTap,
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: UiColors.surfacePressed,
              borderRadius: BorderRadius.circular(999),
              border: Border.all(color: UiColors.border),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
              child: Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: UiTypography.label.copyWith(
                  color: UiColors.textMuted,
                  fontSize: 11,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Renders a system message exactly as it appears in a text channel.
///
/// Message-history and other secondary surfaces reuse this widget so inline
/// avatars, profile cards, role colors, and information actions stay
/// interactive instead of falling back to plain text.
class ChatSystemMessageContent extends StatelessWidget {
  const ChatSystemMessageContent({
    super.key,
    required this.message,
    required this.event,
    required this.currentUser,
    required this.ownerUserId,
    required this.live,
    this.messageActions,
    this.enableContextMenu = true,
    this.highlighted = false,
    this.showSurface = true,
    this.alignment = Alignment.center,
    this.wrapAlignment = WrapAlignment.center,
    this.textStyle = UiTypography.label,
    this.avatarSize = 16,
    this.onResolveSenderProfile,
    this.onResolveRoomProfile,
    this.onEnterProfileRoom,
    this.profileActionBuilder,
  });

  final Message message;
  final message_display.SystemMessageEvent event;
  final CurrentUser currentUser;
  final String? ownerUserId;
  final LiveState? live;
  final ChatMessageActions? messageActions;
  final bool enableContextMenu;
  final bool highlighted;
  final bool showSurface;
  final AlignmentGeometry alignment;
  final WrapAlignment wrapAlignment;
  final TextStyle textStyle;
  final double avatarSize;
  final Future<UserSummary> Function(UserSummary sender)?
  onResolveSenderProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterProfileRoom;
  final UserProfileActionBuilder? profileActionBuilder;

  @override
  Widget build(BuildContext context) {
    return _SystemMessageContainer(
      message: enableContextMenu ? message : null,
      actions: enableContextMenu ? messageActions : null,
      highlighted: highlighted,
      showSurface: showSurface,
      alignment: alignment,
      wrapAlignment: wrapAlignment,
      children: _SystemMessageParts(
        event: event,
        currentUser: currentUser,
        ownerUserId: ownerUserId,
        live: live,
        textStyle: textStyle,
        avatarSize: avatarSize,
        onResolveSenderProfile: onResolveSenderProfile,
        onResolveRoomProfile: onResolveRoomProfile,
        onEnterProfileRoom: onEnterProfileRoom,
        profileActionBuilder: profileActionBuilder,
      ).build(context),
    );
  }
}

class _SystemMessageContainer extends StatefulWidget {
  const _SystemMessageContainer({
    required this.children,
    this.message,
    this.actions,
    this.highlighted = false,
    this.showSurface = true,
    this.alignment = Alignment.center,
    this.wrapAlignment = WrapAlignment.center,
  });

  final List<Widget> children;
  final Message? message;
  final ChatMessageActions? actions;
  final bool highlighted;
  final bool showSurface;
  final AlignmentGeometry alignment;
  final WrapAlignment wrapAlignment;

  @override
  State<_SystemMessageContainer> createState() =>
      _SystemMessageContainerState();
}

class _SystemMessageContainerState extends State<_SystemMessageContainer> {
  bool _contextMenuActive = false;

  @override
  Widget build(BuildContext context) {
    final active = _contextMenuActive;
    final contentColor = UiColors.surfacePressed.withValues(alpha: 0.82);
    final highlightColor = active
        ? UiColors.selected
        : widget.highlighted
        ? UiColors.selected.withValues(alpha: 0.86)
        : contentColor;
    final parts = Wrap(
      alignment: widget.wrapAlignment,
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: 5,
      runSpacing: 5,
      children: widget.children,
    );
    final content = widget.showSurface
        ? AnimatedContainer(
            duration: widget.highlighted
                ? Duration.zero
                : const Duration(milliseconds: 90),
            curve: Curves.easeOutCubic,
            decoration: BoxDecoration(
              color: highlightColor,
              borderRadius: BorderRadius.circular(999),
              border: Border.all(
                color: active || widget.highlighted
                    ? UiColors.selectedBorder
                    : UiColors.border,
              ),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: highlightColor,
                  borderRadius: BorderRadius.circular(999),
                ),
                child: parts,
              ),
            ),
          )
        : parts;
    final menuMessage = widget.message;
    final menuActions = widget.actions;
    final wrappedContent = menuMessage == null || menuActions == null
        ? content
        : _MessageContextMenuRegion(
            message: menuMessage,
            actions: menuActions,
            onContextMenuActiveChanged: _setContextMenuActive,
            child: content,
          );

    return Align(
      alignment: widget.alignment,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: _messageMaxWidth + 96),
        child: wrappedContent,
      ),
    );
  }

  void _setContextMenuActive(bool active) {
    if (!mounted || _contextMenuActive == active) return;
    setState(() => _contextMenuActive = active);
  }
}

/// Renders a recalled or force-deleted message with the text-channel layout.
class ChatRemovedMessageContent extends StatelessWidget {
  const ChatRemovedMessageContent({
    super.key,
    required this.message,
    required this.currentUser,
    required this.ownerUserId,
    required this.live,
    required this.messageActions,
    this.enableContextMenu = true,
    this.highlighted = false,
    this.showSurface = true,
    this.alignment = Alignment.center,
    this.wrapAlignment = WrapAlignment.center,
    this.onResolveSenderProfile,
    this.onResolveRoomProfile,
    this.onEnterProfileRoom,
    this.profileActionBuilder,
  });

  final Message message;
  final CurrentUser currentUser;
  final String? ownerUserId;
  final LiveState? live;
  final ChatMessageActions messageActions;
  final bool enableContextMenu;
  final bool highlighted;
  final bool showSurface;
  final AlignmentGeometry alignment;
  final WrapAlignment wrapAlignment;
  final Future<UserSummary> Function(UserSummary sender)?
  onResolveSenderProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterProfileRoom;
  final UserProfileActionBuilder? profileActionBuilder;

  @override
  Widget build(BuildContext context) {
    return _SystemMessageContainer(
      message: enableContextMenu ? message : null,
      actions: enableContextMenu ? messageActions : null,
      highlighted: highlighted,
      showSurface: showSurface,
      alignment: alignment,
      wrapAlignment: wrapAlignment,
      children: _buildParts(),
    );
  }

  List<Widget> _buildParts() {
    if (message.isForceDeleted) {
      final actor = message.forceDeletedBy;
      return [
        if (actor != null) _userChip(actor),
        _systemText(actor == null ? '消息已被删除' : '删除了一条消息'),
      ];
    }

    final actor = message.recalledBy ?? message.sender;
    final recalledOwnMessage = actor.id == message.sender.id;
    final action = _removedTextAction();
    return [
      _userChip(actor),
      if (recalledOwnMessage)
        _systemTextWithTrailingAction('撤回了一条消息', action)
      else ...[
        _systemText('撤回了一条来自'),
        _userChip(message.sender),
        _systemTextWithTrailingAction('的消息', action),
      ],
    ];
  }

  Widget _userChip(UserSummary user) {
    return _SystemUserChip(
      user: user,
      currentUser: currentUser,
      ownerUserId: ownerUserId,
      onResolveProfile: onResolveSenderProfile,
      onResolveRoomProfile: onResolveRoomProfile,
      onEnterProfileRoom: onEnterProfileRoom,
      profileActionBuilder: profileActionBuilder,
      inLive: live_display.liveParticipantByUserId(live, user.id) != null,
    );
  }

  Widget? _removedTextAction() {
    if (!message.isRecalled || message.type != 'text') return null;
    if (message.body.isEmpty) return null;
    if (messageActions.canReeditRecalledText(message)) {
      return _RemovedMessageInlineButton(
        key: ValueKey('message-reedit-${message.id}'),
        icon: Icons.edit_outlined,
        tooltip: '重新编辑',
        onPressed: () => messageActions.onReeditRecalledText(message),
      );
    }
    if (messageActions.canInspectRecalledText(message)) {
      return _SystemInfoButton(
        key: ValueKey('message-info-${message.id}'),
        message: message.body,
      );
    }
    return null;
  }
}

class _RemovedMessageInlineButton extends StatelessWidget {
  const _RemovedMessageInlineButton({
    super.key,
    required this.icon,
    required this.tooltip,
    this.onPressed,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null;
    return Tooltip(
      message: tooltip,
      waitDuration: const Duration(milliseconds: 350),
      child: MouseRegion(
        cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onPressed,
          child: SizedBox.square(
            dimension: 16,
            child: Icon(icon, size: 14, color: UiColors.accent),
          ),
        ),
      ),
    );
  }
}

class _SystemInfoButton extends StatelessWidget {
  const _SystemInfoButton({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return HoverCardAnchor(
      resetKey: message,
      cardWidth: hoverInfoCardWidth(context, message),
      gap: 8,
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        child: SizedBox.square(
          dimension: 16,
          child: Icon(Icons.info_outline, size: 14, color: UiColors.accent),
        ),
      ),
      cardBuilder: (context) => _SystemInfoCard(message: message),
    );
  }
}

class _SystemInfoCard extends StatelessWidget {
  const _SystemInfoCard({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final hoverScope = HoverCardTapRegionScope.maybeOf(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      child: ReadOnlySelectableText(
        value: message,
        maxLines: 8,
        style: UiTypography.body.copyWith(
          color: UiColors.text,
          fontSize: 12,
          height: 1.38,
        ),
        contextMenuTapRegionGroupId: hoverScope?.tapRegionGroup,
        onContextMenuOpenChanged: hoverScope?.onOverlayActivityChanged,
      ),
    );
  }
}

class _MessageContextMenuRegion extends StatelessWidget {
  const _MessageContextMenuRegion({
    required this.message,
    required this.actions,
    required this.child,
    this.onContextMenuActiveChanged,
  });

  final Message message;
  final ChatMessageActions actions;
  final Widget child;
  final ValueChanged<bool>? onContextMenuActiveChanged;

  @override
  Widget build(BuildContext context) {
    return UiContextMenuTriggerRegion(
      onTriggered: (position) {
        onContextMenuActiveChanged?.call(true);
        unawaited(
          _showChatMessageContextMenu(
            context: context,
            position: position,
            message: message,
            actions: actions,
          ).whenComplete(() => onContextMenuActiveChanged?.call(false)),
        );
      },
      child: child,
    );
  }
}

Future<void> _showChatMessageContextMenu({
  required BuildContext context,
  required Offset position,
  required Message message,
  required ChatMessageActions actions,
}) {
  final sections = _messageContextMenuSections(
    context: context,
    message: message,
    actions: actions,
    includeCopy: true,
    includeQuote: true,
    includeDelete: true,
    includeRecall: !message.isRemoved,
  );
  return showUiContextMenu(context, position: position, sections: sections);
}

List<UiContextMenuSection> _messageContextMenuSections({
  required BuildContext context,
  required Message message,
  required ChatMessageActions actions,
  bool includeCopy = true,
  bool includeQuote = true,
  bool includeDelete = true,
  bool includeRecall = true,
}) {
  return [
    if (includeCopy || (includeQuote && actions.canQuote(message)))
      UiContextMenuSection([
        if (includeCopy)
          UiContextMenuItem(
            label: '复制',
            shortcut: 'Ctrl+C',
            onPressed: () => unawaited(actions.onCopy(context, message)),
          ),
        if (includeQuote && actions.canQuote(message))
          UiContextMenuItem(
            label: '引用',
            onPressed: () => actions.onQuote(message),
          ),
      ]),
    if (includeRecall && actions.canRecall(message))
      UiContextMenuSection([
        UiContextMenuItem(
          label: '撤回',
          onPressed: () => unawaited(actions.onRecall(context, message)),
        ),
      ]),
    if (includeDelete)
      UiContextMenuSection([
        UiContextMenuItem(
          label: '删除',
          onPressed: () => unawaited(actions.onDeleteForMe(context, message)),
        ),
      ]),
  ];
}

Widget _systemText(String value) {
  return Text(
    value,
    maxLines: 1,
    overflow: TextOverflow.ellipsis,
    style: UiTypography.label.copyWith(
      color: UiColors.textSecondary,
      fontSize: 12,
    ),
  );
}

Widget _systemTextWithTrailingAction(String value, Widget? action) {
  if (action == null) return _systemText(value);
  return Row(
    mainAxisSize: MainAxisSize.min,
    crossAxisAlignment: CrossAxisAlignment.center,
    children: [_systemText(value), action],
  );
}

class _SystemMessageParts {
  const _SystemMessageParts({
    required this.event,
    required this.currentUser,
    required this.ownerUserId,
    required this.live,
    this.textStyle = UiTypography.label,
    this.avatarSize = 16,
    this.onResolveSenderProfile,
    this.onResolveRoomProfile,
    this.onEnterProfileRoom,
    this.profileActionBuilder,
  });

  final message_display.SystemMessageEvent event;
  final CurrentUser currentUser;
  final String? ownerUserId;
  final LiveState? live;
  final TextStyle textStyle;
  final double avatarSize;
  final Future<UserSummary> Function(UserSummary sender)?
  onResolveSenderProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterProfileRoom;
  final UserProfileActionBuilder? profileActionBuilder;

  List<Widget> build(BuildContext context) {
    final subject = event.subject;
    switch (event.event) {
      case message_display.kSystemEventRoomMemberJoined:
        return [_userChip(subject), _text('加入了房间')];
      case message_display.kSystemEventRoomMemberLeft:
        return [_userChip(subject), _text('离开了房间')];
      case message_display.kSystemEventRoomMemberRemoved:
        final actor = event.actor;
        return [
          _userChip(subject),
          if (actor == null)
            _text('被踢出了房间')
          else ...[
            _text('被'),
            _userChip(actor),
            _text('踢出了房间'),
          ],
        ];
      case message_display.kSystemEventLiveJoined:
        return [_userChip(subject), _text('进入了语音频道')];
      case message_display.kSystemEventLiveLeft:
        return [_userChip(subject), _text('退出了语音频道')];
      case message_display.kSystemEventRoomRoleChanged:
        final actor = event.actor;
        final roleLabel = message_display.systemMessageRoleLabel(event.toRole);
        final verb = message_display.systemMessageRoleVerb(event);
        final omitActor = message_display.systemMessageRoleChangeOmitsActor(
          event,
        );
        return [
          _userChip(subject, roleOverride: event.toRole),
          if (!omitActor && actor != null) ...[_text('被'), _userChip(actor)],
          _text(verb),
          RoleBadge(
            label: roleLabel,
            padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
            fontSize: 11,
            fontWeight: FontWeight.w700,
          ),
        ];
      case message_display.kSystemEventRoomNameChanged:
        final actor = event.actor ?? event.user;
        return [
          _textWithInfoButton('房间名称', '原房间名称：${event.oldValue ?? ''}'),
          if (actor == null) ...[
            _text('修改为'),
          ] else ...[
            _text('被'),
            _userChip(actor),
            _text('修改为'),
          ],
          _highlightText(_systemChangedValueLabel(event.newValue)),
        ];
      case message_display.kSystemEventRoomDescriptionChanged:
        final actor = event.actor ?? event.user;
        return [
          _textWithInfoButton('房间简介', '原房间简介：${event.oldValue ?? ''}'),
          if (actor == null) ...[
            _text('修改为'),
          ] else ...[
            _text('被'),
            _userChip(actor),
            _text('修改为'),
          ],
          _lineBreak(),
          _highlightText(_systemChangedValueLabel(event.newValue), maxLines: 4),
        ];
      case message_display.kSystemEventRoomVisibilityChanged:
        final actor = event.actor ?? event.user;
        return [
          _textWithInfoButton(
            '房间可见性',
            '原可见性：${message_display.systemMessageVisibilityLabel(event.oldValue)}',
          ),
          if (actor == null) ...[
            _text('修改为'),
          ] else ...[
            _text('被'),
            _userChip(actor),
            _text('修改为'),
          ],
          _highlightText(
            message_display.systemMessageVisibilityLabel(event.newValue),
          ),
        ];
      case message_display.kSystemEventRoomJoinPolicyChanged:
        final actor = event.actor ?? event.user;
        return [
          _textWithInfoButton(
            '房间加入方式',
            '原加入方式：${message_display.systemMessageJoinPolicyLabel(event.oldValue)}',
          ),
          if (actor == null) ...[
            _text('修改为'),
          ] else ...[
            _text('被'),
            _userChip(actor),
            _text('修改为'),
          ],
          _highlightText(
            message_display.systemMessageJoinPolicyLabel(event.newValue),
          ),
        ];
      default:
        final fallback = event.message.body.trim();
        return [_userChip(subject), if (fallback.isNotEmpty) _text(fallback)];
    }
  }

  Widget _userChip(UserSummary user, {String? roleOverride}) {
    return _SystemUserChip(
      user: user,
      roleOverride: roleOverride,
      currentUser: currentUser,
      ownerUserId: ownerUserId,
      onResolveProfile: onResolveSenderProfile,
      onResolveRoomProfile: onResolveRoomProfile,
      onEnterProfileRoom: onEnterProfileRoom,
      profileActionBuilder: profileActionBuilder,
      inLive: live_display.liveParticipantByUserId(live, user.id) != null,
      textStyle: textStyle,
      avatarSize: avatarSize,
    );
  }

  Widget _text(String value) {
    return Text(
      value,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: textStyle.copyWith(color: UiColors.textSecondary),
    );
  }

  Widget _textWithInfoButton(String value, String info) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [_text(value), _infoButton(info)],
    );
  }

  Widget _infoButton(String value) {
    return _SystemInfoButton(
      key: ValueKey(
        'system-info-${event.message.clientMessageId}-${event.event}',
      ),
      message: value,
    );
  }

  Widget _highlightText(String value, {int maxLines = 1}) {
    return ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: _messageMaxWidth),
      child: Text(
        value,
        maxLines: maxLines,
        overflow: TextOverflow.ellipsis,
        style: textStyle.copyWith(
          color: UiColors.accent,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }

  Widget _lineBreak() {
    return const SizedBox(width: _messageMaxWidth + 96, height: 0);
  }

  String _systemChangedValueLabel(String? value) {
    final normalized = value ?? '';
    if (normalized.isEmpty) return '（空）';
    return normalized;
  }
}

class _SystemUserChip extends StatelessWidget {
  const _SystemUserChip({
    required this.user,
    this.roleOverride,
    required this.currentUser,
    required this.ownerUserId,
    required this.inLive,
    this.textStyle = UiTypography.label,
    this.avatarSize = 16,
    this.onResolveProfile,
    this.onResolveRoomProfile,
    this.onEnterProfileRoom,
    this.profileActionBuilder,
  });

  final UserSummary user;
  final String? roleOverride;
  final CurrentUser currentUser;
  final String? ownerUserId;
  final bool inLive;
  final TextStyle textStyle;
  final double avatarSize;
  final Future<UserSummary> Function(UserSummary sender)? onResolveProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterProfileRoom;
  final UserProfileActionBuilder? profileActionBuilder;

  @override
  Widget build(BuildContext context) {
    final name = _senderName(user);
    final colorUser = roleOverride == null
        ? user
        : user.copyWith(roomRole: roleOverride);
    final avatar = Avatar(
      label: room_display.userAvatarLabel(user),
      imageUrl: AppConfigScope.of(context).resolveAssetUrl(user.avatarUrl),
      defaultAvatarKey: user.defaultAvatarKey,
      size: avatarSize,
      activeBorderWidth: 1,
    );
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        _AvatarHoverCard(
          user: user,
          currentUser: currentUser,
          onResolveProfile: onResolveProfile,
          onResolveRoomProfile: onResolveRoomProfile,
          onEnterCommonRoom: onEnterProfileRoom,
          profileActionBuilder: profileActionBuilder,
          inLive: inLive,
          child: avatar,
        ),
        const SizedBox(width: 4),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 128),
          child: Text(
            name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: textStyle.copyWith(
              color: chatRoomUsernameColor(
                user: colorUser,
                currentUser: currentUser,
                ownerUserId: ownerUserId,
              ),
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ],
    );
  }
}

class _MessageRow extends StatelessWidget {
  const _MessageRow({
    required this.message,
    required this.outgoing,
    required this.timestampNow,
    required this.showDetailedTimestamps,
    required this.currentUser,
    required this.currentUserMentionIdentity,
    required this.ownerUserId,
    required this.mentionMembers,
    required this.transfer,
    required this.fileDownloads,
    required this.downloadActions,
    required this.voicePlaybackActions,
    required this.imagePreviewActions,
    required this.messageActions,
    required this.mentionHighlighted,
    required this.mentionTargetsCurrentUser,
    required this.inLive,
    this.onResolveSenderProfile,
    this.onResolveRoomProfile,
    this.onEnterProfileRoom,
    this.profileActionBuilder,
    this.onMentionUser,
    this.isUserInLive,
  });

  final Message message;
  final bool outgoing;
  final DateTime timestampNow;
  final bool showDetailedTimestamps;
  final CurrentUser currentUser;
  final UserSummary currentUserMentionIdentity;
  final String? ownerUserId;
  final List<RoomMember> mentionMembers;
  final FileTransferState? transfer;
  final Map<String, FileTransferState> fileDownloads;
  final ChatFileDownloadActions downloadActions;
  final ChatVoicePlaybackActions voicePlaybackActions;
  final ChatImagePreviewActions imagePreviewActions;
  final ChatMessageActions messageActions;
  final bool mentionHighlighted;
  final bool mentionTargetsCurrentUser;
  final bool inLive;
  final Future<UserSummary> Function(UserSummary sender)?
  onResolveSenderProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterProfileRoom;
  final UserProfileActionBuilder? profileActionBuilder;
  final ValueChanged<UserSummary>? onMentionUser;
  final bool Function(String userId)? isUserInLive;

  @override
  Widget build(BuildContext context) {
    final sender = _senderName(message.sender);
    final senderColor = chatRoomUsernameColor(
      user: message.sender,
      currentUser: currentUser,
      ownerUserId: ownerUserId,
    );
    final bubble = Flexible(
      child: Column(
        crossAxisAlignment: outgoing
            ? CrossAxisAlignment.end
            : CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: _messageMaxWidth),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 2),
              child: Text(
                sender,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: outgoing ? TextAlign.right : TextAlign.left,
                style: UiTypography.label.copyWith(
                  color: senderColor,
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
          const SizedBox(height: 4),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: _messageMaxWidth),
            child: _MessageBubble(
              message: message,
              outgoing: outgoing,
              timestampNow: timestampNow,
              showDetailedTimestamps: showDetailedTimestamps,
              transfer: transfer,
              fileDownloads: fileDownloads,
              downloadActions: downloadActions,
              voicePlaybackActions: voicePlaybackActions,
              imagePreviewActions: imagePreviewActions,
              messageActions: messageActions,
              mentionHighlighted: mentionHighlighted,
              mentionTargetsCurrentUser: mentionTargetsCurrentUser,
              currentUser: currentUser,
              currentUserMentionIdentity: currentUserMentionIdentity,
              ownerUserId: ownerUserId,
              mentionMembers: mentionMembers,
              onResolveSenderProfile: onResolveSenderProfile,
              onResolveRoomProfile: onResolveRoomProfile,
              onEnterProfileRoom: onEnterProfileRoom,
              profileActionBuilder: profileActionBuilder,
              isUserInLive: isUserInLive,
            ),
          ),
        ],
      ),
    );

    final avatar = Avatar(
      label: room_display.userAvatarLabel(message.sender),
      imageUrl: AppConfigScope.of(
        context,
      ).resolveAssetUrl(message.sender.avatarUrl),
      defaultAvatarKey: message.sender.defaultAvatarKey,
      size: 32,
      showBorder: false,
    );

    final avatarWithContextMenu = _MessageAvatarMentionMenu(
      user: message.sender,
      onMentionUser: message.sender.id == currentUser.id ? null : onMentionUser,
      child: avatar,
    );
    final avatarHoverCard = _AvatarHoverCard(
      user: message.sender,
      currentUser: currentUser,
      onResolveProfile: onResolveSenderProfile,
      onResolveRoomProfile: onResolveRoomProfile,
      onEnterCommonRoom: onEnterProfileRoom,
      profileActionBuilder: profileActionBuilder,
      inLive: inLive,
      child: avatarWithContextMenu,
    );

    return Row(
      mainAxisAlignment: outgoing
          ? MainAxisAlignment.end
          : MainAxisAlignment.start,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (!outgoing) ...[avatarHoverCard, const SizedBox(width: 10)],
        bubble,
        if (outgoing) ...[const SizedBox(width: 10), avatarHoverCard],
      ],
    );
  }
}

class _MessageAvatarMentionMenu extends StatelessWidget {
  const _MessageAvatarMentionMenu({
    required this.user,
    required this.child,
    this.onMentionUser,
  });

  final UserSummary user;
  final Widget child;
  final ValueChanged<UserSummary>? onMentionUser;

  @override
  Widget build(BuildContext context) {
    final onMention = onMentionUser;
    if (onMention == null) return child;
    final label = _senderName(user);
    return UiContextMenuTriggerRegion(
      onTriggered: (position) {
        unawaited(
          showUiContextMenu(
            context,
            position: position,
            sections: [
              UiContextMenuSection([
                UiContextMenuItem(
                  label: '@$label',
                  onPressed: () => onMention(user),
                ),
              ]),
            ],
          ),
        );
      },
      child: child,
    );
  }
}

/// Renders the exact contents used inside a normal chat message bubble without
/// adding the bubble background, border, padding, or message context menu.
///
/// Message-history and other read-only surfaces use this widget so links,
/// mentions, stickers, voice messages, files, and image previews keep the same
/// behavior as the main chat instead of maintaining a second renderer.
class ChatMessageContent extends StatelessWidget {
  const ChatMessageContent({
    super.key,
    required this.message,
    required this.outgoing,
    required this.fileDownloads,
    required this.downloadActions,
    required this.voicePlaybackActions,
    required this.imagePreviewActions,
    required this.currentUser,
    required this.currentUserMentionIdentity,
    required this.ownerUserId,
    required this.mentionMembers,
    this.transfer,
    this.mentionHighlighted = false,
    this.onResolveSenderProfile,
    this.onResolveRoomProfile,
    this.onEnterProfileRoom,
    this.profileActionBuilder,
    this.isUserInLive,
    this.onSelectionActiveChanged,
    this.textSelectionResetListenable,
    this.onOpenQuote,
    this.timestampNow,
    this.showDetailedTimestamps = false,
    this.onViewSharedPlaylist,
  });

  final Message message;
  final bool outgoing;
  final FileTransferState? transfer;
  final Map<String, FileTransferState> fileDownloads;
  final ChatFileDownloadActions downloadActions;
  final ChatVoicePlaybackActions voicePlaybackActions;
  final ChatImagePreviewActions imagePreviewActions;
  final CurrentUser currentUser;
  final UserSummary currentUserMentionIdentity;
  final String? ownerUserId;
  final List<RoomMember> mentionMembers;
  final bool mentionHighlighted;
  final Future<UserSummary> Function(UserSummary sender)?
  onResolveSenderProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterProfileRoom;
  final UserProfileActionBuilder? profileActionBuilder;
  final bool Function(String userId)? isUserInLive;
  final ValueChanged<bool>? onSelectionActiveChanged;
  final Listenable? textSelectionResetListenable;
  final Future<void> Function(BuildContext context, MessageQuote quote)?
  onOpenQuote;
  final DateTime? timestampNow;
  final bool showDetailedTimestamps;
  final Future<void> Function(
    BuildContext context,
    Message message,
    SharedMusicPlaylist playlist,
  )?
  onViewSharedPlaylist;

  @override
  Widget build(BuildContext context) {
    final contentKind = message_display.messageContentKind(message);
    final status = message_display.messageDeliveryStatusText(message);
    final quotes = message.effectiveQuotes;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var index = 0; index < quotes.length; index++) ...[
          _MessageQuoteCard(
            quote: quotes[index],
            timestampNow: timestampNow,
            showDetailedTimestamps: showDetailedTimestamps,
            imagePreviewActions: imagePreviewActions,
            onTap: onOpenQuote == null
                ? null
                : () => unawaited(onOpenQuote!(context, quotes[index])),
          ),
          SizedBox(height: index == quotes.length - 1 ? 8 : 6),
        ],
        switch (contentKind) {
          message_display.MessageContentKind.sticker => _StickerBody(
            message: message,
            attachment: message.stickerAttachment!,
            imagePreviewActions: imagePreviewActions,
          ),
          message_display.MessageContentKind.voice => _VoiceBody(
            message: message,
            attachment: voice_display.voiceMessageAttachment(message)!,
            playbackActions: voicePlaybackActions,
          ),
          message_display.MessageContentKind.files => _FileBody(
            message: message,
            outgoing: outgoing,
            transfer: transfer,
            fileDownloads: fileDownloads,
            downloadActions: downloadActions,
            imagePreviewActions: imagePreviewActions,
          ),
          message_display.MessageContentKind.playlist => _PlaylistShareBody(
            message: message,
            playlist: message.playlistAttachment!.playlist!,
            onView: onViewSharedPlaylist,
            currentUser: currentUser,
            onResolveUserProfile: onResolveSenderProfile,
            onResolveRoomProfile: onResolveRoomProfile,
            onEnterCommonRoom: onEnterProfileRoom,
            userProfileActionBuilder: profileActionBuilder,
          ),
          message_display.MessageContentKind.text => _TextBody(
            message: message,
            profileCurrentUser: currentUser,
            currentUser: currentUserMentionIdentity,
            ownerUserId: ownerUserId,
            mentionMembers: mentionMembers,
            mentionHighlighted: mentionHighlighted,
            onResolveSenderProfile: onResolveSenderProfile,
            onResolveRoomProfile: onResolveRoomProfile,
            onEnterProfileRoom: onEnterProfileRoom,
            profileActionBuilder: profileActionBuilder,
            isUserInLive: isUserInLive,
            onSelectionActiveChanged:
                onSelectionActiveChanged ?? _ignoreMessageSelectionChange,
            selectionResetListenable: textSelectionResetListenable,
          ),
        },
        if (status != null) ...[
          const SizedBox(height: 7),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (message.pending && !message.failed) ...[
                const SizedBox.square(
                  dimension: 11,
                  child: CircularProgressIndicator(
                    strokeWidth: 1.6,
                    color: UiColors.textMuted,
                  ),
                ),
                const SizedBox(width: 6),
              ],
              Text(
                status,
                style: UiTypography.label.copyWith(
                  color: message.failed ? UiColors.danger : UiColors.textMuted,
                ),
              ),
            ],
          ),
        ],
      ],
    );
  }
}

void _ignoreMessageSelectionChange(bool active) {}

class _PlaylistShareBody extends StatelessWidget {
  const _PlaylistShareBody({
    required this.message,
    required this.playlist,
    required this.onView,
    required this.currentUser,
    required this.onResolveUserProfile,
    required this.onResolveRoomProfile,
    required this.onEnterCommonRoom,
    required this.userProfileActionBuilder,
  });

  final Message message;
  final SharedMusicPlaylist playlist;
  final Future<void> Function(
    BuildContext context,
    Message message,
    SharedMusicPlaylist playlist,
  )?
  onView;
  final CurrentUser currentUser;
  final UserProfileResolver? onResolveUserProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterCommonRoom;
  final UserProfileActionBuilder? userProfileActionBuilder;

  Future<void> _open(BuildContext context) {
    final view = onView;
    if (view == null) return Future<void>.value();
    return view(context, message, playlist);
  }

  @override
  Widget build(BuildContext context) {
    final cardData = MusicPlaylistCardData(
      id: playlist.id,
      name: playlist.name,
      songCount: playlist.itemCount,
      createdAt: playlist.createdAt,
      creator: playlist.creator,
    );
    return MusicPlaylistHoverCard(
      data: cardData,
      currentUser: currentUser,
      onResolveUserProfile: onResolveUserProfile,
      onResolveRoomProfile: onResolveRoomProfile,
      onEnterCommonRoom: onEnterCommonRoom,
      userProfileActionBuilder: userProfileActionBuilder,
      onPlayAll: null,
      showPrimaryActionWhenDisabled: false,
      onViewPlaylist: () => _open(context),
      child: Container(
        key: ValueKey('shared-music-playlist-message-${playlist.id}'),
        constraints: const BoxConstraints(minWidth: 210, maxWidth: 360),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: UiColors.surfaceRaised,
          border: Border.all(color: UiColors.border),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            const Icon(Icons.queue_music, color: UiColors.accent, size: 26),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    playlist.name,
                    style: UiTypography.body.copyWith(
                      color: UiColors.text,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    '${playlist.itemCount} 首歌曲',
                    style: UiTypography.label.copyWith(
                      color: UiColors.textMuted,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            IconButton(
              key: ValueKey<String>(
                'shared-music-playlist-open-${playlist.id}',
              ),
              tooltip: '查看歌单',
              onPressed: onView == null
                  ? null
                  : () => unawaited(_open(context)),
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints.tightFor(width: 36, height: 40),
              icon: const Icon(
                Icons.chevron_right,
                color: UiColors.textMuted,
                size: 22,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MessageBubble extends StatefulWidget {
  const _MessageBubble({
    required this.message,
    required this.outgoing,
    required this.timestampNow,
    required this.showDetailedTimestamps,
    required this.transfer,
    required this.fileDownloads,
    required this.downloadActions,
    required this.voicePlaybackActions,
    required this.imagePreviewActions,
    required this.messageActions,
    required this.mentionHighlighted,
    required this.mentionTargetsCurrentUser,
    required this.currentUser,
    required this.currentUserMentionIdentity,
    required this.ownerUserId,
    required this.mentionMembers,
    this.onResolveSenderProfile,
    this.onResolveRoomProfile,
    this.onEnterProfileRoom,
    this.profileActionBuilder,
    this.isUserInLive,
  });

  final Message message;
  final bool outgoing;
  final DateTime timestampNow;
  final bool showDetailedTimestamps;
  final FileTransferState? transfer;
  final Map<String, FileTransferState> fileDownloads;
  final ChatFileDownloadActions downloadActions;
  final ChatVoicePlaybackActions voicePlaybackActions;
  final ChatImagePreviewActions imagePreviewActions;
  final ChatMessageActions messageActions;
  final bool mentionHighlighted;
  final bool mentionTargetsCurrentUser;
  final CurrentUser currentUser;
  final UserSummary currentUserMentionIdentity;
  final String? ownerUserId;
  final List<RoomMember> mentionMembers;
  final Future<UserSummary> Function(UserSummary sender)?
  onResolveSenderProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterProfileRoom;
  final UserProfileActionBuilder? profileActionBuilder;
  final bool Function(String userId)? isUserInLive;

  @override
  State<_MessageBubble> createState() => _MessageBubbleState();
}

class _MessageBubbleState extends State<_MessageBubble> {
  late final UiAndroidLongPressTracker _androidLongPressTracker =
      UiAndroidLongPressTracker(onLongPress: _handleAndroidBubbleLongPress);
  final ValueNotifier<int> _textSelectionReset = ValueNotifier<int>(0);
  bool _textSelectionActive = false;
  bool _contextMenuActive = false;
  bool _androidLongPressEnabled = false;
  bool _textSelectionActiveAtPointerDown = false;
  bool _androidLongPressHandled = false;

  @override
  void dispose() {
    _androidLongPressTracker.cancel();
    _textSelectionReset.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    _androidLongPressEnabled =
        Theme.of(context).platform == TargetPlatform.android;
    final contentKind = message_display.messageContentKind(widget.message);
    final contextMenuActive = _contextMenuActive;
    final backgroundColor = widget.outgoing ? _outgoingBubble : _incomingBubble;
    final mentionBackgroundColor = widget.outgoing
        ? UiColors.amber.withValues(alpha: 0.18)
        : UiColors.amber.withValues(alpha: 0.13);
    final contextMentionBackgroundColor = widget.outgoing
        ? UiColors.amber.withValues(alpha: 0.28)
        : UiColors.amber.withValues(alpha: 0.22);
    final targetsCurrentUser = widget.mentionTargetsCurrentUser;
    final mentionHighlightColor = Color.alphaBlend(
      mentionBackgroundColor,
      backgroundColor,
    );
    final contextMentionHighlightColor = Color.alphaBlend(
      contextMentionBackgroundColor,
      backgroundColor,
    );
    final mentionEmphasized =
        targetsCurrentUser && (contextMenuActive || widget.mentionHighlighted);
    final highlightColor = contextMenuActive && !targetsCurrentUser
        ? UiColors.selected
        : targetsCurrentUser
        ? mentionEmphasized
              ? contextMentionHighlightColor
              : mentionHighlightColor
        : widget.mentionHighlighted
        ? UiColors.selected.withValues(alpha: 0.86)
        : backgroundColor;
    final borderColor = contextMenuActive && !targetsCurrentUser
        ? UiColors.selectedBorder
        : targetsCurrentUser
        ? mentionEmphasized
              ? UiColors.amber.withValues(alpha: 0.72)
              : UiColors.amber.withValues(alpha: 0.58)
        : widget.mentionHighlighted
        ? UiColors.selectedBorder
        : widget.outgoing
        ? UiColors.accentBorder
        : UiColors.border;

    return Listener(
      onPointerDown: (event) {
        _handleBubblePointerDown(event, contentKind);
        _textSelectionActiveAtPointerDown = _textSelectionActive;
        _androidLongPressHandled = false;
        _androidLongPressTracker.handlePointerDown(
          event,
          enabled: _androidLongPressEnabled,
        );
      },
      onPointerMove: _androidLongPressTracker.handlePointerMove,
      onPointerUp: (event) {
        _androidLongPressTracker.handlePointerUp(event);
        _androidLongPressHandled = false;
      },
      onPointerCancel: (event) {
        _androidLongPressTracker.handlePointerCancel(event);
        _androidLongPressHandled = false;
      },
      child: GestureDetector(
        behavior: HitTestBehavior.translucent,
        onLongPressStart: _androidLongPressEnabled
            ? (details) => _handleAndroidBubbleLongPress(details.globalPosition)
            : null,
        child: AnimatedContainer(
          key: ValueKey('message-bubble-surface-${widget.message.id}'),
          duration:
              widget.mentionTargetsCurrentUser || widget.mentionHighlighted
              ? Duration.zero
              : const Duration(milliseconds: 90),
          curve: Curves.easeOutCubic,
          decoration: BoxDecoration(
            color: highlightColor,
            borderRadius: BorderRadius.circular(UiRadii.lg),
            border: Border.all(color: borderColor),
          ),
          child: Padding(
            padding: _messageBubblePadding,
            child: DecoratedBox(
              key: ValueKey('message-bubble-content-${widget.message.id}'),
              decoration: BoxDecoration(
                color: highlightColor,
                borderRadius: BorderRadius.circular(
                  math.max(0, UiRadii.lg - 5),
                ),
              ),
              child: ChatMessageContent(
                message: widget.message,
                outgoing: widget.outgoing,
                timestampNow: widget.timestampNow,
                showDetailedTimestamps: widget.showDetailedTimestamps,
                transfer: widget.transfer,
                fileDownloads: widget.fileDownloads,
                downloadActions: widget.downloadActions,
                voicePlaybackActions: widget.voicePlaybackActions,
                imagePreviewActions: widget.imagePreviewActions,
                currentUser: widget.currentUser,
                currentUserMentionIdentity: widget.currentUserMentionIdentity,
                ownerUserId: widget.ownerUserId,
                mentionMembers: widget.mentionMembers,
                mentionHighlighted: widget.mentionHighlighted,
                onResolveSenderProfile: widget.onResolveSenderProfile,
                onResolveRoomProfile: widget.onResolveRoomProfile,
                onEnterProfileRoom: widget.onEnterProfileRoom,
                profileActionBuilder: widget.profileActionBuilder,
                isUserInLive: widget.isUserInLive,
                onSelectionActiveChanged: _handleTextSelectionActiveChanged,
                textSelectionResetListenable: _textSelectionReset,
                onOpenQuote: widget.messageActions.onOpenQuote,
                onViewSharedPlaylist:
                    widget.messageActions.onViewSharedPlaylist,
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _handleAndroidBubbleLongPress(Offset position) {
    if (!_androidLongPressEnabled || _androidLongPressHandled) return;
    _androidLongPressHandled = true;
    final contentKind = message_display.messageContentKind(widget.message);
    if (contentKind == message_display.MessageContentKind.text &&
        _textSelectionActiveAtPointerDown) {
      return;
    }
    if (contentKind == message_display.MessageContentKind.text) {
      _textSelectionReset.value++;
    }
    ContextMenuController.removeAny();
    _showBubbleContextMenuAt(
      position,
      contentKind,
      textSelectionActive: _textSelectionActiveAtPointerDown,
    );
  }

  void _handleBubblePointerDown(
    PointerDownEvent event,
    message_display.MessageContentKind contentKind,
  ) {
    if ((event.buttons & kSecondaryMouseButton) == 0) return;
    _showBubbleContextMenuAt(
      event.position,
      contentKind,
      textSelectionActive: _textSelectionActive,
    );
  }

  void _showBubbleContextMenuAt(
    Offset position,
    message_display.MessageContentKind contentKind, {
    required bool textSelectionActive,
  }) {
    if (contentKind == message_display.MessageContentKind.text &&
        textSelectionActive) {
      return;
    }
    if (contentKind == message_display.MessageContentKind.sticker) {
      _showContextMenuWithHighlight(
        () => _showStickerContextMenu(
          context: context,
          position: position,
          message: widget.message,
          attachment: widget.message.stickerAttachment!,
          imagePreviewActions: widget.imagePreviewActions,
          messageActions: widget.messageActions,
        ),
      );
      return;
    }
    _showContextMenuWithHighlight(
      () => _showChatMessageContextMenu(
        context: context,
        position: position,
        message: widget.message,
        actions: widget.messageActions,
      ),
    );
  }

  void _handleTextSelectionActiveChanged(bool active) {
    if (_textSelectionActive == active) return;
    setState(() => _textSelectionActive = active);
  }

  void _showContextMenuWithHighlight(Future<void> Function() showMenu) {
    _setContextMenuActive(true);
    unawaited(
      showMenu().whenComplete(() {
        if (mounted) _setContextMenuActive(false);
      }),
    );
  }

  void _setContextMenuActive(bool active) {
    if (!mounted || _contextMenuActive == active) return;
    setState(() => _contextMenuActive = active);
  }
}

class _MessageQuoteCard extends StatelessWidget {
  const _MessageQuoteCard({
    required this.quote,
    this.onTap,
    this.onClose,
    this.compact = false,
    this.imagePreviewActions,
    this.timestampNow,
    this.showDetailedTimestamps = false,
  });

  final MessageQuote quote;
  final VoidCallback? onTap;
  final VoidCallback? onClose;
  final bool compact;
  final ChatImagePreviewActions? imagePreviewActions;
  final DateTime? timestampNow;
  final bool showDetailedTimestamps;

  @override
  Widget build(BuildContext context) {
    final body = quote.body.trim().isEmpty ? '[消息]' : quote.body.trim();
    final hasThumbnail = quote.previewAttachment?.asset != null;
    final timestamp = showDetailedTimestamps
        ? message_display.formatDetailedChatTimestamp(quote.createdAt)
        : message_display.formatChatTimestamp(
            quote.createdAt,
            now: timestampNow,
          );
    final senderDisplayName = quote.senderDisplayName.trim();
    final header = senderDisplayName.isEmpty
        ? timestamp
        : '$senderDisplayName  $timestamp';
    final card = Container(
      key: ValueKey('message-quote-${quote.messageId}'),
      width: double.infinity,
      padding: EdgeInsets.fromLTRB(10, compact ? 7 : 8, 6, compact ? 7 : 8),
      decoration: BoxDecoration(
        color: UiColors.surfaceLow,
        borderRadius: BorderRadius.circular(UiRadii.md),
        border: Border.all(color: UiColors.border),
      ),
      child: Stack(
        children: [
          Positioned(
            left: 0,
            top: 0,
            bottom: 0,
            width: 3,
            child: Container(
              key: ValueKey('message-quote-indicator-${quote.messageId}'),
              decoration: BoxDecoration(
                color: UiColors.textMuted,
                borderRadius: BorderRadius.circular(999),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.only(left: 12),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Expanded(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        header,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: UiTypography.label.copyWith(
                          color: UiColors.textSecondary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 3),
                      if (hasThumbnail)
                        _MessageQuoteThumbnail(
                          quote: quote,
                          actions: imagePreviewActions,
                          compact: compact,
                          fallbackText: body,
                        )
                      else
                        Text(
                          body,
                          maxLines: compact ? 1 : 4,
                          overflow: TextOverflow.ellipsis,
                          style: UiTypography.body.copyWith(
                            color: UiColors.text,
                            fontSize: 12,
                            height: 1.35,
                          ),
                        ),
                    ],
                  ),
                ),
                if (onClose != null) ...[
                  const SizedBox(width: 6),
                  IconButton(
                    key: ValueKey('composer-quote-close-${quote.messageId}'),
                    onPressed: onClose,
                    icon: const Icon(Icons.close),
                    iconSize: 16,
                    color: UiColors.textMuted,
                    tooltip: '取消引用',
                    padding: EdgeInsets.zero,
                    constraints: const BoxConstraints(
                      minWidth: 28,
                      minHeight: 28,
                    ),
                    splashRadius: 16,
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
    if (onTap == null) return card;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: card,
      ),
    );
  }
}

class _MessageQuoteThumbnail extends StatelessWidget {
  const _MessageQuoteThumbnail({
    required this.quote,
    required this.actions,
    required this.compact,
    required this.fallbackText,
  });

  final MessageQuote quote;
  final ChatImagePreviewActions? actions;
  final bool compact;
  final String fallbackText;

  @override
  Widget build(BuildContext context) {
    final attachment = quote.previewAttachment;
    final asset = attachment?.asset;
    if (attachment == null || asset == null) return const SizedBox.shrink();
    final config = AppConfigScope.of(context);
    final thumbnailUrl = config.resolveAssetUrl(
      asset.thumbnailUrl ?? asset.url,
    );
    final fullUrl = config.resolveAssetUrl(asset.url);
    if (thumbnailUrl == null || thumbnailUrl.isEmpty) {
      return Text(
        fallbackText,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: UiTypography.body.copyWith(color: UiColors.text, fontSize: 12),
      );
    }
    final isSticker = attachment.type == 'sticker';
    final title = isSticker
        ? message_display.stickerPreviewFilename(attachment)
        : file_display.fileAttachmentTitle(attachment);
    final dimension = compact ? 44.0 : 64.0;
    final image = ClipRRect(
      borderRadius: BorderRadius.circular(UiRadii.sm),
      child: CachedAssetImage(
        url: thumbnailUrl,
        filename: asset.filename ?? title,
        mimeType: asset.mimeType,
        expectedBytes: asset.sizeBytes,
        cache: actions?.mediaCache,
        width: dimension,
        height: dimension,
        fit: isSticker ? BoxFit.contain : BoxFit.cover,
        errorBuilder: (context, error, stackTrace) => SizedBox(
          width: compact ? 120 : 180,
          child: Text(
            fallbackText,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: UiTypography.body.copyWith(
              color: UiColors.text,
              fontSize: 12,
            ),
          ),
        ),
      ),
    );
    final previewActions = actions;
    final previewUrl = fullUrl == null || fullUrl.isEmpty
        ? thumbnailUrl
        : fullUrl;
    if (previewActions == null) return image;
    return Tooltip(
      message: '查看预览',
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        child: GestureDetector(
          key: ValueKey('message-quote-thumbnail-${quote.messageId}'),
          behavior: HitTestBehavior.opaque,
          onTap: () => showChatImagePreview(
            context,
            imageUrl: previewUrl,
            suggestedName: title,
            actions: previewActions,
          ),
          child: image,
        ),
      ),
    );
  }
}

class _TextBody extends StatefulWidget {
  const _TextBody({
    required this.message,
    required this.profileCurrentUser,
    required this.currentUser,
    required this.ownerUserId,
    required this.mentionMembers,
    required this.mentionHighlighted,
    required this.onSelectionActiveChanged,
    this.selectionResetListenable,
    this.onResolveSenderProfile,
    this.onResolveRoomProfile,
    this.onEnterProfileRoom,
    this.profileActionBuilder,
    this.isUserInLive,
  });

  final Message message;
  final CurrentUser profileCurrentUser;
  final UserSummary currentUser;
  final String? ownerUserId;
  final List<RoomMember> mentionMembers;
  final bool mentionHighlighted;
  final ValueChanged<bool> onSelectionActiveChanged;
  final Listenable? selectionResetListenable;
  final Future<UserSummary> Function(UserSummary sender)?
  onResolveSenderProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onEnterProfileRoom;
  final UserProfileActionBuilder? profileActionBuilder;
  final bool Function(String userId)? isUserInLive;

  @override
  State<_TextBody> createState() => _TextBodyState();
}

class _TextBodyState extends State<_TextBody> {
  late final _MessageTextController _controller;
  final FocusNode _focusNode = FocusNode();
  final UndoHistoryController _undoController = UndoHistoryController();
  final Object _tapRegionGroup = Object();
  late final EditableTextContextMenuBuilder _contextMenuBuilder =
      _buildContextMenu;
  EditableTextState? _activeTextContextMenuState;
  Offset? _lastPointerDownPosition;
  bool _textContextMenuOpen = false;
  bool _textContextMenuActionPressed = false;
  int? _androidOutsidePointer;
  Offset? _androidOutsidePointerOrigin;
  TextSelection? _androidOutsideInitialSelection;
  bool _androidOutsidePointerMoved = false;
  bool _androidOutsideSelectionChanged = false;

  @override
  void initState() {
    super.initState();
    _controller = _MessageTextController(
      text: _displayMessageBody(widget.message),
      currentUser: widget.currentUser,
      ownerUserId: widget.ownerUserId,
      mentions: widget.message.mentions,
      mentionMembers: widget.mentionMembers,
      mentionHighlighted: widget.mentionHighlighted,
      onOpenMentionUser: _handleOpenMentionUser,
      onOpenLink: _handleOpenLink,
    );
    _controller.addListener(_handleControllerChanged);
    widget.selectionResetListenable?.addListener(_handleSelectionReset);
  }

  @override
  void didUpdateWidget(_TextBody oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.selectionResetListenable != widget.selectionResetListenable) {
      oldWidget.selectionResetListenable?.removeListener(_handleSelectionReset);
      widget.selectionResetListenable?.addListener(_handleSelectionReset);
    }
    _controller.updateMentionContext(
      currentUser: widget.currentUser,
      ownerUserId: widget.ownerUserId,
      mentions: widget.message.mentions,
      mentionMembers: widget.mentionMembers,
      mentionHighlighted: widget.mentionHighlighted,
      onOpenMentionUser: _handleOpenMentionUser,
    );
    final oldBody = _displayMessageBody(oldWidget.message);
    final nextBody = _displayMessageBody(widget.message);
    if (oldBody != nextBody) {
      final selection = _controller.selection;
      _controller.value = TextEditingValue(
        text: nextBody,
        selection: _clampMessageTextSelection(selection, nextBody.length),
      );
    }
  }

  @override
  void dispose() {
    _stopAndroidOutsidePointerTracking();
    widget.selectionResetListenable?.removeListener(_handleSelectionReset);
    _controller.removeListener(_handleControllerChanged);
    widget.onSelectionActiveChanged(false);
    _controller.dispose();
    _focusNode.dispose();
    _undoController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return TapRegion(
      groupId: _tapRegionGroup,
      onTapOutside: _handleTapOutside,
      child: Listener(
        onPointerDown: _handlePointerDown,
        child: TextFieldEditingShortcuts(
          controller: _controller,
          focusNode: _focusNode,
          undoController: _undoController,
          onGlobalPrimaryPointerDownDuringSecondaryClickProtection:
              _handleGlobalPrimaryPointerDownDuringTextSelectionProtection,
          child: IntrinsicWidth(
            child: _TightReadOnlyEditableLayout(
              key: ValueKey('message-text-layout-${widget.message.id}'),
              child: TextField(
                controller: _controller,
                focusNode: _focusNode,
                readOnly: true,
                showCursor: false,
                enableInteractiveSelection: true,
                minLines: 1,
                maxLines: null,
                mouseCursor: SystemMouseCursors.text,
                style: UiTypography.body,
                cursorColor: UiColors.accent,
                undoController: _undoController,
                contextMenuBuilder: _contextMenuBuilder,
                decoration: const InputDecoration(
                  isCollapsed: true,
                  border: InputBorder.none,
                  enabledBorder: InputBorder.none,
                  focusedBorder: InputBorder.none,
                  disabledBorder: InputBorder.none,
                  contentPadding: EdgeInsets.zero,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _handleControllerChanged() {
    final initialSelection = _androidOutsideInitialSelection;
    if (initialSelection != null && _controller.selection != initialSelection) {
      _androidOutsideSelectionChanged = true;
    }
    widget.onSelectionActiveChanged(_hasSelection);
  }

  void _handleSelectionReset() {
    _collapseSelection(hideToolbar: true);
    // The bubble context menu and EditableText both resolve the same Android
    // long press. EditableText can publish its selection later in this frame,
    // so reconcile once more after both recognizers have completed.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _collapseSelection(hideToolbar: true);
    });
  }

  Widget _buildContextMenu(
    BuildContext context,
    EditableTextState editableTextState,
  ) {
    _activeTextContextMenuState = editableTextState;
    return buildTextFieldContextMenu(
      context,
      editableTextState,
      readOnly: true,
      showReadOnlySelectAll: false,
      tapRegionGroupId: _tapRegionGroup,
      onOpenChanged: _handleTextContextMenuOpenChanged,
      onActionPressed: _handleTextContextMenuActionPressed,
    );
  }

  void _handlePointerDown(PointerDownEvent event) {
    if ((event.buttons & kPrimaryMouseButton) != 0) {
      _lastPointerDownPosition = event.position;
    }
  }

  void _handleTextContextMenuActionPressed() {
    _textContextMenuActionPressed = true;
  }

  void _handleTextContextMenuOpenChanged(bool open) {
    if (!mounted) return;
    if (open) {
      _textContextMenuOpen = true;
      _textContextMenuActionPressed = false;
      return;
    }

    final closedByOutsideClick =
        _textContextMenuOpen && !_textContextMenuActionPressed;
    _textContextMenuOpen = false;
    _textContextMenuActionPressed = false;
    _activeTextContextMenuState = null;
    if (closedByOutsideClick &&
        Theme.of(context).platform != TargetPlatform.android) {
      _collapseSelection();
    }
  }

  bool get _hasSelection {
    final selection = _controller.selection;
    return selection.isValid && !selection.isCollapsed;
  }

  void _handleTapOutside(PointerDownEvent event) {
    if ((event.buttons & kPrimaryMouseButton) == 0) return;
    if (Theme.of(context).platform == TargetPlatform.android) {
      _startAndroidOutsidePointerTracking(event);
      return;
    }
    _collapseSelection(hideToolbar: true);
  }

  void _startAndroidOutsidePointerTracking(PointerDownEvent event) {
    if (!_hasSelection) return;
    _stopAndroidOutsidePointerTracking();
    _androidOutsidePointer = event.pointer;
    _androidOutsidePointerOrigin = event.position;
    _androidOutsideInitialSelection = _controller.selection;
    _androidOutsidePointerMoved = false;
    _androidOutsideSelectionChanged = false;
    GestureBinding.instance.pointerRouter.addGlobalRoute(
      _handleAndroidOutsidePointerEvent,
    );
  }

  void _handleAndroidOutsidePointerEvent(PointerEvent event) {
    if (event.pointer != _androidOutsidePointer) return;
    final origin = _androidOutsidePointerOrigin;
    if (origin != null && event.position != origin) {
      _androidOutsidePointerMoved =
          _androidOutsidePointerMoved ||
          (event.position - origin).distance > kTouchSlop;
    }
    final initialSelection = _androidOutsideInitialSelection;
    if (initialSelection != null && _controller.selection != initialSelection) {
      _androidOutsideSelectionChanged = true;
    }
    if (event is! PointerUpEvent && event is! PointerCancelEvent) return;

    final shouldCollapse =
        event is PointerUpEvent &&
        !_androidOutsidePointerMoved &&
        !_androidOutsideSelectionChanged;
    final selectionToCollapse = _androidOutsideInitialSelection;
    _stopAndroidOutsidePointerTracking();
    if (!shouldCollapse || selectionToCollapse == null) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || _controller.selection != selectionToCollapse) return;
      _collapseSelection(hideToolbar: true);
    });
  }

  void _stopAndroidOutsidePointerTracking() {
    if (_androidOutsidePointer != null) {
      GestureBinding.instance.pointerRouter.removeGlobalRoute(
        _handleAndroidOutsidePointerEvent,
      );
    }
    _androidOutsidePointer = null;
    _androidOutsidePointerOrigin = null;
    _androidOutsideInitialSelection = null;
    _androidOutsidePointerMoved = false;
    _androidOutsideSelectionChanged = false;
  }

  void _handleGlobalPrimaryPointerDownDuringTextSelectionProtection(
    PointerDownEvent event,
  ) {
    if (isTextContextMenuPanelHit(event.position)) return;
    _collapseSelection(hideToolbar: true);
  }

  void _collapseSelection({bool hideToolbar = false}) {
    final selection = _controller.selection;
    if (!selection.isValid || selection.isCollapsed) return;
    final offset = selection.extentOffset.clamp(0, _controller.text.length);
    _controller.selection = TextSelection.collapsed(offset: offset);
    widget.onSelectionActiveChanged(false);
    if (hideToolbar) _activeTextContextMenuState?.hideToolbar();
  }

  void _handleOpenLink(Uri uri) {
    unawaited(_openLink(uri));
  }

  void _handleOpenMentionUser(UserSummary user) {
    final position = _lastPointerDownPosition ?? _fallbackProfilePosition();
    unawaited(_openMentionUserProfile(user, position));
  }

  Future<void> _openMentionUserProfile(
    UserSummary user,
    Offset position,
  ) async {
    final resolver = widget.onResolveSenderProfile;
    var displayUser = user;
    if (resolver != null) {
      try {
        displayUser = await resolver(user);
      } catch (_) {
        displayUser = user;
      }
    }
    if (!mounted) return;
    await showUserProfileCardAtPosition(
      context,
      position: position,
      user: displayUser,
      currentUser: widget.profileCurrentUser,
      onResolveProfile: resolver,
      onResolveRoomProfile: widget.onResolveRoomProfile,
      onEnterCommonRoom: widget.onEnterProfileRoom,
      profileActionBuilder: widget.profileActionBuilder,
      inLive: widget.isUserInLive?.call(displayUser.id) ?? false,
      showRoomRole: true,
      resolveOnOpen: false,
    );
  }

  Offset _fallbackProfilePosition() {
    final box = context.findRenderObject() as RenderBox?;
    if (box == null || !box.hasSize) return Offset.zero;
    return box.localToGlobal(Offset(box.size.width / 2, box.size.height));
  }

  Future<void> _openLink(Uri uri) async {
    try {
      await const ExternalUriLauncher().open(uri);
    } catch (_) {
      if (!mounted) return;
      _showMessageContextNotice(
        context,
        '无法打开链接',
        tone: FloatingNoticeTone.error,
      );
    }
  }
}

String _displayMessageBody(Message message) => message.body.trimRight();

/// Sizes the read-only editor from its actual styled text instead of from
/// platform-specific editor chrome (for example the hidden caret allowance).
/// The editor keeps that allowance internally, so wrapping and selection
/// geometry stay intact on every platform and display scale.
class _TightReadOnlyEditableLayout extends SingleChildRenderObjectWidget {
  const _TightReadOnlyEditableLayout({super.key, required super.child});

  @override
  RenderProxyBox createRenderObject(BuildContext context) {
    return _RenderTightReadOnlyEditableLayout();
  }
}

class _RenderTightReadOnlyEditableLayout extends RenderProxyBox {
  @override
  double computeMinIntrinsicWidth(double height) {
    final child = this.child;
    if (child == null) return 0;
    return math.max(
      0,
      child.getMinIntrinsicWidth(height) - _editorTrailingAllowance(height),
    );
  }

  @override
  double computeMaxIntrinsicWidth(double height) {
    final child = this.child;
    if (child == null) return 0;
    return math.max(
      0,
      child.getMaxIntrinsicWidth(height) - _editorTrailingAllowance(height),
    );
  }

  @override
  Size computeDryLayout(BoxConstraints constraints) {
    final child = this.child;
    if (child == null) return constraints.smallest;
    final allowance = _editorTrailingAllowance(constraints.maxHeight);
    final childSize = child.getDryLayout(
      _childConstraints(constraints, allowance),
    );
    return constraints.constrain(
      Size(math.max(0, childSize.width - allowance), childSize.height),
    );
  }

  @override
  void performLayout() {
    final child = this.child;
    if (child == null) {
      size = constraints.smallest;
      return;
    }
    final allowance = _editorTrailingAllowance(constraints.maxHeight);
    child.layout(
      _childConstraints(constraints, allowance),
      parentUsesSize: true,
    );
    size = constraints.constrain(
      Size(math.max(0, child.size.width - allowance), child.size.height),
    );
  }

  double _editorTrailingAllowance(double height) {
    final child = this.child;
    final editable = _findEditable();
    final text = editable?.text;
    if (child == null || editable == null || text == null) return 0;

    final painter = TextPainter(
      text: text,
      textAlign: editable.textAlign,
      textDirection: editable.textDirection,
      textScaler: editable.textScaler,
      maxLines: editable.maxLines,
      locale: editable.locale,
      strutStyle: editable.strutStyle,
      textWidthBasis: editable.textWidthBasis,
      textHeightBehavior: editable.textHeightBehavior,
    )..layout();
    final textWidth = painter.maxIntrinsicWidth;
    painter.dispose();
    return math.max(0, child.getMaxIntrinsicWidth(height) - textWidth);
  }

  RenderEditable? _findEditable() {
    RenderEditable? result;

    void visit(RenderObject object) {
      if (result != null) return;
      if (object is RenderEditable) {
        result = object;
        return;
      }
      object.visitChildren(visit);
    }

    child?.visitChildren(visit);
    return result;
  }

  BoxConstraints _childConstraints(
    BoxConstraints constraints,
    double allowance,
  ) {
    return BoxConstraints(
      minWidth: constraints.minWidth + allowance,
      maxWidth: constraints.maxWidth.isFinite
          ? constraints.maxWidth + allowance
          : double.infinity,
      minHeight: constraints.minHeight,
      maxHeight: constraints.maxHeight,
    );
  }
}

class _MessageTextController extends TextEditingController {
  _MessageTextController({
    required String text,
    required UserSummary currentUser,
    required String? ownerUserId,
    required List<Map<String, Object?>> mentions,
    required List<RoomMember> mentionMembers,
    required bool mentionHighlighted,
    required this.onOpenMentionUser,
    required this.onOpenLink,
  }) : _currentUser = currentUser,
       _ownerUserId = ownerUserId,
       _mentions = mentions,
       _mentionMembers = mentionMembers,
       _mentionHighlighted = mentionHighlighted,
       super(text: text);

  final ValueChanged<Uri> onOpenLink;
  ValueChanged<UserSummary> onOpenMentionUser;
  Map<String, TapGestureRecognizer> _linkRecognizers = {};
  Map<String, TapGestureRecognizer> _mentionRecognizers = {};
  UserSummary _currentUser;
  String? _ownerUserId;
  List<Map<String, Object?>> _mentions;
  List<RoomMember> _mentionMembers;
  bool _mentionHighlighted;

  void updateMentionContext({
    required UserSummary currentUser,
    required String? ownerUserId,
    required List<Map<String, Object?>> mentions,
    required List<RoomMember> mentionMembers,
    required bool mentionHighlighted,
    required ValueChanged<UserSummary> onOpenMentionUser,
  }) {
    final changed =
        _currentUser.id != currentUser.id ||
        _currentUser.displayName != currentUser.displayName ||
        _currentUser.username != currentUser.username ||
        _currentUser.uid != currentUser.uid ||
        _currentUser.roomDisplayName != currentUser.roomDisplayName ||
        _currentUser.roomRole != currentUser.roomRole ||
        _ownerUserId != ownerUserId ||
        !identical(_mentions, mentions) ||
        !identical(_mentionMembers, mentionMembers) ||
        _mentionHighlighted != mentionHighlighted;
    _currentUser = currentUser;
    _ownerUserId = ownerUserId;
    _mentions = mentions;
    _mentionMembers = mentionMembers;
    _mentionHighlighted = mentionHighlighted;
    this.onOpenMentionUser = onOpenMentionUser;
    if (changed) notifyListeners();
  }

  @override
  TextSpan buildTextSpan({
    required BuildContext context,
    TextStyle? style,
    required bool withComposing,
  }) {
    final linkMatches = _messageLinkMatches(text).toList(growable: false);
    final mentionMatches = message_mentions
        .messageMentionRanges(text, labels: _knownMentionLabels)
        .where((range) => !_overlapsAnyLink(range, linkMatches))
        .toList(growable: false);
    if (linkMatches.isEmpty && mentionMatches.isEmpty) {
      _disposeStaleLinkRecognizers(_linkRecognizers);
      _linkRecognizers = {};
      _disposeStaleLinkRecognizers(_mentionRecognizers);
      _mentionRecognizers = {};
      return TextSpan(text: text, style: style);
    }

    final baseStyle = style ?? DefaultTextStyle.of(context).style;
    final linkStyle = baseStyle.copyWith(
      color: UiColors.accent,
      decoration: TextDecoration.underline,
      decorationColor: UiColors.accent,
    );
    final mentionStyle = baseStyle.copyWith(
      color: UiColors.controlAccent,
      fontWeight: FontWeight.w600,
      decoration: TextDecoration.none,
    );
    final segments = <_MessageInlineSegment>[
      for (final match in linkMatches) _MessageInlineSegment.link(match),
      for (final match in mentionMatches) _MessageInlineSegment.mention(match),
    ]..sort((a, b) => a.start.compareTo(b.start));
    final children = <InlineSpan>[];
    final staleRecognizers = Map<String, TapGestureRecognizer>.of(
      _linkRecognizers,
    );
    final nextRecognizers = <String, TapGestureRecognizer>{};
    final staleMentionRecognizers = Map<String, TapGestureRecognizer>.of(
      _mentionRecognizers,
    );
    final nextMentionRecognizers = <String, TapGestureRecognizer>{};
    var cursor = 0;
    for (final segment in segments) {
      if (segment.start < cursor) continue;
      if (segment.start > cursor) {
        children.add(TextSpan(text: text.substring(cursor, segment.start)));
      }
      if (segment.link != null) {
        final match = segment.link!;
        final key = '${match.start}:${match.end}:${match.uri}';
        final recognizer =
            staleRecognizers.remove(key) ?? TapGestureRecognizer();
        recognizer.onTap = () => onOpenLink(match.uri);
        nextRecognizers[key] = recognizer;
        children.add(
          TextSpan(
            text: text.substring(match.start, match.end),
            style: linkStyle,
            recognizer: recognizer,
            mouseCursor: SystemMouseCursors.click,
          ),
        );
      } else {
        final range = segment.mention!;
        final label = text.substring(range.start + 1, range.end);
        final mentionMember =
            message_mentions.messageMentionKindForLabel(label) ==
                message_mentions.MessageMentionKind.user
            ? message_mentions.resolveMessageMentionMember(
                label: label,
                members: _mentionMembers,
              )
            : null;
        TapGestureRecognizer? recognizer;
        if (mentionMember != null) {
          final key = '${range.start}:${range.end}:${mentionMember.user.id}';
          recognizer =
              staleMentionRecognizers.remove(key) ?? TapGestureRecognizer();
          recognizer.onTap = () => onOpenMentionUser(mentionMember.user);
          nextMentionRecognizers[key] = recognizer;
        }
        children.add(
          TextSpan(
            text: text.substring(range.start, range.end),
            style: mentionStyle,
            recognizer: recognizer,
          ),
        );
      }
      cursor = segment.end;
    }
    if (cursor < text.length) {
      children.add(TextSpan(text: text.substring(cursor)));
    }
    _disposeStaleLinkRecognizers(staleRecognizers);
    _linkRecognizers = nextRecognizers;
    _disposeStaleLinkRecognizers(staleMentionRecognizers);
    _mentionRecognizers = nextMentionRecognizers;
    return TextSpan(style: baseStyle, children: children);
  }

  @override
  void dispose() {
    _disposeStaleLinkRecognizers(_linkRecognizers);
    _linkRecognizers = {};
    _disposeStaleLinkRecognizers(_mentionRecognizers);
    _mentionRecognizers = {};
    super.dispose();
  }

  void _disposeStaleLinkRecognizers(
    Map<String, TapGestureRecognizer> recognizers,
  ) {
    for (final recognizer in recognizers.values) {
      recognizer.dispose();
    }
  }

  Iterable<String> get _knownMentionLabels sync* {
    for (final mention in _mentions) {
      final label = mention['label'] as String?;
      if (label != null && label.trim().isNotEmpty) yield label;
    }
  }
}

class _MessageInlineSegment {
  const _MessageInlineSegment.link(this.link) : mention = null;

  const _MessageInlineSegment.mention(this.mention) : link = null;

  final _MessageLinkMatch? link;
  final message_mentions.MessageMentionRange? mention;

  int get start => link?.start ?? mention!.start;

  int get end => link?.end ?? mention!.end;
}

bool _overlapsAnyLink(
  message_mentions.MessageMentionRange range,
  List<_MessageLinkMatch> links,
) {
  for (final link in links) {
    if (range.start < link.end && range.end > link.start) return true;
  }
  return false;
}

class _MessageLinkMatch {
  const _MessageLinkMatch({
    required this.start,
    required this.end,
    required this.uri,
  });

  final int start;
  final int end;
  final Uri uri;
}

final RegExp _messageLinkPattern = RegExp(
  r"""(?:(?:https?:\/\/)|(?:www\.))[^\s<>{}\[\]"']+""",
  caseSensitive: false,
);

const String _messageLinkTrailingPunctuation = '.,!?;:，。！？；：、)]}）】》';

Iterable<_MessageLinkMatch> _messageLinkMatches(String value) sync* {
  for (final match in _messageLinkPattern.allMatches(value)) {
    var end = match.end;
    while (end > match.start &&
        _messageLinkTrailingPunctuation.contains(value[end - 1])) {
      end--;
    }
    if (end <= match.start) continue;
    final raw = value.substring(match.start, end);
    final uri = _messageLinkUri(raw);
    if (uri == null) continue;
    yield _MessageLinkMatch(start: match.start, end: end, uri: uri);
  }
}

Uri? _messageLinkUri(String value) {
  final lower = value.toLowerCase();
  final normalized = lower.startsWith('http://') || lower.startsWith('https://')
      ? value
      : 'https://$value';
  final uri = Uri.tryParse(normalized);
  if (uri == null || !uri.hasScheme || uri.host.isEmpty) return null;
  if (uri.scheme != 'http' && uri.scheme != 'https') return null;
  return uri;
}

TextSelection _clampMessageTextSelection(TextSelection selection, int length) {
  if (!selection.isValid) return TextSelection.collapsed(offset: length);
  return TextSelection(
    baseOffset: math.min(selection.baseOffset, length),
    extentOffset: math.min(selection.extentOffset, length),
    affinity: selection.affinity,
    isDirectional: selection.isDirectional,
  );
}

class _StickerBody extends StatelessWidget {
  const _StickerBody({
    required this.message,
    required this.attachment,
    required this.imagePreviewActions,
  });

  final Message message;
  final MessageAttachment attachment;
  final ChatImagePreviewActions imagePreviewActions;

  @override
  Widget build(BuildContext context) {
    final asset = attachment.asset;
    final config = AppConfigScope.of(context);
    final imageUrl = asset == null
        ? null
        : config.resolveAssetUrl(asset.thumbnailUrl ?? asset.url);
    // The preview shows the full-resolution sticker, not the thumbnail.
    final previewUrl = asset == null ? null : config.resolveAssetUrl(asset.url);
    final name = message_display.stickerAttachmentTitle(attachment);
    final image = imageUrl != null && imageUrl.isNotEmpty
        ? ClipRRect(
            borderRadius: BorderRadius.circular(UiRadii.md),
            child: CachedAssetImage(
              url: imageUrl,
              filename: asset?.filename ?? name,
              mimeType: asset?.mimeType,
              expectedBytes: asset?.sizeBytes,
              cache: imagePreviewActions.mediaCache,
              width: 132,
              height: 132,
              fit: BoxFit.contain,
              errorBuilder: (context, error, stackTrace) =>
                  _StickerFallback(name: name),
            ),
          )
        : _StickerFallback(name: name);

    final tappablePreviewUrl = previewUrl != null && previewUrl.isNotEmpty
        ? previewUrl
        : (imageUrl != null && imageUrl.isNotEmpty ? imageUrl : null);

    final preview = tappablePreviewUrl == null
        ? image
        : MouseRegion(
            cursor: SystemMouseCursors.click,
            child: GestureDetector(
              onTap: () => showChatImagePreview(
                context,
                imageUrl: tappablePreviewUrl,
                suggestedName: message_display.stickerPreviewFilename(
                  attachment,
                ),
                actions: imagePreviewActions,
                stickerSource: attachment.stickerId != null
                    ? (message: message, attachment: attachment)
                    : null,
              ),
              child: image,
            ),
          );

    return Tooltip(
      message: name,
      waitDuration: const Duration(milliseconds: 350),
      child: preview,
    );
  }
}

Future<void> _showStickerContextMenu({
  required BuildContext context,
  required Offset position,
  required Message message,
  required MessageAttachment attachment,
  required ChatImagePreviewActions imagePreviewActions,
  required ChatMessageActions messageActions,
}) {
  final stickerId = attachment.stickerId;

  final stickerItems = <UiContextMenuItem>[
    if (imagePreviewActions.onSaveSticker != null)
      UiContextMenuItem(
        label: '添加到我的表情包',
        onPressed: () => unawaited(
          _runStickerContextAction(
            context,
            message,
            attachment,
            imagePreviewActions.onSaveSticker!,
            successMessage: '已添加到我的表情包',
          ),
        ),
      ),
    if (imagePreviewActions.onSaveRoomSticker != null)
      UiContextMenuItem(
        label: '添加到房间表情包',
        onPressed: () => unawaited(
          _runStickerContextAction(
            context,
            message,
            attachment,
            imagePreviewActions.onSaveRoomSticker!,
            successMessage: '已添加到房间表情包',
          ),
        ),
      ),
  ];
  final sections = [
    ..._messageContextMenuSections(
      context: context,
      message: message,
      actions: messageActions,
      includeCopy: true,
      includeQuote: true,
      includeDelete: false,
      includeRecall: false,
    ),
    if (stickerId != null && stickerId.isNotEmpty && stickerItems.isNotEmpty)
      UiContextMenuSection(stickerItems),
    ..._messageContextMenuSections(
      context: context,
      message: message,
      actions: messageActions,
      includeCopy: false,
      includeQuote: false,
      includeDelete: false,
      includeRecall: true,
    ),
    ..._messageContextMenuSections(
      context: context,
      message: message,
      actions: messageActions,
      includeCopy: false,
      includeQuote: false,
      includeDelete: true,
      includeRecall: false,
    ),
  ];

  return showUiContextMenu(context, position: position, sections: sections);
}

Future<void> _runStickerContextAction(
  BuildContext context,
  Message message,
  MessageAttachment attachment,
  Future<void> Function(Message message, MessageAttachment attachment) action, {
  required String successMessage,
}) async {
  try {
    await action(message, attachment);
    if (!context.mounted) return;
    _showMessageContextNotice(
      context,
      successMessage,
      tone: FloatingNoticeTone.success,
    );
  } catch (error) {
    if (!context.mounted) return;
    _showMessageContextNotice(
      context,
      userFacingErrorMessage(error, fallback: '表情操作失败'),
      tone: FloatingNoticeTone.error,
    );
  }
}

void _showMessageContextNotice(
  BuildContext context,
  String message, {
  FloatingNoticeTone tone = FloatingNoticeTone.info,
}) {
  showFloatingNotice(context, message, tone: tone);
}

class _StickerFallback extends StatelessWidget {
  const _StickerFallback({required this.name});

  final String name;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.surfaceLow,
        borderRadius: BorderRadius.circular(UiRadii.md),
        border: Border.all(color: UiColors.border),
      ),
      child: SizedBox(
        width: 132,
        height: 96,
        child: Center(
          child: Text(
            name,
            maxLines: 2,
            textAlign: TextAlign.center,
            overflow: TextOverflow.ellipsis,
            style: UiTypography.label,
          ),
        ),
      ),
    );
  }
}

const _voiceAccent = Colors.white;

class _VoiceBody extends StatelessWidget {
  const _VoiceBody({
    required this.message,
    required this.attachment,
    required this.playbackActions,
  });

  final Message message;
  final MessageAttachment attachment;
  final ChatVoicePlaybackActions playbackActions;

  @override
  Widget build(BuildContext context) {
    final resolvedUrl = AppConfigScope.of(
      context,
    ).resolveAssetUrl(attachment.asset?.url);
    final playbackKey = message.clientMessageId;
    final playing = playbackActions.isPlaying(playbackKey);
    final canPlay =
        resolvedUrl != null &&
        resolvedUrl.isNotEmpty &&
        playbackActions.onToggle != null;
    final duration = voice_display.voiceAttachmentDuration(attachment);
    final durationText = voice_display.formatVoiceBubbleDuration(duration);
    final waveformWidth = voice_display.voiceWaveformWidth(duration);
    final playbackProgress = playbackActions.progressFor(
      playbackKey,
      fallbackDuration: duration,
    );

    void togglePlayback() {
      final url = resolvedUrl;
      final onToggle = playbackActions.onToggle;
      if (url == null || url.isEmpty || onToggle == null) return;
      onToggle(playbackKey, url);
    }

    return Semantics(
      button: canPlay,
      label: playing ? '停止播放录音' : '播放录音',
      child: MouseRegion(
        cursor: canPlay ? SystemMouseCursors.click : MouseCursor.defer,
        child: GestureDetector(
          onTap: canPlay ? togglePlayback : null,
          behavior: HitTestBehavior.opaque,
          child: ConstrainedBox(
            key: const ValueKey('voice-body'),
            constraints: const BoxConstraints(minWidth: 150, maxWidth: 304),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox.square(
                  dimension: 22,
                  child: Icon(
                    playing ? Icons.stop_rounded : Icons.play_arrow_rounded,
                    color: canPlay ? _voiceAccent : UiColors.textMuted,
                    size: 22,
                  ),
                ),
                const SizedBox(width: 8),
                _VoiceWaveform(
                  key: const ValueKey('voice-waveform'),
                  width: waveformWidth,
                  progress: playbackProgress,
                ),
                if (durationText.isNotEmpty) ...[
                  const SizedBox(width: 12),
                  Text(
                    durationText,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: UiTypography.body.copyWith(
                      color: _voiceAccent,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _VoiceWaveform extends StatelessWidget {
  const _VoiceWaveform({
    super.key,
    required this.width,
    required this.progress,
  });

  final double width;
  final double progress;

  static const _heights = <double>[
    8,
    16,
    22,
    13,
    18,
    10,
    24,
    15,
    9,
    19,
    12,
    21,
    7,
  ];

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      height: 28,
      child: Stack(
        children: [
          _VoiceWaveformBars(
            color: _voiceAccent.withValues(alpha: 0.28),
            width: width,
          ),
          Positioned.fill(
            child: ClipRect(
              clipper: _VoiceProgressClipper(progress),
              child: _VoiceWaveformBars(color: _voiceAccent, width: width),
            ),
          ),
        ],
      ),
    );
  }
}

class _VoiceWaveformBars extends StatelessWidget {
  const _VoiceWaveformBars({required this.color, required this.width});

  final Color color;
  final double width;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      height: 28,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          for (final height in _waveformHeightsForWidth(width))
            DecoratedBox(
              decoration: BoxDecoration(
                color: color,
                borderRadius: BorderRadius.circular(999),
              ),
              child: SizedBox(width: 3, height: height),
            ),
        ],
      ),
    );
  }
}

List<double> _waveformHeightsForWidth(double width) {
  final count = (width / 6).round().clamp(12, 28);
  return [
    for (var i = 0; i < count; i++)
      _VoiceWaveform._heights[i % _VoiceWaveform._heights.length],
  ];
}

class _VoiceProgressClipper extends CustomClipper<Rect> {
  const _VoiceProgressClipper(this.progress);

  final double progress;

  @override
  Rect getClip(Size size) {
    final clamped = progress.clamp(0, 1).toDouble();
    return Rect.fromLTWH(0, 0, size.width * clamped, size.height);
  }

  @override
  bool shouldReclip(covariant _VoiceProgressClipper oldClipper) {
    return oldClipper.progress != progress;
  }
}

class _FileBody extends StatelessWidget {
  const _FileBody({
    required this.message,
    required this.outgoing,
    required this.transfer,
    required this.fileDownloads,
    required this.downloadActions,
    required this.imagePreviewActions,
  });

  final Message message;
  final bool outgoing;
  final FileTransferState? transfer;
  final Map<String, FileTransferState> fileDownloads;
  final ChatFileDownloadActions downloadActions;
  final ChatImagePreviewActions imagePreviewActions;

  @override
  Widget build(BuildContext context) {
    final attachments = message.fileAttachments.toList(growable: false);
    final showBody = message_display.shouldShowFileAttachmentBody(
      body: message.body,
      attachments: attachments,
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var index = 0; index < attachments.length; index++) ...[
          if (index > 0) const SizedBox(height: 8),
          _FileAttachmentTile(
            message: message,
            outgoing: outgoing,
            attachment: attachments[index],
            index: index,
            // The outgoing upload transfer only ever applies to the first
            // attachment; the slot helper resolves upload vs. download for us.
            slot: file_display.fileAttachmentTransferSlot(
              message: message,
              attachment: attachments[index],
              index: index,
              uploadTransfer: transfer,
              downloads: fileDownloads,
            ),
            downloadActions: downloadActions,
            imagePreviewActions: imagePreviewActions,
          ),
        ],
        if (showBody) ...[
          const SizedBox(height: 8),
          Text(message.body, style: UiTypography.body),
        ],
      ],
    );
  }
}

class _FileAttachmentTile extends StatelessWidget {
  const _FileAttachmentTile({
    required this.message,
    required this.outgoing,
    required this.attachment,
    required this.index,
    required this.slot,
    required this.downloadActions,
    required this.imagePreviewActions,
  });

  final Message message;
  final bool outgoing;
  final MessageAttachment attachment;
  final int index;
  final file_display.FileAttachmentTransferSlot slot;
  final ChatFileDownloadActions downloadActions;
  final ChatImagePreviewActions imagePreviewActions;

  @override
  Widget build(BuildContext context) {
    final asset = attachment.asset;
    final transfer = slot.transfer;
    final title = file_display.fileAttachmentTitle(attachment);
    final meta = transfer == null
        ? file_display.fileAttachmentMeta(asset)
        : file_display.fileTransferProgressState(transfer).label;
    final progress = transfer == null
        ? null
        : file_display.fileTransferProgressState(transfer);
    final previewPath = file_display.fileAttachmentPreviewPath(asset);

    // Resolve the asset URL here, at the widget layer, so the download
    // orchestration stays free of [AppConfig].
    final config = AppConfigScope.of(context);
    final resolvedUrl = config.resolveAssetUrl(asset?.url);
    final previewUrl = config.resolveAssetUrl(previewPath);
    final previewSize = file_display.fileAttachmentPreviewSize(asset);
    final interaction = file_display.fileAttachmentInteractionState(
      title: title,
      url: resolvedUrl,
      transfer: transfer,
    );
    final trailing = file_display.fileAttachmentTrailingState(
      transfer: transfer,
      canDownload: interaction.canDownload,
    );

    void startDownload() {
      final url = resolvedUrl;
      if (url == null) return;
      downloadActions.onDownload(message, attachment, index, url);
    }

    final tile = DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(UiRadii.md),
        // No fill; the outline matches the surrounding bubble so it reads on
        // both the green outgoing bubble and the darker incoming one.
        border: Border.all(
          color: outgoing ? UiColors.accentBorder : UiColors.borderStrong,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(10),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (previewUrl != null && previewUrl.isNotEmpty) ...[
              _FileImagePreview(
                url: previewUrl,
                title: title,
                width: previewSize.width,
                height: previewSize.height,
                mediaCache: imagePreviewActions.mediaCache,
                onTap: () {
                  final fullUrl =
                      (resolvedUrl != null && resolvedUrl.isNotEmpty)
                      ? resolvedUrl
                      : previewUrl;
                  showChatImagePreview(
                    context,
                    imageUrl: fullUrl,
                    suggestedName: title,
                    actions: imagePreviewActions,
                  );
                },
              ),
              const SizedBox(height: 10),
            ],
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  fileIconForMime(asset?.mimeType),
                  color: UiColors.textSecondary,
                  size: 22,
                ),
                const SizedBox(width: 10),
                Flexible(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: UiTypography.body.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      if (meta.isNotEmpty) ...[
                        const SizedBox(height: 3),
                        Text(
                          meta,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: UiTypography.label.copyWith(
                            color: progress?.failed == true
                                ? UiColors.danger
                                : UiColors.textMuted,
                          ),
                        ),
                      ],
                      if (progress != null) ...[
                        const SizedBox(height: 8),
                        SizedBox(
                          width: 180,
                          child: LinearProgressIndicator(
                            value: progress.value,
                            minHeight: 3,
                            color: progress.failed
                                ? UiColors.danger
                                : UiColors.accent,
                            backgroundColor: UiColors.border,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(width: 10),
                _FileAttachmentTrailing(
                  state: trailing,
                  downloadKey: slot.downloadKey,
                  actions: downloadActions,
                ),
              ],
            ),
          ],
        ),
      ),
    );

    // Tapping an idle, downloadable tile starts the download. While a transfer
    // is in flight the trailing controls own the interaction instead.
    if (trailing.kind != file_display.FileAttachmentTrailingKind.download) {
      return tile;
    }
    return Tooltip(
      message: title,
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        child: GestureDetector(onTap: startDownload, child: tile),
      ),
    );
  }
}

class _FileImagePreview extends StatelessWidget {
  const _FileImagePreview({
    required this.url,
    required this.title,
    required this.width,
    required this.height,
    this.mediaCache,
    this.onTap,
  });

  final String url;
  final String title;
  final double width;
  final double height;
  final MediaCacheController? mediaCache;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final preview = ClipRRect(
      borderRadius: BorderRadius.circular(UiRadii.sm),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: UiColors.surfaceLow,
          border: Border.all(color: UiColors.border),
        ),
        child: SizedBox(
          width: width,
          height: height,
          child: _filePreviewImage(),
        ),
      ),
    );

    if (onTap == null) return preview;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      child: GestureDetector(onTap: onTap, child: preview),
    );
  }

  Widget _filePreviewImage() {
    return CachedAssetImage(
      url: url,
      filename: title,
      cache: mediaCache,
      fit: BoxFit.contain,
      errorBuilder: (context, error, stackTrace) => _filePreviewFallback(),
    );
  }

  Widget _filePreviewFallback() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Text(
          title,
          maxLines: 2,
          textAlign: TextAlign.center,
          overflow: TextOverflow.ellipsis,
          style: UiTypography.label.copyWith(color: UiColors.textMuted),
        ),
      ),
    );
  }
}

/// Trailing controls for a file tile. Renders purely from
/// [file_display.FileAttachmentTrailingState]; the decision of which controls
/// to show lives in `file_display.dart`, so this widget only paints them.
class _FileAttachmentTrailing extends StatelessWidget {
  const _FileAttachmentTrailing({
    required this.state,
    required this.downloadKey,
    required this.actions,
  });

  final file_display.FileAttachmentTrailingState state;
  final String downloadKey;
  final ChatFileDownloadActions actions;

  @override
  Widget build(BuildContext context) {
    switch (state.kind) {
      // Idle/downloadable tiles carry no trailing control: tapping the tile
      // itself starts the download.
      case file_display.FileAttachmentTrailingKind.download:
        return const SizedBox.shrink();
      case file_display.FileAttachmentTrailingKind.activeTransfer:
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            ButtonIcon(
              icon: Icon(
                state.pauseResumeIsResume
                    ? Icons.play_arrow_rounded
                    : Icons.pause_rounded,
              ),
              tooltip: state.pauseResumeTooltip ?? '',
              size: 32,
              onPressed: () => state.pauseResumeIsResume
                  ? actions.onResume(downloadKey)
                  : actions.onPause(downloadKey),
            ),
            const SizedBox(width: 4),
            ButtonIcon(
              icon: const Icon(Icons.close_rounded),
              tooltip: state.cancelTooltip ?? '',
              size: 32,
              onPressed: () => actions.onCancel(downloadKey),
            ),
          ],
        );
      case file_display.FileAttachmentTrailingKind.failed:
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (state.showDismiss)
              ButtonIcon(
                icon: const Icon(Icons.close_rounded),
                tooltip: '清除',
                size: 32,
                onPressed: () => actions.onDismiss(downloadKey),
              ),
          ],
        );
      case file_display.FileAttachmentTrailingKind.sending:
        return const SizedBox.square(
          dimension: 18,
          child: CircularProgressIndicator(
            strokeWidth: 2,
            color: UiColors.accent,
          ),
        );
      case file_display.FileAttachmentTrailingKind.placeholder:
        return const SizedBox.shrink();
    }
  }
}

/// Test-only entry point for a single private message bubble.
@visibleForTesting
class MessageBubbleForTest extends StatelessWidget {
  const MessageBubbleForTest({
    super.key,
    required this.message,
    required this.downloadActions,
    this.imagePreviewActions,
    this.outgoing = false,
    this.transfer,
    this.fileDownloads = const {},
    this.voicePlaybackActions = const ChatVoicePlaybackActions.disabled(),
    this.messageActions = const ChatMessageActions.disabled(),
    this.timestampNow,
    this.showDetailedTimestamps = false,
  });

  final Message message;
  final ChatFileDownloadActions downloadActions;
  final ChatImagePreviewActions? imagePreviewActions;
  final bool outgoing;
  final FileTransferState? transfer;
  final Map<String, FileTransferState> fileDownloads;
  final ChatVoicePlaybackActions voicePlaybackActions;
  final ChatMessageActions messageActions;
  final DateTime? timestampNow;
  final bool showDetailedTimestamps;

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: _messageMaxWidth),
      child: _MessageBubble(
        message: message,
        outgoing: outgoing,
        timestampNow: timestampNow ?? DateTime.now(),
        showDetailedTimestamps: showDetailedTimestamps,
        transfer: transfer,
        fileDownloads: fileDownloads,
        downloadActions: downloadActions,
        voicePlaybackActions: voicePlaybackActions,
        imagePreviewActions:
            imagePreviewActions ?? ChatImagePreviewActions.disabled(),
        messageActions: messageActions,
        mentionHighlighted: false,
        mentionTargetsCurrentUser: false,
        currentUser: _avatarHoverCardTestCurrentUser,
        currentUserMentionIdentity: _avatarHoverCardTestCurrentUser.toSummary(),
        ownerUserId: null,
        mentionMembers: const [],
      ),
    );
  }
}

class _CenteredState extends StatelessWidget {
  const _CenteredState({
    required this.icon,
    required this.title,
    required this.detail,
    this.action,
  });

  final IconData icon;
  final String title;
  final String detail;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 320),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, color: UiColors.textMuted, size: 30),
              const SizedBox(height: 12),
              Text(
                title,
                textAlign: TextAlign.center,
                style: UiTypography.title,
              ),
              const SizedBox(height: 7),
              Text(
                detail,
                textAlign: TextAlign.center,
                style: UiTypography.body.copyWith(color: UiColors.textMuted),
              ),
              if (action != null) ...[const SizedBox(height: 16), action!],
            ],
          ),
        ),
      ),
    );
  }
}

Color chatRoomUsernameColor({
  required UserSummary user,
  required CurrentUser currentUser,
  String? ownerUserId,
}) {
  if (user.id == currentUser.id) return UiColors.accent;
  return roleBadgeForegroundColorForLabel(
    room_display.roomRoleLabel(user, ownerUserId: ownerUserId),
  );
}

String _senderName(UserSummary user) {
  final roomName = user.roomDisplayName?.trim();
  if (roomName != null && roomName.isNotEmpty) return roomName;
  return user.displayName;
}
