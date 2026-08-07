part of 'room_management.dart';

class _MemberFilters extends StatelessWidget {
  const _MemberFilters({
    required this.controller,
    required this.filterCounts,
    required this.presenceFilter,
    required this.roleFilter,
    required this.onPresenceChanged,
    required this.onRoleChanged,
  });

  final TextEditingController controller;
  final member_filter.RoomMemberFilterCounts filterCounts;
  final member_filter.RoomMemberPresenceFilter presenceFilter;
  final member_filter.RoomMemberRoleFilter roleFilter;
  final ValueChanged<member_filter.RoomMemberPresenceFilter> onPresenceChanged;
  final ValueChanged<member_filter.RoomMemberRoleFilter> onRoleChanged;

  @override
  Widget build(BuildContext context) {
    final presenceSegments = <Segment<member_filter.RoomMemberPresenceFilter>>[
      Segment(
        value: member_filter.RoomMemberPresenceFilter.all,
        label: member_filter.roomMemberPresenceFilterLabel(
          member_filter.RoomMemberPresenceFilter.all,
          filterCounts,
        ),
      ),
      Segment(
        value: member_filter.RoomMemberPresenceFilter.live,
        label: member_filter.roomMemberPresenceFilterLabel(
          member_filter.RoomMemberPresenceFilter.live,
          filterCounts,
        ),
      ),
      Segment(
        value: member_filter.RoomMemberPresenceFilter.online,
        label: member_filter.roomMemberPresenceFilterLabel(
          member_filter.RoomMemberPresenceFilter.online,
          filterCounts,
        ),
      ),
      Segment(
        value: member_filter.RoomMemberPresenceFilter.offline,
        label: member_filter.roomMemberPresenceFilterLabel(
          member_filter.RoomMemberPresenceFilter.offline,
          filterCounts,
        ),
      ),
    ];
    final roleSegments = <Segment<member_filter.RoomMemberRoleFilter>>[
      Segment(
        value: member_filter.RoomMemberRoleFilter.all,
        label: member_filter.roomMemberRoleFilterLabel(
          member_filter.RoomMemberRoleFilter.all,
          filterCounts,
        ),
      ),
      Segment(
        value: member_filter.RoomMemberRoleFilter.member,
        label: member_filter.roomMemberRoleFilterLabel(
          member_filter.RoomMemberRoleFilter.member,
          filterCounts,
        ),
      ),
      Segment(
        value: member_filter.RoomMemberRoleFilter.admin,
        label: member_filter.roomMemberRoleFilterLabel(
          member_filter.RoomMemberRoleFilter.admin,
          filterCounts,
        ),
      ),
      Segment(
        value: member_filter.RoomMemberRoleFilter.creator,
        label: member_filter.roomMemberRoleFilterLabel(
          member_filter.RoomMemberRoleFilter.creator,
          filterCounts,
        ),
      ),
    ];
    final presence = SegmentedControl<member_filter.RoomMemberPresenceFilter>(
      key: const ValueKey('member-presence-filter'),
      expanded: true,
      value: presenceFilter,
      onChanged: onPresenceChanged,
      segments: presenceSegments,
    );
    final roles = SegmentedControl<member_filter.RoomMemberRoleFilter>(
      key: const ValueKey('member-role-filter'),
      expanded: true,
      value: roleFilter,
      onChanged: onRoleChanged,
      segments: roleSegments,
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        final narrow = HomeAdaptiveLayout.usesCompactLayout(context);
        final pairedControlWidth = constraints.maxWidth.isFinite
            ? ((constraints.maxWidth - 10) / 2)
                  .clamp(0.0, double.infinity)
                  .toDouble()
            : double.infinity;
        final stackBeforeOverflow =
            SegmentedControl.minimumWidthFor(context, presenceSegments) >
                pairedControlWidth ||
            SegmentedControl.minimumWidthFor(context, roleSegments) >
                pairedControlWidth;
        final stackFilters = narrow || stackBeforeOverflow;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Input(
              controller: controller,
              hintText: '搜索成员',
              prefixIcon: Icons.search,
              showClearButton: true,
            ),
            const SizedBox(height: 10),
            if (stackFilters) ...[
              presence,
              const SizedBox(height: 10),
              roles,
            ] else
              Row(
                children: [
                  Expanded(child: presence),
                  const SizedBox(width: 10),
                  Expanded(child: roles),
                ],
              ),
          ],
        );
      },
    );
  }
}

class _MemberRow extends StatelessWidget {
  const _MemberRow({
    required this.member,
    required this.currentUser,
    required this.live,
    required this.permission,
    required this.stackActions,
    required this.nameMaxLines,
    required this.ownerUserId,
    required this.query,
    required this.busy,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onOpenRoom,
    required this.onEditRoomDisplayName,
    required this.onSetAdmin,
    required this.onUnsetAdmin,
    required this.onRemoveMember,
    required this.onTransferCreator,
  });

  final RoomMember member;
  final CurrentUser currentUser;
  final LiveState live;
  final member_filter.RoomMemberPermissionState permission;
  final bool stackActions;
  final int nameMaxLines;
  final String? ownerUserId;
  final String query;
  final bool busy;
  final Future<UserSummary> Function(UserSummary user) onResolveProfile;
  final RoomProfileResolver onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;
  final VoidCallback onEditRoomDisplayName;
  final VoidCallback onSetAdmin;
  final VoidCallback onUnsetAdmin;
  final VoidCallback onRemoveMember;
  final VoidCallback onTransferCreator;

  @override
  Widget build(BuildContext context) {
    final presence = member_filter.roomMemberPresence(member, live: live);
    final role = room_display.roomRoleLabel(
      member.user,
      ownerUserId: ownerUserId,
    );
    final avatar = Avatar(
      label: room_display.userAvatarLabel(member.user),
      imageUrl: AppConfigScope.of(
        context,
      ).resolveAssetUrl(member.user.avatarUrl),
      defaultAvatarKey: member.user.defaultAvatarKey,
      size: 38,
    );
    final actionCount = _memberActionCount(permission);
    final hasActions = actionCount > 0;
    final actions = SizedBox(
      width: _memberActionsWidth(actionCount),
      child: Wrap(
        key: ValueKey('member-action-wrap-${member.user.id}'),
        spacing: 6,
        runSpacing: 8,
        alignment: WrapAlignment.end,
        children: [
          if (permission.canEditRoomDisplayName)
            ButtonIcon(
              tooltip: '修改房间内用户名',
              icon: const Icon(Icons.edit_outlined),
              onPressed: onEditRoomDisplayName,
              size: 34,
            ),
          if (permission.canRoleEdit)
            ButtonIcon(
              tooltip: permission.isAdmin ? '移除管理员' : '设为管理员',
              icon: Icon(
                permission.isAdmin
                    ? Icons.admin_panel_settings_outlined
                    : Icons.admin_panel_settings,
              ),
              selected: permission.isAdmin,
              onPressed: permission.isAdmin ? onUnsetAdmin : onSetAdmin,
              size: 34,
            ),
          if (permission.canRemoveMember)
            ButtonIcon(
              tooltip: '踢出此用户',
              icon: const Icon(Icons.person_remove_outlined),
              tone: ButtonTone.danger,
              onPressed: onRemoveMember,
              size: 34,
            ),
          if (permission.canRoleEdit)
            ButtonIcon(
              tooltip: '转让创建者',
              icon: const Icon(Icons.swap_horiz),
              tone: ButtonTone.danger,
              onPressed: onTransferCreator,
              size: 34,
            ),
        ],
      ),
    );
    return _RowSurface(
      child: Builder(
        builder: (context) {
          final information = ConstrainedBox(
            key: ValueKey('member-information-${member.user.id}'),
            constraints: const BoxConstraints(minHeight: 48),
            child: Row(
              children: [
                UserHoverCard(
                  user: member.user,
                  currentUser: currentUser,
                  onResolveProfile: onResolveProfile,
                  onResolveRoomProfile: onResolveRoomProfile,
                  onEnterCommonRoom: onOpenRoom,
                  inLive: presence == member_filter.RoomMemberPresence.live,
                  showRoomRole: true,
                  child: avatar,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      AdaptiveHighlightedText(
                        text: member_filter.roomMemberDisplayName(member),
                        query: query,
                        comfortableLines: nameMaxLines,
                        style: UiTypography.body.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '@${member.user.username}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: UiTypography.label.copyWith(
                          color: UiColors.textMuted,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 10),
                PresencePill.member(
                  presence,
                  key: ValueKey('member-presence-${member.user.id}'),
                ),
                const SizedBox(width: 6),
                RoleBadge(
                  key: ValueKey('member-role-${member.user.id}'),
                  label: role,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 5,
                  ),
                ),
                if (busy) ...[
                  const SizedBox(width: 10),
                  const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(
                      color: UiColors.accent,
                      strokeWidth: 2,
                    ),
                  ),
                ],
              ],
            ),
          );
          if (stackActions && !busy && hasActions) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                information,
                const SizedBox(height: 8),
                Align(alignment: Alignment.centerRight, child: actions),
              ],
            );
          }
          return Row(
            children: [
              Expanded(child: information),
              if (!busy && hasActions) ...[const SizedBox(width: 8), actions],
            ],
          );
        },
      ),
    );
  }
}

bool _memberRowNeedsStackedActions({
  required BuildContext context,
  required double availableWidth,
  required RoomMember member,
  required LiveState live,
  required member_filter.RoomMemberPermissionState permission,
  required String? ownerUserId,
}) {
  final actionCount = _memberActionCount(permission);
  if (actionCount == 0) return false;

  final presence = member_filter.roomMemberPresence(member, live: live);
  final role = room_display.roomRoleLabel(
    member.user,
    ownerUserId: ownerUserId,
  );
  final displayNameStyle = UiTypography.body.copyWith(
    fontWeight: FontWeight.w600,
  );
  final usernameStyle = UiTypography.label.copyWith(
    color: UiColors.textMuted,
    fontSize: 12,
  );
  final presenceStyle = UiTypography.label.copyWith(
    fontWeight: FontWeight.w600,
  );
  final roleStyle = UiTypography.label.copyWith(
    fontSize: 12,
    fontWeight: FontWeight.w600,
  );
  final displayNameWidth = _memberRowTextWidth(
    context,
    member_filter.roomMemberDisplayName(member),
    displayNameStyle,
  );
  final usernameWidth = _memberRowTextWidth(
    context,
    '@${member.user.username}',
    usernameStyle,
  );
  final identityWidth = displayNameWidth > usernameWidth
      ? displayNameWidth
      : usernameWidth;
  final displayNameGuardWidth = _memberRowTextWidth(
    context,
    '…',
    displayNameStyle,
  );
  final usernameGuardWidth = _memberRowTextWidth(context, '…', usernameStyle);
  final truncationGuardWidth = displayNameGuardWidth > usernameGuardWidth
      ? displayNameGuardWidth
      : usernameGuardWidth;
  final presenceWidth =
      2 +
      18 +
      10 +
      6 +
      _memberRowTextWidth(
        context,
        member_filter.roomMemberPresenceLabel(presence),
        presenceStyle,
      );
  final roleWidth = 2 + 16 + _memberRowTextWidth(context, role, roleStyle);
  final actionsWidth = _memberActionsWidth(actionCount);
  const measurementSafety = 2.0;
  final requiredWidth =
      38 +
      10 +
      identityWidth +
      10 +
      presenceWidth +
      6 +
      roleWidth +
      8 +
      actionsWidth +
      truncationGuardWidth +
      measurementSafety;
  return requiredWidth > availableWidth;
}

bool _memberRowNeedsTwoLineName({
  required BuildContext context,
  required double availableWidth,
  required RoomMember member,
  required LiveState live,
  required String? ownerUserId,
  required bool busy,
}) {
  final presence = member_filter.roomMemberPresence(member, live: live);
  final role = room_display.roomRoleLabel(
    member.user,
    ownerUserId: ownerUserId,
  );
  final displayNameStyle = UiTypography.body.copyWith(
    fontWeight: FontWeight.w600,
  );
  final presenceStyle = UiTypography.label.copyWith(
    fontWeight: FontWeight.w600,
  );
  final roleStyle = UiTypography.label.copyWith(
    fontSize: 12,
    fontWeight: FontWeight.w600,
  );
  final presenceWidth =
      2 +
      18 +
      10 +
      6 +
      _memberRowTextWidth(
        context,
        member_filter.roomMemberPresenceLabel(presence),
        presenceStyle,
      );
  final roleWidth = 2 + 16 + _memberRowTextWidth(context, role, roleStyle);
  final requiredWidth =
      38 +
      10 +
      _memberRowTextWidth(
        context,
        member_filter.roomMemberDisplayName(member),
        displayNameStyle,
      ) +
      _memberRowTextWidth(context, '…', displayNameStyle) +
      10 +
      presenceWidth +
      6 +
      roleWidth +
      (busy ? 10 + 18 : 0) +
      2;
  return requiredWidth > availableWidth;
}

int _memberActionCount(member_filter.RoomMemberPermissionState permission) {
  return (permission.canEditRoomDisplayName ? 1 : 0) +
      (permission.canRoleEdit ? 2 : 0) +
      (permission.canRemoveMember ? 1 : 0);
}

double _memberActionsWidth(int actionCount) {
  if (actionCount <= 0) return 0;
  return actionCount * 34 + (actionCount - 1) * 6;
}

double _memberRowTextWidth(BuildContext context, String text, TextStyle style) {
  final painter = TextPainter(
    text: TextSpan(text: text, style: style),
    maxLines: 1,
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
  )..layout();
  return painter.width;
}

class _EditRoomDisplayNameDialog extends StatefulWidget {
  const _EditRoomDisplayNameDialog({required this.member});

  final RoomMember member;

  @override
  State<_EditRoomDisplayNameDialog> createState() =>
      _EditRoomDisplayNameDialogState();
}

class _EditRoomDisplayNameDialogState
    extends State<_EditRoomDisplayNameDialog> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(
      text: member_filter.roomMemberRoomDisplayNameValue(widget.member),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final defaultName = member_filter.roomMemberDefaultDisplayName(
      widget.member,
    );
    final originalName = member_filter.roomMemberRoomDisplayNameOriginalLabel(
      widget.member,
    );
    return DialogFrame(
      title: '修改$defaultName的房间内用户名',
      icon: Icons.edit_outlined,
      adaptiveActions: [
        ResponsiveDialogAction(
          label: '取消',
          onPressed: () => Navigator.of(context).pop(),
        ),
        ResponsiveDialogAction(
          label: '确认修改',
          tone: ButtonTone.primary,
          onPressed: () => Navigator.of(context).pop(_controller.text),
        ),
      ],
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Input(controller: _controller, hintText: defaultName),
          const SizedBox(height: UiSpacing.xs),
          Text(
            '原名称：$originalName',
            style: UiTypography.label.copyWith(color: UiColors.textMuted),
          ),
        ],
      ),
    );
  }
}

class _InviteSection extends StatelessWidget {
  const _InviteSection({
    required this.controller,
    required this.currentUser,
    required this.query,
    required this.searching,
    required this.results,
    required this.members,
    required this.pendingInviteUserIds,
    required this.busyUserIds,
    required this.error,
    required this.enabled,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onOpenRoom,
    required this.onInvite,
  });

  final TextEditingController controller;
  final CurrentUser currentUser;
  final String query;
  final bool searching;
  final List<UserSummary> results;
  final List<RoomMember> members;
  final Set<String> pendingInviteUserIds;
  final Set<String> busyUserIds;
  final String? error;
  final bool enabled;
  final Future<UserSummary> Function(UserSummary user) onResolveProfile;
  final RoomProfileResolver onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;
  final ValueChanged<UserSummary> onInvite;

  @override
  Widget build(BuildContext context) {
    final candidates = room_invites.roomInviteCandidates(
      searchResults: results,
      members: members,
      query: query,
      pendingInviteUserIds: pendingInviteUserIds,
      busyUserIds: busyUserIds,
      invitesEnabled: enabled,
    );
    return SettingsCard(
      title: '邀请成员',
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Input(
              controller: controller,
              hintText: '按用户名、昵称或 UID 搜索',
              prefixIcon: Icons.person_add_alt_1,
              enabled: enabled,
              showClearButton: true,
            ),
            if (searching) ...[
              const SizedBox(height: 10),
              const LinearProgressIndicator(
                minHeight: 2,
                color: UiColors.accent,
                backgroundColor: UiColors.surfacePressed,
              ),
            ] else if (enabled && query.length >= 2) ...[
              const SizedBox(height: 8),
              if (candidates.isEmpty)
                Text(
                  '未找到用户',
                  style: UiTypography.label.copyWith(color: UiColors.textMuted),
                )
              else
                for (final candidate in candidates) ...[
                  _InviteUserRow(
                    user: candidate.user,
                    currentUser: currentUser,
                    query: query,
                    alreadyMember: candidate.existing,
                    pending: candidate.pending,
                    busy: candidate.busy,
                    enabled: enabled,
                    onResolveProfile: onResolveProfile,
                    onResolveRoomProfile: onResolveRoomProfile,
                    onOpenRoom: onOpenRoom,
                    onInvite: () => onInvite(candidate.user),
                  ),
                  if (candidate != candidates.last) const SizedBox(height: 6),
                ],
            ],
          ],
        ),
      ],
    );
  }
}

class _InviteUserRow extends StatelessWidget {
  const _InviteUserRow({
    required this.user,
    required this.currentUser,
    required this.query,
    required this.alreadyMember,
    required this.pending,
    required this.busy,
    required this.enabled,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onOpenRoom,
    required this.onInvite,
  });

  final UserSummary user;
  final CurrentUser currentUser;
  final String query;
  final bool alreadyMember;
  final bool pending;
  final bool busy;
  final bool enabled;
  final Future<UserSummary> Function(UserSummary user) onResolveProfile;
  final RoomProfileResolver onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;
  final VoidCallback onInvite;

  @override
  Widget build(BuildContext context) {
    final actionLabel = alreadyMember ? '在房间内' : (pending ? '已邀请' : '邀请');
    return _RowSurface(
      compact: true,
      child: Row(
        children: [
          UserHoverCard(
            user: user,
            currentUser: currentUser,
            onResolveProfile: onResolveProfile,
            onResolveRoomProfile: onResolveRoomProfile,
            onEnterCommonRoom: onOpenRoom,
            child: Avatar(
              label: room_display.userAvatarLabel(user),
              imageUrl: AppConfigScope.of(
                context,
              ).resolveAssetUrl(user.avatarUrl),
              defaultAvatarKey: user.defaultAvatarKey,
              size: 32,
            ),
          ),
          const SizedBox(width: 9),
          Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                HighlightedText(
                  text: room_display.userPrimaryName(user),
                  query: query,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.label.copyWith(
                    color: UiColors.text,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                HighlightedText(
                  text: room_display.userUsernameLabel(user),
                  query: query,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.label.copyWith(color: UiColors.textMuted),
                ),
              ],
            ),
          ),
          Button(
            height: 34,
            loading: busy,
            onPressed: !enabled || alreadyMember || pending ? null : onInvite,
            child: Text(actionLabel),
          ),
        ],
      ),
    );
  }
}

class _BlockUserSection extends StatelessWidget {
  const _BlockUserSection({
    required this.controller,
    required this.currentUser,
    required this.query,
    required this.searching,
    required this.results,
    required this.members,
    required this.blacklist,
    required this.busyUserIds,
    required this.error,
    required this.enabled,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onOpenRoom,
    required this.onBlock,
    required this.onUnblock,
  });

  final TextEditingController controller;
  final CurrentUser currentUser;
  final String query;
  final bool searching;
  final List<UserSummary> results;
  final List<RoomMember> members;
  final List<RoomBlacklistEntry> blacklist;
  final Set<String> busyUserIds;
  final String? error;
  final bool enabled;
  final Future<UserSummary> Function(UserSummary user) onResolveProfile;
  final RoomProfileResolver onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;
  final ValueChanged<UserSummary> onBlock;
  final ValueChanged<UserSummary> onUnblock;

  @override
  Widget build(BuildContext context) {
    final candidates = room_blacklist.roomBlacklistCandidates(
      searchResults: results,
      blacklist: blacklist,
      members: members,
      query: query,
      busyUserIds: busyUserIds,
    );
    return SettingsCard(
      title: '拉黑用户',
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Input(
              controller: controller,
              hintText: '按用户名、昵称或 UID 搜索',
              prefixIcon: Icons.block,
              enabled: enabled,
              showClearButton: true,
              textInputAction: TextInputAction.search,
            ),
            if (searching) ...[
              const SizedBox(height: 10),
              const LinearProgressIndicator(
                minHeight: 2,
                color: UiColors.accent,
                backgroundColor: UiColors.surfacePressed,
              ),
            ] else if (enabled && query.length >= 2) ...[
              const SizedBox(height: 8),
              if (candidates.isEmpty)
                Text(
                  '未找到用户',
                  style: UiTypography.label.copyWith(color: UiColors.textMuted),
                )
              else
                for (final candidate in candidates) ...[
                  _BlockUserRow(
                    user: candidate.user,
                    currentUser: currentUser,
                    query: query,
                    member: candidate.member,
                    blocked: candidate.blocked,
                    superuser: candidate.superuser,
                    busy: candidate.busy,
                    enabled: enabled,
                    onResolveProfile: onResolveProfile,
                    onResolveRoomProfile: onResolveRoomProfile,
                    onOpenRoom: onOpenRoom,
                    onBlock: () => onBlock(candidate.user),
                    onUnblock: () => onUnblock(candidate.user),
                  ),
                  if (candidate != candidates.last) const SizedBox(height: 6),
                ],
            ],
          ],
        ),
      ],
    );
  }
}

class _BlockUserRow extends StatelessWidget {
  const _BlockUserRow({
    required this.user,
    required this.currentUser,
    required this.query,
    required this.member,
    required this.blocked,
    required this.superuser,
    required this.busy,
    required this.enabled,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onOpenRoom,
    required this.onBlock,
    required this.onUnblock,
  });

  final UserSummary user;
  final CurrentUser currentUser;
  final String query;
  final bool member;
  final bool blocked;
  final bool superuser;
  final bool busy;
  final bool enabled;
  final Future<UserSummary> Function(UserSummary user) onResolveProfile;
  final RoomProfileResolver onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;
  final VoidCallback onBlock;
  final VoidCallback onUnblock;

  @override
  Widget build(BuildContext context) {
    final actionLabel = room_blacklist.roomBlacklistActionLabel(
      member: member,
      superuser: superuser,
      blocked: blocked,
    );
    final onPressed = !enabled || member || superuser
        ? null
        : blocked
        ? onUnblock
        : onBlock;
    return _RowSurface(
      compact: true,
      child: Row(
        children: [
          UserHoverCard(
            user: user,
            currentUser: currentUser,
            onResolveProfile: onResolveProfile,
            onResolveRoomProfile: onResolveRoomProfile,
            onEnterCommonRoom: onOpenRoom,
            child: Avatar(
              label: room_display.userAvatarLabel(user),
              imageUrl: AppConfigScope.of(
                context,
              ).resolveAssetUrl(user.avatarUrl),
              defaultAvatarKey: user.defaultAvatarKey,
              size: 32,
            ),
          ),
          const SizedBox(width: 9),
          Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                HighlightedText(
                  text: room_display.userPrimaryName(user),
                  query: query,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.label.copyWith(
                    color: UiColors.text,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                HighlightedText(
                  text: room_display.userUsernameLabel(user),
                  query: query,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.label.copyWith(color: UiColors.textMuted),
                ),
              ],
            ),
          ),
          Button(
            height: 34,
            loading: busy,
            tone: blocked ? ButtonTone.neutral : ButtonTone.danger,
            onPressed: onPressed,
            child: Text(actionLabel),
          ),
        ],
      ),
    );
  }
}

class _BlacklistSection extends StatelessWidget {
  const _BlacklistSection({
    required this.entries,
    required this.currentUser,
    required this.busyUserIds,
    required this.loading,
    required this.error,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onOpenRoom,
    required this.onUnblock,
  });

  final List<RoomBlacklistEntry> entries;
  final CurrentUser currentUser;
  final Set<String> busyUserIds;
  final bool loading;
  final String? error;
  final Future<UserSummary> Function(UserSummary user) onResolveProfile;
  final RoomProfileResolver onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;
  final ValueChanged<UserSummary> onUnblock;

  @override
  Widget build(BuildContext context) {
    return SettingsCard(
      title: '黑名单',
      children: [
        ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 64),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (loading) ...[
                const SizedBox(height: 8),
                const LinearProgressIndicator(
                  minHeight: 2,
                  color: UiColors.accent,
                  backgroundColor: UiColors.surfacePressed,
                ),
              ] else if (entries.isEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 12, bottom: 6),
                  child: Text(
                    '暂无黑名单用户',
                    style: UiTypography.label.copyWith(
                      color: UiColors.textMuted,
                    ),
                  ),
                )
              else
                for (final entry in entries) ...[
                  _BlacklistUserRow(
                    user: entry.user,
                    currentUser: currentUser,
                    busy: busyUserIds.contains(entry.user.id),
                    onResolveProfile: onResolveProfile,
                    onResolveRoomProfile: onResolveRoomProfile,
                    onOpenRoom: onOpenRoom,
                    onUnblock: () => onUnblock(entry.user),
                  ),
                  if (entry != entries.last) const SizedBox(height: 6),
                ],
            ],
          ),
        ),
      ],
    );
  }
}

class _BlacklistUserRow extends StatelessWidget {
  const _BlacklistUserRow({
    required this.user,
    required this.currentUser,
    required this.busy,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onOpenRoom,
    required this.onUnblock,
  });

  final UserSummary user;
  final CurrentUser currentUser;
  final bool busy;
  final Future<UserSummary> Function(UserSummary user) onResolveProfile;
  final RoomProfileResolver onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;
  final VoidCallback onUnblock;

  @override
  Widget build(BuildContext context) {
    return _RowSurface(
      compact: true,
      child: Row(
        children: [
          UserHoverCard(
            user: user,
            currentUser: currentUser,
            onResolveProfile: onResolveProfile,
            onResolveRoomProfile: onResolveRoomProfile,
            onEnterCommonRoom: onOpenRoom,
            child: Avatar(
              label: room_display.userAvatarLabel(user),
              imageUrl: AppConfigScope.of(
                context,
              ).resolveAssetUrl(user.avatarUrl),
              defaultAvatarKey: user.defaultAvatarKey,
              size: 32,
            ),
          ),
          const SizedBox(width: 9),
          Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  room_display.userPrimaryName(user),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.label.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  room_display.userUsernameLabel(user),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.label.copyWith(color: UiColors.textMuted),
                ),
              ],
            ),
          ),
          Button(
            height: 34,
            loading: busy,
            onPressed: onUnblock,
            child: const Text('取消拉黑'),
          ),
        ],
      ),
    );
  }
}

class _JoinRequestsSection extends StatelessWidget {
  const _JoinRequestsSection({
    required this.requests,
    required this.currentUser,
    required this.busyRequestIds,
    required this.activeDetailRequestId,
    required this.error,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onOpenRoom,
    required this.onDetail,
    required this.onApprove,
    required this.onReject,
  });

  final List<JoinRequest> requests;
  final CurrentUser currentUser;
  final Set<String> busyRequestIds;
  final String? activeDetailRequestId;
  final String? error;
  final Future<UserSummary> Function(UserSummary user) onResolveProfile;
  final RoomProfileResolver onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;
  final ValueChanged<JoinRequest> onDetail;
  final ValueChanged<JoinRequest> onApprove;
  final ValueChanged<JoinRequest> onReject;

  @override
  Widget build(BuildContext context) {
    return SettingsCard(
      title: '加入申请',
      children: [
        ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 64),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (requests.isEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 12, bottom: 6),
                  child: Text(
                    '暂无待处理申请',
                    style: UiTypography.label.copyWith(
                      color: UiColors.textMuted,
                    ),
                  ),
                )
              else
                for (final request in requests) ...[
                  _JoinRequestRow(
                    request: request,
                    currentUser: currentUser,
                    busy: busyRequestIds.contains(request.id),
                    detailActive: activeDetailRequestId == request.id,
                    onResolveProfile: onResolveProfile,
                    onResolveRoomProfile: onResolveRoomProfile,
                    onOpenRoom: onOpenRoom,
                    onDetail: () => onDetail(request),
                    onApprove: () => onApprove(request),
                    onReject: () => onReject(request),
                  ),
                  if (request != requests.last) const SizedBox(height: 6),
                ],
            ],
          ),
        ),
      ],
    );
  }
}

class _JoinRequestRow extends StatelessWidget {
  const _JoinRequestRow({
    required this.request,
    required this.currentUser,
    required this.busy,
    required this.detailActive,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onOpenRoom,
    required this.onDetail,
    required this.onApprove,
    required this.onReject,
  });

  final JoinRequest request;
  final CurrentUser currentUser;
  final bool busy;
  final bool detailActive;
  final Future<UserSummary> Function(UserSummary user) onResolveProfile;
  final RoomProfileResolver onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;
  final VoidCallback onDetail;
  final VoidCallback onApprove;
  final VoidCallback onReject;

  @override
  Widget build(BuildContext context) {
    return _RowSurface(
      compact: true,
      child: Row(
        children: [
          UserHoverCard(
            user: request.user,
            currentUser: currentUser,
            onResolveProfile: onResolveProfile,
            onResolveRoomProfile: onResolveRoomProfile,
            onEnterCommonRoom: onOpenRoom,
            child: Avatar(
              label: room_display.userAvatarLabel(request.user),
              imageUrl: AppConfigScope.of(
                context,
              ).resolveAssetUrl(request.user.avatarUrl),
              defaultAvatarKey: request.user.defaultAvatarKey,
              size: 32,
            ),
          ),
          const SizedBox(width: 9),
          Expanded(
            child: Text(
              room_display.userPrimaryName(request.user),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: UiTypography.label.copyWith(fontWeight: FontWeight.w600),
            ),
          ),
          if (busy)
            const SizedBox.square(
              dimension: 18,
              child: CircularProgressIndicator(
                color: UiColors.accent,
                strokeWidth: 2,
              ),
            )
          else ...[
            ButtonIcon(
              tooltip: '详情',
              icon: const Icon(Icons.info_outline),
              selected: detailActive,
              onPressed: onDetail,
              size: 32,
            ),
            const SizedBox(width: 6),
            ButtonIcon(
              tooltip: '拒绝',
              icon: const Icon(Icons.close),
              tone: ButtonTone.danger,
              onPressed: onReject,
              size: 32,
            ),
            const SizedBox(width: 6),
            ButtonIcon(
              tooltip: '通过',
              icon: const Icon(Icons.check),
              tone: ButtonTone.primary,
              onPressed: onApprove,
              size: 32,
            ),
          ],
        ],
      ),
    );
  }
}

class _JoinRequestDetailsDialog extends StatelessWidget {
  const _JoinRequestDetailsDialog({
    required this.request,
    required this.currentUser,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onOpenRoom,
  });

  final JoinRequest request;
  final CurrentUser currentUser;
  final Future<UserSummary> Function(UserSummary user) onResolveProfile;
  final RoomProfileResolver onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;

  @override
  Widget build(BuildContext context) {
    return DialogFrame(
      title: '申请详情',
      icon: Icons.fact_check_outlined,
      actions: [
        Button(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('关闭'),
        ),
      ],
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _JoinRequestDetailBlock(
            label: '来源',
            child: _JoinRequestSourceContent(
              request: request,
              currentUser: currentUser,
              onResolveProfile: onResolveProfile,
              onResolveRoomProfile: onResolveRoomProfile,
              onOpenRoom: onOpenRoom,
            ),
          ),
          const SizedBox(height: 14),
          _JoinRequestDetailBlock(
            label: '申请理由',
            child: Text(
              room_join_requests.joinRequestDetailReasonText(request),
              style: UiTypography.body,
            ),
          ),
        ],
      ),
    );
  }
}

class _JoinRequestDetailBlock extends StatelessWidget {
  const _JoinRequestDetailBlock({required this.label, required this.child});

  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          label,
          style: UiTypography.label.copyWith(
            color: UiColors.textMuted,
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 6),
        child,
      ],
    );
  }
}

class _JoinRequestSourceContent extends StatelessWidget {
  const _JoinRequestSourceContent({
    required this.request,
    required this.currentUser,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onOpenRoom,
  });

  final JoinRequest request;
  final CurrentUser currentUser;
  final Future<UserSummary> Function(UserSummary user) onResolveProfile;
  final RoomProfileResolver onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;

  @override
  Widget build(BuildContext context) {
    if (!room_join_requests.joinRequestFromInvitation(request) ||
        request.inviters.isEmpty) {
      return Text(
        room_join_requests.joinRequestSourceText(request),
        style: UiTypography.body.copyWith(fontWeight: FontWeight.w600),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final inviter in request.inviters) ...[
          _JoinRequestInviterSourceLine(
            user: inviter,
            currentUser: currentUser,
            onResolveProfile: onResolveProfile,
            onResolveRoomProfile: onResolveRoomProfile,
            onOpenRoom: onOpenRoom,
          ),
          if (inviter != request.inviters.last) const SizedBox(height: 6),
        ],
      ],
    );
  }
}

class _JoinRequestInviterSourceLine extends StatelessWidget {
  const _JoinRequestInviterSourceLine({
    required this.user,
    required this.currentUser,
    required this.onResolveProfile,
    required this.onResolveRoomProfile,
    required this.onOpenRoom,
  });

  final UserSummary user;
  final CurrentUser currentUser;
  final Future<UserSummary> Function(UserSummary user) onResolveProfile;
  final RoomProfileResolver onResolveRoomProfile;
  final ValueChanged<PublicRoom>? onOpenRoom;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        UserHoverCard(
          user: user,
          currentUser: currentUser,
          onResolveProfile: onResolveProfile,
          onResolveRoomProfile: onResolveRoomProfile,
          onEnterCommonRoom: onOpenRoom,
          child: Avatar(
            label: room_display.userAvatarLabel(user),
            imageUrl: AppConfigScope.of(
              context,
            ).resolveAssetUrl(user.avatarUrl),
            defaultAvatarKey: user.defaultAvatarKey,
            size: 28,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            '${room_display.userPrimaryName(user)} 的邀请',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: UiTypography.body.copyWith(fontWeight: FontWeight.w600),
          ),
        ),
      ],
    );
  }
}
