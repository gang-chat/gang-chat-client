part of 'room_management.dart';

enum _RoomMembersSection { roomMembers, newMembers, blacklist }

class RoomMembersDialog extends StatefulWidget {
  const RoomMembersDialog({
    super.key,
    required this.controller,
    required this.room,
    required this.currentUser,
    required this.initialLive,
    this.initialSearchQuery = '',
    this.hasPendingJoinRequests = false,
    this.reloadToken = 0,
    this.embedded = false,
    this.onClose,
    this.onChanged,
    this.onPendingJoinRequestsChanged,
    this.onOpenRoom,
  });

  final RoomsController controller;
  final RoomDetail room;
  final CurrentUser currentUser;
  final LiveState initialLive;
  final String initialSearchQuery;
  final bool hasPendingJoinRequests;

  /// Incremented by the host when a realtime event (join requests updated, role
  /// changed) means this panel's data is stale. A change triggers a reload.
  final int reloadToken;

  final bool embedded;
  final VoidCallback? onClose;
  final VoidCallback? onChanged;
  final ValueChanged<bool>? onPendingJoinRequestsChanged;
  final ValueChanged<PublicRoom>? onOpenRoom;

  @override
  State<RoomMembersDialog> createState() => _RoomMembersDialogState();
}

class _RoomMembersDialogState extends State<RoomMembersDialog> {
  final _memberSearchController = TextEditingController();
  final _inviteSearchController = TextEditingController();
  final _blockSearchController = TextEditingController();
  final _busyRequestIds = <String>{};
  final _busyMemberIds = <String>{};
  final _busyInviteUserIds = <String>{};
  final _busyBlockUserIds = <String>{};
  final _pendingInviteUserIds = <String>{};

  Timer? _inviteSearchDebounce;
  Timer? _blockSearchDebounce;
  int _inviteSearchSeq = 0;
  int _blockSearchSeq = 0;

  late RoomDetail _room;
  late LiveState _live;
  List<RoomMember> _members = const [];
  List<String> _memberDisplayOrder = const [];
  List<JoinRequest> _requests = const [];
  List<UserSummary> _inviteResults = const [];
  List<UserSummary> _blockResults = const [];
  List<RoomBlacklistEntry> _blacklist = const [];

  bool _loading = true;
  bool _searchingInvites = false;
  bool _searchingBlocks = false;
  bool _loadingBlacklist = false;
  bool _blacklistLoaded = false;
  bool _changed = false;
  String _memberQuery = '';
  String _inviteQuery = '';
  String _blockQuery = '';
  String _lastMemberSearchText = '';
  String _lastInviteSearchText = '';
  String _lastBlockSearchText = '';
  String? _error;
  String? _requestError;
  String? _inviteError;
  String? _blockError;
  String? _blacklistError;
  String? _notice;
  int _floatingNoticeSerial = 0;
  final Map<String, int> _floatingNoticeEventKeys = {};
  String? _activeJoinRequestDetailId;
  member_filter.RoomMemberPresenceFilter _presenceFilter =
      member_filter.RoomMemberPresenceFilter.all;
  member_filter.RoomMemberRoleFilter _roleFilter =
      member_filter.RoomMemberRoleFilter.all;
  _RoomMembersSection _section = _RoomMembersSection.roomMembers;
  bool _syncingMemberSearch = false;

  bool get _canReviewRequests => room_display
      .roomAccessState(room: _room, currentUser: widget.currentUser)
      .canReviewJoinRequests;

  bool get _canEditCreatorOnly => room_display
      .roomManagementPermissionState(
        room: _room,
        currentUser: widget.currentUser,
      )
      .canEditCreatorOnly;

  bool get _canInviteMembers => _canViewNewMembers;

  bool get _canViewNewMembers =>
      room_invites.roomInvitesEnabled(_room.joinPolicy);

  bool get _canViewJoinRequests => _canViewNewMembers && _canReviewRequests;

  bool get _canManageMembers => room_display
      .roomAccessState(room: _room, currentUser: widget.currentUser)
      .canManageRoom;

  bool get _canViewBlacklist => _canManageMembers;

  @override
  void initState() {
    super.initState();
    _room = widget.room;
    _live = widget.initialLive;
    _applyMemberSearchQuery(widget.initialSearchQuery);
    _memberSearchController.addListener(_onMemberSearchChanged);
    _inviteSearchController.addListener(_onInviteSearchChanged);
    _blockSearchController.addListener(_onBlockSearchChanged);
    unawaited(_load());
  }

  @override
  void dispose() {
    _inviteSearchDebounce?.cancel();
    _blockSearchDebounce?.cancel();
    _memberSearchController.dispose();
    _inviteSearchController.dispose();
    _blockSearchController.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(RoomMembersDialog oldWidget) {
    super.didUpdateWidget(oldWidget);
    final liveChanged = !identical(widget.initialLive, oldWidget.initialLive);
    final liveBelongsToRoom = widget.initialLive.roomId == widget.room.id;
    if (widget.initialSearchQuery != oldWidget.initialSearchQuery) {
      setState(() {
        _applyMemberSearchQuery(widget.initialSearchQuery);
        _refreshMemberDisplayOrder();
        _section = _RoomMembersSection.roomMembers;
      });
    }
    // The host bumps reloadToken when a realtime event invalidates this panel
    // (join requests changed, or the current user's role changed). Re-pull the
    // member/request lists, and adopt the freshest room so permission checks
    // (canReviewRequests, canManageMembers) reflect the new role.
    if (widget.reloadToken != oldWidget.reloadToken ||
        !identical(widget.room, oldWidget.room)) {
      final roomChanged = widget.room.id != oldWidget.room.id;
      _room = widget.room;
      if (roomChanged) {
        _resetBlacklistState();
        _memberDisplayOrder = const [];
      }
      if (liveChanged && liveBelongsToRoom) {
        _live = widget.initialLive;
      }
      unawaited(_load(refreshDisplayOrder: roomChanged));
      if (_section == _RoomMembersSection.blacklist) {
        unawaited(_loadBlacklist());
      }
    } else if (liveChanged && liveBelongsToRoom) {
      setState(() => _live = widget.initialLive);
    }
  }

  void _close() {
    if (widget.embedded) {
      if (_changed) widget.onChanged?.call();
      widget.onClose?.call();
      return;
    }
    Navigator.of(context).pop(_changed);
  }

  void _selectSection(_RoomMembersSection section) {
    if (section == _RoomMembersSection.newMembers && !_canViewNewMembers) {
      return;
    }
    if (section == _RoomMembersSection.blacklist && !_canViewBlacklist) {
      return;
    }
    setState(() => _section = section);
    if (section == _RoomMembersSection.blacklist && !_blacklistLoaded) {
      unawaited(_loadBlacklist());
    }
  }

  void _resetBlacklistState() {
    _blockSearchDebounce?.cancel();
    _blockSearchSeq += 1;
    _blockResults = const [];
    _blacklist = const [];
    _searchingBlocks = false;
    _loadingBlacklist = false;
    _blacklistLoaded = false;
    _blockError = null;
    _blacklistError = null;
  }

  void _onMemberSearchChanged() {
    if (_syncingMemberSearch) return;
    final text = _memberSearchController.text;
    if (text == _lastMemberSearchText) return;
    _lastMemberSearchText = text;
    setState(() {
      _memberQuery = text;
      _refreshMemberDisplayOrder();
    });
  }

  void _applyMemberSearchQuery(String query) {
    final value = query.trim();
    _memberQuery = value;
    _lastMemberSearchText = value;
    if (_memberSearchController.text == value) return;
    _syncingMemberSearch = true;
    _memberSearchController.value = TextEditingValue(
      text: value,
      selection: TextSelection.collapsed(offset: value.length),
    );
    _syncingMemberSearch = false;
  }

  void _onInviteSearchChanged() {
    final text = _inviteSearchController.text;
    if (text == _lastInviteSearchText) return;
    _lastInviteSearchText = text;
    if (!_canInviteMembers) return;
    final query = text.trim();
    setState(() {
      _inviteQuery = query;
      _inviteError = null;
      if (query.isEmpty) {
        _inviteResults = const [];
        _searchingInvites = false;
      }
    });
    _inviteSearchDebounce?.cancel();
    if (query.length < 2) {
      _inviteSearchSeq += 1;
      return;
    }
    final seq = ++_inviteSearchSeq;
    _inviteSearchDebounce = Timer(const Duration(milliseconds: 260), () {
      unawaited(_searchInviteCandidates(query, seq));
    });
  }

  void _onBlockSearchChanged() {
    final text = _blockSearchController.text;
    if (text == _lastBlockSearchText) return;
    _lastBlockSearchText = text;
    if (!_canManageMembers) return;
    final query = text.trim();
    setState(() {
      _blockQuery = query;
      _blockError = null;
      if (query.isEmpty) {
        _blockResults = const [];
        _searchingBlocks = false;
      }
    });
    _blockSearchDebounce?.cancel();
    if (query.length < 2) {
      _blockSearchSeq += 1;
      return;
    }
    final seq = ++_blockSearchSeq;
    _blockSearchDebounce = Timer(const Duration(milliseconds: 260), () {
      unawaited(_searchBlockCandidates(query, seq));
    });
  }

  Future<void> _load({bool refreshDisplayOrder = true}) async {
    setState(() {
      _loading = true;
      _error = null;
      _requestError = null;
    });
    try {
      final snapshot = await widget.controller.loadRoomMembersSnapshot(
        roomId: _room.id,
        fallbackLive: _live,
        includeJoinRequests: _canViewJoinRequests,
      );
      if (!mounted) return;
      setState(() {
        _members = snapshot.members;
        _live = snapshot.live;
        if (refreshDisplayOrder || _memberDisplayOrder.isEmpty) {
          _refreshMemberDisplayOrder();
        }
        _requests = snapshot.joinRequests;
        _requestError = snapshot.joinRequestsError;
        _loading = false;
      });
      _notifyPendingJoinRequestsChanged(
        snapshot.joinRequests,
        snapshot.joinRequestsError,
      );
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = userFacingErrorMessage(error);
        _markFloatingNoticeEvent('error', _error);
      });
    }
  }

  Future<void> _loadBlacklist() async {
    if (!_canManageMembers) return;
    if (_loadingBlacklist) return;
    setState(() {
      _loadingBlacklist = true;
      _blacklistError = null;
    });
    try {
      final entries = await widget.controller.listRoomBlacklist(_room.id);
      if (!mounted) return;
      setState(() {
        _blacklist = entries;
        _blacklistLoaded = true;
        _loadingBlacklist = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loadingBlacklist = false;
        _blacklistError = userFacingErrorMessage(error);
      });
    }
  }

  Future<void> _searchInviteCandidates(String query, int seq) async {
    if (!_canInviteMembers) return;
    setState(() {
      _searchingInvites = true;
      _inviteError = null;
    });
    try {
      final results = await widget.controller.searchUsers(
        query: query,
        limit: 20,
      );
      if (!mounted || seq != _inviteSearchSeq) return;
      setState(() {
        _inviteResults = results;
        _searchingInvites = false;
      });
    } catch (error) {
      if (!mounted || seq != _inviteSearchSeq) return;
      setState(() {
        _searchingInvites = false;
        _inviteError = userFacingErrorMessage(error);
      });
    }
  }

  Future<void> _searchBlockCandidates(String query, int seq) async {
    if (!_canManageMembers) return;
    setState(() {
      _searchingBlocks = true;
      _blockError = null;
    });
    try {
      final results = await widget.controller.searchUsers(
        query: query,
        limit: 20,
      );
      if (!mounted || seq != _blockSearchSeq) return;
      setState(() {
        _blockResults = results;
        _searchingBlocks = false;
      });
    } catch (error) {
      if (!mounted || seq != _blockSearchSeq) return;
      setState(() {
        _searchingBlocks = false;
        _blockError = userFacingErrorMessage(error);
      });
    }
  }

  Future<void> _invite(UserSummary user) async {
    if (!_canInviteMembers) return;
    if (_busyInviteUserIds.contains(user.id)) return;
    if (_pendingInviteUserIds.contains(user.id)) return;
    if (_members.any((member) => member.user.id == user.id)) return;
    setState(() {
      _busyInviteUserIds.add(user.id);
      _inviteError = null;
      _notice = null;
    });
    try {
      await widget.controller.inviteMember(roomId: _room.id, userId: user.id);
      if (!mounted) return;
      setState(() {
        _busyInviteUserIds.remove(user.id);
        _pendingInviteUserIds.add(user.id);
        _changed = true;
        _notice = '邀请已发送';
        _markFloatingNoticeEvent('notice', _notice);
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _busyInviteUserIds.remove(user.id);
        _inviteError = userFacingErrorMessage(error);
      });
    }
  }

  Future<void> _blockUser(UserSummary user) async {
    if (!_canManageMembers) return;
    if (_busyBlockUserIds.contains(user.id)) return;
    if (_blacklist.any((entry) => entry.user.id == user.id)) return;
    if (_members.any((member) => member.user.id == user.id)) return;
    if (user.isSuperuser) return;
    setState(() {
      _busyBlockUserIds.add(user.id);
      _blockError = null;
      _blacklistError = null;
      _notice = null;
    });
    try {
      final entry = await widget.controller.blockRoomUser(
        roomId: _room.id,
        userId: user.id,
      );
      if (!mounted) return;
      setState(() {
        _busyBlockUserIds.remove(user.id);
        _blacklist = room_blacklist.upsertRoomBlacklistEntry(_blacklist, entry);
        _blacklistLoaded = true;
        _changed = true;
        _notice = '已加入黑名单';
        _markFloatingNoticeEvent('notice', _notice);
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _busyBlockUserIds.remove(user.id);
        _blockError = userFacingErrorMessage(error);
      });
    }
  }

  Future<void> _unblockUser(UserSummary user) async {
    if (!_canManageMembers) return;
    if (_busyBlockUserIds.contains(user.id)) return;
    setState(() {
      _busyBlockUserIds.add(user.id);
      _blockError = null;
      _blacklistError = null;
      _notice = null;
    });
    try {
      await widget.controller.unblockRoomUser(
        roomId: _room.id,
        userId: user.id,
      );
      if (!mounted) return;
      setState(() {
        _busyBlockUserIds.remove(user.id);
        _blacklist = room_blacklist.removeRoomBlacklistEntry(
          _blacklist,
          user.id,
        );
        _blacklistLoaded = true;
        _changed = true;
        _notice = '已取消拉黑';
        _markFloatingNoticeEvent('notice', _notice);
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _busyBlockUserIds.remove(user.id);
        _blacklistError = userFacingErrorMessage(error);
      });
    }
  }

  Future<void> _reviewRequest(JoinRequest request, bool approve) async {
    if (_busyRequestIds.contains(request.id)) return;
    setState(() {
      _busyRequestIds.add(request.id);
      _requestError = null;
      _notice = null;
    });
    try {
      await widget.controller.reviewJoinRequest(
        roomId: _room.id,
        requestId: request.id,
        approve: approve,
      );
      if (!mounted) return;
      setState(() {
        _busyRequestIds.remove(request.id);
        _requests = [
          for (final item in _requests)
            if (item.id != request.id) item,
        ];
        _changed = true;
        _notice = approve ? '申请已通过' : '申请已拒绝';
        _markFloatingNoticeEvent('notice', _notice);
      });
      _notifyPendingJoinRequestsChanged(_requests, _requestError);
      if (approve) unawaited(_load(refreshDisplayOrder: false));
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _busyRequestIds.remove(request.id);
        _requestError = userFacingErrorMessage(error);
      });
    }
  }

  void _notifyPendingJoinRequestsChanged(
    List<JoinRequest> requests,
    String? requestError,
  ) {
    if (!_canViewJoinRequests || requestError != null) return;
    widget.onPendingJoinRequestsChanged?.call(requests.isNotEmpty);
  }

  void _markFloatingNoticeEvent(String channel, String? message) {
    if (message == null || message.trim().isEmpty) return;
    _floatingNoticeEventKeys[channel] = ++_floatingNoticeSerial;
  }

  Object? _floatingNoticeEventKey(String channel) {
    return _floatingNoticeEventKeys[channel];
  }

  List<FloatingNotice> _floatingNotices() {
    return [
      if (_notice != null)
        FloatingNotice(
          message: _notice!,
          tone: FloatingNoticeTone.success,
          eventKey: _floatingNoticeEventKey('notice'),
        ),
      if (_error != null)
        FloatingNotice(
          message: _error!,
          tone: FloatingNoticeTone.error,
          duration: null,
          eventKey: _floatingNoticeEventKey('error'),
        ),
    ];
  }

  Future<void> _showJoinRequestDetails(JoinRequest request) async {
    setState(() => _activeJoinRequestDetailId = request.id);
    try {
      await showDialog<void>(
        context: context,
        builder: (context) => _JoinRequestDetailsDialog(
          request: request,
          currentUser: widget.currentUser,
          onResolveProfile: _resolveMemberProfile,
          onResolveRoomProfile: _resolveRoomProfile,
          onOpenRoom: widget.onOpenRoom,
        ),
      );
    } finally {
      if (mounted) setState(() => _activeJoinRequestDetailId = null);
    }
  }

  Future<void> _setMemberRole(RoomMember member, String role) async {
    if (_busyMemberIds.contains(member.user.id)) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => _ConfirmDialog(
        title: member_filter.roomMemberRoleUpdateConfirmTitle(role),
        message: member_filter.roomMemberRoleUpdateConfirmBody(member, role),
        confirmLabel: member_filter.roomMemberRoleUpdateConfirmLabel(role),
        danger: role != 'admin',
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _busyMemberIds.add(member.user.id);
      _error = null;
      _notice = null;
    });
    try {
      final updated = await widget.controller.updateRoomMemberRole(
        roomId: _room.id,
        userId: member.user.id,
        role: role,
      );
      if (!mounted) return;
      setState(() {
        _busyMemberIds.remove(member.user.id);
        _members = member_filter.replaceRoomMember(_members, updated);
        _changed = true;
        _notice = member_filter.roomMemberRoleUpdateNotice(role);
        _markFloatingNoticeEvent('notice', _notice);
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _busyMemberIds.remove(member.user.id);
        _error = userFacingErrorMessage(error);
        _markFloatingNoticeEvent('error', _error);
      });
    }
  }

  Future<void> _editRoomDisplayName(RoomMember member) async {
    if (_busyMemberIds.contains(member.user.id)) return;
    final roomDisplayName = await showDialog<String>(
      context: context,
      builder: (context) => _EditRoomDisplayNameDialog(member: member),
    );
    if (roomDisplayName == null || !mounted) return;

    setState(() {
      _busyMemberIds.add(member.user.id);
      _error = null;
      _notice = null;
    });
    try {
      final updated = await widget.controller.updateRoomMemberRoomDisplayName(
        roomId: _room.id,
        userId: member.user.id,
        roomDisplayName: roomDisplayName,
      );
      if (!mounted) return;
      setState(() {
        _busyMemberIds.remove(member.user.id);
        _members = member_filter.replaceRoomMember(_members, updated);
        _changed = true;
        _notice = member_filter.roomMemberRoomDisplayNameUpdatedNotice(updated);
        _markFloatingNoticeEvent('notice', _notice);
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _busyMemberIds.remove(member.user.id);
        _error = userFacingErrorMessage(error);
        _markFloatingNoticeEvent('error', _error);
      });
    }
  }

  Future<void> _transferCreator(RoomMember member) async {
    if (_busyMemberIds.contains(member.user.id)) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => _ConfirmDialog(
        title: member_filter.transferCreatorDialogTitle(),
        message: member_filter.transferCreatorConfirmBody(
          member,
          currentUserIsCreator: _room.isCreator,
        ),
        confirmLabel: member_filter.transferCreatorConfirmLabel(),
        danger: true,
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _busyMemberIds.add(member.user.id);
      _error = null;
      _notice = null;
    });
    try {
      final previousOwnerId = _room.createdBy?.id;
      final updated = await widget.controller.transferRoomCreator(
        roomId: _room.id,
        userId: member.user.id,
      );
      if (!mounted) return;
      setState(() {
        _busyMemberIds.remove(member.user.id);
        _room = updated;
        _members = _membersAfterCreatorTransfer(
          previousOwnerId: previousOwnerId,
          newOwnerId: member.user.id,
        );
        _changed = true;
        _notice = member_filter.transferCreatorSuccessNotice();
        _markFloatingNoticeEvent('notice', _notice);
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _busyMemberIds.remove(member.user.id);
        _error = userFacingErrorMessage(error);
        _markFloatingNoticeEvent('error', _error);
      });
    }
  }

  Future<void> _removeMember(RoomMember member) async {
    if (_busyMemberIds.contains(member.user.id)) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => _ConfirmDialog(
        title: member_filter.removeRoomMemberConfirmTitle(),
        message: member_filter.removeRoomMemberConfirmBody(member),
        confirmLabel: member_filter.removeRoomMemberConfirmLabel(),
        danger: true,
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _busyMemberIds.add(member.user.id);
      _error = null;
      _notice = null;
    });
    try {
      await widget.controller.removeRoomMember(
        roomId: _room.id,
        userId: member.user.id,
      );
      if (!mounted) return;
      final patch = member_filter.roomMemberRemovedSucceeded(
        room: _room,
        members: _members,
        removed: member,
        busyMemberIds: _busyMemberIds,
      );
      setState(() {
        _busyMemberIds
          ..clear()
          ..addAll(patch.busyMemberIds);
        _members = patch.members;
        _changed = patch.changed;
        _error = patch.error;
        _notice = patch.notice;
        _markFloatingNoticeEvent('error', _error);
        _markFloatingNoticeEvent('notice', _notice);
      });
    } catch (error) {
      if (!mounted) return;
      final patch = member_filter.roomMemberRemoveFailed(
        room: _room,
        members: _members,
        changed: _changed,
        userId: member.user.id,
        busyMemberIds: _busyMemberIds,
        failure: error,
      );
      setState(() {
        _busyMemberIds
          ..clear()
          ..addAll(patch.busyMemberIds);
        _error = patch.error;
        _notice = patch.notice;
        _markFloatingNoticeEvent('error', _error);
        _markFloatingNoticeEvent('notice', _notice);
      });
    }
  }

  Future<UserSummary> _resolveMemberProfile(UserSummary user) async {
    try {
      final profile = await widget.controller.getRoomMemberProfile(
        roomId: _room.id,
        userId: user.id,
      );
      return _userWithCurrentRoomCommonRoomName(
        room_display.resolvedRoomMemberProfileUser(
          profile: profile,
          fallback: user,
        ),
      );
    } catch (_) {
      return _resolveUserProfile(user);
    }
  }

  Future<UserSummary> _resolveUserProfile(UserSummary user) async {
    try {
      final profile = await widget.controller.getUserProfile(user.id);
      return _userWithCurrentRoomCommonRoomName(profile.mergeMissing(user));
    } catch (_) {
      return _userWithCurrentRoomCommonRoomName(user);
    }
  }

  UserSummary _userWithCurrentRoomCommonRoomName(UserSummary user) {
    return room_display.userWithLocalCommonRoomNames(
      user: user,
      rooms: [_room.toCard()],
    );
  }

  Future<PublicRoom> _resolveRoomProfile(PublicRoom room) async {
    final detail = await widget.controller.getRoom(room.id);
    return room_display.publicRoomFromRoomDetail(detail);
  }

  List<RoomMember> _visibleMembers() {
    final visible = member_filter.filteredRoomMembers(
      members: _effectiveMembers,
      live: _live,
      presenceFilter: _presenceFilter,
      roleFilter: _roleFilter,
      query: _memberQuery,
      ownerUserId: _room.createdBy?.id,
    );
    return member_filter.orderRoomMembersByUserIds(
      members: visible,
      orderedUserIds: _memberDisplayOrder,
    );
  }

  List<RoomMember> get _effectiveMembers =>
      member_filter.roomMembersWithCurrentUserPresence(
        _members,
        currentUserId: widget.currentUser.id,
      );

  void _refreshMemberDisplayOrder() {
    _memberDisplayOrder = member_filter
        .visibleRoomMembers(
          members: _effectiveMembers,
          live: _live,
          presenceFilter: _presenceFilter,
          roleFilter: _roleFilter,
          query: _memberQuery,
          ownerUserId: _room.createdBy?.id,
        )
        .map((member) => member.user.id)
        .toList();
  }

  List<RoomMember> _membersAfterCreatorTransfer({
    required String? previousOwnerId,
    required String newOwnerId,
  }) {
    return [
      for (final member in _members)
        if (member.user.id == newOwnerId)
          member_filter.roomMemberWithRole(member, 'owner')
        else if (previousOwnerId != null &&
            previousOwnerId != newOwnerId &&
            member.user.id == previousOwnerId)
          member_filter.roomMemberWithRole(member, 'admin')
        else
          member,
    ];
  }

  @override
  Widget build(BuildContext context) {
    final effectiveMembers = _effectiveMembers;
    final filterCounts = member_filter.roomMemberFilterCounts(
      members: effectiveMembers,
      live: _live,
      ownerUserId: _room.createdBy?.id,
    );
    var activeSection = _section;
    if (activeSection == _RoomMembersSection.newMembers &&
        !_canViewNewMembers) {
      activeSection = _RoomMembersSection.roomMembers;
    }
    if (activeSection == _RoomMembersSection.blacklist && !_canViewBlacklist) {
      activeSection = _RoomMembersSection.roomMembers;
    }
    final showJoinRequestsBadge =
        _canViewJoinRequests &&
        (widget.hasPendingJoinRequests || _requests.isNotEmpty);
    final sections = <Segment<_RoomMembersSection>>[
      const Segment(
        value: _RoomMembersSection.roomMembers,
        label: '房间成员',
        icon: Icons.groups_outlined,
      ),
      if (_canViewNewMembers)
        Segment(
          value: _RoomMembersSection.newMembers,
          label: '新成员',
          icon: Icons.person_add_alt_1,
          showBadge: showJoinRequestsBadge,
          badgeKey: ValueKey('new-members-tab-badge'),
        ),
      if (_canViewBlacklist)
        const Segment(
          value: _RoomMembersSection.blacklist,
          label: '黑名单',
          icon: Icons.block,
        ),
    ];
    return FloatingNoticeEmitter(
      notices: _floatingNotices(),
      child: _RoomDialogShell(
        title: '成员',
        icon: Icons.group_outlined,
        maxWidth: _dialogMaxWidth,
        maxHeight: _dialogMaxHeight,
        embedded: widget.embedded,
        onClose: _close,
        pinned: sections.length > 1
            ? SegmentedControl<_RoomMembersSection>(
                expanded: true,
                value: activeSection,
                onChanged: _selectSection,
                segments: sections,
              )
            : null,
        headerAction:
            _canReviewRequests ||
                (activeSection == _RoomMembersSection.blacklist &&
                    _canManageMembers)
            ? ButtonIcon(
                tooltip: '刷新',
                icon: const Icon(Icons.refresh),
                onPressed: activeSection == _RoomMembersSection.blacklist
                    ? _loadBlacklist
                    : _load,
                size: 38,
              )
            : null,
        child: switch (activeSection) {
          _RoomMembersSection.roomMembers => _buildRoomMembersBody(
            filterCounts,
          ),
          _RoomMembersSection.newMembers => _buildNewMembersBody(
            effectiveMembers,
          ),
          _RoomMembersSection.blacklist => _buildBlacklistBody(
            effectiveMembers,
          ),
        },
      ),
    );
  }

  Widget _buildRoomMembersBody(
    member_filter.RoomMemberFilterCounts filterCounts,
  ) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(22, 0, 22, 22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _MemberFilters(
            controller: _memberSearchController,
            filterCounts: filterCounts,
            presenceFilter: _presenceFilter,
            roleFilter: _roleFilter,
            onPresenceChanged: (value) {
              setState(() {
                _presenceFilter = value;
                _refreshMemberDisplayOrder();
              });
            },
            onRoleChanged: (value) {
              setState(() {
                _roleFilter = value;
                _refreshMemberDisplayOrder();
              });
            },
          ),
          const SizedBox(height: 14),
          Expanded(
            child: SizedBox.expand(
              key: const ValueKey('room-members-list'),
              child: _buildMemberList(),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNewMembersBody(List<RoomMember> effectiveMembers) {
    return SettingsList(
      children: [
        _InviteSection(
          controller: _inviteSearchController,
          currentUser: widget.currentUser,
          query: _inviteQuery,
          searching: _searchingInvites,
          results: _inviteResults,
          members: effectiveMembers,
          pendingInviteUserIds: _pendingInviteUserIds,
          busyUserIds: _busyInviteUserIds,
          error: _inviteError,
          enabled: _canInviteMembers,
          onResolveProfile: _resolveUserProfile,
          onResolveRoomProfile: _resolveRoomProfile,
          onOpenRoom: widget.onOpenRoom,
          onInvite: _invite,
        ),
        if (_canViewJoinRequests)
          _JoinRequestsSection(
            requests: _requests,
            currentUser: widget.currentUser,
            busyRequestIds: _busyRequestIds,
            activeDetailRequestId: _activeJoinRequestDetailId,
            error: _requestError,
            onResolveProfile: _resolveMemberProfile,
            onResolveRoomProfile: _resolveRoomProfile,
            onOpenRoom: widget.onOpenRoom,
            onDetail: _showJoinRequestDetails,
            onApprove: (request) => _reviewRequest(request, true),
            onReject: (request) => _reviewRequest(request, false),
          ),
      ],
    );
  }

  Widget _buildBlacklistBody(List<RoomMember> effectiveMembers) {
    return SettingsList(
      children: [
        _BlockUserSection(
          controller: _blockSearchController,
          currentUser: widget.currentUser,
          query: _blockQuery,
          searching: _searchingBlocks,
          results: _blockResults,
          members: effectiveMembers,
          blacklist: _blacklist,
          busyUserIds: _busyBlockUserIds,
          error: _blockError,
          enabled: _canManageMembers,
          onResolveProfile: _resolveUserProfile,
          onResolveRoomProfile: _resolveRoomProfile,
          onOpenRoom: widget.onOpenRoom,
          onBlock: _blockUser,
          onUnblock: _unblockUser,
        ),
        _BlacklistSection(
          entries: _blacklist,
          currentUser: widget.currentUser,
          busyUserIds: _busyBlockUserIds,
          loading: _loadingBlacklist,
          error: _blacklistError,
          onResolveProfile: _resolveUserProfile,
          onResolveRoomProfile: _resolveRoomProfile,
          onOpenRoom: widget.onOpenRoom,
          onUnblock: _unblockUser,
        ),
      ],
    );
  }

  Widget _buildMemberList() {
    if (_loading) {
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
    final members = _visibleMembers();
    if (members.isEmpty) {
      return const _EmptyState(
        icon: Icons.person_search_outlined,
        title: '没有匹配的成员',
      );
    }

    member_filter.RoomMemberPermissionState permissionFor(RoomMember member) {
      return member_filter.roomMemberPermissionState(
        member: member,
        currentUser: widget.currentUser,
        canEditCreatorOnly: _canEditCreatorOnly,
        canManageMembers: _canManageMembers,
        ownerUserId: _room.createdBy?.id,
      );
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        const rowSurfaceHorizontalPadding = 20.0;
        final rowContentWidth = constraints.maxWidth.isFinite
            ? (constraints.maxWidth - rowSurfaceHorizontalPadding)
                  .clamp(0.0, double.infinity)
                  .toDouble()
            : double.infinity;
        final stackMemberActions =
            HomeAdaptiveLayout.usesCompactLayout(context) ||
            members.any(
              (member) => _memberRowNeedsStackedActions(
                context: context,
                availableWidth: rowContentWidth,
                member: member,
                live: _live,
                permission: permissionFor(member),
                ownerUserId: _room.createdBy?.id,
              ),
            );
        return ListView.separated(
          padding: EdgeInsets.zero,
          itemCount: members.length,
          separatorBuilder: (context, index) => const SizedBox(height: 8),
          itemBuilder: (context, index) {
            final member = members[index];
            final permission = permissionFor(member);
            final busy = _busyMemberIds.contains(member.user.id);
            final canMoveActions = _memberActionCount(permission) > 0;
            return _MemberRow(
              member: member,
              currentUser: widget.currentUser,
              live: _live,
              permission: permission,
              stackActions: stackMemberActions,
              nameMaxLines:
                  (stackMemberActions || !canMoveActions) &&
                      _memberRowNeedsTwoLineName(
                        context: context,
                        availableWidth: rowContentWidth,
                        member: member,
                        live: _live,
                        ownerUserId: _room.createdBy?.id,
                        busy: busy,
                      )
                  ? 2
                  : 1,
              ownerUserId: _room.createdBy?.id,
              query: _memberQuery,
              busy: busy,
              onResolveProfile: _resolveMemberProfile,
              onResolveRoomProfile: _resolveRoomProfile,
              onOpenRoom: widget.onOpenRoom,
              onEditRoomDisplayName: () => _editRoomDisplayName(member),
              onSetAdmin: () => _setMemberRole(member, 'admin'),
              onUnsetAdmin: () => _setMemberRole(member, 'member'),
              onRemoveMember: () => _removeMember(member),
              onTransferCreator: () => _transferCreator(member),
            );
          },
        );
      },
    );
  }
}
