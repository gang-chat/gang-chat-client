part of 'home_shell.dart';

class _RoomMessageHistoryPane extends StatefulWidget {
  const _RoomMessageHistoryPane({
    required this.room,
    required this.currentUser,
    required this.roomsController,
    required this.messagesController,
    required this.clipboardService,
    required this.fileDownloads,
    required this.downloadActions,
    required this.voicePlaybackActions,
    required this.imagePreviewActions,
    required this.messageActions,
    required this.onResolveRoomProfile,
    required this.onResolveRoomUserProfile,
    required this.onOpenRoom,
    required this.profileActionBuilder,
    required this.onJumpToMessage,
  });

  final RoomDetail room;
  final CurrentUser currentUser;
  final RoomsController roomsController;
  final MessagesController messagesController;
  final ClipboardService clipboardService;
  final Map<String, FileTransferState> fileDownloads;
  final ChatFileDownloadActions downloadActions;
  final ChatVoicePlaybackActions voicePlaybackActions;
  final ChatImagePreviewActions imagePreviewActions;
  final ChatMessageActions messageActions;
  final RoomProfileResolver? onResolveRoomProfile;
  final Future<UserSummary> Function(String roomId, UserSummary user)?
  onResolveRoomUserProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;
  final UserProfileActionBuilder? profileActionBuilder;
  final ValueChanged<String> onJumpToMessage;

  @override
  State<_RoomMessageHistoryPane> createState() =>
      _RoomMessageHistoryPaneState();
}

class _RoomMessageHistoryPaneState extends State<_RoomMessageHistoryPane> {
  static const _pageSize = 50;

  final TextEditingController _searchController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  Timer? _searchDebounce;
  late room_notifications.RoomNotificationDateRange _defaultDateRange;
  late room_notifications.RoomNotificationDateRange _dateRange;
  room_message_history.RoomMessageHistoryCategory _category =
      room_message_history.RoomMessageHistoryCategory.all;
  List<RoomMember> _members = const [];
  RoomMember? _selectedMember;
  List<Message> _messages = const [];
  Set<String> _selectedMessageIds = const {};
  bool _selectionMode = false;
  bool _loading = true;
  bool _loadingMore = false;
  bool _hasMore = false;
  bool _membersLoading = true;
  String? _nextBefore;
  String? _error;
  int _loadSerial = 0;

  @override
  void initState() {
    super.initState();
    _defaultDateRange = _makeDefaultDateRange();
    _dateRange = _defaultDateRange;
    _scrollController.addListener(_handleScroll);
    unawaited(_loadMembers());
    unawaited(_reload());
  }

  @override
  void dispose() {
    _searchDebounce?.cancel();
    _searchController.dispose();
    _scrollController
      ..removeListener(_handleScroll)
      ..dispose();
    super.dispose();
  }

  room_notifications.RoomNotificationDateRange _makeDefaultDateRange() {
    return room_notifications.RoomNotificationDateRange.defaultFor(
      accountCreatedAt: widget.currentUser.createdAt,
      today: DateTime.now(),
    );
  }

  Future<void> _loadMembers() async {
    try {
      final members = await widget.roomsController.loadAllRoomMembers(
        widget.room.id,
      );
      if (!mounted) return;
      setState(() {
        _members = members;
        _membersLoading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() => _membersLoading = false);
      showFloatingErrorNotice(
        context,
        userFacingErrorMessage(error, fallback: '加载房间成员失败'),
      );
    }
  }

  void _handleScroll() {
    if (!_scrollController.hasClients ||
        _scrollController.position.extentAfter > 240) {
      return;
    }
    unawaited(_loadMore());
  }

  void _handleSearchChanged(String value) {
    _searchDebounce?.cancel();
    _searchDebounce = Timer(const Duration(milliseconds: 280), () {
      if (!mounted) return;
      _clearSelection();
      unawaited(_reload());
    });
  }

  Future<void> _reload() async {
    final serial = ++_loadSerial;
    setState(() {
      _loading = true;
      _loadingMore = false;
      _error = null;
      _messages = const [];
      _hasMore = false;
      _nextBefore = null;
    });
    try {
      final page = await _requestPage();
      if (!mounted || serial != _loadSerial) return;
      setState(() {
        _messages = page.messages;
        _hasMore = page.hasMore;
        _nextBefore = page.nextBefore;
        _loading = false;
      });
    } catch (error) {
      if (!mounted || serial != _loadSerial) return;
      setState(() {
        _loading = false;
        _error = userFacingErrorMessage(error, fallback: '加载消息记录失败');
      });
    }
  }

  Future<void> _loadMore() async {
    if (_loading || _loadingMore || !_hasMore || _nextBefore == null) return;
    final serial = _loadSerial;
    setState(() => _loadingMore = true);
    try {
      final page = await _requestPage(before: _nextBefore);
      if (!mounted || serial != _loadSerial) return;
      final knownIds = _messages.map((message) => message.id).toSet();
      setState(() {
        _messages = [
          ..._messages,
          ...page.messages.where((message) => knownIds.add(message.id)),
        ];
        _hasMore = page.hasMore;
        _nextBefore = page.nextBefore;
        _loadingMore = false;
      });
    } catch (error) {
      if (!mounted || serial != _loadSerial) return;
      setState(() => _loadingMore = false);
      showFloatingErrorNotice(
        context,
        userFacingErrorMessage(error, fallback: '加载更多消息记录失败'),
      );
    }
  }

  Future<MessagePage> _requestPage({String? before}) {
    return widget.messagesController.loadMessageHistory(
      roomId: widget.room.id,
      query: _searchController.text,
      category: _category.apiValue,
      senderUserId: _selectedMember?.user.id,
      startAt: room_message_history.roomMessageHistoryDayStart(
        _dateRange.startDate,
      ),
      endAt: room_message_history.roomMessageHistoryDayEndExclusive(
        _dateRange.endDate,
      ),
      limit: _pageSize,
      before: before,
    );
  }

  Future<void> _showDateFilter() async {
    final result = await showDateRangeFilterDialog(
      context,
      initialRange: _dateRange,
      defaultRange: _defaultDateRange,
      title: '筛选消息日期',
      description: '选择包含首尾日期的消息时间区间。',
    );
    if (!mounted || result == null || result == _dateRange) return;
    setState(() => _dateRange = result);
    _clearSelection();
    await _reload();
  }

  Future<void> _showMemberFilter() async {
    if (_membersLoading) return;
    final result = await showDialog<_HistoryMemberFilterResult>(
      context: context,
      builder: (context) => _HistoryMemberFilterDialog(
        roomId: widget.room.id,
        currentUser: widget.currentUser,
        members: _members,
        selectedMember: _selectedMember,
        onResolveRoomProfile: widget.onResolveRoomProfile,
        onResolveRoomUserProfile: widget.onResolveRoomUserProfile,
        onOpenRoom: widget.onOpenRoom,
      ),
    );
    if (!mounted || result == null) return;
    if (result.member?.user.id == _selectedMember?.user.id) return;
    setState(() => _selectedMember = result.member);
    _clearSelection();
    await _reload();
  }

  void _changeCategory(
    room_message_history.RoomMessageHistoryCategory category,
  ) {
    if (_category == category) return;
    setState(() => _category = category);
    _clearSelection();
    unawaited(_reload());
  }

  void _toggleSelectionMode() {
    setState(() {
      _selectionMode = !_selectionMode;
      _selectedMessageIds = const {};
    });
  }

  void _clearSelection() {
    if (_selectedMessageIds.isEmpty) return;
    setState(() => _selectedMessageIds = const {});
  }

  void _toggleMessage(String messageId) {
    setState(() {
      final ids = {..._selectedMessageIds};
      if (!ids.add(messageId)) ids.remove(messageId);
      _selectedMessageIds = ids;
    });
  }

  void _toggleAllMessages() {
    if (_messages.isEmpty) return;
    final visibleIds = _messages.map((message) => message.id).toSet();
    setState(() {
      if (visibleIds.every(_selectedMessageIds.contains)) {
        _selectedMessageIds = {..._selectedMessageIds}..removeAll(visibleIds);
      } else {
        _selectedMessageIds = {..._selectedMessageIds, ...visibleIds};
      }
    });
  }

  List<Message> get _selectedMessages => _messages
      .where((message) => _selectedMessageIds.contains(message.id))
      .toList(growable: false);

  UserSummary get _currentUserMentionIdentity {
    final roomName = widget.room.personalProfile.displayName?.trim();
    return widget.currentUser.toSummary().copyWith(
      roomDisplayName: roomName != null && roomName.isNotEmpty
          ? roomName
          : widget.currentUser.displayName,
      roomRole: widget.room.myMembership.role,
    );
  }

  ({Widget content, bool inline}) _buildMessageContent(Message message) {
    final systemEvent = message_display.systemMessageEvent(message);
    if (systemEvent != null) {
      return (
        content: ChatSystemMessageContent(
          key: ValueKey('room-message-history-content-${message.id}'),
          message: message,
          event: systemEvent,
          currentUser: widget.currentUser,
          ownerUserId: widget.room.createdBy?.id,
          live: widget.room.live,
          enableContextMenu: false,
          showSurface: false,
          alignment: Alignment.centerLeft,
          wrapAlignment: WrapAlignment.start,
          textStyle: UiTypography.body,
          avatarSize: CompactActivityLayout.avatarSize,
          onResolveSenderProfile: widget.onResolveRoomUserProfile == null
              ? null
              : (user) =>
                    widget.onResolveRoomUserProfile!(widget.room.id, user),
          onResolveRoomProfile: widget.onResolveRoomProfile,
          onEnterProfileRoom: widget.onOpenRoom,
          profileActionBuilder: widget.profileActionBuilder,
        ),
        inline: true,
      );
    }
    if (message.isRemoved) {
      return (
        content: ChatRemovedMessageContent(
          key: ValueKey('room-message-history-content-${message.id}'),
          message: message,
          currentUser: widget.currentUser,
          ownerUserId: widget.room.createdBy?.id,
          live: widget.room.live,
          messageActions: widget.messageActions,
          enableContextMenu: false,
          showSurface: false,
          alignment: Alignment.centerLeft,
          wrapAlignment: WrapAlignment.start,
          onResolveSenderProfile: widget.onResolveRoomUserProfile == null
              ? null
              : (user) =>
                    widget.onResolveRoomUserProfile!(widget.room.id, user),
          onResolveRoomProfile: widget.onResolveRoomProfile,
          onEnterProfileRoom: widget.onOpenRoom,
          profileActionBuilder: widget.profileActionBuilder,
        ),
        inline: true,
      );
    }
    return (
      content: ChatMessageContent(
        key: ValueKey('room-message-history-content-${message.id}'),
        message: message,
        outgoing: message.sender.id == widget.currentUser.id,
        fileDownloads: widget.fileDownloads,
        downloadActions: widget.downloadActions,
        voicePlaybackActions: widget.voicePlaybackActions,
        imagePreviewActions: widget.imagePreviewActions,
        currentUser: widget.currentUser,
        currentUserMentionIdentity: _currentUserMentionIdentity,
        ownerUserId: widget.room.createdBy?.id,
        mentionMembers: _members,
        onResolveSenderProfile: widget.onResolveRoomUserProfile == null
            ? null
            : (user) => widget.onResolveRoomUserProfile!(widget.room.id, user),
        onResolveRoomProfile: widget.onResolveRoomProfile,
        onEnterProfileRoom: widget.onOpenRoom,
        profileActionBuilder: widget.profileActionBuilder,
        isUserInLive: (userId) =>
            live_display.liveParticipantByUserId(widget.room.live, userId) !=
            null,
        onOpenQuote: widget.messageActions.onOpenQuote,
        onCloneSharedPlaylist: widget.messageActions.onCloneSharedPlaylist,
      ),
      inline: false,
    );
  }

  Future<void> _copyMessage(Message message) async {
    await widget.clipboardService.writeText(
      room_message_history.roomMessageHistoryCopyText(message),
    );
    if (mounted) showFloatingSuccessNotice(context, '已复制');
  }

  Future<void> _deleteMessages(List<Message> messages) async {
    if (messages.isEmpty) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) =>
          _HistoryDeleteConfirmDialog(messageCount: messages.length),
    );
    if (confirmed != true || !mounted) return;
    try {
      await widget.messagesController.hideMessageHistory(
        roomId: widget.room.id,
        messageIds: messages.map((message) => message.id),
      );
      if (!mounted) return;
      final deletedIds = messages.map((message) => message.id).toSet();
      setState(() {
        _messages = _messages
            .where((message) => !deletedIds.contains(message.id))
            .toList(growable: false);
        _selectedMessageIds = {..._selectedMessageIds}..removeAll(deletedIds);
      });
      showFloatingSuccessNotice(
        context,
        messages.length == 1 ? '已删除消息记录' : '已删除 ${messages.length} 条消息记录',
      );
    } catch (error) {
      if (mounted) {
        showFloatingErrorNotice(
          context,
          userFacingErrorMessage(error, fallback: '删除消息记录失败'),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final allSelected =
        _messages.isNotEmpty &&
        _messages.every((message) => _selectedMessageIds.contains(message.id));
    return Padding(
      padding: const EdgeInsets.fromLTRB(22, 0, 22, 22),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: Input(
                  key: const ValueKey('room-message-history-search'),
                  controller: _searchController,
                  hintText: '搜索消息记录',
                  prefixIcon: Icons.search,
                  showClearButton: true,
                  onChanged: _handleSearchChanged,
                ),
              ),
              const SizedBox(width: 10),
              ButtonIcon(
                key: const ValueKey('room-message-history-date-filter'),
                tooltip: '筛选消息日期',
                icon: const Icon(Icons.calendar_month_outlined),
                selected: _dateRange != _defaultDateRange,
                onPressed: _showDateFilter,
                size: Input.defaultHeight,
              ),
              const SizedBox(width: 8),
              ButtonIcon(
                key: const ValueKey('room-message-history-member-filter'),
                tooltip: _selectedMember == null
                    ? '筛选成员（所有人）'
                    : '筛选成员：${member_filter.roomMemberDisplayName(_selectedMember!)}',
                icon: const Icon(Icons.person_search_outlined),
                selected: _selectedMember != null,
                loading: _membersLoading,
                onPressed: _membersLoading ? null : _showMemberFilter,
                size: Input.defaultHeight,
              ),
              const SizedBox(width: 8),
              ButtonIcon(
                key: const ValueKey('room-message-history-batch-manage'),
                tooltip: _selectionMode ? '退出批量管理' : '批量管理',
                icon: const Icon(Icons.checklist_outlined),
                selected: _selectionMode,
                onPressed: _toggleSelectionMode,
                size: Input.defaultHeight,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              if (_selectionMode) ...[
                UiCheckbox(
                  key: const ValueKey('room-message-history-select-all'),
                  value: allSelected,
                  onChanged: _messages.isEmpty
                      ? null
                      : (_) => _toggleAllMessages(),
                  tooltip: allSelected ? '取消全选当前消息记录' : '全选当前消息记录',
                  semanticLabel: '全选当前消息记录',
                ),
                const SizedBox(width: 8),
              ],
              Expanded(
                child:
                    SegmentedControl<
                      room_message_history.RoomMessageHistoryCategory
                    >(
                      expanded: true,
                      value: _category,
                      onChanged: _changeCategory,
                      segments: const [
                        Segment(
                          value: room_message_history
                              .RoomMessageHistoryCategory
                              .all,
                          label: '全部',
                          icon: Icons.inbox_outlined,
                        ),
                        Segment(
                          value: room_message_history
                              .RoomMessageHistoryCategory
                              .links,
                          label: '链接',
                          icon: Icons.link,
                        ),
                        Segment(
                          value: room_message_history
                              .RoomMessageHistoryCategory
                              .voice,
                          label: '语音',
                          icon: Icons.mic_none_outlined,
                        ),
                        Segment(
                          value: room_message_history
                              .RoomMessageHistoryCategory
                              .stickers,
                          label: '表情',
                          icon: Icons.emoji_emotions_outlined,
                        ),
                        Segment(
                          value: room_message_history
                              .RoomMessageHistoryCategory
                              .images,
                          label: '图片',
                          icon: Icons.image_outlined,
                        ),
                        Segment(
                          value: room_message_history
                              .RoomMessageHistoryCategory
                              .files,
                          label: '文件',
                          icon: Icons.attach_file,
                        ),
                        Segment(
                          value: room_message_history
                              .RoomMessageHistoryCategory
                              .system,
                          label: '系统',
                          icon: Icons.info_outline,
                        ),
                      ],
                    ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Expanded(child: _buildBody()),
        ],
      ),
    );
  }

  Widget _buildBody() {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              _error!,
              textAlign: TextAlign.center,
              style: UiTypography.body.copyWith(color: UiColors.danger),
            ),
            const SizedBox(height: 12),
            Button(
              onPressed: _reload,
              icon: const Icon(Icons.refresh),
              child: const Text('重试'),
            ),
          ],
        ),
      );
    }
    if (_messages.isEmpty) {
      return Center(
        child: Text(
          '没有符合条件的消息记录',
          style: UiTypography.body.copyWith(color: UiColors.textMuted),
        ),
      );
    }
    return ListView.separated(
      controller: _scrollController,
      itemCount: _messages.length + (_loadingMore ? 1 : 0),
      separatorBuilder: (_, _) => const SizedBox(height: 8),
      itemBuilder: (context, index) {
        if (index == _messages.length) {
          return const Padding(
            padding: EdgeInsets.all(12),
            child: Center(child: CircularProgressIndicator()),
          );
        }
        final message = _messages[index];
        final renderedContent = _buildMessageContent(message);
        return _HistoryMessageRow(
          message: message,
          content: renderedContent.content,
          inlineContent: renderedContent.inline,
          senderAvatar: renderedContent.inline
              ? null
              : _buildSenderAvatar(message),
          senderColor: chatRoomUsernameColor(
            user: message.sender,
            currentUser: widget.currentUser,
            ownerUserId: widget.room.createdBy?.id,
          ),
          selectionMode: _selectionMode,
          selected: _selectedMessageIds.contains(message.id),
          selectedMessages: _selectedMessages,
          onToggleSelection: () => _toggleMessage(message.id),
          onCopy: _copyMessage,
          onDelete: _deleteMessages,
          onJump: () => widget.onJumpToMessage(message.id),
        );
      },
    );
  }

  Widget _buildSenderAvatar(Message message) {
    return UserHoverCard(
      user: message.sender,
      currentUser: widget.currentUser,
      onResolveProfile: widget.onResolveRoomUserProfile == null
          ? null
          : (user) => widget.onResolveRoomUserProfile!(widget.room.id, user),
      onResolveRoomProfile: widget.onResolveRoomProfile,
      onEnterCommonRoom: widget.onOpenRoom,
      profileActionBuilder: widget.profileActionBuilder,
      inLive:
          live_display.liveParticipantByUserId(
            widget.room.live,
            message.sender.id,
          ) !=
          null,
      showRoomRole: true,
      child: Avatar(
        key: ValueKey('room-message-history-avatar-${message.id}'),
        label: room_display.userAvatarLabel(message.sender),
        imageUrl: AppConfigScope.of(
          context,
        ).resolveAssetUrl(message.sender.avatarUrl),
        defaultAvatarKey: message.sender.defaultAvatarKey,
        size: 38,
      ),
    );
  }
}

class _HistoryMessageRow extends StatefulWidget {
  const _HistoryMessageRow({
    required this.message,
    required this.content,
    required this.inlineContent,
    required this.senderAvatar,
    required this.senderColor,
    required this.selectionMode,
    required this.selected,
    required this.selectedMessages,
    required this.onToggleSelection,
    required this.onCopy,
    required this.onDelete,
    required this.onJump,
  });

  final Message message;
  final Widget content;
  final bool inlineContent;
  final Widget? senderAvatar;
  final Color senderColor;
  final bool selectionMode;
  final bool selected;
  final List<Message> selectedMessages;
  final VoidCallback onToggleSelection;
  final Future<void> Function(Message message) onCopy;
  final Future<void> Function(List<Message> messages) onDelete;
  final VoidCallback onJump;

  @override
  State<_HistoryMessageRow> createState() => _HistoryMessageRowState();
}

class _HistoryMessageRowState extends State<_HistoryMessageRow> {
  bool _contextMenuActive = false;

  @override
  Widget build(BuildContext context) {
    final message = widget.message;
    final senderName = _historySenderName(message.sender);
    final highlighted = widget.selected || _contextMenuActive;
    final useCompactLayout = HomeAdaptiveLayout.usesCompactLayout(context);
    final timestampColumnWidth = useCompactLayout
        ? CompactActivityLayout.compactTimestampColumnWidth
        : CompactActivityLayout.timestampColumnWidth;
    final timestampContentGap = useCompactLayout
        ? CompactActivityLayout.compactTimestampContentGap
        : 8.0;
    final surface = AnimatedContainer(
      key: ValueKey('room-message-history-row-${message.id}'),
      duration: const Duration(milliseconds: 90),
      curve: Curves.easeOutCubic,
      constraints: const BoxConstraints(minHeight: 64),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: highlighted ? UiColors.selected : UiColors.surface,
        borderRadius: BorderRadius.circular(UiRadii.lg),
        border: Border.all(
          color: highlighted ? UiColors.selectedBorder : UiColors.border,
        ),
      ),
      child: Stack(
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(width: timestampColumnWidth),
              SizedBox(width: timestampContentGap),
              if (!widget.inlineContent) ...[
                widget.senderAvatar!,
                const SizedBox(width: 10),
              ],
              Expanded(
                child: widget.inlineContent
                    ? ConstrainedBox(
                        constraints: const BoxConstraints(minHeight: 44),
                        child: Align(
                          alignment: Alignment.centerLeft,
                          child: IgnorePointer(
                            key: ValueKey(
                              'room-message-history-content-interactions-${message.id}',
                            ),
                            ignoring: widget.selectionMode,
                            child: widget.content,
                          ),
                        ),
                      )
                    : Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            senderName,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: UiTypography.label.copyWith(
                              color: widget.senderColor,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(height: 4),
                          IgnorePointer(
                            key: ValueKey(
                              'room-message-history-content-interactions-${message.id}',
                            ),
                            ignoring: widget.selectionMode,
                            child: widget.content,
                          ),
                        ],
                      ),
              ),
              const SizedBox(width: 10),
              const SizedBox.square(dimension: 34),
            ],
          ),
          Positioned(
            left: 0,
            top: 0,
            bottom: 0,
            width: timestampColumnWidth,
            child: Center(
              child: useCompactLayout
                  ? CompactActivityTimestamp(
                      key: ValueKey('room-message-history-time-${message.id}'),
                      timestamp: room_notifications.roomInviteTimestampLabel(
                        message.createdAt,
                      ),
                      style: UiTypography.label.copyWith(
                        color: UiColors.textMuted,
                      ),
                    )
                  : Text(
                      key: ValueKey('room-message-history-time-${message.id}'),
                      room_notifications.roomInviteTimestampLabel(
                        message.createdAt,
                      ),
                      textAlign: TextAlign.center,
                      style: UiTypography.label.copyWith(
                        color: UiColors.textMuted,
                      ),
                    ),
            ),
          ),
          Positioned(
            right: 0,
            top: 0,
            bottom: 0,
            width: 34,
            child: Center(
              child: _HistoryInteractiveTarget(
                child: ButtonIcon(
                  key: ValueKey('room-message-history-jump-${message.id}'),
                  tooltip: '跳转到消息',
                  icon: const Icon(Icons.location_on_rounded),
                  tone: ButtonTone.primary,
                  onPressed: widget.onJump,
                  size: 34,
                ),
              ),
            ),
          ),
        ],
      ),
    );

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        if (widget.selectionMode) ...[
          UiCheckbox(
            key: ValueKey('room-message-history-select-${message.id}'),
            value: widget.selected,
            onChanged: (_) => widget.onToggleSelection(),
            semanticLabel: '选择这条消息记录',
          ),
          const SizedBox(width: 8),
        ],
        Expanded(
          child: UiContextMenuTriggerRegion(
            onTap: widget.selectionMode ? widget.onToggleSelection : null,
            onTriggered: _showContextMenu,
            child: surface,
          ),
        ),
      ],
    );
  }

  void _showContextMenu(Offset position) {
    if (widget.selectionMode && !widget.selected) return;
    final items = widget.selectionMode
        ? widget.selectedMessages
        : <Message>[widget.message];
    if (items.isEmpty) return;
    setState(() => _contextMenuActive = true);
    unawaited(
      showUiContextMenu(
        context,
        position: position,
        sections: [
          if (items.length == 1)
            UiContextMenuSection([
              UiContextMenuItem(
                label: '复制',
                shortcut: 'Ctrl+C',
                onPressed: () => unawaited(widget.onCopy(items.single)),
              ),
            ]),
          UiContextMenuSection([
            UiContextMenuItem(
              label: '删除',
              onPressed: () => unawaited(widget.onDelete(items)),
            ),
          ]),
        ],
      ).whenComplete(() {
        if (mounted) setState(() => _contextMenuActive = false);
      }),
    );
  }
}

class _HistoryInteractiveTarget extends StatelessWidget {
  const _HistoryInteractiveTarget({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      excludeFromSemantics: true,
      onTap: () {},
      child: child,
    );
  }
}

String _historySenderName(UserSummary user) {
  final roomName = user.roomDisplayName?.trim();
  if (roomName != null && roomName.isNotEmpty) return roomName;
  final displayName = user.displayName.trim();
  if (displayName.isNotEmpty) return displayName;
  return user.username;
}

class _HistoryMemberFilterResult {
  const _HistoryMemberFilterResult(this.member);

  final RoomMember? member;
}

class _HistoryMemberFilterDialog extends StatefulWidget {
  const _HistoryMemberFilterDialog({
    required this.roomId,
    required this.currentUser,
    required this.members,
    required this.selectedMember,
    required this.onResolveRoomProfile,
    required this.onResolveRoomUserProfile,
    required this.onOpenRoom,
  });

  final String roomId;
  final CurrentUser currentUser;
  final List<RoomMember> members;
  final RoomMember? selectedMember;
  final RoomProfileResolver? onResolveRoomProfile;
  final Future<UserSummary> Function(String roomId, UserSummary user)?
  onResolveRoomUserProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;

  @override
  State<_HistoryMemberFilterDialog> createState() =>
      _HistoryMemberFilterDialogState();
}

class _HistoryMemberFilterDialogState
    extends State<_HistoryMemberFilterDialog> {
  final TextEditingController _controller = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  RoomMember? _selectedMember;

  @override
  void initState() {
    super.initState();
    _selectedMember = widget.selectedMember;
    _controller.addListener(_handleSearchChanged);
  }

  @override
  void dispose() {
    _controller
      ..removeListener(_handleSearchChanged)
      ..dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _handleSearchChanged() => setState(() {});

  List<RoomMember> get _visibleMembers {
    final query = _controller.text.trim();
    final members = widget.members
        .where(
          (member) => member_filter.roomMemberSearchRank(member, query) < 99,
        )
        .toList(growable: false);
    members.sort((a, b) {
      final rank = member_filter
          .roomMemberSearchRank(a, query)
          .compareTo(member_filter.roomMemberSearchRank(b, query));
      if (rank != 0) return rank;
      return member_filter
          .roomMemberDisplayName(a)
          .toLowerCase()
          .compareTo(member_filter.roomMemberDisplayName(b).toLowerCase());
    });
    return members;
  }

  @override
  Widget build(BuildContext context) {
    final visibleMembers = _visibleMembers;
    return DialogFrame(
      title: '筛选消息成员',
      icon: Icons.person_search_outlined,
      maxWidth: 440,
      actionBar: ResponsiveDialogActionBar(
        leadingActionCount: 1,
        actions: [
          ResponsiveDialogAction(
            buttonKey: const ValueKey('message-history-member-reset'),
            label: '重置',
            icon: Icons.restart_alt,
            onPressed: () => Navigator.of(
              context,
            ).pop(const _HistoryMemberFilterResult(null)),
          ),
          ResponsiveDialogAction(
            label: '取消',
            onPressed: () => Navigator.of(context).pop(),
          ),
          ResponsiveDialogAction(
            buttonKey: const ValueKey('message-history-member-apply'),
            label: '应用',
            icon: Icons.check,
            tone: ButtonTone.primary,
            onPressed: () => Navigator.of(
              context,
            ).pop(_HistoryMemberFilterResult(_selectedMember)),
          ),
        ],
      ),
      child: SizedBox(
        height: 360,
        child: Column(
          children: [
            Input(
              key: const ValueKey('message-history-member-search'),
              controller: _controller,
              hintText: '搜索成员',
              prefixIcon: Icons.search,
              showClearButton: true,
            ),
            const SizedBox(height: 12),
            Expanded(
              child: RawScrollbar(
                key: const ValueKey('message-history-member-scrollbar'),
                controller: _scrollController,
                interactive: true,
                radius: const Radius.circular(999),
                thickness: 7,
                thumbColor: UiColors.textMuted.withValues(alpha: 0.82),
                child: ScrollConfiguration(
                  behavior: ScrollConfiguration.of(
                    context,
                  ).copyWith(scrollbars: false),
                  child: ListView(
                    controller: _scrollController,
                    primary: false,
                    padding: const EdgeInsets.only(right: 10),
                    children: [
                      _HistoryMemberOption(
                        label: '所有人',
                        selected: _selectedMember == null,
                        icon: Icons.groups_outlined,
                        onPressed: () => setState(() => _selectedMember = null),
                      ),
                      for (final member in visibleMembers)
                        _HistoryMemberOption(
                          label: member_filter.roomMemberDisplayName(member),
                          meta: '@${member.user.username}',
                          roleLabel: room_display.roomRoleLabel(member.user),
                          selected: _selectedMember?.user.id == member.user.id,
                          user: member.user,
                          currentUser: widget.currentUser,
                          onResolveProfile:
                              widget.onResolveRoomUserProfile == null
                              ? null
                              : (user) => widget.onResolveRoomUserProfile!(
                                  widget.roomId,
                                  user,
                                ),
                          onResolveRoomProfile: widget.onResolveRoomProfile,
                          onOpenRoom: widget.onOpenRoom,
                          onPressed: () =>
                              setState(() => _selectedMember = member),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _HistoryMemberOption extends StatelessWidget {
  const _HistoryMemberOption({
    required this.label,
    required this.selected,
    required this.onPressed,
    this.meta,
    this.roleLabel,
    this.user,
    this.icon,
    this.currentUser,
    this.onResolveProfile,
    this.onResolveRoomProfile,
    this.onOpenRoom,
  });

  final String label;
  final String? meta;
  final String? roleLabel;
  final UserSummary? user;
  final IconData? icon;
  final CurrentUser? currentUser;
  final UserProfileResolver? onResolveProfile;
  final RoomProfileResolver? onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;
  final bool selected;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 7),
      child: AnimatedContainer(
        key: user == null
            ? const ValueKey('message-history-member-all')
            : ValueKey('message-history-member-${user!.id}'),
        duration: const Duration(milliseconds: 90),
        curve: Curves.easeOutCubic,
        height: meta == null ? 46 : 54,
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
        decoration: BoxDecoration(
          color: selected ? UiColors.selected : UiColors.surfaceLow,
          borderRadius: BorderRadius.circular(UiRadii.sm),
          border: Border.all(
            color: selected ? UiColors.selectedBorder : UiColors.border,
          ),
        ),
        child: GestureDetector(
          behavior: HitTestBehavior.translucent,
          onTap: user == null ? onPressed : null,
          child: Row(
            children: [
              if (user != null)
                UserHoverCard(
                  user: user!,
                  currentUser: currentUser,
                  onResolveProfile: onResolveProfile,
                  onResolveRoomProfile: onResolveRoomProfile,
                  onEnterCommonRoom: onOpenRoom,
                  showRoomRole: true,
                  child: Avatar(
                    key: ValueKey('message-history-member-avatar-${user!.id}'),
                    label: room_display.userAvatarLabel(user!),
                    imageUrl: AppConfigScope.of(
                      context,
                    ).resolveAssetUrl(user!.avatarUrl),
                    defaultAvatarKey: user!.defaultAvatarKey,
                    size: 30,
                  ),
                )
              else
                SizedBox.square(
                  dimension: 30,
                  child: Icon(icon, color: UiColors.textSecondary, size: 19),
                ),
              const SizedBox(width: 10),
              Expanded(
                child: GestureDetector(
                  behavior: HitTestBehavior.translucent,
                  onTap: onPressed,
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              label,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: UiTypography.body.copyWith(
                                color: UiColors.text,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            if (meta != null) ...[
                              const SizedBox(height: 2),
                              Text(
                                meta!,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: UiTypography.label.copyWith(
                                  color: UiColors.textMuted,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                      if (roleLabel != null) ...[
                        const SizedBox(width: 8),
                        RoleBadge(
                          key: ValueKey(
                            'message-history-member-role-${user?.id}',
                          ),
                          label: roleLabel!,
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 5,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _HistoryDeleteConfirmDialog extends StatelessWidget {
  const _HistoryDeleteConfirmDialog({required this.messageCount});

  final int messageCount;

  @override
  Widget build(BuildContext context) {
    return DialogFrame(
      title: messageCount == 1 ? '删除消息记录' : '删除选中的消息记录',
      icon: Icons.warning_amber_outlined,
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
      child: Text(
        messageCount == 1
            ? '删除后仅会从当前账号看到的消息记录中移除，不会撤回或删除房间消息'
            : '确定删除所有选中的 $messageCount 条消息记录吗？删除只对当前账号生效',
        style: UiTypography.body,
      ),
    );
  }
}
